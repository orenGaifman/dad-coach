package com.dadcoach.api.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * A deployed Dad Coach never signs tokens with the development secret committed in this repository: anyone could
 * forge a father's token with it (and, for example, delete his account). Startup fails with the dev default or a
 * secret shorter than 32 characters when the {@code prod} profile is active, so the previous version keeps serving.
 */
@Component
public class JwtSecretGuard {

    static final String DEV_DEFAULT_PREFIX = "default-dev-secret";

    public JwtSecretGuard(@Value("${dad-coach.security.jwt.secret:}") String secret, Environment environment) {
        boolean deployed = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (deployed && (secret == null || secret.length() < 32 || secret.startsWith(DEV_DEFAULT_PREFIX))) {
            throw new IllegalStateException("JWT_SECRET must be set to a private value of at least 32 characters in production"
                    + " (the development default is public)");
        }
    }
}
