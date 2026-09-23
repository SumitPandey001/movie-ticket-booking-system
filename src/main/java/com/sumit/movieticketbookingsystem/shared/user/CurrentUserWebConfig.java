package com.sumit.movieticketbookingsystem.shared.user;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
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
        registry.addInterceptor(new CurrentUserInterceptor()).addPathPatterns("/api/v1/**");
        registry.addInterceptor(new UserSyncInterceptor(directory)).addPathPatterns("/api/v1/**");
        registry.addInterceptor(new AdminOnlyInterceptor()).addPathPatterns("/api/v1/admin/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
