package com.dadcoach.api.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * The tool/context key lets its holder act as any father (book, cancel, read the family). A deployed Dad Coach refuses
 * to start with a short key or with the development default committed in this repository - the previous version keeps
 * serving - the same way {@link JwtSecretGuard} protects the token secret.
 */
@Component
public class ServiceKeyGuard {

    static final String DEV_DEFAULT = "e76625b0ee030a88400a68b3dd9bf23fe24e657455f1dd08b295991073d439f6";
    static final int MIN_LENGTH = 32;

    public ServiceKeyGuard(@Value("${tool-api.api-key:}") String toolKey, Environment environment) {
        boolean deployed = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (deployed && (toolKey == null || toolKey.length() < MIN_LENGTH || DEV_DEFAULT.equals(toolKey))) {
            throw new IllegalStateException("TOOL_API_KEY must be a private value of at least " + MIN_LENGTH
                    + " characters in production (the development default is public)");
        }
    }
}
