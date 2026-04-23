package cn.qdd.qforce.service;

import cn.qdd.qforce.api.dto.EventInput;
import cn.qdd.qforce.api.dto.PredictResponse;
import cn.qdd.qforce.repository.BehaviorEventRepository;
import cn.qdd.qforce.repository.MemorySnippetRepository;
import cn.qdd.qforce.repository.PredictionLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeBaseService {

    private final BehaviorEventRepository behaviorEventRepository;
    private final PredictionLogRepository predictionLogRepository;
    private final MemorySnippetRepository memorySnippetRepository;

    public KnowledgeBaseService(
            BehaviorEventRepository behaviorEventRepository,
            PredictionLogRepository predictionLogRepository,
            MemorySnippetRepository memorySnippetRepository
    ) {
        this.behaviorEventRepository = behaviorEventRepository;
        this.predictionLogRepository = predictionLogRepository;
        this.memorySnippetRepository = memorySnippetRepository;
    }

    @Transactional
    public long recordPrediction(List<EventInput> events, String predictionHorizon, PredictResponse response) {
        memorySnippetRepository.upsertFromEvents(events);
        List<Long> eventIds = behaviorEventRepository.saveAll(events);
        long predictionId = predictionLogRepository.save(predictionHorizon, response);
        predictionLogRepository.bindEvents(predictionId, eventIds);
        return predictionId;
    }
}
