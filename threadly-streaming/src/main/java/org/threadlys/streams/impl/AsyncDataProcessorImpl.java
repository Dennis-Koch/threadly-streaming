package org.threadlys.streams.impl;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.threadlys.threading.ContextSnapshot;
import org.threadlys.threading.ContextSnapshotFactory;
import org.threadlys.threading.impl.ForkJoinPoolGuard;
import org.threadlys.utils.IStateRollback;
import org.threadlys.utils.ListenersMapListAdapter;
import org.threadlys.utils.SneakyThrowUtil;
import org.threadlys.utils.StateRollback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.threadlys.streams.AsyncDataProcessor;
import org.threadlys.streams.CheckedConsumer;
import org.threadlys.streams.DataProcessor;
import org.threadlys.streams.DataProcessorContext;
import org.threadlys.streams.DataProcessorExceptionHandler;
import org.threadlys.streams.DataProcessorExtendable;
import org.threadlys.streams.DataScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

//CHECKSTYLE: IllegalCatch OFF
//CHECKSTYLE: HiddenField OFF
@SuppressWarnings({ "PMD.UnnecessaryCast", "PMD.FormalParameterNamingConventions", "checkstyle:IllegalCatch" })
@Slf4j
@Component
public class AsyncDataProcessorImpl implements AsyncDataProcessor, DataProcessorExtendable {
    private static final float BUCKET_LOAD_FACTOR = 0.75f;

    @SuppressWarnings("rawtypes")
    public static class ConfigurationState {
        @Getter
        protected final Map<Class<?>, Map<DataScope, List<DataProcessor>>> entityTypeToScopeToDataProcessorMap = new LinkedHashMap<>();

        @Getter
        protected final Map<DataProcessor, List<? super DataScope>> dataProcessorToDataScopesMap = new LinkedHashMap<>();

        @Getter
        protected final Map<DataProcessor, List<? super DataScope>> dataProcessorToRequiredDataScopesMap = new LinkedHashMap<>();

        @Getter
        protected final Map<DataProcessor, List<? super DataProcessorExceptionHandler>> dataProcessorToExceptionHandlerMap = new LinkedHashMap<>();

        protected Map<DataProcessor, Set<DataScope>> dataProcessorToRequiredDataScopesCascadeMap;

        public ConfigurationState() {
            // intended blank
        }

        public ConfigurationState(ConfigurationState original) {
            entityTypeToScopeToDataProcessorMap.putAll(original.getEntityTypeToScopeToDataProcessorMap());
            dataProcessorToDataScopesMap.putAll(original.getDataProcessorToDataScopesMap());
            dataProcessorToRequiredDataScopesMap.putAll(original.getDataProcessorToRequiredDataScopesMap());
            dataProcessorToExceptionHandlerMap.putAll(original.getDataProcessorToExceptionHandlerMap());
        }

        public Map<DataProcessor, Set<DataScope>> getDataProcessorToRequiredDataScopesCascadeMap() {
            if (dataProcessorToRequiredDataScopesCascadeMap != null) {
                return dataProcessorToRequiredDataScopesCascadeMap;
            }
            synchronized (this) {
                if (dataProcessorToRequiredDataScopesCascadeMap != null) {
                    return dataProcessorToRequiredDataScopesCascadeMap;
                }
                Map<DataScope, List<DataProcessor>> dataScopeToDataProcessorMap = new LinkedHashMap<>();
                Map<DataProcessor, Set<Object>> tempMap = new LinkedHashMap<>();
                var dataProcessorToDataScopesMap = getDataProcessorToDataScopesMap();
                dataProcessorToDataScopesMap.forEach((dataProcessor, dataScopes) -> {
                    dataScopes.forEach(dataScope -> {
                        var dataProcessors = dataScopeToDataProcessorMap.computeIfAbsent((DataScope) dataScope, key -> new ArrayList<>());
                        dataProcessors.add(dataProcessor);
                    });
                    tempMap.put(dataProcessor, new LinkedHashSet<>());
                });
                dataProcessorToRequiredDataScopesMap.forEach((dataProcessor, requiredDataScopes) -> {
                    tempMap.computeIfAbsent(dataProcessor, key -> new LinkedHashSet<>())
                            .addAll(requiredDataScopes);
                });
                tempMap.forEach((dataProcessor, requiredDataScopes) -> {
                    var additionalRequiredDataScopes = new ArrayList<>();
                    requiredDataScopes.forEach(requiredDataScope -> {
                        var requiredDataProcessors = dataScopeToDataProcessorMap.get(requiredDataScope);
                        if (requiredDataProcessors == null) {
                            return;
                        }
                        requiredDataProcessors.forEach(requiredDataProcessor -> {
                            var requiredDataScopeCascade = tempMap.get(requiredDataProcessor);
                            if (requiredDataScopeCascade != null && requiredDataScopes != requiredDataScopeCascade) {
                                // intentionally add the reference to the cascade set itself
                                // we do NOT want to unfold its content at this point
                                additionalRequiredDataScopes.add(requiredDataScopeCascade);
                            }
                        });
                    });
                    requiredDataScopes.addAll(additionalRequiredDataScopes);
                });
                Map<DataProcessor, Set<DataScope>> dataProcessorToRequiredDataScopesCascadeMap = new LinkedHashMap<>();
                tempMap.forEach((dataProcessor, requiredDataScopes) -> {
                    Set<DataScope> requiredDataScopesCascade = new LinkedHashSet<>();
                    unfoldDataScope(requiredDataScopes, requiredDataScopesCascade::add);
                    requiredDataScopesCascade.removeAll(dataProcessorToDataScopesMap.getOrDefault(dataProcessor, List.of()));
                    dataProcessorToRequiredDataScopesCascadeMap.put(dataProcessor, requiredDataScopesCascade);
                });

                this.dataProcessorToRequiredDataScopesCascadeMap = dataProcessorToRequiredDataScopesCascadeMap;

                // debugging structure not yet reasonable
                // StringBuilder sb = new StringBuilder();
                // printDataProcessorTree(sb);
                // log.warn(sb.toString());

                return dataProcessorToRequiredDataScopesCascadeMap;
            }
        }

        protected void unfoldDataScope(Object dataScope, Consumer<DataScope> dataScopeHandler) {
            unfoldDataScopeIntern(dataScope, dataScopeHandler, new IdentityHashMap<>());
        }

        protected void unfoldDataScopeIntern(Object dataScope, Consumer<DataScope> dataScopeHandler, Map<Object, Boolean> alreadyProcessedSet) {
            if (alreadyProcessedSet.putIfAbsent(dataScope, Boolean.TRUE) != null) {
                return;
            }
            if (dataScope instanceof Iterable) {
                for (Object item : (Iterable) dataScope) {
                    unfoldDataScopeIntern(item, dataScopeHandler, alreadyProcessedSet);
                }
                return;
            }
            dataScopeHandler.accept((DataScope) dataScope);
        }

        @SneakyThrows
        public void printDataProcessorTree(StringBuilder sb) {
            var entityTypeToScopeToDataProcessorMap = getEntityTypeToScopeToDataProcessorMap();

            var entityTypes = entityTypeToScopeToDataProcessorMap.keySet()
                    .stream()//
                    .sorted(Comparator.comparing(entityType -> entityType.getClass()
                            .getName()))//
                    .collect(Collectors.toList());

            var json = new LinkedHashMap<String, Object>();

            entityTypes.stream()//
                    .map(entityType -> entityTypeToScopeToDataProcessorMap.get(entityType))//
                    .forEach(scopeToDataProcessorMap -> {
                        scopeToDataProcessorMap.values()
                                .stream()//
                                .flatMap(dataProcessors -> dataProcessors.stream())//
                                .distinct()//
                                .sorted(Comparator.comparing(dataProcessor -> dataProcessor.getClass()
                                        .getName()))//
                                .forEach(dataProcessor -> {
                                    var dataProcessorJson = new LinkedHashMap<String, Object>();
                                    json.put(dataProcessor.getClass()
                                            .getSimpleName(), dataProcessorJson);
                                    var alreadyProcessedSet = new IdentityHashMap<Object, Boolean>();
                                    printDataProcessorTreeIntern(dataProcessor, scopeToDataProcessorMap, dataProcessorJson, alreadyProcessedSet);
                                });
                    });
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) throws IOException {
                    sb.append(cbuf, off, len);
                }

                @Override
                public void flush() throws IOException {
                    // intended blank
                }

                @Override
                public void close() throws IOException {
                    // intended blank
                }
            }, json);
        }

        protected void printDataProcessorTreeIntern(DataProcessor dataProcessor, Map<DataScope, List<DataProcessor>> scopeToDataProcessorMap, Map<String, Object> dataProcessorJson,
                Map<Object, Boolean> alreadyProcessedSet) {
            if (dataProcessor == null) {
                return;
            }
            if (alreadyProcessedSet.putIfAbsent(dataProcessor, Boolean.TRUE) != null) {
                dataProcessorJson.put("cycle", dataProcessor.getClass()
                        .getName());
                return;
            }
            if (alreadyProcessedSet.size() > 10) {
                throw new IllegalStateException();
            }
            var dataProcessorToRequiredDataScopesMap = getDataProcessorToRequiredDataScopesMap();

            var requiredDataScopes = dataProcessorToRequiredDataScopesMap.getOrDefault(dataProcessor, List.of());
            if (requiredDataScopes.isEmpty()) {
                return;
            }
            var requiresJson = new LinkedHashMap<String, Object>();
            dataProcessorJson.put("requires", requiresJson);
            requiredDataScopes.forEach(requiredDataScope -> {
                var requiredDataProcessors = scopeToDataProcessorMap.get(requiredDataScope);

                var requiredDataProcessorJson = new LinkedHashMap<String, Object>();
                requiresJson.put(Integer.toString(requiresJson.size() + 0), requiredDataProcessorJson);

                requiredDataProcessorJson.put("requiredDataScope", requiredDataScope.getClass()
                        .getSimpleName() + "." + requiredDataScope);
                if (requiredDataProcessors == null || requiredDataProcessors.isEmpty()) {
                    requiredDataProcessorJson.put("no-implementation", true);
                    return;
                }
                requiredDataProcessors.stream()//
                        .sorted(Comparator.comparing(requiredDataProcessor -> requiredDataProcessor.getClass()
                                .getName()))//
                        .forEach(requiredDataProcessor -> {
                            printDataProcessorTreeIntern(requiredDataProcessor, scopeToDataProcessorMap, requiredDataProcessorJson, alreadyProcessedSet);
                        });
            });
        }
    }

    @Value
    public static class ExecutionState<E, C extends DataProcessorContext> {
        protected final ConfigurationState configurationState;

        protected final Class<E> entityType;

        protected final Collection<?> entityList;

        protected final Function<E, C> contextBuilder;

        protected final Map<Object, Map<Object, Set<DataScope>>> entityToUsedDataScopes;
    }

    @Value
    class RunnableSupplier<E, C extends DataProcessorContext> {
        DataProcessor<E, C> dataProcessor;

        C dataProcessorContext;

        E entity;

        Supplier<Callable<CheckedConsumer<E>>> supplier;
    }

    protected ConfigurationState state = new ConfigurationState();

    @Autowired
    protected ContextSnapshotFactory contextSnapshotFactory;

    @Autowired
    protected ForkJoinPoolGuard forkJoinPoolGuard;

    @Autowired
    protected SneakyThrowUtil sneakyThrowUtil;

    protected final Lock readLock;

    protected final Lock writeLock;

    public AsyncDataProcessorImpl() {
        var rwLock = new ReentrantReadWriteLock();
        readLock = rwLock.readLock();
        writeLock = rwLock.writeLock();
    }

    @Override
    public <E, C extends DataProcessorContext> void processAllEntities(Class<E> entityType, Collection<?> entityList, Collection<DataScope> dataScopes, Function<E, C> contextBuilder,
            Map<Object, Map<Object, Set<DataScope>>> entityToUsedDataScopes) {
        Objects.requireNonNull(entityType, "entityType must be valid");
        Objects.requireNonNull(contextBuilder, "contextBuilder must be valid");

        if (entityList == null || entityList.isEmpty()) {
            // nothing to do
            return;
        }
        if (dataScopes == null || dataScopes.isEmpty()) {
            // nothing to do
            return;
        }

        var effectiveEntityToUsedDataScopes = entityToUsedDataScopes != null ? entityToUsedDataScopes : new ConcurrentHashMap<Object, Map<Object, Set<DataScope>>>();
        var executionState = new ExecutionState<>(this.state, entityType, entityList, contextBuilder, effectiveEntityToUsedDataScopes);
        var applicableProcessorsChain = resolveApplicableDataProcessorsChain(dataScopes, executionState);
        applyDataProcessorsToEntities(entity -> applicableProcessorsChain, executionState);
    }

    @Override
    public <E, C extends DataProcessorContext> void processAllEntities(Class<E> entityType, Collection<?> entityList, Function<E, Collection<DataScope>> dataScopeSupplier,
            Function<E, C> contextBuilder, Map<Object, Map<Object, Set<DataScope>>> entityToUsedDataScopes) {
        Objects.requireNonNull(entityType, "entityType must be valid");
        Objects.requireNonNull(contextBuilder, "contextBuilder must be valid");

        if (entityList == null || entityList.isEmpty()) {
            // nothing to do
            return;
        }
        if (dataScopeSupplier == null) {
            // nothing to do
            return;
        }

        var effectiveEntityToUsedDataScopes = entityToUsedDataScopes != null ? entityToUsedDataScopes : new ConcurrentHashMap<Object, Map<Object, Set<DataScope>>>();
        var executionState = new ExecutionState<>(this.state, entityType, entityList, contextBuilder, effectiveEntityToUsedDataScopes);
        applyDataProcessorsToEntities(entity -> {
            var dataScopes = dataScopeSupplier.apply(entity);
            return resolveApplicableDataProcessorsChain(dataScopes, executionState);
        }, executionState);
    }

    protected <E, C extends DataProcessorContext> void applyDataProcessorsToEntities(Function<E, List<Map<DataProcessor<E, C>, Collection<DataScope>>>> applicableProcessorsProvider,
            ExecutionState<E, C> executionState) {
        var stageToRunnableSuppliersList = buildDataProcessorStages(applicableProcessorsProvider, executionState);
        executeDataProcessorStages(stageToRunnableSuppliersList);
    }

    protected <E, C extends DataProcessorContext> void executeDataProcessorStages(List<List<RunnableSupplier<E, C>>> stageToRunnableSuppliersList) {
        var rollback = StateRollback.empty();
        try {
            var fjp = forkJoinPoolGuard.currentForkJoinPool();
            if (fjp == null) {
                fjp = forkJoinPoolGuard.getDefaultForkJoinPool();
                rollback = forkJoinPoolGuard.pushForkJoinPool(fjp);
            }
            for (var runnableSuppliersList : stageToRunnableSuppliersList) {
                var callables = new ArrayList<Callable<CheckedConsumer<E>>>(runnableSuppliersList.size());

                // this map stores us the info for which callable, leading to a future handle, which entityUpdater is connected to which entity
                var indexToEntityMap = new HashMap<Integer, E>((int) (runnableSuppliersList.size() / BUCKET_LOAD_FACTOR) + 1, BUCKET_LOAD_FACTOR);

                buildIndexToEntityMap(runnableSuppliersList, callables, indexToEntityMap);

                if (callables.isEmpty()) {
                    continue;
                }
                var futures = fjp.invokeAll(callables);

                // wait for all futures to concurrently finish, but also help with current thread
                // this is especially critical as our current thread might be the last & only unblocked thread
                // of the current ForkJoinPool
                for (var future : futures) {
                    while (!future.isDone() && !future.isCancelled()) {
                        fjp.awaitQuiescence(1, TimeUnit.MILLISECONDS);
                    }
                }
                // now we know all futures have been finished
                updateEntities(futures, indexToEntityMap, fjp);
            }
        } finally {
            rollback.rollback();
        }
    }

    protected <E, C extends DataProcessorContext> void buildIndexToEntityMap(List<RunnableSupplier<E, C>> runnableSuppliersList, List<Callable<CheckedConsumer<E>>> callables,
            Map<Integer, E> indexToEntityMap) {
        for (int a = 0, size = runnableSuppliersList.size(); a < size; a++) {
            var runnableSupplier = runnableSuppliersList.get(a);
            var entity = runnableSupplier.getEntity();
            if (!runnableSupplier.getDataProcessor()
                    .expectsExecution(entity, runnableSupplier.getDataProcessorContext())) {
                continue;
            }
            var callable = runnableSupplier.getSupplier()
                    .get();
            if (callable == null) {
                continue;
            }
            int index = callables.size();
            callables.add(callable);
            indexToEntityMap.put(Integer.valueOf(index), entity);
        }
    }

    protected <E> void updateEntities(List<Future<CheckedConsumer<E>>> futures, Map<Integer, E> indexToEntityMap, ForkJoinPool fjp) {
        for (int a = 0, size = futures.size(); a < size; a++) {
            try {
                var entityUpdater = retrieveEntityUpdater(futures.get(a), fjp);
                if (entityUpdater != null) {
                    var entity = indexToEntityMap.get(a);
                    entityUpdater.accept(entity);
                }
            } catch (Throwable e) {
                var ex = sneakyThrowUtil.mergeStackTraceWithCause(e);
                throw sneakyThrowUtil.sneakyThrow(ex);
            }
        }
    }

    protected <E> CheckedConsumer<E> retrieveEntityUpdater(Future<CheckedConsumer<E>> future, ForkJoinPool fjp) throws Throwable {
        while (!fjp.isShutdown()) {
            try {
                return future.get();
            } catch (InterruptedException e) {
                Thread.interrupted();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected <E, C extends DataProcessorContext> List<List<RunnableSupplier<E, C>>> buildDataProcessorStages(
            Function<E, List<Map<DataProcessor<E, C>, Collection<DataScope>>>> applicableProcessorsProvider, ExecutionState<E, C> executionState) {
        var contextBuilder = executionState.getContextBuilder();
        var stageToRunnableSuppliersList = new ArrayList<List<RunnableSupplier<E, C>>>();
        var cs = contextSnapshotFactory.createSnapshot();

        for (var entityObject : executionState.getEntityList()) {
            if (entityObject == null) {
                continue;
            }
            E entity = (E) entityObject;
            var dataProcessorContext = contextBuilder.apply(entity);
            var dataProcessorChainPerEntity = applicableProcessorsProvider.apply(entity);

            // merge all data processors for the current entity with the global stage that
            // processes all entities in a batch-per-stage manner
            for (int stageLevel = 0, size = dataProcessorChainPerEntity.size(); stageLevel < size; stageLevel++) {
                if (stageToRunnableSuppliersList.size() <= stageLevel) {
                    stageToRunnableSuppliersList.add(new ArrayList<>());
                }
                var runnableSuppliersList = stageToRunnableSuppliersList.get(stageLevel);
                var dataProcessorsPerEntity = dataProcessorChainPerEntity.get(stageLevel);
                dataProcessorsPerEntity.forEach((dataProcessor, dc) -> {
                    var runnableSupplier = new RunnableSupplier<E, C>(dataProcessor, dataProcessorContext, entity,
                            () -> asyncProcess(dataProcessor, dataProcessorContext, entity, dc, executionState, cs));
                    runnableSuppliersList.add(runnableSupplier);
                });
            }
        }
        return stageToRunnableSuppliersList;
    }

    @SuppressWarnings("unchecked")
    protected <E, C extends DataProcessorContext> List<Map<DataProcessor<E, C>, Collection<DataScope>>> resolveApplicableDataProcessorsChain(Collection<DataScope> dataScopes,
            ExecutionState<E, C> executionState) {

        var allApplicableDataProcessors = resolveAllApplicableDataProcessors(dataScopes, executionState);
        var dataProcessorToRequiredDataScopesMap = executionState.getConfigurationState()
                .getDataProcessorToRequiredDataScopesCascadeMap();
        var dataProcessorToDataScopesMap = executionState.getConfigurationState()
                .getDataProcessorToDataScopesMap();
        var dataProcessorChain = new ArrayList<Map<DataProcessor<E, C>, Collection<DataScope>>>();

        allApplicableDataProcessors.forEach(dataProcessor -> {
            var dataScopesOfDataProcessor = (Collection<DataScope>) dataProcessorToDataScopesMap.get(dataProcessor);
            var requiringDataScopes = dataProcessorToRequiredDataScopesMap.get(dataProcessor);

            boolean dataProcessorInserted = false;

            for (int a = dataProcessorChain.size(); a-- > 0;) {
                var dataProcessorsPerStage = dataProcessorChain.get(a);
                var stageIndex = a;

                dataProcessorInserted = dataProcessorsPerStage.entrySet()
                        .stream()
                        .anyMatch(entry -> {
                            var chainedDataProcessor = entry.getKey();
                            var dataScopesOfChainedDataProcessor = entry.getValue();

                            if (requiringDataScopes != null && tryToAppendToDownstreamStage(dataProcessorToRequiredDataScopesMap, dataProcessorChain, dataProcessor, dataScopesOfDataProcessor,
                                    requiringDataScopes, stageIndex, dataScopesOfChainedDataProcessor)) {
                                return true;
                            }
                            // so this chained data processor is not required by the current one. but maybe
                            // it is the other way around
                            // so let us find out
                            var dataScopesCascadeOfChainedDataProcessor = dataProcessorToRequiredDataScopesMap.get(chainedDataProcessor);
                            return tryToAppendToUpstreamStage(dataProcessorToRequiredDataScopesMap, dataProcessorChain, dataProcessor, dataScopesOfDataProcessor, stageIndex,
                                    dataScopesCascadeOfChainedDataProcessor);
                        });

                if (dataProcessorInserted) {
                    break;
                }
            }

            if (dataProcessorInserted) {
                return;
            }
            // at this point none of the existing chained data processors has any dependency
            // in any direction to our current one
            // this means we can queue our current one to the first stage

            if (dataProcessorChain.isEmpty()) {
                dataProcessorChain.add(new LinkedHashMap<DataProcessor<E, C>, Collection<DataScope>>());
            }
            var stage = dataProcessorChain.get(0);
            stage.put(dataProcessor, dataScopesOfDataProcessor);
        });
        return dataProcessorChain;
    }

    @SuppressWarnings("rawtypes")
    protected <E, C extends DataProcessorContext> boolean tryToAppendToDownstreamStage(Map<DataProcessor, Set<DataScope>> dataProcessorToRequiredDataScopesMap,
            ArrayList<Map<DataProcessor<E, C>, Collection<DataScope>>> dataProcessorChain, DataProcessor<E, C> dataProcessor, Collection<DataScope> dataScopesOfDataProcessor,
            Set<DataScope> requiringDataScopes, int stageIndex, Collection<DataScope> dataScopesOfChainedDataProcessor) {
        return requiringDataScopes.stream()//
                .filter(dataScopesOfChainedDataProcessor::contains)//
                .map(requiringDataScope -> {
                    var downstreamStageIndex = stageIndex + 1;
                    if (dataProcessorChain.size() == downstreamStageIndex) {
                        // we are currently in the last stage, but require a data processor from it
                        // so we need a new last stage appended
                        dataProcessorChain.add(new LinkedHashMap<>());
                    }
                    var downstreamStage = dataProcessorChain.get(downstreamStageIndex);
                    addToStageOrAppendStageAfterIndex(dataProcessor, dataScopesOfDataProcessor, dataProcessorChain, downstreamStageIndex, dataProcessorToRequiredDataScopesMap);
                    downstreamStage.put(dataProcessor, dataScopesOfDataProcessor);
                    return true;
                })//
                .findFirst()//
                .isPresent();
    }

    @SuppressWarnings("rawtypes")
    protected <E, C extends DataProcessorContext> boolean tryToAppendToUpstreamStage(Map<DataProcessor, Set<DataScope>> dataProcessorToRequiredDataScopesMap,
            ArrayList<Map<DataProcessor<E, C>, Collection<DataScope>>> dataProcessorChain, DataProcessor<E, C> dataProcessor, Collection<DataScope> dataScopesOfDataProcessor, int stageIndex,
            Set<DataScope> dataScopesCascadeOfChainedDataProcessor) {
        return dataScopesCascadeOfChainedDataProcessor.stream()//
                .filter(dataScopesOfDataProcessor::contains)//
                .map(chainedDataScope -> {
                    var upstreamStageIndex = stageIndex - 1;
                    if (upstreamStageIndex == -1) {
                        // we are currently in the first stage, but a data processor from this stage
                        // requires the current one
                        // so we need to insert a new first stage
                        dataProcessorChain.add(0, new LinkedHashMap<DataProcessor<E, C>, Collection<DataScope>>());
                        upstreamStageIndex = 0;
                    }
                    addToStageOrAppendStageAfterIndex(dataProcessor, dataScopesOfDataProcessor, dataProcessorChain, upstreamStageIndex, dataProcessorToRequiredDataScopesMap);
                    return true;
                })//
                .findFirst()//
                .isPresent();
    }

    @SuppressWarnings("rawtypes")
    protected <E, C extends DataProcessorContext> Comparator<DataProcessor<E, C>> buildComparator(Map<DataProcessor, List<? super DataScope>> dataProcessorToDataScopesMap,
            Map<DataProcessor, Set<DataScope>> dataProcessorToRequiredDataScopesMap) {
        BiFunction<List<? super DataScope>, Set<DataScope>, Boolean> isLeftRequiredByRight = isLeftRequiredByRight();

        return (left, right) -> {
            var leftRequiredDataScopes = dataProcessorToRequiredDataScopesMap.get(left);
            var rightRequiredDataScopes = dataProcessorToRequiredDataScopesMap.get(right);
            if (leftRequiredDataScopes == null || leftRequiredDataScopes.isEmpty()) {
                if (rightRequiredDataScopes == null || rightRequiredDataScopes.isEmpty()) {
                    return 0;
                }
                var leftDataScopes = dataProcessorToDataScopesMap.get(left);
                if (isLeftRequiredByRight.apply(leftDataScopes, rightRequiredDataScopes)) {
                    return -1;
                }
            }
            if (rightRequiredDataScopes == null || rightRequiredDataScopes.isEmpty()) {
                // a data processor with dependencies is always AFTER a data processor without
                // any dependencies
                var rightDataScopes = dataProcessorToDataScopesMap.get(right);
                if (isLeftRequiredByRight.apply(rightDataScopes, leftRequiredDataScopes)) {
                    return 1;
                }
            }
            // that is the only interesting case: both data processor have requirements.
            // lets check for semantic order
            var leftDataScopes = dataProcessorToDataScopesMap.get(left);
            if (isLeftRequiredByRight.apply(leftDataScopes, rightRequiredDataScopes)) {
                return -1;
            }
            var rightDataScopes = dataProcessorToDataScopesMap.get(right);
            if (isLeftRequiredByRight.apply(rightDataScopes, leftRequiredDataScopes)) {
                return 1;
            }
            return 0;
        };
    }

    protected BiFunction<List<? super DataScope>, Set<DataScope>, Boolean> isLeftRequiredByRight() {
        return (leftDataScopes, rightRequiredDataScopes) -> leftDataScopes.stream()//
                .anyMatch(rightRequiredDataScopes::contains);
    }

    @SuppressWarnings("rawtypes")
    protected <E, C extends DataProcessorContext> void addToStageOrAppendStageAfterIndex(DataProcessor<E, C> dataProcessor, Collection<DataScope> dataScopesOfDataProcessor,
            ArrayList<Map<DataProcessor<E, C>, Collection<DataScope>>> dataProcessorChain, int stageIndex, Map<DataProcessor, Set<DataScope>> dataProcessorToRequiredDataScopesMap) {
        var stage = dataProcessorChain.get(stageIndex);
        if (stage.isEmpty()) {
            stage.put(dataProcessor, dataScopesOfDataProcessor);
            return;
        }
        var processorDependsOnStage = stage.entrySet()
                .stream()
                .anyMatch(entry -> {
                    var doesDepend = entry.getValue()
                            .stream()
                            .anyMatch(dataScopeInStage -> {
                                var requiredDataScopes = dataProcessorToRequiredDataScopesMap.get(dataProcessor);
                                if (requiredDataScopes == null || requiredDataScopes.isEmpty()) {
                                    return false;
                                }
                                return requiredDataScopes.contains(dataScopeInStage);
                            });
                    if (doesDepend) {
                        return true;
                    }
                    var requiredDataScopes = dataProcessorToRequiredDataScopesMap.get(entry.getKey());
                    if (requiredDataScopes == null || requiredDataScopes.isEmpty()) {
                        return false;
                    }
                    return dataScopesOfDataProcessor.stream()
                            .anyMatch(requiredDataScopes::contains);
                });

        if (processorDependsOnStage) {
            dataProcessorChain.add(stageIndex + 1, new LinkedHashMap<DataProcessor<E, C>, Collection<DataScope>>());
            stage = dataProcessorChain.get(stageIndex + 1);
        }
        // dataProcessor does not depend on current stage, but maybe one of the existing
        // chained processors there does

        stage.put(dataProcessor, dataScopesOfDataProcessor);
    }

    protected <E, C extends DataProcessorContext> List<DataProcessor<E, C>> resolveAllApplicableDataProcessors(Collection<DataScope> dataScopes, ExecutionState<E, C> executionState) {
        var dataProcessorToDataScopesMap = executionState.getConfigurationState()
                .getDataProcessorToDataScopesMap();
        var dataProcessorToRequiredDataScopesMap = executionState.getConfigurationState()
                .getDataProcessorToRequiredDataScopesCascadeMap();
        var alreadyProcessedDataScopes = new HashSet<>();
        var pendingDataScopes = new LinkedHashSet<>(dataScopes);
        List<DataProcessor<E, C>> allApplicableDataProcessors = new ArrayList<>();
        while (!pendingDataScopes.isEmpty()) {
            List<DataProcessor<E, C>> applicableDataProcessors = resolveApplicableDataProcessors(pendingDataScopes, executionState);
            allApplicableDataProcessors.addAll(applicableDataProcessors);

            alreadyProcessedDataScopes.addAll(pendingDataScopes);
            pendingDataScopes.clear();

            applicableDataProcessors.forEach(dataProcessor -> {
                var requiringDataScopes = dataProcessorToRequiredDataScopesMap.get(dataProcessor);
                if (requiringDataScopes == null || requiringDataScopes.isEmpty()) {
                    return;
                }
                requiringDataScopes.stream()
                        .forEach(requiringDataScope -> {
                            if (!alreadyProcessedDataScopes.contains(requiringDataScope)) {
                                pendingDataScopes.add(requiringDataScope);
                            }
                        });
            });
        }
        Collections.sort(allApplicableDataProcessors, buildComparator(dataProcessorToDataScopesMap, dataProcessorToRequiredDataScopesMap));
        return allApplicableDataProcessors;
    }

    /**
     * Resolves all relevant data processors for the given data scopes
     *
     * @param <E>
     * @param <C>
     * @param dataScopes
     * @param executionState
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected <E, C extends DataProcessorContext> List<DataProcessor<E, C>> resolveApplicableDataProcessors(Collection<DataScope> dataScopes, ExecutionState<E, C> executionState) {
        ConfigurationState configurationState = executionState.getConfigurationState();
        Map<DataScope, List<DataProcessor>> scopeToDataProcessorMap = configurationState.getEntityTypeToScopeToDataProcessorMap()
                .get(executionState.getEntityType());
        if (scopeToDataProcessorMap == null) {
            return Collections.emptyList();
        }
        return scopeToDataProcessorMap.entrySet()
                .stream()//
                .map(entry -> {
                    Optional<?> hasSupportedScope = dataScopes.stream()
                            .filter(dataScope -> dataScope.isPartOf(entry.getKey()))
                            .findFirst();
                    return hasSupportedScope.isPresent() ? (List<? extends DataProcessor<E, C>>) (List) entry.getValue() : null;
                })//
                .filter(Objects::nonNull)//
                .flatMap(Collection::stream)//
                .distinct()//
                .collect(Collectors.toList());
    }

    /**
     * Returns a callable that executes the specified processor in the specified processor context when passed to an ExecutorService<br>
     * <br>
     * This is helpful for streaming API purposes where you want to queue up a bunch of operations before waiting for all their results
     *
     * @param <E>
     *            The processed entity type
     * @param <C>
     *            The processor context type
     * @param dataProcessor
     * @param processorContext
     * @param entity
     *            The processed entity
     * @return
     */
    protected <E, C extends DataProcessorContext> Callable<CheckedConsumer<E>> asyncProcess(DataProcessor<E, C> dataProcessor, C processorContext, E entity, Collection<DataScope> dataScopes,
            ExecutionState<E, C> executionState, ContextSnapshot cs) {
        var entityToUsedDataScopes = executionState.getEntityToUsedDataScopes();
        if (entityToUsedDataScopes != null) {
            var domainRef = processorContext.extractDomainRef(entity);
            if (domainRef != null) {
                var processorType = dataProcessor.getClass()
                        .getName();
                var processorToUsedDataScopes = entityToUsedDataScopes.computeIfAbsent(domainRef, currentDomainRef -> new HashMap<>());
                var usedDataScopes = processorToUsedDataScopes.computeIfAbsent(processorType, currentProcessorType -> new HashSet<>());
                boolean scopeAlreadyProcessedOnEntity = dataScopes.stream()
                        .map(usedDataScopes::contains)
                        .findFirst()
                        .orElse(Boolean.FALSE);
                usedDataScopes.addAll(dataScopes);

                if (scopeAlreadyProcessedOnEntity) {
                    if (log.isDebugEnabled()) {
                        log.debug("Skipped data processor '{}' for entity '{}'!", dataProcessor.getClass()
                                .getSimpleName(), domainRef);
                    }
                    return null;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Calling data processor '{}' for entity '{}'...", dataProcessor.getClass()
                            .getSimpleName(), domainRef);
                }
            }
        }
        var dataProcessorToExceptionHandlerMap = executionState.getConfigurationState()
                .getDataProcessorToExceptionHandlerMap();
        var exceptionHandlers = dataProcessorToExceptionHandlerMap.get(dataProcessor);
        if (exceptionHandlers == null || exceptionHandlers.isEmpty()) {
            return () -> {
                var rollback = cs.apply();
                try {
                    return dataProcessor.process(processorContext);
                } finally {
                    rollback.rollback();
                }
            };
        } else {
            return () -> {
                var rollback = cs.apply();
                try {
                    return dataProcessor.process(processorContext);
                } catch (Throwable e) {
                    var lastExceptionHandler = (DataProcessorExceptionHandler) exceptionHandlers.get(exceptionHandlers.size() - 1);
                    return lastExceptionHandler.handleProcessException(dataProcessor, processorContext, e);
                } finally {
                    rollback.rollback();
                }
            };
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public <E> IStateRollback registerDataProcessor(DataProcessor<E, ?> dataProcessor, Class<? extends E> entityType, DataScope dataScope) {
        writeLock.lock();
        try {
            ConfigurationState newState = new ConfigurationState(state);
            var scopeToDataProcessorMap = newState.getEntityTypeToScopeToDataProcessorMap()
                    .computeIfAbsent(entityType, currentEntityType -> new ConcurrentHashMap<>());
            ListenersMapListAdapter.registerListener(dataProcessor, dataScope, (Map) scopeToDataProcessorMap);

            ListenersMapListAdapter.registerListener(dataScope, dataProcessor, newState.getDataProcessorToDataScopesMap());
            this.state = newState;
        } finally {
            writeLock.unlock();
        }
        return () -> unregisterDataProcessor(dataProcessor, entityType, dataScope);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected <E> void unregisterDataProcessor(DataProcessor<E, ?> dataProcessor, Class<? extends E> entityType, DataScope dataScope) {
        writeLock.lock();
        try {
            ConfigurationState newState = new ConfigurationState(state);
            ListenersMapListAdapter.unregisterListener(dataScope, dataProcessor, newState.getDataProcessorToDataScopesMap());

            var scopeToDataProcessorMap = newState.getEntityTypeToScopeToDataProcessorMap()
                    .get(entityType);
            ListenersMapListAdapter.unregisterListener(dataProcessor, dataScope, (Map) scopeToDataProcessorMap);

            if (scopeToDataProcessorMap.isEmpty()) {
                newState.getEntityTypeToScopeToDataProcessorMap()
                        .remove(entityType);
            }
            this.state = newState;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <E> IStateRollback registerDataProcessorDependency(DataProcessor<E, ?> dataProcessor, DataScope requiredDataScope) {
        writeLock.lock();
        try {
            ConfigurationState newState = new ConfigurationState(state);
            ListenersMapListAdapter.registerListener(requiredDataScope, dataProcessor, newState.getDataProcessorToRequiredDataScopesMap());
            this.state = newState;
        } finally {
            writeLock.unlock();
        }
        return () -> unregisterDataProcessorDependency(dataProcessor, requiredDataScope);
    }

    protected <E> void unregisterDataProcessorDependency(DataProcessor<E, ?> dataProcessor, DataScope requiredDataScope) {
        writeLock.lock();
        try {
            ConfigurationState newState = new ConfigurationState(state);
            ListenersMapListAdapter.unregisterListener(requiredDataScope, dataProcessor, newState.getDataProcessorToRequiredDataScopesMap());
            this.state = newState;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <E> IStateRollback registerDataProcessorExceptionHandler(DataProcessor<E, ?> dataProcessor, DataProcessorExceptionHandler exceptionHandler) {
        writeLock.lock();
        try {
            ConfigurationState newState = new ConfigurationState(state);
            ListenersMapListAdapter.registerListener(exceptionHandler, dataProcessor, newState.getDataProcessorToExceptionHandlerMap());
            this.state = newState;
        } finally {
            writeLock.unlock();
        }
        return () -> unregisterDataProcessorExceptionHandler(dataProcessor, exceptionHandler);
    }

    protected <E> void unregisterDataProcessorExceptionHandler(DataProcessor<E, ?> dataProcessor, DataProcessorExceptionHandler exceptionHandler) {
        writeLock.lock();
        try {
            ConfigurationState newState = new ConfigurationState(state);
            ListenersMapListAdapter.unregisterListener(exceptionHandler, dataProcessor, newState.getDataProcessorToExceptionHandlerMap());
            this.state = newState;
        } finally {
            writeLock.unlock();
        }
    }
}
