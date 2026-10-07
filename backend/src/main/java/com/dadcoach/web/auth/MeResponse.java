package com.dadcoach.web.auth;

import java.util.List;

/**
 * Who is signed in and which areas the SPA may show ({@code capabilities}: "father", "admin", "training"). The server
 * still checks every call. {@code kind} is ADMIN when the person is on the team, else FATHER.
 */
public record MeResponse(String kind, String name, List<String> capabilities, Long fatherId) {
}
