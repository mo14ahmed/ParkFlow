package com.parkflow.ParkFlow.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Three security chains, checked in order:
 * 1. /api/clipboard/**  the private clipboard, protected by the X-API-Key header (unchanged behavior).
 * 2. /api/**            the parking API, protected by login tokens (JWT) and roles.
 * 3. everything else    the static website pages: public.
 */
@Configuration
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    @Order(1)
    SecurityFilterChain clipboardChain(HttpSecurity http, @Value("${parkflow.api-key:}") String key) throws Exception {
        if (key.isBlank()) log.warn("PARKFLOW_API_KEY is not set: the clipboard API is turned off.");
        http.securityMatcher("/api/clipboard", "/api/clipboard/**")
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(c -> c.disable())
            .authorizeHttpRequests(a -> a.anyRequest().hasAuthority("CLIPBOARD_API"))
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, err) -> json(res, 401, "Invalid API key")))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                    response.setHeader("Cache-Control", "no-store");
                    String supplied = request.getHeader("X-API-Key");
                    if (key.isBlank() || supplied == null
                            || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                        json(response, 401, "Invalid API key");
                        return;
                    }
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(new UsernamePasswordAuthenticationToken(
                            "clipboard-client", null, List.of(new SimpleGrantedAuthority("CLIPBOARD_API"))));
                    SecurityContextHolder.setContext(context);
                    try {
                        chain.doFilter(request, response);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }
            }, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, TokenService tokens) throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);

        http.securityMatcher("/api", "/api/**")
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(c -> c.disable()) // no cookies: the token is sent in a header, so CSRF does not apply
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/provider/**").hasRole("PROVIDER")
                .requestMatchers("/api/places").hasAnyRole("CUSTOMER", "PROVIDER", "ADMIN") // providers use it to find their address
                .requestMatchers("/api/search", "/api/buildings/**").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers("/api/me", "/api/me/**").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o
                .jwt(j -> j.decoder(tokens.decoder()).jwtAuthenticationConverter(converter))
                .authenticationEntryPoint((req, res, err) -> json(res, 401, "Log in first, or your session has expired."))
                .accessDeniedHandler((req, res, err) -> json(res, 403, "Your account type cannot do this.")))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, err) -> json(res, 401, "Log in first, or your session has expired."))
                .accessDeniedHandler((req, res, err) -> json(res, 403, "Your account type cannot do this.")))
            .headers(h -> h.cacheControl(c -> {}));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain websiteChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/", "/*.html", "/css/**", "/js/**", "/img/**", "/vendor/**",
                        "/clipboard.js", "/clipboard.css", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().denyAll());
        return http.build();
    }

    private static void json(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        res.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
    }
}
