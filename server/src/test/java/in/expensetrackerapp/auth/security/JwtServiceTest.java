package in.expensetrackerapp.auth.security;

import in.expensetrackerapp.config.JwtProperties;
import in.expensetrackerapp.user.model.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties jwtProperties;
    private static final String VALID_TEST_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLW11c3QtYmUtYXQtbGVhc3QtMjU2LWJpdHM=";

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(VALID_TEST_SECRET);
        jwtProperties.setExpirationMs(3600000); // 1 hour
        jwtProperties.setIssuer("expensetrackerapp");
        jwtService = new JwtService(jwtProperties);
    }

    @Test
    void testGenerateAndExtractUserId() {
        User user = new User("user-abc-123", "Test User", "test@example.com", "hashed_pwd", Instant.now(), Instant.now());
        String token = jwtService.generateToken(user);

        assertNotNull(token);
        String extractedUserId = jwtService.extractUserId(token);
        assertEquals("user-abc-123", extractedUserId);
    }

    @Test
    void testJwtClaims_ContainsUserIdSubjectAndNoEmailOrName() {
        User user = new User("user-abc-123", "Test User", "test@example.com", "hashed_pwd", Instant.now(), Instant.now());
        String token = jwtService.generateToken(user);

        Claims claims = jwtService.extractAllClaims(token);

        // Subject MUST be the stable user ID
        assertEquals("user-abc-123", claims.getSubject());
        assertEquals("expensetrackerapp", claims.getIssuer());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());

        // Verify neither email nor name is stored in JWT claims
        assertNull(claims.get("email"));
        assertNull(claims.get("name"));
    }

    @Test
    void testIsTokenValid_SuccessWithUserId() {
        User user = new User("user-abc-123", "Test User", "test@example.com", "hashed_pwd", Instant.now(), Instant.now());
        String token = jwtService.generateToken(user);

        assertTrue(jwtService.isTokenValid(token, "user-abc-123"));
        assertFalse(jwtService.isTokenValid(token, "different-user-id"));
    }

    @Test
    void testIsTokenExpired_WithNegativeExpiration() {
        jwtProperties.setExpirationMs(-1000); // Already expired
        JwtService expiredJwtService = new JwtService(jwtProperties);

        User user = new User("user-abc-123", "Test User", "test@example.com", "hashed_pwd", Instant.now(), Instant.now());
        String token = expiredJwtService.generateToken(user);

        assertFalse(expiredJwtService.isTokenValid(token, "user-abc-123"));
    }

    @Test
    void testMissingJwtSecret_ThrowsIllegalStateException() {
        JwtProperties missingSecretProperties = new JwtProperties();
        missingSecretProperties.setSecret(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new JwtService(missingSecretProperties));
        assertTrue(ex.getMessage().contains("JWT_SECRET configuration is missing"));
    }

    @Test
    void testEmptyJwtSecret_ThrowsIllegalStateException() {
        JwtProperties emptySecretProperties = new JwtProperties();
        emptySecretProperties.setSecret("   ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new JwtService(emptySecretProperties));
        assertTrue(ex.getMessage().contains("JWT_SECRET configuration is missing"));
    }

    @Test
    void testWeakJwtSecret_ThrowsIllegalStateException() {
        JwtProperties weakSecretProperties = new JwtProperties();
        weakSecretProperties.setSecret("too-short-secret"); // less than 32 bytes

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new JwtService(weakSecretProperties));
        assertTrue(ex.getMessage().contains("JWT_SECRET is too short"));
    }
}
