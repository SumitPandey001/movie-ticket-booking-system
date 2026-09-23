package com.sumit.movieticketbookingsystem.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    // Anything time-dependent takes this bean instead of calling Instant.now(), so tests can move time.
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
