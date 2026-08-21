package com.dadcoach.api.dev;

/**
 * Thrown when dev endpoints are accessed in a production environment.
 *
 * <p>This exception is used by the DevEnvironmentGuard to signal that
 * dev endpoint access is not allowed in the current environment.</p>
 */
public class DevEndpointsDisabledException extends RuntimeException {

    public DevEndpointsDisabledException() {
        super("Dev endpoints disabled in production");
    }
}
