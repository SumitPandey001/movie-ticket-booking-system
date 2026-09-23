package com.sumit.movieticketbookingsystem.shared.user;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == CurrentUser.class;
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object user = webRequest.getAttribute(CurrentUserInterceptor.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (user == null) {
            // controller outside /api/v1/** asked for a user; the interceptor never ran
            throw new IllegalStateException("No CurrentUser on request " + webRequest.getDescription(false));
        }
        return (CurrentUser) user;
    }
}
