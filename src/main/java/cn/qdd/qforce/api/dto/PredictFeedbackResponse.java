package cn.qdd.qforce.api.dto;

public record PredictFeedbackResponse(
        long predictionId,
        String actualAction,
        boolean matchedTopBehavior,
        String matchedBehavior,
        UserProfile adaptiveProfile
) {
}
