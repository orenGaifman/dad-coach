package com.dadcoach.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dadcoach.api.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** A request rejected by a security chain gets the same error shape as everything else, and is logged. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper json;

    public RestAuthenticationEntryPoint(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(json.writeValueAsString(new ErrorResponse(Instant.now(),
                HttpStatus.UNAUTHORIZED.value(), "UNAUTHENTICATED", "Authentication is required",
                CorrelationIdFilter.currentCorrelationId(), List.of())));
        log.atInfo().setMessage("http.request.completed")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("endpoint", request.getRequestURI())
                .addKeyValue("status", HttpStatus.UNAUTHORIZED.value())
                .addKeyValue("durationMs", CorrelationIdFilter.elapsedMillis(request))
                .log();
    }
}
