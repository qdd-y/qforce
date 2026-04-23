# qforce

Spring AI based personal behavior predictor MVP.

详细接口文档见：`API.md`

> Current version removes direct `VectorStore` compile dependency to keep startup simple.
> You can later replace `MemoryService` with pgvector/Milvus/Elastic vector retrieval.

## What it does

- Accepts surrounding events (`/api/predict-behavior`)
- Supports one-sentence simple input (`/api/predict-behavior/simple`)
- Computes deterministic impact scores (`mood/focus/stress`)
- Retrieves personal memory from PostgreSQL `memory_snippet`
- Uses one Self Agent (LLM) to predict top concrete actions
- Falls back to rule-based prediction when LLM is unavailable
- Persists behavior events + prediction results to PostgreSQL knowledge base
- Auto-ingests extracted events into `memory_snippet` for future retrieval
- Supports outcome feedback to auto-tune adaptive profile (`/api/predict-behavior/feedback`)
- Feedback tuning uses dynamic learning rate with recent-sample smoothing

## Run

1. Configure `application.yml` (or env vars) for DB + LLM:
   - `OPENAI_API_KEY`
   - `OPENAI_BASE_URL` (optional)
   - `OPENAI_MODEL` (optional)
   - `PG_HOST` (optional, default `localhost`)
   - `PG_PORT` (optional, default `5432`)
   - `PG_DATABASE` (optional, default `qforce`)
   - `PG_USERNAME` (optional, default `postgres`)
   - `PG_PASSWORD` (optional, default `postgres`)
2. Start:
   - `mvn spring-boot:run`
3. Open demo page:
   - `http://localhost:8080/demo.html`

> Tables are auto-created at startup from `src/main/resources/schema.sql`.
> Initial memory snippets are loaded from `src/main/resources/data.sql`.

## Knowledge base tables

- `memory_snippet`: long-term memory snippets for retrieval
- `behavior_event`: raw behavior/event records from requests
- `prediction_log`: prediction outputs (scores, behaviors, reasoning, evidence)
- `prediction_event`: many-to-many relation between predictions and source events

## API

`POST /api/predict-behavior`

```json
{
  "events": [
    {
      "type": "工作",
      "description": "项目deadline提前",
      "occurredAt": "2026-04-21T08:20:00Z",
      "intensity": 0.9,
      "credibility": 0.95,
      "distance": "direct"
    },
    {
      "type": "社交",
      "description": "和同事发生冲突",
      "occurredAt": "2026-04-21T09:00:00Z",
      "intensity": 0.8,
      "credibility": 0.9,
      "distance": "direct"
    }
  ],
  "profile": {
    "moodSensitivity": 1.2,
    "focusSensitivity": 1.0,
    "stressSensitivity": 1.4,
    "procrastinationBaseline": 0.6,
    "actionBaseline": 0.5,
    "helpSeekingBaseline": 0.4
  },
  "memoryTopK": 5,
  "predictionHorizon": "24h"
}
```

`POST /api/predict-behavior/auto` (AI 先解析自然语言事件再预测)

```json
{
  "narrative": "今天上午老板通知项目提前交付，我和同事还因为方案争执了半小时，下午家人安慰了我。",
  "profile": {
    "moodSensitivity": 1.1,
    "focusSensitivity": 1.0,
    "stressSensitivity": 1.3,
    "procrastinationBaseline": 0.5,
    "actionBaseline": 0.55,
    "helpSeekingBaseline": 0.45
  },
  "memoryTopK": 5,
  "predictionHorizon": "24h"
}
```

