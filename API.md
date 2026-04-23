# qforce API 接口文档

Base URL: `http://localhost:8080`

Content-Type: `application/json`

---

## 1) 结构化输入预测

**POST** `/api/predict-behavior`

### 请求体

```json
{
  "events": [
    {
      "type": "工作",
      "description": "项目deadline提前",
      "occurredAt": "2026-04-23T01:00:00Z",
      "intensity": 0.9,
      "credibility": 0.95,
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

### 字段约束

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---|---|
| events | array | 是 | 至少 1 条 |
| events[].type | string | 是 | 非空 |
| events[].description | string | 是 | 非空 |
| events[].occurredAt | string(ISO-8601) | 是 | 如 `2026-04-23T01:00:00Z` |
| events[].intensity | number | 是 | 0~1 |
| events[].credibility | number | 是 | 0~1 |
| events[].distance | string | 是 | 非空，建议 `direct/indirect/news` |
| profile | object | 是 | 用户画像 |
| profile.moodSensitivity | number | 是 | 0~2 |
| profile.focusSensitivity | number | 是 | 0~2 |
| profile.stressSensitivity | number | 是 | 0~2 |
| profile.procrastinationBaseline | number | 是 | 0~1 |
| profile.actionBaseline | number | 是 | 0~1 |
| profile.helpSeekingBaseline | number | 是 | 0~1 |
| memoryTopK | int | 否 | 1~20，缺省/0 时按 5 |
| predictionHorizon | string | 否 | 缺省时为 `"24h"` |

### 成功响应（200）

```json
{
  "predictionId": 1024,
  "impactScore": {
    "mood": -0.9123,
    "focus": 0.2311,
    "stress": 1.3365
  },
  "topBehaviors": [
    {
      "behavior": "先回避冲突相关沟通，减少直接接触",
      "probability": 0.4235,
      "reason": "..."
    },
    {
      "behavior": "先找一个可信的人聊聊并寻求建议",
      "probability": 0.3122,
      "reason": "..."
    },
    {
      "behavior": "先拆解任务并启动一个最小可执行步骤",
      "probability": 0.2643,
      "reason": "..."
    }
  ],
  "evidence": [
    "工作 | w=0.855 | Δmood=-0.307 Δfocus=0.598 Δstress=0.684",
    "memory:auto-8af5f3ab12cd90ef"
  ],
  "modelReasoning": "...",
  "usedLlm": true
}
```

---

## 2) 自然语言自动提取 + 预测

**POST** `/api/predict-behavior/auto`

### 请求体

```json
{
  "narrative": "今天老板突然要求项目提前交付，我和同事因为方案起了争执，晚上朋友安慰了我。",
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

### 字段约束

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---|---|
| narrative | string | 是 | 非空，自然语言描述 |
| profile | object | 否 | 为空时使用默认画像 |
| memoryTopK | int | 否 | 1~20，缺省时 5 |
| predictionHorizon | string | 否 | 缺省时 `"24h"` |

> 该接口先调用 AI 将 `narrative` 提取为结构化 `events`，再走与接口 1 相同的预测流程。

### 成功响应

响应结构同 `/api/predict-behavior`。

> `topBehaviors[].behavior` 当前语义为“具体行动文本”，不再是固定枚举标签。

---

## 3) 极简输入（普通用户）

**POST** `/api/predict-behavior/simple`

### 请求体

```json
{
  "event": "今天老板突然要求项目提前交付，我和同事因为方案起了争执。"
}
```

### 字段约束

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---|---|
| event | string | 是 | 非空，直接输入发生的一件事 |

> 该接口会使用默认 profile、默认 memoryTopK=5、默认 predictionHorizon=24h。

### 成功响应

响应结构同 `/api/predict-behavior`。

---

## 4) 预测结果反馈（用于学习）

**POST** `/api/predict-behavior/feedback`

### 请求体

```json
{
  "predictionId": 1024,
  "actualAction": "我先找同事沟通并约了下午同步方案。",
  "occurredAt": "2026-04-23T06:30:00Z"
}
```

### 字段约束

| 字段 | 类型 | 必填 | 约束/说明 |
|---|---|---|---|
| predictionId | long | 是 | 预测接口返回的 `predictionId` |
| actualAction | string | 是 | 真实发生的行动（非空） |
| occurredAt | string(ISO-8601) | 否 | 真实行动发生时间，缺省为当前时间 |

### 成功响应

```json
{
  "predictionId": 1024,
  "actualAction": "我先找同事沟通并约了下午同步方案。",
  "matchedTopBehavior": true,
  "matchedBehavior": "先找一个可信的人聊聊并寻求建议",
  "adaptiveProfile": {
    "moodSensitivity": 1.0,
    "focusSensitivity": 1.0,
    "stressSensitivity": 1.0,
    "procrastinationBaseline": 0.49,
    "actionBaseline": 0.52,
    "helpSeekingBaseline": 0.42
  }
}
```

> 学习策略说明：反馈学习采用**动态步长 + 最近样本平滑**。系统会根据近期命中率、连续命中/未命中、以及本次匹配置信度自动调整更新幅度，避免参数抖动。

---

## 错误响应

### 400 参数校验失败

```json
{
  "success": false,
  "error": "Validation failed",
  "details": [
    "events: must not be empty",
    "profile: must not be null"
  ]
}
```

### 503 服务暂不可用（例如 ChatClient 未配置）

```json
{
  "success": false,
  "error": "ChatClient is not available. Please configure a valid ChatModel first."
}
```

---

## cURL 示例（PowerShell）

```powershell
curl.exe -X POST "http://localhost:8080/api/predict-behavior/auto" `
  -H "Content-Type: application/json" `
  -d "{\"narrative\":\"今天老板突然要求项目提前交付，我和同事因为方案起了争执，晚上朋友安慰了我。\",\"memoryTopK\":5,\"predictionHorizon\":\"24h\"}"
```

```powershell
curl.exe -X POST "http://localhost:8080/api/predict-behavior/simple" `
  -H "Content-Type: application/json" `
  -d "{\"event\":\"今天老板突然要求项目提前交付，我和同事因为方案起了争执，晚上朋友安慰了我。\"}"
```

```powershell
curl.exe -X POST "http://localhost:8080/api/predict-behavior/feedback" `
  -H "Content-Type: application/json" `
  -d "{\"predictionId\":1024,\"actualAction\":\"我先找同事沟通并约了下午同步方案。\"}"
```

```powershell
curl.exe -X POST "http://localhost:8080/api/predict-behavior" `
  -H "Content-Type: application/json" `
  -d "{\"events\":[{\"type\":\"工作\",\"description\":\"项目deadline提前\",\"occurredAt\":\"2026-04-23T01:00:00Z\",\"intensity\":0.9,\"credibility\":0.95,\"distance\":\"direct\"}],\"profile\":{\"moodSensitivity\":1.2,\"focusSensitivity\":1.0,\"stressSensitivity\":1.4,\"procrastinationBaseline\":0.6,\"actionBaseline\":0.5,\"helpSeekingBaseline\":0.4},\"memoryTopK\":5,\"predictionHorizon\":\"24h\"}"
```

---

## 数据落库行为（当前实现）

每次预测会执行以下持久化：

1. `behavior_event`：保存本次输入事件
2. `prediction_log`：保存预测结果（含 `top_behaviors`、`evidence`）
3. `prediction_event`：关联预测与事件
4. `memory_snippet`：从事件自动提炼并 upsert，供后续检索
5. `prediction_feedback`：保存真实行动反馈，并自动更新 `adaptive_profile`

