package com.dadcoach.api.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves controller method parameters annotated with {@link AuthActor}
 * by providing the current {@link ActorContext} from thread-local storage.
 * <p>
 * If no ActorContext is available (unauthenticated request) and the annotation
 * has {@code required = true} (default), throws an {@link UnauthorizedActorException}
 * that results in a 401 response. If {@code required = false}, returns null.
 */
@Component
public class AuthActorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthActor.class)
                && ActorContext.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        ActorContext context = ActorContext.current();
        
        if (context == null) {
            AuthActor annotation = parameter.getParameterAnnotation(AuthActor.class);
            boolean required = annotation == null || annotation.required();
            
            if (required) {
                throw new UnauthorizedActorException();
            }
            // If not required, return null
            return null;
        }
        return context;
    }

    /**
     * Exception thrown when @AuthActor is used but no authenticated actor is present.
     * Should be mapped to 401 Unauthorized by the global exception handler.
     */
    public static class UnauthorizedActorException extends RuntimeException {
        public UnauthorizedActorException() {
            super("No authenticated actor context available");
        }
    }
}
