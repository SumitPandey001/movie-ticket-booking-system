package com.sumit.movieticketbookingsystem.shared.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Second line of defence for admin paths. The gateway already blocks non-admins,
 * but the service shouldn't rely on that alone.
 */
class AdminOnlyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        CurrentUser user = (CurrentUser) request.getAttribute(CurrentUserInterceptor.ATTRIBUTE);
        if (user == null || !user.isAdmin()) {
            throw new ForbiddenException();
        }
        return true;
    }
}
