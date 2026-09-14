package com.ssafy.a107.trading.backtest.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@DisplayName("백테스트 작업 커밋 후 즉시 발행")
class BacktestJobPublishAfterCommitIntegrationTest {

    @Test
    @DisplayName("커밋 후 발행을 새 트랜잭션에서 처리한다")
    void publishesInNewTransactionAfterCommit() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                TestConfiguration.class
        )) {
            BacktestJobOutboxRepository repository = context.getBean(
                    BacktestJobOutboxRepository.class
            );
            BacktestJobPublisher publisher = context.getBean(BacktestJobPublisher.class);
            RecordingTransactionManager transactionManager = context.getBean(
                    RecordingTransactionManager.class
            );
            BacktestJobMessage message = message();
            BacktestJobOutbox outbox = new BacktestJobOutbox(message);
            given(repository.findByJobIdForUpdate(message.jobId()))
                    .willReturn(Optional.of(outbox));

            new TransactionTemplate(transactionManager).executeWithoutResult(
                    status -> context.publishEvent(new BacktestJobQueuedEvent(message))
            );

            assertThat(outbox.isPublished()).isTrue();
            then(publisher).should().publish(message);
            assertThat(transactionManager.propagationBehaviors())
                    .containsExactly(
                            TransactionDefinition.PROPAGATION_REQUIRED,
                            TransactionDefinition.PROPAGATION_REQUIRES_NEW
                    );
        }
    }

    private BacktestJobMessage message() {
        return new BacktestJobMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                "market-v1",
                "engine-v1"
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        RecordingTransactionManager transactionManager() {
            return new RecordingTransactionManager();
        }

        @Bean
        BacktestJobOutboxRepository backtestJobOutboxRepository() {
            return mock(BacktestJobOutboxRepository.class);
        }

        @Bean
        BacktestJobPublisher backtestJobPublisher() {
            return mock(BacktestJobPublisher.class);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        BacktestJobOutboxService backtestJobOutboxService(
                BacktestJobOutboxRepository repository,
                BacktestJobPublisher publisher,
                Clock clock
        ) {
            return new BacktestJobOutboxService(repository, publisher, clock);
        }

        @Bean
        BacktestJobPublishEventListener backtestJobPublishEventListener(
                BacktestJobOutboxService service
        ) {
            return new BacktestJobPublishEventListener(service);
        }
    }

    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private final ThreadLocal<TransactionState> currentTransaction = new ThreadLocal<>();
        private final List<Integer> propagationBehaviors = new ArrayList<>();

        List<Integer> propagationBehaviors() {
            return List.copyOf(propagationBehaviors);
        }

        @Override
        protected Object doGetTransaction() {
            TransactionState transaction = currentTransaction.get();
            return transaction != null ? transaction : new TransactionState();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((TransactionState) transaction).active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            TransactionState transactionState = (TransactionState) transaction;
            transactionState.active = true;
            currentTransaction.set(transactionState);
            propagationBehaviors.add(definition.getPropagationBehavior());
        }

        @Override
        protected Object doSuspend(Object transaction) {
            currentTransaction.remove();
            return transaction;
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
            currentTransaction.set((TransactionState) suspendedResources);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            TransactionState transactionState = (TransactionState) transaction;
            if (currentTransaction.get() == transactionState) {
                currentTransaction.remove();
            }
            transactionState.active = false;
        }
    }

    static class TransactionState {

        private boolean active;
    }
}
