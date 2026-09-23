package com.sumit.movieticketbookingsystem.shared.persistence;

import com.sumit.movieticketbookingsystem.shared.user.CurrentUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "currentUserAuditor", dateTimeProviderRef = "auditingDateTimeProvider")
class JpaAuditingConfig {

    // Empty outside an HTTP request (jobs, event listeners), which leaves created_by / updated_by null.
    @Bean
    AuditorAware<UUID> currentUserAuditor() {
        return () -> CurrentUser.fromCurrentRequest().map(CurrentUser::id);
    }

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
