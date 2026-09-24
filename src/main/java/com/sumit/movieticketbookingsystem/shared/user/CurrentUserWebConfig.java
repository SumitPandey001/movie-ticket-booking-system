package com.sumit.movieticketbookingsystem.shared.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class CurrentUserWebConfig implements WebMvcConfigurer {

    private final UserDirectory directory;

    CurrentUserWebConfig(UserDirectory directory) {
        this.directory = directory;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // order matters: the others read the user the first interceptor stored
        registry.addInterceptor(skippingPreflight(new CurrentUserInterceptor())).addPathPatterns("/api/v1/**");
        registry.addInterceptor(skippingPreflight(new UserSyncInterceptor(directory))).addPathPatterns("/api/v1/**");
        registry.addInterceptor(skippingPreflight(new AdminOnlyInterceptor())).addPathPatterns("/api/v1/admin/**");
    }

    // A browser's CORS preflight carries no user headers and runs no controller; Spring still passes it through
    // interceptors, so let it by. The real request that follows is checked as usual.
    private static HandlerInterceptor skippingPreflight(HandlerInterceptor interceptor) {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws Exception {
                return CorsUtils.isPreFlightRequest(request) || interceptor.preHandle(request, response, handler);
            }
        };
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
