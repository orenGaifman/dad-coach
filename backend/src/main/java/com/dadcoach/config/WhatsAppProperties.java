package com.dadcoach.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dad-coach.whatsapp")
public record WhatsAppProperties(String apiBaseUrl, String apiVersion, String phoneNumberId, String accessToken, String verifyToken) {}
