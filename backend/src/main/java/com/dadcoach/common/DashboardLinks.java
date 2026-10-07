package com.dadcoach.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Links into the Dad Coach dashboard, built from WEB_BASE_URL ({@code dad-coach.web.base-url}) - D-006. The link
 * the coach sends is the login page: it never carries a father id or a token (the dashboard asks for a one-time
 * login link on WhatsApp). Belt images are served by the dashboard under /belts.
 */
@Component
public class DashboardLinks {

    private final String baseUrl;

    public DashboardLinks(@Value("${dad-coach.web.base-url:http://localhost:3000}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** What the coach sends as dashboard_url. */
    public String loginUrl() {
        return baseUrl + "/login";
    }
}
