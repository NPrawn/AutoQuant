package com.ssafy.a107.trading.backtest.messaging;

import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class BacktestJobPublisher {

    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbitTemplate;
    private final BacktestJobQueueProperties properties;

    public BacktestJobPublisher(
            RabbitTemplate rabbitTemplate,
            BacktestJobQueueProperties properties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(BacktestJobMessage message) {
        Objects.requireNonNull(message);

        CorrelationData correlationData = new CorrelationData(
                message.jobId().toString()
        );
        rabbitTemplate.convertAndSend(
                properties.exchange(),
                properties.routingKey(),
                message,
                correlationData
        );

        awaitBrokerConfirmation(correlationData);
    }

    private void awaitBrokerConfirmation(CorrelationData correlationData) {
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(
                    CONFIRM_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException(
                        "Backtest job publish was not acknowledged by RabbitMQ"
                );
            }

            ReturnedMessage returnedMessage = correlationData.getReturned();
            if (returnedMessage != null) {
                throw new IllegalStateException(
                        "Backtest job publish was not routed to a queue"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Backtest job publish confirmation was interrupted",
                    exception
            );
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException(
                    "Backtest job publish was not confirmed by RabbitMQ",
                    exception
            );
        }
    }
}
