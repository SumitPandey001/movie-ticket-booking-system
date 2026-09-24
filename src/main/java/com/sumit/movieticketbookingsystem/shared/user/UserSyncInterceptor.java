package com.sumit.movieticketbookingsystem.shared.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Records the caller in app_user. Runs after CurrentUserInterceptor, which has already
 * rejected requests without a valid user.
 */
class UserSyncInterceptor implements HandlerInterceptor {

    private final UserDirectory directory;

    UserSyncInterceptor(UserDirectory directory) {
        this.directory = directory;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        directory.sync((CurrentUser) request.getAttribute(CurrentUserInterceptor.ATTRIBUTE));
        return true;
    }
}
