package com.parkflow.ParkFlow.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.parkflow.ParkFlow.user.User;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

/**
 * Issues and checks login tokens (JWT signed with HS256).
 * The same token works for the website, the iOS app and the ESP32 car screen:
 * send it as "Authorization: Bearer <token>".
 */
@Service
public class TokenService {
    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    static final String ISSUER = "parkflow";

    private final SecretKey key;
    private final Duration ttl;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public TokenService(@Value("${parkflow.jwt.secret:}") String secret,
                        @Value("${parkflow.jwt.ttl:7d}") Duration ttl) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // Without a fixed secret, tokens stop working after every restart. Fine for a quick local run only.
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            log.warn("PARKFLOW_JWT_SECRET is missing or shorter than 32 characters. Using a random secret: "
                    + "everyone is logged out when the server restarts. Set PARKFLOW_JWT_SECRET in production.");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.ttl = ttl;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        d.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        this.decoder = d;
    }

    public record IssuedToken(String value, Instant expiresAt) {}

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expires = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(expires)
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expires);
    }

    public JwtDecoder decoder() { return decoder; }
}
