package com.dadcoach.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * A deployed Dad Coach (Render, or the prod profile) refuses to start misconfigured — before it serves a single
 * request, so the previous version keeps serving (playbook §19, Tair's guard). Names only in the message, never
 * values. Local runs and tests are not checked.
 *
 * <p>Checked: the platform is on (D-001: platform-only) with an https base URL and its key; the tool key; the
 * callback key when the callback receiver is on; every WhatsApp secret; the link-signing secret; the optional
 * admin key. Keys must be at least {@value #MIN_KEY_LENGTH} characters and never a development default that is
 * public in this repository.</p>
 */
@Component
// eager even with spring.main.lazy-initialization=true (the Dockerfile sets it): a lazy guard would never run
@org.springframework.context.annotation.Lazy(false)
public class ProductionStartupGuard implements SmartInitializingSingleton {

    static final int MIN_KEY_LENGTH = 24;
    static final int MIN_SECRET_LENGTH = 32;
    /** Development defaults that were committed to this repository - public, so never valid in production. */
    static final List<String> PUBLIC_DEFAULTS = List.of(
            "e76625b0ee030a88400a68b3dd9bf23fe24e657455f1dd08b295991073d439f6",
            "default-dev-secret-change-in-production-must-be-at-least-256-bits-long!!",
            "dad-coach-local-dev");

    private final Environment env;

    public ProductionStartupGuard(Environment env) {
        this.env = env;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!isDeployed()) {
            return;
        }
        List<String> problems = problems(new Settings(
                env.getProperty("workflow.platform.enabled", Boolean.class, false),
                env.getProperty("workflow.platform.base-url", ""),
                env.getProperty("workflow.platform.api-key", ""),
                env.getProperty("workflow.platform.worker-key", ""),
                env.getProperty("workflow.platform.workflow-key", ""),
                env.getProperty("tool-api.api-key", ""),
                env.getProperty("workflow.platform.scheduled-response.enabled", Boolean.class, false),
                env.getProperty("workflow.platform.scheduled-response.api-key", ""),
                env.getProperty("dad-coach.whatsapp.phone-number-id", ""),
                env.getProperty("dad-coach.whatsapp.access-token", ""),
                env.getProperty("dad-coach.whatsapp.webhook-secret", ""),
                env.getProperty("dad-coach.whatsapp.verify-token", ""),
                env.getProperty("dad-coach.security.link-secret", ""),
                env.getProperty("dad-coach.security.admin-api-key", ""),
                env.getProperty("dad-coach.web.base-url", "")));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start a deployed instance: " + String.join("; ", problems));
        }
    }

    private boolean isDeployed() {
        return env.getProperty("RENDER") != null || Arrays.asList(env.getActiveProfiles()).contains("prod");
    }

    record Settings(boolean platformEnabled, String platformBaseUrl, String platformApiKey, String workerKey,
                    String workflowKey, String toolKey, boolean callbackEnabled, String callbackKey,
                    String phoneNumberId, String accessToken, String webhookSecret, String verifyToken,
                    String linkSecret, String adminKey, String webBaseUrl) {
    }

    static List<String> problems(Settings s) {
        List<String> problems = new ArrayList<>();
        if (!s.platformEnabled()) {
            problems.add("WORKFLOW_PLATFORM_ENABLED must be true (Dad Coach runs only on the AI Workflow Platform)");
        }
        if (s.platformBaseUrl() == null || !s.platformBaseUrl().startsWith("https://")) {
            problems.add("WORKFLOW_PLATFORM_BASE_URL must be an https URL");
        }
        checkKey(problems, "WORKFLOW_PLATFORM_API_KEY", s.platformApiKey(), MIN_KEY_LENGTH, true);
        if (blank(s.workerKey())) {
            problems.add("WORKFLOW_PLATFORM_WORKER_KEY is required");
        }
        if (blank(s.workflowKey())) {
            problems.add("WORKFLOW_PLATFORM_WORKFLOW_KEY is required");
        }
        checkKey(problems, "TOOL_API_KEY", s.toolKey(), MIN_KEY_LENGTH, true);
        if (s.callbackEnabled()) {
            checkKey(problems, "WORKFLOW_PLATFORM_CALLBACK_API_KEY", s.callbackKey(), MIN_KEY_LENGTH, true);
        }
        if (blank(s.phoneNumberId())) {
            problems.add("WHATSAPP_PHONE_NUMBER_ID is required");
        }
        checkKey(problems, "WHATSAPP_ACCESS_TOKEN", s.accessToken(), 1, true);
        checkKey(problems, "WHATSAPP_WEBHOOK_SECRET", s.webhookSecret(), 1, true);
        checkKey(problems, "WHATSAPP_VERIFY_TOKEN", s.verifyToken(), 1, true);
        checkKey(problems, "JWT_SECRET (link signing)", s.linkSecret(), MIN_SECRET_LENGTH, true);
        checkKey(problems, "DADCOACH_ADMIN_API_KEY", s.adminKey(), MIN_KEY_LENGTH, false);
        if (s.webBaseUrl() == null || !s.webBaseUrl().startsWith("https://")) {
            problems.add("WEB_BASE_URL must be an https URL (the dashboard links the coach sends)");
        }
        return problems;
    }

    private static void checkKey(List<String> problems, String name, String value, int minLength, boolean required) {
        if (blank(value)) {
            if (required) {
                problems.add(name + " is required");
            }
        } else if (PUBLIC_DEFAULTS.contains(value) || value.startsWith("default-dev-secret")) {
            problems.add(name + " is a public development default");
        } else if (value.length() < minLength) {
            problems.add(name + " must be at least " + minLength + " characters");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
