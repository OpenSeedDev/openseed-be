package com.seedrank.ops.http;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class HttpAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpAccessLogFilter.class);
    private static final String UNMAPPED_ROUTE = "UNMAPPED";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
            Object routeAttribute = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String route = routeAttribute == null ? UNMAPPED_ROUTE : routeAttribute.toString();
            log.info(
                    "{{\"event\":\"http_request\",\"requestId\":\"{}\",\"method\":\"{}\",\"route\":\"{}\",\"status\":{},\"durationMs\":{}}}",
                    requestId,
                    request.getMethod(),
                    route,
                    response.getStatus(),
                    durationMs);
        }
    }
}
