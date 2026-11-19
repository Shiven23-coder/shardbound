package com.shardbound.web;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Registers the {@link CurrentGame} resolver and hides it from the OpenAPI document. */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentGame.class);
    }

    private final CurrentGameResolver currentGameResolver;

    public WebConfig(CurrentGameResolver currentGameResolver) {
        this.currentGameResolver = currentGameResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentGameResolver);
    }
}
