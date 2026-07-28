package com.dadcoach.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Response DTO returned after completing the onboarding wizard (provisioning).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response after completing onboarding and provisioning all entities")
public record ProvisioningResponse(

    @JsonProperty("father_id")
    @Schema(description = "The newly created father's ID")
    Long fatherId,

    @JsonProperty("activation_id")
    @Schema(description = "The activation record ID for the WhatsApp flow")
    UUID activationId,

    @JsonProperty("deep_link")
    @Schema(description = "WhatsApp deep link for activation", example = "https://wa.me/972501234567?text=%F0%9F%9A%80%20START")
    String deepLink,

    @JsonProperty("activation_status")
    @Schema(description = "Current activation status", example = "PENDING")
    String activationStatus,

    @JsonProperty("whatsapp_number")
    @Schema(description = "The WhatsApp number for the coach", example = "+972501234567")
    String whatsappNumber,

    @JsonProperty("already_provisioned")
    @Schema(description = "True if this was an idempotent duplicate (already provisioned)")
    Boolean alreadyProvisioned
) {
}
