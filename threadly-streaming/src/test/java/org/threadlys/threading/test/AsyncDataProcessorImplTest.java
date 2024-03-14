package org.threadlys.threading.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.threadlys.configuration.CommonsThreadingSpringConfig;
import org.threadlys.streams.AsyncDataProcessor;
import org.threadlys.streams.CheckedConsumer;
import org.threadlys.streams.DataProcessor;
import org.threadlys.streams.DataProcessorContext;
import org.threadlys.streams.DataProcessorExceptionHandler;
import org.threadlys.streams.DataProcessorExtendable;
import org.threadlys.streams.DataScope;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(SpringExtension.class)
@ContextConfiguration
class AsyncDataProcessorImplTest {
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
    void testExceptionWithoutExceptionHandler() {
        StateRollback.chain(chain -> {
            AtomicInteger invocationCount1 = new AtomicInteger();
            TestEntity te1 = new TestEntity().setDomainRef(1);
            TestEntity te2 = new TestEntity().setDomainRef(2);
            chain.append(dataProcessorExtendable.registerDataProcessor((DataProcessor<TestEntity, TestEntityContext>) context -> {
                if (context.getCurrentEntity() == te1) {
                    throw new SocketTimeoutException();
                }
                return entity -> {
                    invocationCount1.incrementAndGet();
                };
            }, TestEntity.class, List.of(TestDataScope.DS1), null));

            var entityToUsedDataScopes = new ConcurrentHashMap<Object, Map<Object, Set<DataScope>>>();

            assertThrows(SocketTimeoutException.class, () -> asyncDataProcessor.processAllEntities(TestEntity.class, Arrays.asList(te1, te2), (entity) -> Arrays.asList(TestDataScope.DS1),
                    entity -> new TestEntityContext(entity), entityToUsedDataScopes));
        })
                .rollback();
    }

    @Test
    void testExceptionWithExceptionHandler() {
        StateRollback.chain(chain -> {
            AtomicInteger invocationCount1 = new AtomicInteger();
            TestEntity te1 = new TestEntity().setDomainRef(1);
            TestEntity te2 = new TestEntity().setDomainRef(2);
            var dataProcessor = (DataProcessor<TestEntity, TestEntityContext>) context -> {
                if (context.getCurrentEntity() == te1) {
                    throw new SocketTimeoutException();
                }
                return entity -> {
                    invocationCount1.incrementAndGet();
                };
            };
            chain.append(dataProcessorExtendable.registerDataProcessor(dataProcessor, TestEntity.class, List.of(TestDataScope.DS1), null));
            chain.append(dataProcessorExtendable.registerDataProcessorExceptionHandler(dataProcessor, new DataProcessorExceptionHandler() {
                @Override
                public <E, C> CheckedConsumer<E> handleProcessException(DataProcessor<E, C> dataProcessor, C context, Throwable e) {
                    throw new RuntimeException(e);
                }
            }));

            var entityToUsedDataScopes = new ConcurrentHashMap<Object, Map<Object, Set<DataScope>>>();

            var exception = assertThrows(RuntimeException.class, () -> asyncDataProcessor.processAllEntities(TestEntity.class, Arrays.asList(te1, te2), (entity) -> Arrays.asList(TestDataScope.DS1),
                    entity -> new TestEntityContext(entity), entityToUsedDataScopes));
            assertThat(exception.getCause()).isInstanceOf(SocketTimeoutException.class);
        })
                .rollback();
    }
}
