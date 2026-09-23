package com.sumit.movieticketbookingsystem.shared.user;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * The caller, as identified by the API gateway. Declare it as a controller parameter to get it injected.
 * Name, email and phone are optional; the gateway only sends what the user has on file.
 */
public record CurrentUser(UUID id, Role role, String name, String email, String phone) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * For code that isn't a controller (e.g. JPA auditing). Empty when not inside an API request.
     */
    public static Optional<CurrentUser> fromCurrentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                (CurrentUser) attributes.getAttribute(CurrentUserInterceptor.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
    }
}
