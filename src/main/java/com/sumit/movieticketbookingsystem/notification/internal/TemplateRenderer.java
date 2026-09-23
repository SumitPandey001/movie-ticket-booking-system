package com.sumit.movieticketbookingsystem.notification.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
class TemplateRenderer {

    private final ITemplateEngine engine;

    TemplateRenderer(ITemplateEngine engine) {
        this.engine = engine;
    }

    /**
     * Boot's resolver handles the HTML emails; SMS templates are plain text, so they get their own resolver.
     * It only answers for {@code sms/*} names and goes first, so the two never compete.
     */
    @Bean
    static ITemplateResolver smsTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setResolvablePatterns(Set.of("sms/*"));
        resolver.setOrder(0);
        return resolver;
    }

    RenderedMessage render(NotificationType type, Channel channel, Map<String, Object> model) {
        String template = channel.name().toLowerCase(Locale.ROOT) + "/" + type.templateName();
        String body = engine.process(template, new Context(Locale.ENGLISH, model));
        return new RenderedMessage(type.subject((String) model.get("bookingRef")), body);
    }
}
