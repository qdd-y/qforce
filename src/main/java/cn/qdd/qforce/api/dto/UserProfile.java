package cn.qdd.qforce.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UserProfile(
        //
        @Min(0) @Max(2) double moodSensitivity,
        @Min(0) @Max(2) double focusSensitivity,
        @Min(0) @Max(2) double stressSensitivity,
        @Min(0) @Max(1) double procrastinationBaseline,
        @Min(0) @Max(1) double actionBaseline,
        @Min(0) @Max(1) double helpSeekingBaseline
) {
}

