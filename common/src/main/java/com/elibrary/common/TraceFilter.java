package com.elibrary.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {
    public static final String TRACE_ID_ATTRIBUTE = "traceId";

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String trace=request.getHeader("X-Trace-Id");
        if (trace==null || trace.isBlank() || trace.length()>128) trace=UUID.randomUUID().toString();
        request.setAttribute(TRACE_ID_ATTRIBUTE, trace);
        response.setHeader("X-Trace-Id", trace);
        MDC.put(TRACE_ID_ATTRIBUTE, trace);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_ATTRIBUTE);
        }
    }
}
