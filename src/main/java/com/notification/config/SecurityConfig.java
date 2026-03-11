package com.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 + JWT security. In production, set spring.security.oauth2.resourceserver.jwt.issuer-uri
 * or jwk-set-uri to your IdP (e.g. Auth0, Cognito, Azure AD).
 * When issuer is not set (e.g. local dev), API and GraphQL are permitted without auth.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        boolean useJwt = issuerUri != null && !issuerUri.isBlank();
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (useJwt) {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/v1/internal/**").permitAll() // API-key checked in controller
                    .requestMatchers("/api/v1/**", "/graphql", "/graphql/**").authenticated()
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        } else {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }
        return http.build();
    }
}
