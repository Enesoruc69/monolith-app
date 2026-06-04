package com.shopwave.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String MDC_CORRELATION_KEY = "correlationId";
    private static final String MDC_REQUEST_KEY = "requestId";

    @Value("${shopwave.logging.correlation-id.enabled:false}")
    private boolean enabled;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (enabled && request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            String correlationId = httpRequest.getHeader(CORRELATION_HEADER);
            if (correlationId == null || correlationId.trim().isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }
            MDC.put(MDC_CORRELATION_KEY, correlationId);
            MDC.put(MDC_REQUEST_KEY, correlationId);
            httpResponse.setHeader(CORRELATION_HEADER, correlationId);
        }
        
        try {
            chain.doFilter(request, response);
        } finally {
            if (enabled) {
                MDC.remove(MDC_CORRELATION_KEY);
                MDC.remove(MDC_REQUEST_KEY);
            }
        }
    }
}
