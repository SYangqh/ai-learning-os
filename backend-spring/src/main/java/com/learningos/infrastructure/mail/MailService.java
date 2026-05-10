package com.learningos.infrastructure.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 邮件发送服务。
 *
 * <p>所有发送操作均标注 {@code @Async}，在独立线程执行，不阻塞主流程。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.magic-link.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    /**
     * 发送魔法链接登录邮件（异步）。
     *
     * @param toEmail   收件人邮箱
     * @param rawToken  未经哈希的原始 token（用于构建 URL）
     */
    @Async
    public void sendMagicLink(String toEmail, String rawToken) {
        try {
            String magicUrl = frontendBaseUrl + "/auth/verify?token=" + rawToken;

            Context ctx = new Context(Locale.SIMPLIFIED_CHINESE);
            ctx.setVariable("magicUrl", magicUrl);
            ctx.setVariable("expiryMinutes", 10);

            String html = templateEngine.process("email/magic-link", ctx);

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                msg,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name()
            );
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("您的登录链接 — AI Learning OS");
            helper.setText(html, true);   // true = isHtml

            mailSender.send(msg);
            log.info("Magic link sent to {}", maskEmail(toEmail));
        } catch (Exception e) {
            // 邮件发送失败不应影响主流程（前端已收到"邮件已发送"提示）
            log.error("Failed to send magic link to {}: {}", maskEmail(toEmail), e.getMessage());
        }
    }

    /** 对邮箱做简单脱敏，防止日志泄露 */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return "**" + domain;
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }
}
