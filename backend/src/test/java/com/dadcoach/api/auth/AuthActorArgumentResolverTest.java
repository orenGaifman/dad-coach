package com.dadcoach.api.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthActorArgumentResolverTest {

    private AuthActorArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AuthActorArgumentResolver();
        ActorContext.clear();
    }

    @AfterEach
    void tearDown() {
        ActorContext.clear();
    }

    @Test
    void shouldSupportParameterWithAuthActorAnnotation() throws Exception {
        MethodParameter param = getMethodParameter("annotatedMethod", ActorContext.class);

        assertThat(resolver.supportsParameter(param)).isTrue();
    }

    @Test
    void shouldNotSupportParameterWithoutAnnotation() throws Exception {
        MethodParameter param = getMethodParameter("nonAnnotatedMethod", ActorContext.class);

        assertThat(resolver.supportsParameter(param)).isFalse();
    }

    @Test
    void shouldNotSupportWrongParameterType() throws Exception {
        MethodParameter param = getMethodParameter("wrongTypeMethod", String.class);

        assertThat(resolver.supportsParameter(param)).isFalse();
    }

    @Test
    void shouldResolveCurrentActorContext() throws Exception {
        UUID fatherId = UUID.randomUUID();
        ActorContext expected = new ActorContext(ActorType.FATHER, fatherId);
        ActorContext.set(expected);

        MethodParameter param = getMethodParameter("annotatedMethod", ActorContext.class);
        Object result = resolver.resolveArgument(param, new ModelAndViewContainer(), null, null);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void shouldThrowUnauthorizedActorException_whenNoContext() throws Exception {
        MethodParameter param = getMethodParameter("annotatedMethod", ActorContext.class);

        assertThatThrownBy(() ->
                resolver.resolveArgument(param, new ModelAndViewContainer(), null, null))
                .isInstanceOf(AuthActorArgumentResolver.UnauthorizedActorException.class);
    }

    // --- Test helper methods to simulate controller parameters ---

    @SuppressWarnings("unused")
    private static void annotatedMethod(@AuthActor ActorContext actor) {
    }

    @SuppressWarnings("unused")
    private static void nonAnnotatedMethod(ActorContext actor) {
    }

    @SuppressWarnings("unused")
    private static void wrongTypeMethod(@AuthActor String notAnActorContext) {
    }

    private MethodParameter getMethodParameter(String methodName, Class<?> paramType) throws Exception {
        Method method = AuthActorArgumentResolverTest.class.getDeclaredMethod(methodName, paramType);
        return new MethodParameter(method, 0);
    }
}
