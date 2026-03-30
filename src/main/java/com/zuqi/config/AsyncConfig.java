package com.zuqi.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Named task executor used by all {@code @Async} methods.
     *
     * <p>Wrapped with {@link DelegatingSecurityContextAsyncTaskExecutor} so that
     * the Spring {@code SecurityContext} (and therefore the authenticated user) is
     * propagated to every async thread. Without this, {@code SecurityContextHolder}
     * would return {@code null} inside {@code @Async} methods because they run on a
     * different thread that never received the ThreadLocal security context.
     *
     * <p>Keeps tuning/training threads alive through DevTools restarts because
     * Spring DevTools only restarts the application context, not the JVM.
     * The {@code waitForTasksToCompleteOnShutdown} flag ensures a clean shutdown.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("zuqi-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300);
        executor.initialize();
        // Propagate SecurityContext to @Async threads so getCurrentUser() works
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
            log.error("Uncaught exception in @Async method {}: {}", method.getName(), ex.getMessage(), ex);
    }
}
