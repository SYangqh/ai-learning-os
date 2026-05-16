package com.learningos.modules.path.controller;

import com.learningos.modules.path.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 学习报告 PDF 导出。
 */
@RestController
@RequestMapping("/api/path")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * GET /api/path/{pathId}/report
     * 下载当前用户指定学习路径的 PDF 报告。
     */
    @GetMapping("/{pathId}/report")
    public ResponseEntity<byte[]> downloadReport(
            @PathVariable UUID pathId,
            @AuthenticationPrincipal UUID userId) {

        byte[] pdf = reportService.generatePathReport(pathId, userId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"learning-report.pdf\"")
                .body(pdf);
    }
}
