package cn.qdd.qforce.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class PredictionFeedbackRepository {

    private final JdbcTemplate jdbcTemplate;

    public PredictionFeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(
            long predictionId,
            String actualAction,
            OffsetDateTime occurredAt,
            boolean matchedTopBehavior,
            String matchedBehavior
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO prediction_feedback (
                    prediction_id, actual_action, occurred_at, matched_top_behavior, matched_behavior, updated_at
                )
                VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (prediction_id) DO UPDATE
                SET actual_action = EXCLUDED.actual_action,
                    occurred_at = EXCLUDED.occurred_at,
                    matched_top_behavior = EXCLUDED.matched_top_behavior,
                    matched_behavior = EXCLUDED.matched_behavior,
                    updated_at = NOW()
                """,
                predictionId,
                actualAction,
                occurredAt,
                matchedTopBehavior,
                matchedBehavior
        );
    }

    public RecentFeedbackStats recentStats(int limit) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT 1 FROM prediction_feedback ORDER BY updated_at DESC LIMIT ?) t",
                Integer.class,
                limit
        );
        Integer matched = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT matched_top_behavior FROM prediction_feedback ORDER BY updated_at DESC LIMIT ?) t WHERE matched_top_behavior = true",
                Integer.class,
                limit
        );
        return new RecentFeedbackStats(
                total == null ? 0 : total,
                matched == null ? 0 : matched
        );
    }

    public int recentStreak(boolean matchedValue, int limit) {
        List<Boolean> values = jdbcTemplate.query(
                "SELECT matched_top_behavior FROM prediction_feedback ORDER BY updated_at DESC LIMIT ?",
                (rs, rowNum) -> rs.getBoolean("matched_top_behavior"),
                limit
        );
        int streak = 0;
        for (Boolean value : values) {
            if (value != null && value == matchedValue) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    public record RecentFeedbackStats(int total, int matched) {
    }
}
