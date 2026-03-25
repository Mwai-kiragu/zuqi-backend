package com.zuqi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Named task executor used by all {@code @Async} methods.
     *
     * <p>Keeps tuning/training threads alive through DevTools restarts because
     * Spring DevTools only restarts the application context, not the JVM. Threads
     * running on this executor continue until they complete naturally; they are not
     * killed by the context restart. The {@code waitForTasksToCompleteOnShutdown}
     * flag ensures a clean shutdown — threads finish their work before the JVM exits.
     *
     * <p>Sizing: 4 core threads cover concurrent training + tuning jobs without
     * overwhelming the machine. Queue capacity of 10 prevents OOM if many jobs are
     * submitted simultaneously.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("zuqi-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300); // wait up to 5 min for tuning to finish
        executor.initialize();
        return executor;
    }
}
