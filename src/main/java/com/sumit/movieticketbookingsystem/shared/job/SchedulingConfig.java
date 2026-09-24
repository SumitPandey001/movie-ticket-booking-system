package com.sumit.movieticketbookingsystem.shared.job;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Every scheduled job takes a ShedLock lock first, so with several instances running only one does each round.
 * Lock times come from the database clock, so instances with drifting clocks still agree.
 */
@Configuration(proxyBeanMethods = false)
@EnableSchedulerLock(defaultLockAtMostFor = "PT2M")
class SchedulingConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    /** The schedules themselves; jobs.enabled=false turns them off (tests run jobs by hand). */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnBooleanProperty(name = "jobs.enabled", matchIfMissing = true)
    static class Schedules {
    }
}
