package com.ssafy.a107.trading.backtest.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("백테스트 작업 큐 토폴로지")
class BacktestJobQueueConfigTest {

    private static final BacktestJobQueueProperties PROPERTIES = new BacktestJobQueueProperties(
            "backtest.exchange",
            "backtest.queue",
            "backtest.run",
            "backtest.dead-letter.exchange",
            "backtest.dead-letter.queue",
            "backtest.dead-letter"
    );

    private final BacktestJobQueueConfig config = new BacktestJobQueueConfig();

    @Test
    @DisplayName("내구성 있는 Direct Exchange, Queue, Binding을 생성한다")
    void createsDurableTopology() {
        DirectExchange exchange = config.backtestJobExchange(PROPERTIES);
        Queue queue = config.backtestJobQueue(PROPERTIES);
        Binding binding = config.backtestJobBinding(queue, exchange, PROPERTIES);

        assertThat(exchange.getName()).isEqualTo(PROPERTIES.exchange());
        assertThat(exchange.isDurable()).isTrue();
        assertThat(queue.getName()).isEqualTo(PROPERTIES.queue());
        assertThat(queue.isDurable()).isTrue();
        assertThat(binding.getExchange()).isEqualTo(PROPERTIES.exchange());
        assertThat(binding.getDestination()).isEqualTo(PROPERTIES.queue());
        assertThat(binding.getRoutingKey()).isEqualTo(PROPERTIES.routingKey());

        DirectExchange deadLetterExchange = config
                .backtestJobDeadLetterExchange(PROPERTIES);
        Queue deadLetterQueue = config.backtestJobDeadLetterQueue(PROPERTIES);
        Binding deadLetterBinding = config.backtestJobDeadLetterBinding(
                deadLetterQueue,
                deadLetterExchange,
                PROPERTIES
        );

        assertThat(deadLetterExchange.getName())
                .isEqualTo(PROPERTIES.deadLetterExchange());
        assertThat(deadLetterQueue.getName())
                .isEqualTo(PROPERTIES.deadLetterQueue());
        assertThat(deadLetterBinding.getRoutingKey())
                .isEqualTo(PROPERTIES.deadLetterRoutingKey());
    }
}
