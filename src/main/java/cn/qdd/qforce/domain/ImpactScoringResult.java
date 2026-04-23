package cn.qdd.qforce.domain;

import cn.qdd.qforce.api.dto.ImpactScore;

import java.util.List;

public record ImpactScoringResult(ImpactScore score, List<String> contributions) {
}

