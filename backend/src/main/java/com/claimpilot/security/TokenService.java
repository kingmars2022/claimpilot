package com.claimpilot.security;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.claimpilot.config.AppProperties;
import com.claimpilot.user.AppUser;

@Service
public class TokenService {

    private static final String ISSUER = "claimpilot";

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

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
