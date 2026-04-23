package cn.qdd.qforce.repository;

import cn.qdd.qforce.api.dto.EventInput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
public class BehaviorEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public BehaviorEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> saveAll(List<EventInput> events) {
        List<Long> ids = new ArrayList<>(events.size());

        for (EventInput event : events) {
            Long id = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO behavior_event (event_type, description, occurred_at, intensity, credibility, distance)
                    VALUES (?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """,
                    Long.class,
                    event.type(),
                    event.description(),
                    OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC),
                    event.intensity(),
                    event.credibility(),
                    event.distance()
            );

            if (id == null) {
                throw new IllegalStateException("Failed to insert behavior event.");
            }
            ids.add(id);
        }
        return ids;
    }
}
