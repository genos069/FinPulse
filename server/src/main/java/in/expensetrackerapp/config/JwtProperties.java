package in.expensetrackerapp.config;

import io.jsonwebtoken.io.Decoders;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

@Configuration
@ConfigurationProperties(prefix = "application.jwt")
public class JwtProperties {

    /**
     * Secret key for HMAC-SHA256 signing.
     * MUST be supplied through the JWT_SECRET environment variable.
     * No default fallback is permitted.
     */
    private String secret;

    /**
     * Default expiration: 24 hours (86,400,000 ms)
     */
    private long expirationMs = 86400000;

    /**
     * Token issuer claim.
     */
    private String issuer = "expensetrackerapp";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    /**
     * Validates that JWT_SECRET is present and at least 256 bits (32 bytes).
     * Fails fast at startup if missing or weak.
     */
    public void validate() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT_SECRET configuration is missing! Set the JWT_SECRET environment variable with a 256-bit (minimum 32 bytes) secret."
            );
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
            if (keyBytes.length < 32) {
                keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short! HMAC-SHA256 requires at least 256 bits (32 bytes). Provided length: " + keyBytes.length + " bytes."
            );
        }
    }
}
