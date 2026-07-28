package com.dadcoach.api.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RolePermissionTest {

    @Test
    void fatherShouldOwnTheirOwnResource() {
        UUID fatherId = UUID.randomUUID();
        ActorContext actor = new ActorContext(ActorType.FATHER, fatherId);

        assertThat(RolePermission.isOwner(actor, fatherId)).isTrue();
    }

    @Test
    void fatherShouldNotOwnAnotherFathersResource() {
        UUID fatherId = UUID.randomUUID();
        UUID otherFatherId = UUID.randomUUID();
        ActorContext actor = new ActorContext(ActorType.FATHER, fatherId);

        assertThat(RolePermission.isOwner(actor, otherFatherId)).isFalse();
    }

    @Test
    void adminShouldAccessAnyResource() {
        UUID adminId = UUID.randomUUID();
        UUID resourceOwnerId = UUID.randomUUID();
        ActorContext actor = new ActorContext(ActorType.ADMIN, adminId);

        assertThat(RolePermission.isOwner(actor, resourceOwnerId)).isTrue();
    }

    @Test
    void serviceShouldAccessAnyResource() {
        UUID serviceId = UUID.randomUUID();
        UUID resourceOwnerId = UUID.randomUUID();
        ActorContext actor = new ActorContext(ActorType.SERVICE, serviceId);

        assertThat(RolePermission.isOwner(actor, resourceOwnerId)).isTrue();
    }

    @Test
    void shouldReturnFalse_whenActorIsNull() {
        assertThat(RolePermission.isOwner(null, UUID.randomUUID())).isFalse();
    }

    @Test
    void shouldReturnFalse_whenResourceFatherIdIsNull() {
        ActorContext actor = new ActorContext(ActorType.FATHER, UUID.randomUUID());

        assertThat(RolePermission.isOwner(actor, null)).isFalse();
    }

    @Test
    void assertOwnershipShouldPass_whenFatherOwnsResource() {
        UUID fatherId = UUID.randomUUID();
        ActorContext actor = new ActorContext(ActorType.FATHER, fatherId);
        UUID childId = UUID.randomUUID();

        // Should not throw
        RolePermission.assertOwnership(actor, fatherId, "Child", childId);
    }

    @Test
    void assertOwnershipShouldThrowResourceNotOwnedException_whenFatherDoesNotOwnResource() {
        UUID fatherId = UUID.randomUUID();
        UUID otherFatherId = UUID.randomUUID();
        ActorContext actor = new ActorContext(ActorType.FATHER, fatherId);
        UUID childId = UUID.randomUUID();

        assertThatThrownBy(() ->
                RolePermission.assertOwnership(actor, otherFatherId, "Child", childId))
                .isInstanceOf(RolePermission.ResourceNotOwnedException.class)
                .hasMessageContaining("Child")
                .hasMessageContaining(childId.toString());
    }

    @Test
    void assertOwnershipShouldPass_forAdmin_evenWhenNotOwner() {
        UUID adminId = UUID.randomUUID();
        UUID resourceOwnerId = UUID.randomUUID();
        ActorContext actor = new ActorContext(ActorType.ADMIN, adminId);
        UUID resourceId = UUID.randomUUID();

        // Should not throw — admin can access any resource
        RolePermission.assertOwnership(actor, resourceOwnerId, "Child", resourceId);
    }

    @Test
    void adminShouldHaveReadPermission() {
        ActorContext actor = new ActorContext(ActorType.ADMIN, UUID.randomUUID());

        assertThat(RolePermission.hasAdminPermission(actor, RolePermission.AdminPermission.READ)).isTrue();
    }

    @Test
    void fatherShouldNotHaveAdminPermission() {
        ActorContext actor = new ActorContext(ActorType.FATHER, UUID.randomUUID());

        assertThat(RolePermission.hasAdminPermission(actor, RolePermission.AdminPermission.READ)).isFalse();
    }

    @Test
    void shouldReturnFalse_whenNullActor_forAdminPermission() {
        assertThat(RolePermission.hasAdminPermission(null, RolePermission.AdminPermission.READ)).isFalse();
    }

    @Test
    void resourceNotOwnedException_shouldContainResourceDetails() {
        UUID resourceId = UUID.randomUUID();
        var exception = new RolePermission.ResourceNotOwnedException("Goal", resourceId);

        assertThat(exception.getResourceType()).isEqualTo("Goal");
        assertThat(exception.getResourceId()).isEqualTo(resourceId);
    }
}
