package com.pawpasta.glowscan_be.profile.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateAvatarRequest(@NotNull UUID uploadIntentId) {
}
