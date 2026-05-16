package com.learningos.modules.path.service;

import com.learningos.common.exception.AppException;
import com.learningos.modules.artifact.entity.Artifact;
import com.learningos.modules.artifact.repository.ArtifactRepository;
import com.learningos.modules.path.entity.LearningPath;
import com.learningos.modules.path.entity.Stage;
import com.learningos.modules.path.repository.LearningPathRepository;
import com.learningos.modules.path.repository.StageRepository;
import com.learningos.modules.session.entity.LearningSession;
import com.learningos.modules.session.repository.LearningSessionRepository;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PDF 报告生成服务。
 * 内容：学习路径标题 + 各阶段（状态、目标、产出、评分）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final LearningPathRepository pathRepository;
    private final StageRepository stageRepository;
    private final LearningSessionRepository sessionRepository;
    private final ArtifactRepository artifactRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    // 使用 OpenPDF 内置字体（Helvetica 不含中文，实际部署需挂载 CJK 字体；此处用于演示）
    private static final Font TITLE_FONT   = new Font(Font.HELVETICA, 20, Font.BOLD,   new Color(30, 30, 30));
    private static final Font H2_FONT      = new Font(Font.HELVETICA, 14, Font.BOLD,   new Color(50, 50, 200));
    private static final Font LABEL_FONT   = new Font(Font.HELVETICA, 10, Font.BOLD,   new Color(80, 80, 80));
    private static final Font BODY_FONT    = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(50, 50, 50));
    private static final Font MONO_FONT    = new Font(Font.COURIER,   9,  Font.NORMAL, new Color(20, 20, 20));
    private static final Font FAINT_FONT   = new Font(Font.HELVETICA, 9,  Font.ITALIC, new Color(120, 120, 120));

    public byte[] generatePathReport(UUID pathId, UUID userId) {
        LearningPath path = pathRepository.findById(pathId)
                .orElseThrow(() -> AppException.notFound("学习路径不存在"));
        if (!path.getUserId().equals(userId)) {
            throw AppException.forbidden("无权访问该学习路径");
        }

        List<Stage> stages = stageRepository.findByPathIdOrderByStageIndex(path.getId());
        if (stages.isEmpty()) {
            throw AppException.badRequest("该路径暂无阶段，无法生成报告");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 60);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // ── 封面 ──────────────────────────────────────────────────────────
            doc.add(new Paragraph("AI Learning OS", TITLE_FONT));
            doc.add(new Paragraph("Learning Report", H2_FONT));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Path: " + path.getTitle(), BODY_FONT));
            if (path.getDescription() != null) {
                doc.add(new Paragraph(path.getDescription(), FAINT_FONT));
            }
            doc.add(new Paragraph("Created: " + path.getCreatedAt().format(FMT), FAINT_FONT));
            doc.add(new Paragraph("Status: " + path.getStatus(), FAINT_FONT));
            doc.add(Chunk.NEWLINE);
            doc.add(new LineSeparator());
            doc.add(Chunk.NEWLINE);

            // ── 各阶段 ────────────────────────────────────────────────────────
            for (Stage stage : stages) {
                String statusEmoji = switch (stage.getStatus()) {
                    case "completed" -> "[PASS] ";
                    case "active"    -> "[IN PROGRESS] ";
                    default          -> "[LOCKED] ";
                };

                doc.add(new Paragraph(statusEmoji + "Stage " + (stage.getStageIndex() + 1)
                        + ": " + stage.getTitle(), H2_FONT));

                if (stage.getGoal() != null) {
                    doc.add(new Paragraph("Goal: " + stage.getGoal(), BODY_FONT));
                }

                // 查找该阶段的 session（取最近一条）
                List<LearningSession> sessions = sessionRepository.findByStageIdOrderByStartedAtDesc(stage.getId());
                if (!sessions.isEmpty()) {
                    LearningSession session = sessions.get(0);

                    // 该阶段的所有产出物（按 stageId 查）
                    List<Artifact> artifacts = artifactRepository
                            .findByStageIdAndUserIdOrderByCreatedAtDesc(stage.getId(), userId);

                    if (!artifacts.isEmpty()) {
                        doc.add(new Paragraph("Artifacts:", LABEL_FONT));
                        for (Artifact artifact : artifacts) {
                            doc.add(new Paragraph("  [" + artifact.getType() + "] "
                                    + "Status: " + artifact.getStatus(),
                                    BODY_FONT));

                            // 内容预览（最多 500 字符）
                            if (artifact.getContent() != null && !artifact.getContent().isBlank()) {
                                String preview = artifact.getContent().length() > 500
                                        ? artifact.getContent().substring(0, 500) + "..."
                                        : artifact.getContent();
                                doc.add(new Paragraph(preview, MONO_FONT));
                            }
                        }
                    }

                    // 进度 JSON 里的 review 结果
                    Map<String, Object> progress = session.getProgress();
                    if (progress != null) {
                        Object rubricScore = progress.get("rubric_score");
                        Object rubricFeedback = progress.get("rubric_feedback");
                        if (rubricScore != null) {
                            doc.add(new Paragraph("Review Score: " + rubricScore, LABEL_FONT));
                        }
                        if (rubricFeedback != null) {
                            doc.add(new Paragraph("Review: " + rubricFeedback, FAINT_FONT));
                        }
                    }
                }

                doc.add(Chunk.NEWLINE);
            }

            doc.close();
            log.info("Generated PDF report for path={} user={} pages=?", pathId, userId);
            return baos.toByteArray();

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF generation failed: {}", e.getMessage());
            throw AppException.internal("报告生成失败，请稍后重试");
        }
    }
}
