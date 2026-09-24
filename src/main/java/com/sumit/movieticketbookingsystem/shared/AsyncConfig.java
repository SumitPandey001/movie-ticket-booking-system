package com.sumit.movieticketbookingsystem.shared;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Module event listeners run asynchronously after commit, on Boot's virtual-thread task executor.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfig {
}
