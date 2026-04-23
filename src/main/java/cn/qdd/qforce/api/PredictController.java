package cn.qdd.qforce.api;

import cn.qdd.qforce.api.dto.NaturalLanguagePredictRequest;
import cn.qdd.qforce.api.dto.PredictFeedbackRequest;
import cn.qdd.qforce.api.dto.PredictFeedbackResponse;
import cn.qdd.qforce.api.dto.PredictRequest;
import cn.qdd.qforce.api.dto.PredictResponse;
import cn.qdd.qforce.api.dto.SimpleEventPredictRequest;
import cn.qdd.qforce.service.BehaviorPredictService;
import cn.qdd.qforce.service.FeedbackLearningService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PredictController {

    private final BehaviorPredictService behaviorPredictService;
    private final FeedbackLearningService feedbackLearningService;

    public PredictController(BehaviorPredictService behaviorPredictService, FeedbackLearningService feedbackLearningService) {
        this.behaviorPredictService = behaviorPredictService;
        this.feedbackLearningService = feedbackLearningService;
    }

    @PostMapping("/predict-behavior")
    public PredictResponse predictBehavior(@Valid @RequestBody PredictRequest request) {
        return behaviorPredictService.predict(request);
    }

    @PostMapping("/predict-behavior/auto")
    public PredictResponse predictBehaviorFromNarrative(@Valid @RequestBody NaturalLanguagePredictRequest request) {
        return behaviorPredictService.predictFromNarrative(request);
    }

    @PostMapping("/predict-behavior/simple")
    public PredictResponse predictBehaviorFromSimpleEvent(@Valid @RequestBody SimpleEventPredictRequest request) {
        return behaviorPredictService.predictFromSimpleEvent(request.event());
    }

    @PostMapping("/predict-behavior/feedback")
    public PredictFeedbackResponse submitFeedback(@Valid @RequestBody PredictFeedbackRequest request) {
        return feedbackLearningService.recordFeedback(request);
    }
}

