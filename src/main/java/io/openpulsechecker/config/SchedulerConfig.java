package io.openpulsechecker.config;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        SchedulerProperties.class,
        AlertingProperties.class,
        EmailAlertingProperties.class,
        TelegramAlertingProperties.class,
        SlackAlertingProperties.class,
        DiscordAlertingProperties.class,
        TeamsAlertingProperties.class
})
public class SchedulerConfig {

    @Bean
    public Clock appClock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService monitorCheckExecutor(SchedulerProperties schedulerProperties) {
        int poolSize = Math.max(1, schedulerProperties.workerPoolSize());
        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(poolSize * 100),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
