package com.tinytrail.config;

import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AppConfig implements WebMvcConfigurer {

    private final AppProperties props;

    /** Injected (not Instant.now()) so expiry logic is testable with a fixed clock. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Dedicated bounded pool for click tracking so a slow DB can never starve request threads.
     * When the queue is full we DROP the click (DiscardPolicy) instead of CallerRuns: a lost
     * analytics row is acceptable, a redirect that waits on the DB is not.
     */
    @Bean("clickExecutor")
    ThreadPoolTaskExecutor clickExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("click-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return ex;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.corsOrigin().split(","))
                .allowedMethods("GET", "POST")
                .exposedHeaders("Retry-After"); // lets the browser read it on 429
    }
}
