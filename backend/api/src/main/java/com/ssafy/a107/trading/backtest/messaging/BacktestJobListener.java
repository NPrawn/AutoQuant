package com.ssafy.a107.trading.backtest.messaging;

import com.ssafy.a107.trading.backtest.service.BacktestJobExecutionService;
import com.ssafy.a107.trading.backtest.service.BacktestJobFailureService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Profile("backtest-worker")
public class BacktestJobListener {

    private final BacktestJobExecutionService backtestJobExecutionService;
    private final BacktestJobFailureService backtestJobFailureService;

    public BacktestJobListener(
            BacktestJobExecutionService backtestJobExecutionService,
            BacktestJobFailureService backtestJobFailureService
    ) {
        this.backtestJobExecutionService = Objects.requireNonNull(
                backtestJobExecutionService
        );
        this.backtestJobFailureService = Objects.requireNonNull(
                backtestJobFailureService
        );
    }

    @RabbitListener(queues = "${backtest.job.queue}")
    public void consume(BacktestJobMessage message, Message rawMessage) {
        try {
            backtestJobExecutionService.execute(
                    message,
                    Boolean.TRUE.equals(
                            rawMessage.getMessageProperties().isRedelivered()
                    )
            );
        } catch (RuntimeException exception) {
            boolean failed = backtestJobFailureService.fail(
                    message.backtestId(),
                    exception
            );
            if (failed) {
                throw new AmqpRejectAndDontRequeueException(
                        "Backtest job execution failed",
                        exception
                );
            }
        }
    }
}
