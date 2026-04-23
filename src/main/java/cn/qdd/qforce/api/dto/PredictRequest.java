package cn.qdd.qforce.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PredictRequest(
        @NotEmpty @Valid
        List<EventInput> events,
        @NotNull @Valid
        UserProfile profile,
        @Min(1) @Max(20)
        int memoryTopK,
        String predictionHorizon
) {
    public PredictRequest {
        if (predictionHorizon == null || predictionHorizon.isBlank()) {
            predictionHorizon = "24h";
        }
        if (memoryTopK == 0) {
            memoryTopK = 5;
        }
    }
}

