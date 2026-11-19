package in.expensetrackerapp.auth.service;

import in.expensetrackerapp.auth.controller.AuthController;
import in.expensetrackerapp.auth.security.JwtAuthenticationEntryPoint;
import in.expensetrackerapp.auth.security.JwtAuthenticationFilter;
import in.expensetrackerapp.auth.security.JwtService;
import in.expensetrackerapp.auth.security.SecurityConfig;
import in.expensetrackerapp.common.exception.GlobalExceptionHandler;
import in.expensetrackerapp.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/** The PasswordEncoder bean the application actually uses is BCrypt. */
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        CorsConfig.class, GlobalExceptionHandler.class})
class PasswordEncoderWiringTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void theConfiguredEncoderIsBCryptAndRoundTrips() {
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        String hash = passwordEncoder.encode("Password123");
        assertThat(hash).startsWith("$2");
        assertThat(passwordEncoder.matches("Password123", hash)).isTrue();
        assertThat(passwordEncoder.matches("password123", hash)).isFalse();
    }
}
