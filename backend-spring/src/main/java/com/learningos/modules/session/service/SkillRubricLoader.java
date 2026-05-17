package com.learningos.modules.session.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 classpath:skills/{skillId}.skill.yaml 读取教学资源。
 * Phase 4 实现：rubric、task_description、analogy_map 三节均已接入。
 */
@Component
@Slf4j
public class SkillRubricLoader {

    public record RubricCriteria(List<String> passCriteria, List<String> failHints) {
        public static final RubricCriteria EMPTY = new RubricCriteria(List.of(), List.of());
    }

    public record StageData(String taskDescription) {
        public static final StageData EMPTY = new StageData(null);
        public boolean hasTaskDescription() { return taskDescription != null && !taskDescription.isBlank(); }
    }

    /**
     * 加载指定 skillId 和 stageIndex（0-based）对应的 Rubric 评审标准。
     */
    @SuppressWarnings("unchecked")
    public RubricCriteria load(String skillId, int stageIndex) {
        if (skillId == null || skillId.isBlank()) return RubricCriteria.EMPTY;

        String path = "skills/" + skillId + ".skill.yaml";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.debug("Skill YAML not found: {}", path);
            return RubricCriteria.EMPTY;
        }

        try (InputStream is = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            List<Map<String, Object>> stages = (List<Map<String, Object>>) root.get("stages");
            if (stages == null) return RubricCriteria.EMPTY;

            int targetIndex = stageIndex + 1;
            for (Map<String, Object> stage : stages) {
                Object idxObj = stage.get("index");
                if (idxObj == null) continue;
                if (((Number) idxObj).intValue() == targetIndex) {
                    Map<String, Object> rubric = (Map<String, Object>) stage.get("rubric");
                    if (rubric == null) return RubricCriteria.EMPTY;
                    List<String> pass = (List<String>) rubric.getOrDefault("pass_criteria", List.of());
                    List<String> fail = (List<String>) rubric.getOrDefault("fail_hints", List.of());
                    return new RubricCriteria(pass, fail);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load skill rubric for skillId={}, stage={}: {}", skillId, stageIndex, e.getMessage());
        }
        return RubricCriteria.EMPTY;
    }

    /**
     * 加载指定 stageIndex（0-based）对应的 artifact_type（大写）。
     * 若 YAML 未声明或文件不存在，默认返回 "CODE"（向后兼容）。
     * 合法值：CODE / NOTE / DIAGRAM / ESSAY / PROOF / NONE
     */
    @SuppressWarnings("unchecked")
    public String loadArtifactType(String skillId, int stageIndex) {
        if (skillId == null || skillId.isBlank()) return "CODE";

        String path = "skills/" + skillId + ".skill.yaml";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) return "CODE";

        try (InputStream is = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            List<Map<String, Object>> stages = (List<Map<String, Object>>) root.get("stages");
            if (stages == null) return "CODE";

            int targetIndex = stageIndex + 1;
            for (Map<String, Object> stage : stages) {
                Object idxObj = stage.get("index");
                if (idxObj == null) continue;
                if (((Number) idxObj).intValue() == targetIndex) {
                    Object typeObj = stage.get("artifact_type");
                    return typeObj != null ? typeObj.toString().toUpperCase() : "CODE";
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load artifact_type for skillId={}, stage={}: {}", skillId, stageIndex, e.getMessage());
        }
        return "CODE";
    }

    /**
     * 加载指定 stageIndex（0-based）对应的 task_description。
     */
    @SuppressWarnings("unchecked")
    public StageData loadStageData(String skillId, int stageIndex) {
        if (skillId == null || skillId.isBlank()) return StageData.EMPTY;

        String path = "skills/" + skillId + ".skill.yaml";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) return StageData.EMPTY;

        try (InputStream is = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            List<Map<String, Object>> stages = (List<Map<String, Object>>) root.get("stages");
            if (stages == null) return StageData.EMPTY;

            int targetIndex = stageIndex + 1;
            for (Map<String, Object> stage : stages) {
                Object idxObj = stage.get("index");
                if (idxObj == null) continue;
                if (((Number) idxObj).intValue() == targetIndex) {
                    String taskDesc = (String) stage.get("task_description");
                    return new StageData(taskDesc);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load stage data for skillId={}, stage={}: {}", skillId, stageIndex, e.getMessage());
        }
        return StageData.EMPTY;
    }

    /**
     * 加载指定背景对应的 analogy_map 条目。
     * background 为自由文本（如"前端工程师"），内部规范化为 YAML key。
     * 返回 concept→analogy 的有序 Map，若无对应数据则返回空 Map。
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> loadAnalogies(String skillId, String background) {
        if (skillId == null || skillId.isBlank()) return Map.of();

        String bgKey = resolveBackgroundKey(background);
        String path = "skills/" + skillId + ".skill.yaml";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) return Map.of();

        try (InputStream is = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            Map<String, Object> analogyMap = (Map<String, Object>) root.get("analogy_map");
            if (analogyMap == null) return Map.of();
            Object bgEntry = analogyMap.get(bgKey);
            if (bgEntry instanceof Map<?, ?> bgMap) {
                Map<String, String> result = new LinkedHashMap<>();
                bgMap.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
                return result;
            }
        } catch (Exception e) {
            log.warn("Failed to load analogies for skillId={}, bg={}: {}", skillId, background, e.getMessage());
        }
        return Map.of();
    }

    /**
     * 将自由文本背景映射到 YAML analogy_map 的标准 key。
     */
    private String resolveBackgroundKey(String background) {
        if (background == null) return "other";
        String lower = background.toLowerCase();
        if (lower.contains("frontend") || lower.contains("前端") || lower.contains("react")
                || lower.contains("vue") || lower.contains("js") || lower.contains("css")) return "frontend";
        if (lower.contains("hardware") || lower.contains("硬件") || lower.contains("嵌入式")
                || lower.contains("fpga") || lower.contains("单片机") || lower.contains("芯片")) return "hardware";
        if (lower.contains("finance") || lower.contains("金融") || lower.contains("量化")
                || lower.contains("银行") || lower.contains("投资")) return "finance";
        if (lower.contains("product") || lower.contains("产品") || lower.contains("pm")
                || lower.contains("运营")) return "product";
        return "other";
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Phase 9B: 混合作答模式与预制答案
    // ────────────────────────────────────────────────────────────────────────────────

    public record InteractionConfig(String mode, List<PresetAnswer> presetAnswers) {
        public static final InteractionConfig DEFAULT = new InteractionConfig("HYBRID", List.of());
    }

    public record PresetAnswer(String id, String text, String confidence, Integer stageIndex) {}

    /**
     * 加载 Skill 的交互模式配置（interaction_mode + preset_answers）。
     * @param skillId Skill ID
     * @param stageIndex 0-based 阶段索引（可选，用于过滤 stage 专属的预制答案）
     * @return InteractionConfig（mode + 可用的预制答案列表）
     */
    @SuppressWarnings("unchecked")
    public InteractionConfig loadInteractionConfig(String skillId, Integer stageIndex) {
        if (skillId == null || skillId.isBlank()) {
            log.debug("[InteractionConfig] skillId 为空，返回默认配置");
            return InteractionConfig.DEFAULT;
        }

        String path = "skills/" + skillId + ".skill.yaml";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            log.warn("[InteractionConfig] Skill YAML 不存在: {}", path);
            return InteractionConfig.DEFAULT;
        }

        try (InputStream is = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);

            // 读取 interaction_mode（默认 HYBRID）
            String mode = (String) root.getOrDefault("interaction_mode", "HYBRID");
            log.debug("[InteractionConfig] skillId={}, stageIndex={}, mode={}", skillId, stageIndex, mode);

            // 读取 preset_answers 列表
            List<Map<String, Object>> rawAnswers = (List<Map<String, Object>>) root.get("preset_answers");
            List<PresetAnswer> answers = List.of();
            if (rawAnswers != null) {
                log.debug("[InteractionConfig] 原始预制答案数量: {}", rawAnswers.size());
                answers = rawAnswers.stream()
                        .map(m -> {
                            String id = (String) m.get("id");
                            String text = (String) m.get("text");
                            String confidence = (String) m.getOrDefault("confidence", "medium");
                            Object stgIdx = m.get("stage_index");
                            Integer targetStage = stgIdx instanceof Number ? ((Number) stgIdx).intValue() : null;
                            return new PresetAnswer(id, text, confidence, targetStage);
                        })
                        .filter(a -> {
                            // 如果 stageIndex 参数为 null，返回全局答案（targetStage == null）
                            // 否则返回匹配当前 stage 的答案 + 全局答案
                            if (stageIndex == null) return a.stageIndex() == null;
                            boolean match = a.stageIndex() == null || a.stageIndex() == (stageIndex + 1);  // YAML 中 stage_index 是 1-based
                            if (match) {
                                log.debug("[InteractionConfig] 预制答案匹配: id={}, text={}, confidence={}, targetStage={}", 
                                    a.id(), a.text(), a.confidence(), a.stageIndex());
                            }
                            return match;
                        })
                        .toList();
                log.debug("[InteractionConfig] 过滤后预制答案数量: {}", answers.size());
            }

            InteractionConfig result = new InteractionConfig(mode, answers);
            log.info("[InteractionConfig] 返回配置: skillId={}, stageIndex={}, mode={}, answersCount={}", 
                skillId, stageIndex, mode, answers.size());
            return result;
        } catch (Exception e) {
            log.warn("Failed to load interaction config for skillId={}, stage={}: {}", skillId, stageIndex, e.getMessage());
        }
        return InteractionConfig.DEFAULT;
    }
}

