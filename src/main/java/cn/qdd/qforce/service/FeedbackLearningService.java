package cn.qdd.qforce.service;

import cn.qdd.qforce.api.dto.BehaviorPrediction;
import cn.qdd.qforce.api.dto.PredictFeedbackRequest;
import cn.qdd.qforce.api.dto.PredictFeedbackResponse;
import cn.qdd.qforce.api.dto.UserProfile;
import cn.qdd.qforce.repository.AdaptiveProfileRepository;
import cn.qdd.qforce.repository.PredictionFeedbackRepository;
import cn.qdd.qforce.repository.PredictionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class FeedbackLearningService {

    private static final int RECENT_WINDOW = 20;
    private static final int STREAK_WINDOW = 10;

    private final PredictionLogRepository predictionLogRepository;
    private final PredictionFeedbackRepository predictionFeedbackRepository;
    private final AdaptiveProfileRepository adaptiveProfileRepository;

    public FeedbackLearningService(
            PredictionLogRepository predictionLogRepository,
            PredictionFeedbackRepository predictionFeedbackRepository,
            AdaptiveProfileRepository adaptiveProfileRepository
    ) {
        this.predictionLogRepository = predictionLogRepository;
        this.predictionFeedbackRepository = predictionFeedbackRepository;
        this.adaptiveProfileRepository = adaptiveProfileRepository;
    }

    @Transactional
    public PredictFeedbackResponse recordFeedback(PredictFeedbackRequest request) {
        List<BehaviorPrediction> predicted = predictionLogRepository.findTopBehaviors(request.predictionId());
        MatchResult match = matchTopBehavior(request.actualAction(), predicted);
        PredictionFeedbackRepository.RecentFeedbackStats stats = predictionFeedbackRepository.recentStats(RECENT_WINDOW);
        int streak = predictionFeedbackRepository.recentStreak(match.matched(), STREAK_WINDOW);

        OffsetDateTime occurredAt = request.occurredAt() == null
                ? OffsetDateTime.now(ZoneOffset.UTC)
                : OffsetDateTime.ofInstant(request.occurredAt(), ZoneOffset.UTC);

        predictionFeedbackRepository.upsert(
                request.predictionId(),
                request.actualAction(),
                occurredAt,
                match.matched(),
                match.matchedBehavior()
        );

        UserProfile updatedProfile = tuneProfile(
                request.actualAction(),
                match.matched(),
                match.confidence(),
                stats,
                streak
        );
        return new PredictFeedbackResponse(
                request.predictionId(),
                request.actualAction(),
                match.matched(),
                match.matchedBehavior(),
                updatedProfile
        );
    }

    private MatchResult matchTopBehavior(String actualAction, List<BehaviorPrediction> predicted) {
        String actual = normalize(actualAction);
        BehaviorPrediction best = null;
        double bestScore = 0;
        double bestProbability = 0;

        for (BehaviorPrediction p : predicted) {
            if (p.behavior() == null || p.behavior().isBlank()) {
                continue;
            }
            double score = similarity(actual, normalize(p.behavior()));
            if (score > bestScore) {
                bestScore = score;
                best = p;
                bestProbability = p.probability();
            }
        }

        if (best == null || bestScore < 0.35) {
            double fallbackConfidence = predicted.stream()
                    .mapToDouble(BehaviorPrediction::probability)
                    .max()
                    .orElse(0);
            return new MatchResult(false, null, clamp(fallbackConfidence, 0, 1));
        }
        double confidence = clamp((bestScore + clamp(bestProbability, 0, 1)) / 2.0, 0, 1);
        return new MatchResult(true, best.behavior(), confidence);
    }

    private UserProfile tuneProfile(
            String actualAction,
            boolean matched,
            double confidence,
            PredictionFeedbackRepository.RecentFeedbackStats stats,
            int streak
    ) {
        UserProfile current = adaptiveProfileRepository.getCurrent();
        double step = dynamicStep(matched, confidence, stats, streak);
        double alpha = smoothingAlpha(stats.total());
        String text = normalize(actualAction);

        double procrastination = current.procrastinationBaseline();
        double action = current.actionBaseline();
        double help = current.helpSeekingBaseline();

        if (containsAny(text, "拖", "延后", "晚点", "先不", "回头再")) {
            procrastination += step;
            action -= step * 0.5;
        }
        if (containsAny(text, "开始", "执行", "推进", "处理", "完成", "落实")) {
            action += step;
            procrastination -= step * 0.5;
        }
        if (containsAny(text, "请教", "求助", "沟通", "讨论", "问", "咨询")) {
            help += step;
        }

        double targetProcrastination = clamp(procrastination, 0, 1);
        double targetAction = clamp(action, 0, 1);
        double targetHelp = clamp(help, 0, 1);

        UserProfile next = new UserProfile(
                current.moodSensitivity(),
                current.focusSensitivity(),
                current.stressSensitivity(),
                blend(current.procrastinationBaseline(), targetProcrastination, alpha),
                blend(current.actionBaseline(), targetAction, alpha),
                blend(current.helpSeekingBaseline(), targetHelp, alpha)
        );
        return adaptiveProfileRepository.update(next);
    }

    private double dynamicStep(
            boolean matched,
            double confidence,
            PredictionFeedbackRepository.RecentFeedbackStats stats,
            int streak
    ) {
        double base = matched ? 0.008 : 0.018;
        double safeConfidence = clamp(confidence, 0, 1);

        double confidenceFactor = matched
                ? (1.15 - 0.45 * safeConfidence)
                : (1.0 + 0.7 * safeConfidence);

        double recentAccuracy = stats.total() == 0 ? 0.5 : (double) stats.matched() / stats.total();
        double accuracyFactor = matched
                ? (1.0 + (0.5 - recentAccuracy) * 0.4)
                : (1.0 + (0.5 - recentAccuracy) * 0.8);

        double streakFactor = streak >= 3 ? 0.85 : 1.0;

        return clamp(base * confidenceFactor * accuracyFactor * streakFactor, 0.004, 0.04);
    }

    private double smoothingAlpha(int totalFeedbackCount) {
        if (totalFeedbackCount < 5) {
            return 0.7;
        }
        if (totalFeedbackCount < 20) {
            return 0.5;
        }
        return 0.35;
    }

    private double blend(double current, double target, double alpha) {
        return clamp(current * (1 - alpha) + target * alpha, 0, 1);
    }

    private boolean containsAny(String text, String... candidates) {
        for (String c : candidates) {
            if (text.contains(c)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Punct}\\s]+", "").toLowerCase();
    }

    private double similarity(String a, String b) {
        if (a.isBlank() || b.isBlank()) {
            return 0;
        }
        if (a.contains(b) || b.contains(a)) {
            return 1.0;
        }
        int common = 0;
        for (int i = 0; i < a.length(); i++) {
            if (b.indexOf(a.charAt(i)) >= 0) {
                common++;
            }
        }
        return (double) common / Math.max(a.length(), b.length());
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record MatchResult(boolean matched, String matchedBehavior, double confidence) {
    }
}
