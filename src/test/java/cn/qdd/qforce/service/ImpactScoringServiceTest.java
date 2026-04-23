package cn.qdd.qforce.service;

import cn.qdd.qforce.api.dto.EventInput;
import cn.qdd.qforce.api.dto.UserProfile;
import cn.qdd.qforce.domain.ImpactScoringResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactScoringServiceTest {

    @Test
    void shouldAccumulateStressForConflictAndDeadline() {
        ImpactScoringService service = new ImpactScoringService();

        UserProfile profile = new UserProfile(1.0, 1.0, 1.0, 0.5, 0.5, 0.5);
        List<EventInput> events = List.of(
                new EventInput("工作", "项目deadline提前", Instant.now().minusSeconds(3600), 0.9, 0.9, "direct"),
                new EventInput("社交", "和同事发生冲突", Instant.now().minusSeconds(1800), 0.8, 0.9, "direct")
        );

        ImpactScoringResult result = service.score(events, profile, Instant.now());

        assertNotNull(result);
        assertTrue(result.score().stress() > 0);
        assertTrue(result.contributions().size() >= 2);
    }
}

