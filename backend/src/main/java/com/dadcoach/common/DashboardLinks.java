package com.dadcoach.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Links into the Dad Coach dashboard, built from WEB_BASE_URL ({@code dad-coach.web.base-url}) - D-006. The plain
 * dashboard address (the weekly plan's dashboard_url) never carries a father id or a token; the father's own way in
 * is the button the coach sends with dad_dashboard_link (D-027). Belt images are served by the dashboard under /belts.
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

    /** The weekly plan's dashboard_url: the plain dashboard address (no sign-in in it). */
    public String loginUrl() {
        return baseUrl + "/login";
    }
}
