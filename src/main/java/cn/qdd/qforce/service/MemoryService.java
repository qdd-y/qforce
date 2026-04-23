package cn.qdd.qforce.service;

import cn.qdd.qforce.domain.MemorySnippet;
import cn.qdd.qforce.repository.MemorySnippetRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class MemoryService {

    private static final List<MemorySnippet> DEFAULT_MEMORY = List.of(
            new MemorySnippet(
                    "m-1",
                    "当工作压力高、且缺乏明确优先级时，你更容易先拖延，再在临近截止前集中处理。"
            ),
            new MemorySnippet(
                    "m-2",
                    "当出现人际冲突时，你倾向于先回避，随后通过寻求可信同伴建议来稳定状态。"
            ),
            new MemorySnippet(
                    "m-3",
                    "当事件直接影响你的职业目标时，你通常会提升执行动作概率。"
            )
    );

    private final MemorySnippetRepository memorySnippetRepository;

    public MemoryService(MemorySnippetRepository memorySnippetRepository) {
        this.memorySnippetRepository = memorySnippetRepository;
    }

    public List<MemorySnippet> retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalized = query.toLowerCase(Locale.ROOT);
        int limit = Math.max(topK, 1);
        List<MemorySnippet> allMemory = memorySnippetRepository.findAll();
        List<MemorySnippet> source = allMemory.isEmpty() ? DEFAULT_MEMORY : allMemory;

        return source.stream()
                .sorted(Comparator.comparingInt((MemorySnippet m) -> score(normalized, m.content())).reversed())
                .limit(limit)
                .toList();
    }

    private int score(String query, String memoryText) {
        String m = memoryText.toLowerCase(Locale.ROOT);
        int score = 0;
        if (query.contains("工作") && m.contains("工作")) score += 2;
        if (query.contains("冲突") && m.contains("冲突")) score += 2;
        if (query.contains("压力") && m.contains("压力")) score += 2;
        if (query.contains("拖延") && m.contains("拖延")) score += 1;
        if (query.contains("寻求") && m.contains("寻求")) score += 1;
        return score;
    }
}

