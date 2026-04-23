package cn.qdd.qforce.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record NaturalLanguagePredictRequest(
        @NotBlank String narrative,
        @Valid UserProfile profile,
        @Min(1) @Max(20) Integer memoryTopK,
        String predictionHorizon
) {
}

