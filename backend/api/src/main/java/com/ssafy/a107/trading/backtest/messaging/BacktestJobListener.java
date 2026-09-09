package com.ssafy.a107.trading.backtest.messaging;

import com.ssafy.a107.trading.backtest.service.BacktestJobExecutionService;
import com.ssafy.a107.trading.backtest.service.BacktestJobFailureService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Profile("backtest-worker")
public class BacktestJobListener {

    private final BacktestJobExecutionService backtestJobExecutionService;
    private final BacktestJobFailureService backtestJobFailureService;
    private final BacktestJobDeadLetterPublisher backtestJobDeadLetterPublisher;

    public BacktestJobListener(
            BacktestJobExecutionService backtestJobExecutionService,
            BacktestJobFailureService backtestJobFailureService,
            BacktestJobDeadLetterPublisher backtestJobDeadLetterPublisher
    ) {
        this.backtestJobExecutionService = Objects.requireNonNull(
                backtestJobExecutionService
        );
        this.backtestJobFailureService = Objects.requireNonNull(
                backtestJobFailureService
        );
        this.backtestJobDeadLetterPublisher = Objects.requireNonNull(
                backtestJobDeadLetterPublisher
        );
    }

    @RabbitListener(queues = "${backtest.job.queue}")
    public void consume(BacktestJobMessage message) {
        try {
            backtestJobExecutionService.execute(message);
        } catch (RuntimeException exception) {
            boolean failed = backtestJobFailureService.fail(
                    message.backtestId(),
                    exception
            );
            if (failed) {
                backtestJobDeadLetterPublisher.publish(message, exception);
            }
        }
    }
}
