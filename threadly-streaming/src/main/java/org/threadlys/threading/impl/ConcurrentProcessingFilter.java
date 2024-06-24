package org.threadlys.threading.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@ConditionalOnClass(HttpServletRequest.class)
@Component
@Order(ConcurrentProcessingFilter.ORDER)
@Slf4j
public class ConcurrentProcessingFilter extends OncePerRequestFilter {

    public static final String THREADLY_HEADER_KEY = "threadly";

    static final int ORDER = -103;

    @Autowired
    private ThreadlyStreamingConfiguration threadlyStreamingConfiguration;

    @Autowired
    private ForkJoinPoolGuard forkJoinPoolGuard;

    private volatile boolean shutdown;

    private final List<ForkJoinPool> fjps = new ArrayList<>();

    @Override
    public void destroy() {
        synchronized (fjps) {
            if (shutdown) {
                return;
            }
            shutdown = true;
            for (int a = fjps.size(); a-- > 0; ) {
                fjps.get(a)
                        .shutdownNow();
            }
            fjps.clear();
        }
        super.destroy();
    }

    protected ForkJoinPool acquireForkJoinPool() {
        synchronized (fjps) {
            if (shutdown) {
                throw new RejectedExecutionException();
            }
            while (true) {
                if (fjps.isEmpty()) {
                    return forkJoinPoolGuard.createForkJoinPool();
                }
                var fjp = fjps.remove(fjps.size() - 1);
                if (!fjp.isShutdown()) {
                    return fjp;
                }
            }
        }
    }

    protected void releaseForkJoinPool(ForkJoinPool fjp) {
        synchronized (fjps) {
            if (fjp.isShutdown()) {
                return;
            }
            fjps.add(fjp);
        }
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, jakarta.servlet.FilterChain filterChain)
            throws jakarta.servlet.ServletException, IOException {
        threadlyStreamingConfiguration.applyThreadlyStreamingConfiguration(getThreadlyStreamingConfigurationFromHeader(request.getHeader(THREADLY_HEADER_KEY)));

        if (!threadlyStreamingConfiguration.isFilterActive()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (Boolean.TRUE.equals(threadlyStreamingConfiguration.getPoolPerRequest())) {
            var fjp = acquireForkJoinPool();
            var revert = forkJoinPoolGuard.pushForkJoinPool(fjp);
            try {
                forkJoinPoolGuard.reentrantInvokeOnForkJoinPool(() -> filterChain.doFilter(request, response));
            } finally {
                revert.revert();
                releaseForkJoinPool(fjp);
            }
        } else {
            var fjp = forkJoinPoolGuard.getDefaultForkJoinPool();
            var revert = forkJoinPoolGuard.pushForkJoinPool(fjp);
            try {
                forkJoinPoolGuard.reentrantInvokeOnForkJoinPool(() -> filterChain.doFilter(request, response));
            } finally {
                revert.revert();
            }
        }
    }

    private ThreadingConfigurationValues getThreadlyStreamingConfigurationFromHeader(String meHomeThreadingJson) {
        var meHomeThreadingConfigurationValues = new ThreadingConfigurationValues();

        if (StringUtils.isNoneBlank(meHomeThreadingJson)) {
            if (threadlyStreamingConfiguration.isThreadlyStreamingHeaderPermitted()) {
                try {
                    meHomeThreadingConfigurationValues = new ObjectMapper().findAndRegisterModules()
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            .readValue(meHomeThreadingJson, ThreadingConfigurationValues.class);
                } catch (JsonProcessingException e) {
                    handleInvalidJsonInput(e);
                }
            } else {
                log.info("'" + THREADLY_HEADER_KEY + "' header is not permitted.");
            }
        }

        return meHomeThreadingConfigurationValues;
    }

    protected void handleInvalidJsonInput(JsonProcessingException e) {
        log.error("Failed to read the '" + THREADLY_HEADER_KEY + "' data", e);
    }
}
