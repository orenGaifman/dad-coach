package com.dadcoach.whatsapp;

import jakarta.validation.constraints.NotBlank;

public record SendTextRequest(@NotBlank String to, @NotBlank String message) {}
