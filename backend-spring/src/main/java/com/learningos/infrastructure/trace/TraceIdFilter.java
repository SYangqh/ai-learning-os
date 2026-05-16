package com.learningos.infrastructure.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 全链路 Trace ID 过滤器。
 * 每个请求生成或透传 UUID trace_id，写入 MDC 供日志使用，并回写 X-Trace-Id 响应头。
 */
@Component
@Order(1)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY    = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 优先使用客户端透传的 trace_id，否则自动生成
        String incoming = request.getHeader(TRACE_ID_HEADER);
        String traceId  = (incoming != null && !incoming.isBlank())
                ? incoming
                : UUID.randomUUID().toString();

        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    /** 供业务代码直接获取当前请求的 trace_id */
    public static String current() {
        String id = MDC.get(TRACE_ID_KEY);
        return id != null ? id : "";
    }
}
