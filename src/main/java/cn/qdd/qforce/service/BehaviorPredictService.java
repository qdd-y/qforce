package cn.qdd.qforce.service;

import cn.qdd.qforce.api.dto.BehaviorPrediction;
import cn.qdd.qforce.api.dto.EventInput;
import cn.qdd.qforce.api.dto.NaturalLanguagePredictRequest;
import cn.qdd.qforce.api.dto.PredictRequest;
import cn.qdd.qforce.api.dto.PredictResponse;
import cn.qdd.qforce.api.dto.UserProfile;
import cn.qdd.qforce.domain.ImpactScoringResult;
import cn.qdd.qforce.domain.MemorySnippet;
import cn.qdd.qforce.repository.AdaptiveProfileRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BehaviorPredictService {

    private final ImpactScoringService impactScoringService;
    private final MemoryService memoryService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AdaptiveProfileRepository adaptiveProfileRepository;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;

    public BehaviorPredictService(
            ImpactScoringService impactScoringService,
            MemoryService memoryService,
            KnowledgeBaseService knowledgeBaseService,
            AdaptiveProfileRepository adaptiveProfileRepository,
            ObjectMapper objectMapper,
            Optional<ChatClient> chatClient
    ) {
        this.impactScoringService = impactScoringService;
        this.memoryService = memoryService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.adaptiveProfileRepository = adaptiveProfileRepository;
        this.objectMapper = objectMapper;
        this.chatClient = chatClient.orElse(null);
    }

    public PredictResponse predict(PredictRequest request) {
        ImpactScoringResult scoring = impactScoringService.score(
                request.events(),
                request.profile(),
                Instant.now()
        );

        String retrievalQuery = request.events().stream()
                .map(e -> e.type() + ": " + e.description())
                .reduce((a, b) -> a + " | " + b)
                .orElse("");

        List<MemorySnippet> memories = memoryService.retrieve(retrievalQuery, request.memoryTopK());

        PredictResponse response;
        Optional<LlmResult> llmResult = tryLlmPredict(request, scoring, memories);
        if (llmResult.isPresent()) {
            List<BehaviorPrediction> normalized = normalizeTop3(llmResult.get().predictions);
            response = new PredictResponse(
                    null,
                    scoring.score(),
                    normalized,
                    buildEvidence(scoring, memories),
                    llmResult.get().overallReasoning,
                    true
            );
        } else {
            List<BehaviorPrediction> fallback = fallbackPredict(request, scoring);
            response = new PredictResponse(
                    null,
                    scoring.score(),
                    normalizeTop3(fallback),
                    buildEvidence(scoring, memories),
                    "LLM unavailable, fallback rule-based predictor was used.",
                    false
            );
        }

        long predictionId = knowledgeBaseService.recordPrediction(request.events(), request.predictionHorizon(), response);
        return new PredictResponse(
                predictionId,
                response.impactScore(),
                response.topBehaviors(),
                response.evidence(),
                response.modelReasoning(),
                response.usedLlm()
        );
    }

    public PredictResponse predictFromNarrative(NaturalLanguagePredictRequest request) {
        if (chatClient == null) {
            throw new IllegalStateException("ChatClient is not available. Please configure a valid ChatModel first.");
        }

        UserProfile profile = request.profile() != null ? request.profile() : defaultProfile();
        int memoryTopK = request.memoryTopK() != null ? request.memoryTopK() : 5;
        String horizon = (request.predictionHorizon() == null || request.predictionHorizon().isBlank())
                ? "24h" : request.predictionHorizon();

        PredictRequest parsed = parseNarrativeToPredictRequest(request.narrative(), profile, memoryTopK, horizon);
        return predict(parsed);
    }

    public PredictResponse predictFromSimpleEvent(String event) {
        return predictFromNarrative(new NaturalLanguagePredictRequest(event, null, null, null));
    }

    private PredictRequest parseNarrativeToPredictRequest(String narrative, UserProfile profile, int memoryTopK, String horizon) {
        try {
            String systemPrompt = """
                    You are an event extraction engine.
                    Convert the user's narrative into strict JSON:
                    {
                      "events":[
                        {
                          "type":"...",
                          "description":"...",
                          "occurredAt":"ISO-8601 timestamp (UTC)",
                          "intensity":0.0,
                          "credibility":0.0,
                          "distance":"direct|indirect|news"
                        }
                      ]
                    }
                    Rules:
                    - intensity and credibility in [0,1]
                    - occurredAt must be valid ISO instant string like 2026-04-21T11:00:00Z
                    - if time is unknown, use current time
                    - output JSON only
                    """;

            String userPrompt = "Narrative:\n" + narrative;

            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                return fallbackFromNarrative(narrative, profile, memoryTopK, horizon);
            }

            ExtractedEvents extracted = objectMapper.readValue(content, ExtractedEvents.class);
            if (extracted.events == null || extracted.events.isEmpty()) {
                return fallbackFromNarrative(narrative, profile, memoryTopK, horizon);
            }

            return new PredictRequest(extracted.events, profile, memoryTopK, horizon);
        } catch (Exception ignored) {
            return fallbackFromNarrative(narrative, profile, memoryTopK, horizon);
        }
    }

    private PredictRequest fallbackFromNarrative(String narrative, UserProfile profile, int memoryTopK, String horizon) {
        EventInput event = new EventInput(
                "生活事件",
                narrative,
                Instant.now(),
                0.6,
                0.8,
                "direct"
        );
        return new PredictRequest(List.of(event), profile, memoryTopK, horizon);
    }

    private UserProfile defaultProfile() {
        return adaptiveProfileRepository.getCurrent();
    }

    private Optional<LlmResult> tryLlmPredict(PredictRequest request, ImpactScoringResult scoring, List<MemorySnippet> memories) {
        if (chatClient == null) {
            return Optional.empty();
        }

        try {
            String systemPrompt = """
                    You are the user's Self Agent.
                    Task: predict likely concrete user actions in next horizon from events + personal memory + impact scores.
                    You must output strict JSON:
                    {
                      "predictions":[
                        {"behavior":"specific action text","probability":0.0,"reason":"..."}
                      ],
                      "overallReasoning":"..."
                    }
                    Rules:
                    - probability in [0,1]
                    - provide at least 3 predictions
                    - behavior must be concrete, observable actions, not abstract labels
                    - each behavior should be a short sentence in Chinese
                    - do not output any text outside JSON
                    """;

            String userPrompt = """
                    Horizon: %s
                    Events: %s
                    ImpactScore: mood=%.4f focus=%.4f stress=%.4f
                    PersonalMemory: %s
                    """.formatted(
                    request.predictionHorizon(),
                    request.events(),
                    scoring.score().mood(),
                    scoring.score().focus(),
                    scoring.score().stress(),
                    memories
            );

            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            if (content == null || content.isBlank()) {
                return Optional.empty();
            }

            LlmResult result = objectMapper.readValue(content, LlmResult.class);
            if (result.predictions == null || result.predictions.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private List<BehaviorPrediction> fallbackPredict(PredictRequest request, ImpactScoringResult scoring) {
        double mood = scoring.score().mood();
        double focus = scoring.score().focus();
        double stress = scoring.score().stress();

        Map<String, Double> scoreMap = new LinkedHashMap<>();
        scoreMap.put("先拆解任务并启动一个最小可执行步骤", sigmoid(focus - 0.5 * stress + request.profile().actionBaseline()));
        scoreMap.put("先拖一会儿，把任务延后到临近截止再处理", sigmoid(stress - focus + request.profile().procrastinationBaseline()));
        scoreMap.put("先找一个可信的人聊聊并寻求建议", sigmoid(stress + 0.3 - request.profile().actionBaseline() + request.profile().helpSeekingBaseline()));
        scoreMap.put("先回避冲突相关沟通，减少直接接触", sigmoid(0.7 * stress - mood + 0.2));
        scoreMap.put("先通过倾诉或运动释放情绪再继续做事", sigmoid(stress - mood + 0.1));

        return scoreMap.entrySet().stream()
                .map(e -> new BehaviorPrediction(
                        e.getKey(),
                        e.getValue(),
                        "rule-score from mood/focus/stress interaction"
                ))
                .toList();
    }

    private List<BehaviorPrediction> normalizeTop3(List<BehaviorPrediction> predictions) {
        List<BehaviorPrediction> filtered = predictions.stream()
                .filter(p -> p.behavior() != null && !p.behavior().isBlank())
                .sorted(Comparator.comparingDouble(BehaviorPrediction::probability).reversed())
                .limit(3)
                .toList();

        if (filtered.isEmpty()) {
            return List.of(
                    new BehaviorPrediction("先拆解任务并启动一个最小可执行步骤", 0.40, "default"),
                    new BehaviorPrediction("先找一个可信的人聊聊并寻求建议", 0.30, "default"),
                    new BehaviorPrediction("先拖一会儿，把任务延后到临近截止再处理", 0.30, "default")
            );
        }

        double sum = filtered.stream().mapToDouble(BehaviorPrediction::probability).sum();
        if (sum <= 0.0001) {
            double uniform = 1.0 / filtered.size();
            return filtered.stream()
                    .map(p -> new BehaviorPrediction(p.behavior(), uniform, p.reason()))
                    .toList();
        }

        return filtered.stream()
                .map(p -> new BehaviorPrediction(
                        p.behavior().trim(),
                        round4(p.probability() / sum),
                        p.reason()
                ))
                .toList();
    }

    private List<String> buildEvidence(ImpactScoringResult scoring, List<MemorySnippet> memories) {
        List<String> evidence = new ArrayList<>();
        evidence.addAll(scoring.contributions().stream().limit(5).toList());
        evidence.addAll(memories.stream().limit(3).map(m -> "memory:" + m.id()).toList());
        return evidence;
    }

    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LlmResult {
        public List<BehaviorPrediction> predictions;
        public String overallReasoning;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ExtractedEvents {
        public List<EventInput> events;
    }
}

