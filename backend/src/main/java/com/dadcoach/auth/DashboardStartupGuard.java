package com.dadcoach.auth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * A deployed instance (Render, or the prod profile) refuses to start with an unsafe dashboard configuration -
 * names only in the message, never values (Tair's ProductionStartupGuard, playbook §19). Local runs are not checked.
 */
@Component
public class DashboardStartupGuard implements SmartInitializingSingleton {

    static final int MIN_KEY_LENGTH = 24;

    private final Environment env;
    private final DashboardProperties properties;

    public DashboardStartupGuard(Environment env, DashboardProperties properties) {
        this.env = env;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        boolean deployed = env.getProperty("RENDER") != null || Arrays.asList(env.getActiveProfiles()).contains("prod");
        if (!deployed) {
            return;
        }
        List<String> problems = problems(properties);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start a deployed instance: " + String.join("; ", problems));
        }
    }

    static List<String> problems(DashboardProperties p) {
        List<String> problems = new ArrayList<>();
        if (!p.isCookieSecure()) {
            problems.add("DADCOACH_COOKIE_SECURE must not be false outside local development");
        }
        String ops = p.getOpsApiKey() == null ? "" : p.getOpsApiKey();
        if (!ops.isBlank() && ops.length() < MIN_KEY_LENGTH) {
            problems.add("DADCOACH_OPS_API_KEY must be at least " + MIN_KEY_LENGTH + " characters");
        }
        return problems;
    }
}
