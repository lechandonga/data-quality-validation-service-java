package com.github.lechandonga.dataqualityvalidationservicejava.config;

import com.github.lechandonga.dataqualityvalidationservicejava.engine.ValidationEngine;
import com.github.lechandonga.dataqualityvalidationservicejava.service.AlertProperties;
import com.github.lechandonga.dataqualityvalidationservicejava.service.AlertService;
import com.github.lechandonga.dataqualityvalidationservicejava.service.InMemoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 服务 Bean 装配。{@link Clock} 可在测试中替换为固定时钟，保证时效与窗口结论可复现。
 */
@Configuration
@EnableConfigurationProperties(AlertProperties.class)
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public InMemoryStore store() {
        return new InMemoryStore();
    }

    @Bean
    public ValidationEngine validationEngine() {
        return new ValidationEngine();
    }

    @Bean
    public ExecutorService taskExecutor() {
        return Executors.newFixedThreadPool(8, r -> {
            Thread thread = new Thread(r, "validation-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public AlertService alertService(InMemoryStore store, Clock clock, AlertProperties properties) {
        return new AlertService(store, clock, properties);
    }
}
