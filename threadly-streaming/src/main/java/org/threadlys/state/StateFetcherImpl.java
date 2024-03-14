package org.threadlys.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.core.NamedThreadLocal;

import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

//CHECKSTYLE: ConstantName OFF
//CHECKSTYLE: HiddenField OFF
@Slf4j
@Builder
public class StateFetcherImpl<S> implements StateFetcher<S> {

    private static class FutureWrapper<S> {
        Future<S> future;
    }

    @Value
    private static class StateChangedEvent {
        StateFetcherImpl<?> source;

        Object oldState;

        Object newState;
    }

    private static class PendingEvents {
        boolean eventsActive;

        final List<StateChangedEvent> events = new ArrayList<>();
    }

    public static final int CURRENT_STATE_ACCEPT = 1;

    public static final int CURRENT_STATE_IGNORE_AND_PURGE = 2;

    public static final int CURRENT_STATE_IGNORE_AND_KEEP = 0;

    public static final int RESOLUTION_SYNC = 4;

    public static final int RESOLUTION_ASYNC = 0;

    protected static final ThreadLocal<PendingEvents> pendingEventsTL = new NamedThreadLocal<>("StateFetcherFactoryImpl.pendingEventsTL") {
        @Override
        protected PendingEvents initialValue() {
            return new PendingEvents();
        }
    };

    protected S state;

    protected final Object stateMutex = new Object();

    @NonNull
    protected final StateFetcherExecutor taskExecutor;

    @NonNull
    protected final Supplier<S> stateSupplier;

    protected final Consumer<S> oldStateProcessor;

    protected final Consumer<S> newStateProcessor;

    protected final List<FutureWrapper<S>> notYetStartedFutures = new ArrayList<>();

    protected final List<FutureWrapper<S>> ongoingFutures = new ArrayList<>();

    protected volatile int inProgressRefreshes;

    StateFetcherImpl(S state, StateFetcherExecutor taskExecutor, Supplier<S> stateSupplier, Consumer<S> oldStateProcessor, Consumer<S> newStateProcessor, int pendingAsyncRefreshes) {
        this.taskExecutor = Objects.requireNonNull(taskExecutor);
        this.stateSupplier = Objects.requireNonNull(stateSupplier);
        this.oldStateProcessor = oldStateProcessor;
        this.newStateProcessor = newStateProcessor;
    }

    @Override
    public void invalidateState() {
        if (this.state == null) {
            return;
        }
        var eventsActivatedOnStack = activateEventsOnStack();
        try {
            synchronized (stateMutex) {
                var oldState = this.state;
                if (oldState == null) {
                    return;
                }
                this.state = null;
                queueEvent(oldState, null);
            }
        } finally {
            flushEvents(eventsActivatedOnStack);
        }
    }

    @Override
    public boolean hasDefinedState() {
        return state != null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Future<S> ensureStateAsync() {
        var state = getStateInternal(RESOLUTION_ASYNC | CURRENT_STATE_ACCEPT);
        if (state instanceof Future<?> f) {
            return (Future<S>) f;
        }
        return (Future<S>) CompletableFuture.completedFuture(state);
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    @Override
    public S resolveState() {
        var state = getStateInternal(RESOLUTION_SYNC | CURRENT_STATE_ACCEPT);
        if (state instanceof Future<?> f) {
            return (S) f.get();
        }
        return (S) state;
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    @Override
    public S refreshState(boolean resetNow) {
        var state = getStateInternal(RESOLUTION_SYNC | (resetNow ? CURRENT_STATE_IGNORE_AND_PURGE : CURRENT_STATE_IGNORE_AND_KEEP));
        if (state instanceof Future<?> f) {
            return (S) f.get();
        }
        return (S) state;
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    @Override
    public Future<S> refreshStateAsync(boolean resetNow) {
        var state = getStateInternal(RESOLUTION_ASYNC | (resetNow ? CURRENT_STATE_IGNORE_AND_PURGE : CURRENT_STATE_IGNORE_AND_KEEP));
        if (state instanceof Future<?> f) {
            return (Future<S>) state;
        }
        return (Future<S>) CompletableFuture.completedFuture(state);
    }

    @SneakyThrows
    protected Object getStateInternal(int flags) {
        var acceptCurrentState = hasFlagCurrentStateAccept(flags);
        var syncResult = hasFlagResolutionSync(flags);
        var state = acceptCurrentState ? this.state : null;
        if (state != null) {
            return state;
        }
        var eventsActivatedOnStack = activateEventsOnStack();
        try {
            CompletableFuture<S> cf = null;
            FutureWrapper<S> futureWrapper = null;
            synchronized (stateMutex) {
                while (true) {
                    state = acceptCurrentState ? this.state : null;
                    if (state != null) {
                        return state;
                    }
                    purgeCurrentStateIfRequired(flags);
                    // check if a concurrent worker already fetches state
                    futureWrapper = notYetStartedFutures.isEmpty() ? null : notYetStartedFutures.get(0);
                    if (futureWrapper != null) {
                        break;
                    }
                    // no state available and no concurrent worker fetching it
                    futureWrapper = new FutureWrapper<S>();
                    if (syncResult) {
                        cf = new CompletableFuture<S>();
                        futureWrapper.future = cf;
                    } else {
                        var futureWrapperF = futureWrapper;
                        futureWrapper.future = taskExecutor.submit(() -> createAndAssignNewState(futureWrapperF));
                    }
                    notYetStartedFutures.add(futureWrapper);
                    // notify the potential very fast worker task that might have already tried to execute
                    stateMutex.notifyAll();
                    break;
                }
            }
            return buildStateInternal(flags, futureWrapper, cf);
        } finally {
            flushEvents(eventsActivatedOnStack);
        }
    }

    protected boolean hasFlagResolutionSync(int flags) {
        return (flags & RESOLUTION_SYNC) != 0;
    }

    protected boolean hasFlagCurrentStateAccept(int flags) {
        return (flags & CURRENT_STATE_ACCEPT) != 0;
    }

    protected boolean hasFlagCurrentStateIgnoreAndPurge(int flags) {
        return (flags & CURRENT_STATE_IGNORE_AND_PURGE) != 0;
    }

    @SneakyThrows
    protected Object buildStateInternal(int flags, FutureWrapper<S> futureWrapper, CompletableFuture<S> cf) {
        if (cf != null) {
            // current thread must complete this future
            state = createAndAssignNewState(futureWrapper);
            cf.complete(state);
            return state;
        }
        var syncResult = hasFlagResolutionSync(flags);
        if (syncResult) {
            if (log.isDebugEnabled()) {
                log.debug("waitForState(): L" + System.identityHashCode(stateMutex) + ", future.get() F" + System.identityHashCode(futureWrapper));
            }
            return futureWrapper.future.get();
        }
        return futureWrapper.future;
    }

    protected void purgeCurrentStateIfRequired(int flags) {
        var purgeCurrentState = hasFlagCurrentStateIgnoreAndPurge(flags);
        if (!purgeCurrentState || this.state == null) {
            return;
        }
        var oldState = this.state;
        this.state = null;
        if (oldState != null) {
            queueEvent(oldState, null);
        }
    }

    @SneakyThrows
    protected S createAndAssignNewState(FutureWrapper<S> futureWrapper) {
        synchronized (stateMutex) {
            while (!notYetStartedFutures.remove(futureWrapper)) {
                // rare case, but theoretically current worker thread was faster than the submitter
                // of the task who had not the chance to memorize & share the return future, yet
                stateMutex.wait();
            }
            ongoingFutures.add(futureWrapper);
        }
        try {
            if (log.isDebugEnabled()) {
                log.debug("createAndAssignNewState(): L" + System.identityHashCode(stateMutex) + ": resolving");
            }
            var newState = stateSupplier.get();
            if (log.isDebugEnabled()) {
                log.debug("createAndAssignNewState(): L" + System.identityHashCode(stateMutex) + ", new state S" + System.identityHashCode(newState));
            }
            synchronized (stateMutex) {
                S oldState = this.state;
                this.state = newState;
                queueEvent(oldState, newState);
            }
            return newState;
        } finally {
            synchronized (stateMutex) {
                if (!ongoingFutures.remove(futureWrapper)) {
                    throw new IllegalStateException("Must never happen");
                }
            }
        }
    }

    protected void queueEvent(S oldState, S newState) {
        var pendingEvents = pendingEventsTL.get();
        pendingEvents.events.add(new StateChangedEvent(this, oldState, newState));
    }

    protected boolean activateEventsOnStack() {
        var pendingEvents = pendingEventsTL.get();
        if (pendingEvents.eventsActive) {
            return false;
        }
        pendingEvents.eventsActive = true;
        return true;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void flushEvents(boolean eventsActivatedOnStack) {
        if (!eventsActivatedOnStack) {
            return;
        }
        var pendingEvents = pendingEventsTL.get();
        pendingEvents.eventsActive = false;
        var events = pendingEvents.events;
        if (events.isEmpty()) {
            return;
        }
        var eventsArray = events.toArray(StateChangedEvent[]::new);
        events.clear();
        for (var evnt : eventsArray) {
            var source = evnt.source;
            if (source.oldStateProcessor != null && evnt.oldState != null) {
                ((Consumer) source.oldStateProcessor).accept(evnt.oldState);
            }
            if (source.newStateProcessor != null && evnt.newState != null) {
                ((Consumer) source.newStateProcessor).accept(evnt.newState);
            }
        }
    }
}
