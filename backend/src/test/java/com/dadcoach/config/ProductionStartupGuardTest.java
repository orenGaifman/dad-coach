package com.dadcoach.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Playbook §19: a deployed instance with production's configuration starts; a weak or missing one never does. */
class ProductionStartupGuardTest {

    static final String KEY = "a".repeat(64);

    /** The shape of production's configuration (values are fakes; the callback key references the tool key). */
    static MockEnvironment productionLike() {
        return new MockEnvironment()
                .withProperty("RENDER", "true")
                .withProperty("workflow.platform.enabled", "true")
                .withProperty("workflow.platform.base-url", "https://platform.example.onrender.com")
                .withProperty("workflow.platform.api-key", "p".repeat(40))
                .withProperty("workflow.platform.worker-key", "dad_3")
                .withProperty("workflow.platform.workflow-key", "dad-coach-3")
                .withProperty("tool-api.api-key", KEY)
                .withProperty("workflow.platform.scheduled-response.enabled", "true")
                .withProperty("workflow.platform.scheduled-response.api-key", KEY)
                .withProperty("dad-coach.whatsapp.phone-number-id", "100000000000001")
                .withProperty("dad-coach.whatsapp.access-token", "EAAG-test")
                .withProperty("dad-coach.whatsapp.webhook-secret", "whsec")
                .withProperty("dad-coach.whatsapp.verify-token", "verify")
                .withProperty("dad-coach.security.link-secret", "s".repeat(63))
                .withProperty("dad-coach.security.admin-api-key", "b".repeat(64))
                .withProperty("dad-coach.web.base-url", "https://dashboard.example.onrender.com");
    }

    @Test
    void productionsConfigurationStarts() {
        assertThatCode(() -> new ProductionStartupGuard(productionLike()).afterSingletonsInstantiated()).doesNotThrowAnyException();
    }

    @Test
    void localRunsAreNotChecked() {
        assertThatCode(() -> new ProductionStartupGuard(new MockEnvironment()).afterSingletonsInstantiated()).doesNotThrowAnyException();
    }

    @Test
    void aDeployedInstanceRefusesEveryMissingOrWeakSecret() {
        MockEnvironment env = productionLike()
                .withProperty("workflow.platform.enabled", "false")
                .withProperty("workflow.platform.base-url", "http://insecure")
                .withProperty("tool-api.api-key", "e76625b0ee030a88400a68b3dd9bf23fe24e657455f1dd08b295991073d439f6")
                .withProperty("workflow.platform.scheduled-response.api-key", "short")
                .withProperty("dad-coach.whatsapp.webhook-secret", "")
                .withProperty("dad-coach.security.link-secret", "default-dev-secret-change-in-production-must-be-at-least-256-bits-long!!")
                .withProperty("dad-coach.web.base-url", "http://localhost:3000");
        assertThatThrownBy(() -> new ProductionStartupGuard(env).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WORKFLOW_PLATFORM_ENABLED")
                .hasMessageContaining("WORKFLOW_PLATFORM_BASE_URL")
                .hasMessageContaining("TOOL_API_KEY is a public development default")
                .hasMessageContaining("WORKFLOW_PLATFORM_CALLBACK_API_KEY must be at least")
                .hasMessageContaining("WHATSAPP_WEBHOOK_SECRET is required")
                .hasMessageContaining("JWT_SECRET (link signing) is a public development default")
                .hasMessageContaining("WEB_BASE_URL")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("e76625b0"));
    }

    @Test
    void theProdProfileAloneAlsoCounts() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertThatThrownBy(() -> new ProductionStartupGuard(env).afterSingletonsInstantiated())
                .hasMessageContaining("TOOL_API_KEY is required");
    }
}
