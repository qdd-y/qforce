package cn.qdd.qforce.repository;

import cn.qdd.qforce.api.dto.UserProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdaptiveProfileRepository {

    private static final UserProfile DEFAULT_PROFILE = new UserProfile(
            1.0, 1.0, 1.0,
            0.5, 0.5, 0.4
    );

    private final JdbcTemplate jdbcTemplate;

    public AdaptiveProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserProfile getCurrent() {
        UserProfile profile = jdbcTemplate.query(
                """
                SELECT mood_sensitivity, focus_sensitivity, stress_sensitivity,
                       procrastination_baseline, action_baseline, help_seeking_baseline
                FROM adaptive_profile
                WHERE id = 1
                """,
                rs -> rs.next() ? new UserProfile(
                        rs.getDouble("mood_sensitivity"),
                        rs.getDouble("focus_sensitivity"),
                        rs.getDouble("stress_sensitivity"),
                        rs.getDouble("procrastination_baseline"),
                        rs.getDouble("action_baseline"),
                        rs.getDouble("help_seeking_baseline")
                ) : null
        );
        return profile == null ? DEFAULT_PROFILE : profile;
    }

    public UserProfile update(UserProfile profile) {
        jdbcTemplate.update(
                """
                INSERT INTO adaptive_profile (
                    id, mood_sensitivity, focus_sensitivity, stress_sensitivity,
                    procrastination_baseline, action_baseline, help_seeking_baseline, updated_at
                )
                VALUES (1, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (id) DO UPDATE
                SET mood_sensitivity = EXCLUDED.mood_sensitivity,
                    focus_sensitivity = EXCLUDED.focus_sensitivity,
                    stress_sensitivity = EXCLUDED.stress_sensitivity,
                    procrastination_baseline = EXCLUDED.procrastination_baseline,
                    action_baseline = EXCLUDED.action_baseline,
                    help_seeking_baseline = EXCLUDED.help_seeking_baseline,
                    updated_at = NOW()
                """,
                profile.moodSensitivity(),
                profile.focusSensitivity(),
                profile.stressSensitivity(),
                profile.procrastinationBaseline(),
                profile.actionBaseline(),
                profile.helpSeekingBaseline()
        );
        return getCurrent();
    }
}
