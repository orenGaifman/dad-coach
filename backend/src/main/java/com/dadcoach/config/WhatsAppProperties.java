package com.dadcoach.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "dad-coach.whatsapp")
public record WhatsAppProperties(
    @NotBlank String apiBaseUrl,
    @NotBlank String apiVersion,
    String phoneNumberId,
    String wabaId,
    String accessToken,
    String verifyToken,
    String webhookSecret
) {}
