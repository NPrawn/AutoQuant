package com.ssafy.a107.realtime.infrastructure.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class RealtimeRedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RealtimeRedisConfig.class);

    @Bean
    public ThreadPoolTaskExecutor realtimeRedisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("realtime-redis-");
        // 수신 순서를 유지한다. 일시적인 과부하는 최신 시세를 우선하는 제한된 큐로 흡수한다.
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2048);
        executor.setRejectedExecutionHandler((task, pool) -> {
            log.warn("Realtime Redis relay queue is full or stopping; discarding pending update");
            new ThreadPoolExecutor.DiscardOldestPolicy().rejectedExecution(task, pool);
        });
        return executor;
    }

    @Bean
    public RedisMessageListenerContainer realtimeRedisContainer(
            RedisConnectionFactory connectionFactory,
            RealtimeRedisListener listener,
            @Qualifier("realtimeRedisExecutor") ThreadPoolTaskExecutor executor
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(executor);
        container.setErrorHandler(exception -> log.warn("Realtime Redis subscription failed: {}",
                exception.getClass().getSimpleName()));
        container.addMessageListener(listener, List.of(
                new ChannelTopic(RealtimeRedisChannels.TRADE),
                new ChannelTopic(RealtimeRedisChannels.LIVE_MINUTE_CANDLE)
        ));
        return container;
    }
}
