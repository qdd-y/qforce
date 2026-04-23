package cn.qdd.qforce.service;

import cn.qdd.qforce.api.dto.EventInput;
import cn.qdd.qforce.api.dto.ImpactScore;
import cn.qdd.qforce.api.dto.UserProfile;
import cn.qdd.qforce.domain.ImpactScoringResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ImpactScoringService {

    public ImpactScoringResult score(List<EventInput> events, UserProfile profile, Instant now) {
        double mood = 0;
        double focus = 0;
        double stress = 0;
        List<String> contributions = new ArrayList<>();

        for (EventInput event : events) {
            ImpactVector base = inferBaseImpact(event.type(), event.description());
            double distanceFactor = distanceFactor(event.distance());
            double hours = Math.max(Duration.between(event.occurredAt(), now).toHours(), 0);
            double decay = Math.exp(-hours / 24.0);
            double weight = event.intensity() * event.credibility() * distanceFactor * decay;

            double moodDelta = weight * base.mood() * profile.moodSensitivity();
            double focusDelta = weight * base.focus() * profile.focusSensitivity();
            double stressDelta = weight * base.stress() * profile.stressSensitivity();

            mood += moodDelta;
            focus += focusDelta;
            stress += stressDelta;

            contributions.add(String.format(
                    Locale.ROOT,
                    "%s | w=%.3f | Δmood=%.3f Δfocus=%.3f Δstress=%.3f",
                    event.type(), weight, moodDelta, focusDelta, stressDelta
            ));
        }

        return new ImpactScoringResult(new ImpactScore(mood, focus, stress), contributions);
    }

    private ImpactVector inferBaseImpact(String eventType, String description) {
        String key = (eventType + " " + description).toLowerCase(Locale.ROOT);

        if (key.contains("冲突") || key.contains("争执") || key.contains("conflict")) {
            return new ImpactVector(-0.8, -0.6, 0.9);
        }
        if (key.contains("deadline") || key.contains("ddl") || key.contains("工作") || key.contains("面试")) {
            return new ImpactVector(-0.3, 0.7, 0.8);
        }
        if (key.contains("家庭") || key.contains("家人") || key.contains("friend") || key.contains("朋友")) {
            return new ImpactVector(0.3, -0.1, -0.3);
        }
        if (key.contains("坏消息") || key.contains("negative") || key.contains("失败")) {
            return new ImpactVector(-0.7, -0.4, 0.7);
        }
        if (key.contains("好消息") || key.contains("positive") || key.contains("成功")) {
            return new ImpactVector(0.8, 0.3, -0.5);
        }

        return new ImpactVector(-0.1, 0.0, 0.2);
    }

    private double distanceFactor(String distance) {
        String d = distance == null ? "" : distance.toLowerCase(Locale.ROOT);
        if (d.contains("direct") || d.contains("直接")) {
            return 1.0;
        }
        if (d.contains("indirect") || d.contains("间接")) {
            return 0.6;
        }
        if (d.contains("news") || d.contains("舆情") || d.contains("社会")) {
            return 0.4;
        }
        return 0.7;
    }

    private record ImpactVector(double mood, double focus, double stress) {
    }
}

