package com.sumit.movieticketbookingsystem.shared.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds the CurrentUser from the headers the gateway sets and stores it on the request.
 * The gateway has already authenticated the caller, so the headers are trusted as-is.
 */
class CurrentUserInterceptor implements HandlerInterceptor {

    static final String ATTRIBUTE = CurrentUser.class.getName();

    private static final String USER_ID = "X-User-Id";
    private static final String USER_ROLE = "X-User-Role";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID id = required(request, USER_ID, UUID::fromString);
        Role role = required(request, USER_ROLE, value -> Role.valueOf(value.toUpperCase(Locale.ROOT)));

        request.setAttribute(ATTRIBUTE, new CurrentUser(id, role,
                request.getHeader("X-User-Name"),
                request.getHeader("X-User-Email"),
                request.getHeader("X-User-Phone")));
        return true;
    }

    private static <T> T required(HttpServletRequest request, String header, Function<String, T> parser) {
        String value = request.getHeader(header);
        if (value == null || value.isBlank()) {
            throw new UnauthenticatedException(header);
        }
        try {
            return parser.apply(value.strip());
        } catch (IllegalArgumentException e) {
            throw new UnauthenticatedException(header);
        }
    }
}
