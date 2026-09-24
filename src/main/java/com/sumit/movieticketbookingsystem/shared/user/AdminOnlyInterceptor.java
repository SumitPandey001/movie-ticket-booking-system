package com.sumit.movieticketbookingsystem.shared.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rejects non-admins on admin paths. The gateway blocks them too; this keeps the service from depending on it.
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
