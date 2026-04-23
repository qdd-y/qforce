package cn.qdd.qforce.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record PredictFeedbackRequest(
        @NotNull @Positive Long predictionId,
        @NotBlank String actualAction,
        Instant occurredAt
) {
}
