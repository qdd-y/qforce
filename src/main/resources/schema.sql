CREATE TABLE IF NOT EXISTS memory_snippet (
    id VARCHAR(64) PRIMARY KEY,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS behavior_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    intensity DOUBLE PRECISION NOT NULL CHECK (intensity >= 0 AND intensity <= 1),
    credibility DOUBLE PRECISION NOT NULL CHECK (credibility >= 0 AND credibility <= 1),
    distance VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'predict-api',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_behavior_event_occurred_at
    ON behavior_event (occurred_at DESC);

CREATE TABLE IF NOT EXISTS prediction_log (
    id BIGSERIAL PRIMARY KEY,
    prediction_horizon VARCHAR(64) NOT NULL,
    impact_mood DOUBLE PRECISION NOT NULL,
    impact_focus DOUBLE PRECISION NOT NULL,
    impact_stress DOUBLE PRECISION NOT NULL,
    used_llm BOOLEAN NOT NULL,
    model_reasoning TEXT,
    top_behaviors JSONB NOT NULL,
    evidence JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS prediction_event (
    prediction_id BIGINT NOT NULL REFERENCES prediction_log(id) ON DELETE CASCADE,
    behavior_event_id BIGINT NOT NULL REFERENCES behavior_event(id) ON DELETE CASCADE,
    PRIMARY KEY (prediction_id, behavior_event_id)
);

CREATE TABLE IF NOT EXISTS prediction_feedback (
    prediction_id BIGINT PRIMARY KEY REFERENCES prediction_log(id) ON DELETE CASCADE,
    actual_action TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    matched_top_behavior BOOLEAN NOT NULL,
    matched_behavior TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS adaptive_profile (
    id SMALLINT PRIMARY KEY,
    mood_sensitivity DOUBLE PRECISION NOT NULL,
    focus_sensitivity DOUBLE PRECISION NOT NULL,
    stress_sensitivity DOUBLE PRECISION NOT NULL,
    procrastination_baseline DOUBLE PRECISION NOT NULL,
    action_baseline DOUBLE PRECISION NOT NULL,
    help_seeking_baseline DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (id = 1),
    CHECK (mood_sensitivity >= 0 AND mood_sensitivity <= 2),
    CHECK (focus_sensitivity >= 0 AND focus_sensitivity <= 2),
    CHECK (stress_sensitivity >= 0 AND stress_sensitivity <= 2),
    CHECK (procrastination_baseline >= 0 AND procrastination_baseline <= 1),
    CHECK (action_baseline >= 0 AND action_baseline <= 1),
    CHECK (help_seeking_baseline >= 0 AND help_seeking_baseline <= 1)
);
