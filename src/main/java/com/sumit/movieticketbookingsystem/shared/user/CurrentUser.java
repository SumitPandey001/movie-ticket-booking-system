package com.sumit.movieticketbookingsystem.shared.user;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;
import java.util.UUID;

public record CurrentUser(UUID id, Role role, String name, String email, String phone) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public static Optional<CurrentUser> fromCurrentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        Object user = attributes.getAttribute(CurrentUserInterceptor.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return Optional.ofNullable((CurrentUser) user);
    }
}
