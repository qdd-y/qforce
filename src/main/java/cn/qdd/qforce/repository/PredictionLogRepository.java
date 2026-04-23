package cn.qdd.qforce.repository;

import cn.qdd.qforce.api.dto.BehaviorPrediction;
import cn.qdd.qforce.api.dto.PredictResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PredictionLogRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PredictionLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long save(String predictionHorizon, PredictResponse response) {
        String topBehaviorsJson = writeJson(response.topBehaviors());
        String evidenceJson = writeJson(response.evidence());

        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO prediction_log (
                    prediction_horizon,
                    impact_mood,
                    impact_focus,
                    impact_stress,
                    used_llm,
                    model_reasoning,
                    top_behaviors,
                    evidence
                )
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                RETURNING id
                """,
                Long.class,
                predictionHorizon,
                response.impactScore().mood(),
                response.impactScore().focus(),
                response.impactScore().stress(),
                response.usedLlm(),
                response.modelReasoning(),
                topBehaviorsJson,
                evidenceJson
        );

        if (id == null) {
            throw new IllegalStateException("Failed to insert prediction log.");
        }
        return id;
    }

    public void bindEvents(long predictionId, List<Long> eventIds) {
        for (Long eventId : eventIds) {
            jdbcTemplate.update(
                    """
                    INSERT INTO prediction_event (prediction_id, behavior_event_id)
                    VALUES (?, ?)
                    """,
                    predictionId,
                    eventId
            );
        }
    }

    public List<BehaviorPrediction> findTopBehaviors(long predictionId) {
        String json = jdbcTemplate.query(
                "SELECT top_behaviors FROM prediction_log WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                predictionId
        );
        if (json == null) {
            throw new IllegalArgumentException("Prediction not found: " + predictionId);
        }
        return readPredictions(json);
    }

    private String writeJson(List<?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize prediction payload.", e);
        }
    }

    private List<BehaviorPrediction> readPredictions(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<BehaviorPrediction>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize top behaviors.", e);
        }
    }
}
