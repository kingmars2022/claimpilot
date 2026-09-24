package com.claimpilot.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import com.claimpilot.config.AppProperties;

/**
 * Stateless API security: clients send "Authorization: Bearer &lt;token&gt;" on every request.
 * No cookies are used, so CSRF protection is not needed. What a user may see is decided per
 * request by ownership (each user sees only their own policies, receipts and claims), not by role.
 */
@Configuration
public class SecurityConfig {

    private static final int MIN_SECRET_BYTES = 32;  // HS256 needs a 256-bit key
    private static final String NOTIFICATION_STREAM = "/api/notifications/stream";

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Follow-up dispatches of an already authorized request (streaming, errors).
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // The stream takes only a one-minute ticket; tickets are refused everywhere else.
                        .requestMatchers(HttpMethod.GET, NOTIFICATION_STREAM).access((authentication, context) ->
                                new AuthorizationDecision(TokenService.isStreamTicket(authentication.get())))
                        .requestMatchers("/api/**").access((authentication, context) -> {
                            Authentication user = authentication.get();
                            return new AuthorizationDecision(user != null && user.isAuthenticated()
                                    && !(user instanceof AnonymousAuthenticationToken)
                                    && !TokenService.isStreamTicket(user));
                        })
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(Customizer.withDefaults());
        return http.build();
    }

    /**
     * The token comes from the Authorization header, except on the notification stream: a browser
     * EventSource cannot set headers, so there a one-minute stream ticket is read from
     * {@code ?access_token=}. A normal session token in a URL is never accepted.
     */
    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
        DefaultBearerTokenResolver query = new DefaultBearerTokenResolver();
        query.setAllowUriQueryParameter(true);
        return request -> NOTIFICATION_STREAM.equals(request.getRequestURI()) ? query.resolve(request)
                : header.resolve(request);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(AppProperties properties) {
        return NimbusJwtEncoder.withSecretKey(signingKey(properties)).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(AppProperties properties) {
        return NimbusJwtDecoder.withSecretKey(signingKey(properties)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static SecretKey signingKey(AppProperties properties) {
        String secret = properties.security().jwtSecret();
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "claimpilot.security.jwt-secret must be at least " + MIN_SECRET_BYTES + " characters.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
