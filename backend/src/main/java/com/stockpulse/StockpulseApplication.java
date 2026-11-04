package com.stockpulse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@SpringBootApplication
@EnableAsync
public class StockpulseApplication {
    public static void main(String[] args){ SpringApplication.run(StockpulseApplication.class,args); }
    @Bean("recommendationExecutor") Executor recommendationExecutor() {
        ThreadPoolTaskExecutor executor=new ThreadPoolTaskExecutor(); executor.setCorePoolSize(2); executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100); executor.setThreadNamePrefix("recommendation-"); executor.setWaitForTasksToCompleteOnShutdown(true); executor.initialize(); return executor;
    }
}
