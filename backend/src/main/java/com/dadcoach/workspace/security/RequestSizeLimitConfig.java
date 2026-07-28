package com.dadcoach.workspace.security;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration for request and response payload size limits.
 *
 * <p>Limits:</p>
 * <ul>
 *   <li>Maximum request body size: 256 KB (configured in application.yml via
 *       spring.servlet.multipart.max-request-size and server.tomcat.max-http-form-post-size)</li>
 *   <li>Maximum response payload size: 5 MB (enforced via pagination)</li>
 *   <li>Maximum in-memory codec size: 256 KB (configured via spring.codec.max-in-memory-size)</li>
 * </ul>
 *
 * <p>The request size is enforced at the servlet container level (Tomcat) and Spring multipart
 * configuration. The response size is enforced by design through pagination on all list endpoints.</p>
 */
@Configuration
public class RequestSizeLimitConfig {

    /**
     * Maximum request body size in bytes (256 KB).
     */
    public static final int MAX_REQUEST_SIZE_BYTES = 256 * 1024;

    /**
     * Maximum response payload size in bytes (5 MB).
     */
    public static final long MAX_RESPONSE_SIZE_BYTES = 5L * 1024 * 1024;

    /**
     * Default page size for paginated endpoints.
     */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Maximum page size to prevent excessive response payloads.
     */
    public static final int MAX_PAGE_SIZE = 50;
}
