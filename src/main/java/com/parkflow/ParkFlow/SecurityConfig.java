package com.parkflow.ParkFlow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
 @Bean
 SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${parkflow.api-key}") String key) throws Exception {
  if (key.isBlank()) throw new IllegalStateException("PARKFLOW_API_KEY must not be blank");
  // Header-only authentication: no cookies, sessions, form login or HTTP Basic.
  http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .csrf(c -> c.ignoringRequestMatchers("/api/**"))
   .authorizeHttpRequests(a -> a
    .requestMatchers("/api", "/api/**").hasAuthority("CLIPBOARD_API")
    .requestMatchers("/", "/index.html", "/mcr1.html", "/clipboard.html", "/clipboard.js", "/clipboard.css", "/error").permitAll()
    .anyRequest().denyAll())
   .exceptionHandling(e -> e.authenticationEntryPoint((request, response, error) -> {
    response.setStatus(401);
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-store");
    response.getWriter().write("{\"error\":\"Invalid API key\"}");
   }))
   .addFilterBefore(new OncePerRequestFilter() {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
     // MockMvc may leave servletPath empty. requestURI also works under a WAR context.
     String path = request.getRequestURI().substring(request.getContextPath().length());
     if (path.equals("/api") || path.startsWith("/api/")) {
      response.setHeader("Cache-Control", "no-store");
      String supplied = request.getHeader("X-API-Key");
      if (supplied == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
       response.setStatus(401); response.setContentType("application/json");
       response.getWriter().write("{\"error\":\"Invalid API key\"}"); return;
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
      return;
     }
     chain.doFilter(request, response);
    }
   }, UsernamePasswordAuthenticationFilter.class);
  return http.build();
 }
}
