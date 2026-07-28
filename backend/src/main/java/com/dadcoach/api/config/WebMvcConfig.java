package com.dadcoach.api.config;

import com.dadcoach.api.auth.AuthActorArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC configuration for custom argument resolvers and other MVC customizations.
 * <p>
 * Registers the {@link AuthActorArgumentResolver} so that controller parameters
 * annotated with {@link com.dadcoach.api.auth.AuthActor @AuthActor} are automatically
 * resolved from the current {@link com.dadcoach.api.auth.ActorContext}.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthActorArgumentResolver authActorArgumentResolver;

    public WebMvcConfig(AuthActorArgumentResolver authActorArgumentResolver) {
        this.authActorArgumentResolver = authActorArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authActorArgumentResolver);
    }
}
