package com.claimpilot.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import com.claimpilot.config.AppProperties;
import com.claimpilot.user.AppUser;

@Service
public class TokenService {

    private static final String ISSUER = "claimpilot";
    /** Claim marking a stream ticket; such a token opens the notification stream and nothing else. */
    static final String PURPOSE = "purpose";
    static final String STREAM = "notification-stream";
    private static final Duration TICKET_TTL = Duration.ofSeconds(60);

    private final JwtEncoder encoder;
    private final AppProperties.Security settings;

    public TokenService(JwtEncoder encoder, AppProperties properties) {
        this.encoder = encoder;
        this.settings = properties.security();
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(settings.tokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * A one-minute ticket for opening the notification stream. It travels in the URL (browsers
     * cannot add headers to an EventSource), so it must be short-lived and useless elsewhere.
     */
    public IssuedToken issueStreamTicket(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(TICKET_TTL);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(PURPOSE, STREAM)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
    }

    static boolean isStreamTicket(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken jwt
                && STREAM.equals(jwt.getToken().getClaimAsString(PURPOSE));
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
