package com.dadcoach.api.auth;

/**
 * Enumeration of actor types that can authenticate against the Application API.
 * <p>
 * Each type maps to a distinct API surface with different access scopes:
 * <ul>
 *   <li>{@code FATHER} — self-service access to own data only</li>
 *   <li>{@code ADMIN} — operational management with role-based permissions</li>
 *   <li>{@code SERVICE} — internal service-to-service operations</li>
 * </ul>
 */
public enum ActorType {
    FATHER,
    ADMIN,
    SERVICE
}
