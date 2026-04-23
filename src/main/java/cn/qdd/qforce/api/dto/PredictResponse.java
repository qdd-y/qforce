package cn.qdd.qforce.api.dto;

import java.util.List;

public record PredictResponse(
        Long predictionId,
        ImpactScore impactScore,
        List<BehaviorPrediction> topBehaviors,
        List<String> evidence,
        String modelReasoning,
        boolean usedLlm
) {
}

