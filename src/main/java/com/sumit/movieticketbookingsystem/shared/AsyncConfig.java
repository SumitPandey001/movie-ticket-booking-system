package com.sumit.movieticketbookingsystem.shared;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @ApplicationModuleListener}s run asynchronously after commit, on Boot's (virtual-thread) task executor.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfig {
}
