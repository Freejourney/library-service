package com.elibrary.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.audiences}") String audience,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri) {
        NimbusJwtDecoder decoder = jwkSetUri.isBlank()
                ? NimbusJwtDecoder.withIssuerLocation(issuer).build()
                : NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Invalid audience", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, exception) ->
                writeSecurityError(request, response, objectMapper, 401, "UNAUTHENTICATED");
        AccessDeniedHandler accessDeniedHandler = (request, response, exception) ->
                writeSecurityError(request, response, objectMapper, 403, "FORBIDDEN");
        return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/books", "/api/v1/books/**").permitAll()
                .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(o -> o
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> {}))
                .build();
    }

    private static void writeSecurityError(HttpServletRequest request, HttpServletResponse response,
                                           ObjectMapper objectMapper, int status, String code) throws IOException {
        Object traceId = request.getAttribute("traceId");
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "code", code,
                "message", status == 401 ? "Authentication required" : "Access denied",
                "details", Map.of(),
                "timestamp", Instant.now(),
                "traceId", traceId == null ? "" : traceId.toString()));
    }
}
