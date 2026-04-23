package cn.qdd.qforce.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SimpleEventPredictRequest(
        @NotBlank String event
) {
}
