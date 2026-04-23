# qforce 中文文档

qforce 是一个“**个人行为预测 + 反馈学习**”系统：  
用户输入“发生了什么事”，系统预测你接下来可能采取的具体行动；当你回填真实结果后，系统会自动更新画像参数，让后续预测逐步更贴近你的习惯。

## 1. 核心能力

- 一句话事件输入预测（普通用户入口）
- 自然语言事件自动抽取
- 输出 Top3 具体行动（含概率、证据、推理）
- 预测结果与真实结果回写入库
- 基于反馈自动微调 `adaptive_profile`（动态步长 + 平滑）

## 2. 整体流程

1. 用户输入事件（推荐 `POST /api/predict-behavior/simple`）
2. 系统提取结构化事件并预测行动
3. 预测与事件写入知识库
4. 用户提交真实行动反馈（`POST /api/predict-behavior/feedback`）
5. 系统更新 `adaptive_profile`，下次预测使用新参数

## 3. 运行方式

1. 配置数据库与模型参数（`application.yml` 或环境变量）
2. 启动服务：
   - `mvn spring-boot:run`
3. 打开演示页：
   - `http://localhost:8080/demo.html`

> 数据库表会在启动时自动初始化：`schema.sql` / `data.sql`

## 4. 主要接口

- `POST /api/predict-behavior/simple`  
  一句话输入事件，返回预测行动（推荐前台使用）

- `POST /api/predict-behavior/auto`  
  自然语言叙述 + 可选 profile，自动抽取后预测

- `POST /api/predict-behavior`  
  传结构化事件直接预测（适合系统对接）

- `POST /api/predict-behavior/feedback`  
  回写真实行动，用于学习调参

详细字段、示例与错误码见：`API.md`

## 5. 知识库表说明

- `memory_snippet`：长期记忆片段（检索输入）
- `behavior_event`：原始事件记录
- `prediction_log`：预测结果（含 top behaviors / evidence）
- `prediction_event`：预测与事件关联
- `prediction_feedback`：真实行动反馈
- `adaptive_profile`：可学习画像参数（预测默认读取）

## 6. 反馈学习如何工作

- 先计算真实行动与预测行动的文本相似度，判断是否命中
- 根据置信度、近期命中率、连续命中/未命中动态计算更新步长
- 用平滑策略更新画像参数，减少抖动
- 更新后的 `adaptive_profile` 自动用于后续预测

