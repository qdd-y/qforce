INSERT INTO memory_snippet (id, content)
VALUES
    ('m-1', '当工作压力高、且缺乏明确优先级时，你更容易先拖延，再在临近截止前集中处理。'),
    ('m-2', '当出现人际冲突时，你倾向于先回避，随后通过寻求可信同伴建议来稳定状态。'),
    ('m-3', '当事件直接影响你的职业目标时，你通常会提升执行动作概率。')
ON CONFLICT (id) DO NOTHING;

INSERT INTO adaptive_profile (
    id,
    mood_sensitivity,
    focus_sensitivity,
    stress_sensitivity,
    procrastination_baseline,
    action_baseline,
    help_seeking_baseline
)
VALUES (1, 1.0, 1.0, 1.0, 0.5, 0.5, 0.4)
ON CONFLICT (id) DO NOTHING;
