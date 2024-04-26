package org.threadlys.profiling;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
@ConditionalOnExpression("$(threadlys.perfomance.logging.enabled:false}")
public class ExecutionTimeAdvice {

    /**
     * Track execution time of a method.
     *
     * @param point
     * @return
     * @throws Throwable
     */
    @Around("@annotation(org.threadlys.profiling.TrackExecutionTime)")
    public Object executionTime(ProceedingJoinPoint point) throws Throwable {
        long startTime = System.currentTimeMillis();
        var object = point.proceed();
        long endTime = System.currentTimeMillis();
        if (log.isInfoEnabled()) {
            log.info("Class: " + point.getSignature().getDeclaringTypeName() + ". Method: " + point.getSignature().getName() + ". Time taken: " + (endTime - startTime) + "ms");
        }
        return object;
    }
}
