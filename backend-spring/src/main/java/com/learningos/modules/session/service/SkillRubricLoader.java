package com.learningos.modules.session.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 从 classpath:skills/{skillId}.skill.yaml 读取 Rubric 评审标准。
 * Phase 4 将由完整 SkillRegistry 替代，此处仅解析 rubric 节。
 */
@Component
@Slf4j
public class SkillRubricLoader {

    public record RubricCriteria(List<String> passCriteria, List<String> failHints) {
        public static final RubricCriteria EMPTY = new RubricCriteria(List.of(), List.of());
    }

    /**
     * 加载指定 skillId 和 stageIndex（0-based）对应的 Rubric 评审标准。
     * YAML 中 stage.index 是 1-based，内部转换后匹配。
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

            // YAML index 是 1-based；stageIndex 是 0-based
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
}
