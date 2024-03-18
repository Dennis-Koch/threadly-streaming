package org.threadlys.threading.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.threadlys.streams.AsyncDataProcessor;
import org.threadlys.streams.DataProcessor;
import org.threadlys.streams.DataProcessorContext;
import org.threadlys.streams.DataProcessorExtendable;
import org.threadlys.utils.StateRollback;
import org.threadlys.utils.configuration.CommonsUtilsSpringConfig;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.threadlys.configuration.CommonsThreadingSpringConfig;
import org.threadlys.streams.DataScope;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

@TestPropertySource(properties = { "threadlys.threading.maximumPoolSize=1", "threadlys.threading.poolSize=1" })
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(SpringExtension.class)
@ContextConfiguration
class SingleThreadAsyncDataProcessorTest {
    @Configuration
    @Import({ CommonsThreadingSpringConfig.class, CommonsUtilsSpringConfig.class })
    static class ContextConfiguration {

    }

    @Data
    @Accessors(chain = true)
    static class TestEntity {
        int domainRef;
    }

    @Value
    @RequiredArgsConstructor
    static class TestEntityContext implements DataProcessorContext {
        private final TestEntity currentEntity;

        @Override
        public Object extractDomainRef(Object entity) {
            return ((TestEntity) entity).getDomainRef();
        }
    }

    static interface TestEntityDataProcessor extends DataProcessor<TestEntity, TestEntityContext> {
        // intended blank
    }

    enum TestDataScope implements DataScope {
        DS1, DS2, DS3, DS4;

        @Override
        public boolean isPartOf(DataScope scope) {
            return scope == this;
        }
    }

    @Autowired
    AsyncDataProcessor asyncDataProcessor;

    @Autowired
    DataProcessorExtendable dataProcessorExtendable;

    @Test
    void test() {
        StateRollback.chain(chain -> {
            AtomicInteger invocationCount1 = new AtomicInteger();
            AtomicInteger invocationCount2 = new AtomicInteger();
            chain.append(dataProcessorExtendable.registerDataProcessor(context -> {
                return entity -> {
                    invocationCount1.incrementAndGet();
                };
            }, TestEntity.class, List.of(TestDataScope.DS1), null));
            chain.append(dataProcessorExtendable.registerDataProcessor(context -> {
                return entity -> {
                    invocationCount2.incrementAndGet();
                };
            }, TestEntity.class, List.of(TestDataScope.DS2), null));

            TestEntity te1 = new TestEntity().setDomainRef(1);
            TestEntity te2 = new TestEntity().setDomainRef(2);

            var entityToUsedDataScopes = new ConcurrentHashMap<Object, Map<Object, Set<DataScope>>>();

            asyncDataProcessor.processAllEntities(TestEntity.class, Arrays.asList(te1), Arrays.asList(TestDataScope.DS1), entity -> new TestEntityContext(entity), entityToUsedDataScopes);

            assertThat(invocationCount1.get()).isEqualTo(1);
            assertThat(invocationCount2.get()).isZero();

            asyncDataProcessor.processAllEntities(TestEntity.class, Arrays.asList(te1, te2), Arrays.asList(TestDataScope.DS1), entity -> new TestEntityContext(entity), entityToUsedDataScopes);
            assertThat(invocationCount1.get()).isEqualTo(2); // only one more hit, has te1 should already be processed for DS1 scope
            assertThat(invocationCount2.get()).isZero();

            asyncDataProcessor.processAllEntities(TestEntity.class, Arrays.asList(te1, te2), Arrays.asList(TestDataScope.DS2), entity -> new TestEntityContext(entity), entityToUsedDataScopes);
            assertThat(invocationCount1.get()).isEqualTo(2);
            assertThat(invocationCount2.get()).isEqualTo(2);
        });
    }

    @Test
    void testWithDataScopeSupplier() {
        StateRollback.chain(chain -> {
            AtomicInteger invocationCount1 = new AtomicInteger();
            AtomicInteger invocationCount2 = new AtomicInteger();
            chain.append(dataProcessorExtendable.registerDataProcessor(context -> {
                return entity -> {
                    invocationCount1.incrementAndGet();
                };
            }, TestEntity.class, List.of(TestDataScope.DS1), null));
            chain.append(dataProcessorExtendable.registerDataProcessor(context -> {
                return entity -> {
                    invocationCount2.incrementAndGet();
                };
            }, TestEntity.class, List.of(TestDataScope.DS2), null));

            TestEntity te1 = new TestEntity().setDomainRef(1);
            TestEntity te2 = new TestEntity().setDomainRef(2);

            var entityToUsedDataScopes = new ConcurrentHashMap<Object, Map<Object, Set<DataScope>>>();

            asyncDataProcessor.processAllEntities(TestEntity.class, Arrays.asList(te1, te2), (entity) -> Arrays.asList(TestDataScope.DS1, TestDataScope.DS2), entity -> new TestEntityContext(entity),
                    entityToUsedDataScopes);
            assertThat(invocationCount1.get()).isEqualTo(2);
            assertThat(invocationCount2.get()).isEqualTo(2);
        })
                .rollback();
    }

    @Test
    void testWithDataProcessorChain() {
        for (int a = 10; a-- > 0;) {
            StateRollback.chain(chain -> {
                AtomicInteger invocationCount1 = new AtomicInteger();
                AtomicInteger invocationCount2 = new AtomicInteger();
                AtomicInteger invocationCount3 = new AtomicInteger();
                AtomicInteger invocationCount4 = new AtomicInteger();
                var name1 = "DS1";
                var name2 = "DS2";
                var name3 = "DS3";
                var name4 = "DS4";

                Map<Object, List<Integer>> entityToInvocationOrder = new ConcurrentHashMap<>();
                Consumer<String> noop = name -> {
                };

                chain.append(dataProcessorExtendable.registerDataProcessor((TestEntityDataProcessor) context -> {
                    noop.accept(name2);
                    entityToInvocationOrder.computeIfAbsent(context.getCurrentEntity(), key -> new CopyOnWriteArrayList<>())
                            .add(2);
                    return entity -> {
                        invocationCount2.incrementAndGet();
                    };
                }, TestEntity.class, List.of(TestDataScope.DS2), List.of(TestDataScope.DS3)));

                chain.append(dataProcessorExtendable.registerDataProcessor((TestEntityDataProcessor) context -> {
                    noop.accept(name1);
                    entityToInvocationOrder.computeIfAbsent(context.getCurrentEntity(), key -> new CopyOnWriteArrayList<>())
                            .add(1);
                    return entity -> {
                        invocationCount1.incrementAndGet();
                    };
                }, TestEntity.class, List.of(TestDataScope.DS1), List.of(TestDataScope.DS2)));

                chain.append(dataProcessorExtendable.registerDataProcessor((TestEntityDataProcessor) context -> {
                    noop.accept(name4);
                    entityToInvocationOrder.computeIfAbsent(context.getCurrentEntity(), key -> new CopyOnWriteArrayList<>())
                            .add(4);
                    return entity -> {
                        invocationCount4.incrementAndGet();
                    };
                }, TestEntity.class, List.of(TestDataScope.DS4), List.of(TestDataScope.DS3, TestDataScope.DS2)));

                chain.append(dataProcessorExtendable.registerDataProcessor((TestEntityDataProcessor) context -> {
                    noop.accept(name3);
                    entityToInvocationOrder.computeIfAbsent(context.getCurrentEntity(), key -> new CopyOnWriteArrayList<>())
                            .add(3);
                    return entity -> {
                        invocationCount3.incrementAndGet();
                    };
                }, TestEntity.class, List.of(TestDataScope.DS3), null));

                TestEntity te1 = new TestEntity().setDomainRef(1);
                TestEntity te2 = new TestEntity().setDomainRef(2);

                List<List<Integer>> possibleExpectedOrders = Arrays.asList(Arrays.asList(3, 2, 4, 1), Arrays.asList(3, 2, 1, 4));

                for (int b = 1000; b-- > 0;) {
                    var entityToUsedDataScopes = new ConcurrentHashMap<Object, Map<Object, Set<DataScope>>>();

                    asyncDataProcessor.processAllEntities(TestEntity.class, Arrays.asList(te1, te2), (entity) -> Arrays.asList(TestDataScope.DS4, TestDataScope.DS1),
                            entity -> new TestEntityContext(entity), entityToUsedDataScopes);
                    assertThat(invocationCount1.get()).isEqualTo(2);
                    assertThat(invocationCount2.get()).isEqualTo(2);
                    assertThat(invocationCount3.get()).isEqualTo(2);
                    assertThat(invocationCount4.get()).isEqualTo(2);

                    assertThat(entityToInvocationOrder.size()).isEqualTo(2);

                    assertThat(possibleExpectedOrders.stream()
                            .filter(expectedOrder -> expectedOrder.equals(entityToInvocationOrder.get(te1)))
                            .findFirst()).isPresent();
                    assertThat(possibleExpectedOrders.stream()
                            .filter(expectedOrder -> expectedOrder.equals(entityToInvocationOrder.get(te2)))
                            .findFirst()).isPresent();

                    invocationCount1.set(0);
                    invocationCount2.set(0);
                    invocationCount3.set(0);
                    invocationCount4.set(0);
                    entityToInvocationOrder.clear();
                }
            })
                    .rollback();
        }
    }
}
