package cn.qdd.qforce.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record EventInput(
        @NotBlank String type,
        @NotBlank String description,
        @NotNull Instant occurredAt,
        @Min(0) @Max(1) double intensity,
        @Min(0) @Max(1) double credibility,
        @NotBlank String distance
) {
}

