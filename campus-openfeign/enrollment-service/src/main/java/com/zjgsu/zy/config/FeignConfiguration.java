package com.zjgsu.zy.config;
import feign.Retryer;
import org.springframework.context.annotation.*;
@Configuration
public class FeignConfiguration {
    // Mutation requests must not be silently replayed after an ambiguous timeout.
    @Bean
    Retryer feignRetryer() { return Retryer.NEVER_RETRY; }
}
