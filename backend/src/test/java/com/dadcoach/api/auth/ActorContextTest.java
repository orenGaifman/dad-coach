package com.dadcoach.api.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActorContextTest {

    @AfterEach
    void tearDown() {
        ActorContext.clear();
    }

    @Test
    void shouldStoreAndRetrieveActorContext() {
        UUID actorId = UUID.randomUUID();
        ActorContext context = new ActorContext(ActorType.FATHER, actorId);

        ActorContext.set(context);

        assertThat(ActorContext.current()).isSameAs(context);
        assertThat(ActorContext.current().getActorType()).isEqualTo(ActorType.FATHER);
        assertThat(ActorContext.current().getActorId()).isEqualTo(actorId);
    }

    @Test
    void shouldReturnNull_whenNoContextSet() {
        assertThat(ActorContext.current()).isNull();
    }

    @Test
    void shouldClearContext() {
        ActorContext.set(new ActorContext(ActorType.ADMIN, UUID.randomUUID()));

        ActorContext.clear();

        assertThat(ActorContext.current()).isNull();
    }

    @Test
    void shouldIdentifyFatherActor() {
        ActorContext context = new ActorContext(ActorType.FATHER, UUID.randomUUID());

        assertThat(context.isFather()).isTrue();
        assertThat(context.isAdmin()).isFalse();
        assertThat(context.isService()).isFalse();
    }

    @Test
    void shouldIdentifyAdminActor() {
        ActorContext context = new ActorContext(ActorType.ADMIN, UUID.randomUUID());

        assertThat(context.isFather()).isFalse();
        assertThat(context.isAdmin()).isTrue();
        assertThat(context.isService()).isFalse();
    }

    @Test
    void shouldIdentifyServiceActor() {
        ActorContext context = new ActorContext(ActorType.SERVICE, UUID.randomUUID());

        assertThat(context.isFather()).isFalse();
        assertThat(context.isAdmin()).isFalse();
        assertThat(context.isService()).isTrue();
    }

    @Test
    void shouldRejectNullActorType() {
        assertThatThrownBy(() -> new ActorContext(null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actorType");
    }

    @Test
    void shouldRejectNullActorId() {
        assertThatThrownBy(() -> new ActorContext(ActorType.FATHER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actorId");
    }
}
