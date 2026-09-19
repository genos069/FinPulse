package in.expensetrackerapp.auth.controller;

import in.expensetrackerapp.auth.dto.AuthResponse;
import in.expensetrackerapp.auth.dto.LoginRequest;
import in.expensetrackerapp.auth.dto.RegisterRequest;
import in.expensetrackerapp.auth.security.JwtAuthenticationEntryPoint;
import in.expensetrackerapp.auth.security.JwtAuthenticationFilter;
import in.expensetrackerapp.auth.security.JwtService;
import in.expensetrackerapp.auth.security.SecurityConfig;
import in.expensetrackerapp.auth.service.AuthService;
import in.expensetrackerapp.common.exception.DuplicateEmailException;
import in.expensetrackerapp.common.exception.GlobalExceptionHandler;
import in.expensetrackerapp.common.exception.InvalidCredentialsException;
import in.expensetrackerapp.config.CorsConfig;
import in.expensetrackerapp.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, CorsConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void testRegister_Success() throws Exception {
        RegisterRequest request = new RegisterRequest("Jane Doe", "jane.doe@example.com", "SecurePass123!");
        UserResponse response = new UserResponse("1", "Jane Doe", "jane.doe@example.com", Instant.now(), Instant.now());

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane.doe@example.com"));
    }

    @Test
    void testRegister_PasswordWithSpacesAndSymbols_Success() throws Exception {
        // Passwords with spaces, symbols, and Unicode are accepted when length is valid (8-100)
        RegisterRequest request = new RegisterRequest("Jane Doe", "jane.doe@example.com", "my pass with spaces & symbols! 🌟");
        UserResponse response = new UserResponse("1", "Jane Doe", "jane.doe@example.com", Instant.now(), Instant.now());

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void testRegister_PasswordTooShort_ValidationFailure() throws Exception {
        // Less than 8 characters
        RegisterRequest request = new RegisterRequest("Jane Doe", "jane.doe@example.com", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    void testRegister_PasswordTooLong_ValidationFailure() throws Exception {
        // More than 100 characters (101 chars)
        String longPassword = "a".repeat(101);
        RegisterRequest request = new RegisterRequest("Jane Doe", "jane.doe@example.com", longPassword);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    void testRegister_DuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("Jane Doe", "jane.doe@example.com", "SecurePass123!");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateEmailException("An account with email jane.doe@example.com already exists"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("An account with email jane.doe@example.com already exists"));
    }

    @Test
    void testRegister_ValidationFailure_InvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("", "invalid-email", "validpassword");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors").isArray());
    }

    @Test
    void testLogin_Success() throws Exception {
        LoginRequest request = new LoginRequest("jane.doe@example.com", "SecurePass123!");
        UserResponse userResponse = new UserResponse("1", "Jane Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        AuthResponse authResponse = new AuthResponse("mocked.jwt.token", "Bearer", 86400, userResponse);

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mocked.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("jane.doe@example.com"));
    }

    @Test
    void testLogin_InvalidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("jane.doe@example.com", "WrongPassword!");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void testRegister_WithInvalidToken_SucceedsWithoutJwt() throws Exception {
        RegisterRequest request = new RegisterRequest("Jane Doe", "jane.doe@example.com", "SecurePass123!");
        UserResponse response = new UserResponse("1", "Jane Doe", "jane.doe@example.com", Instant.now(), Instant.now());

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer invalid.expired.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void testLogin_WithInvalidToken_ReachesServiceWithoutJwt() throws Exception {
        LoginRequest request = new LoginRequest("jane.doe@example.com", "SecurePass123!");
        UserResponse userResponse = new UserResponse("1", "Jane Doe", "jane.doe@example.com", Instant.now(), Instant.now());
        AuthResponse authResponse = new AuthResponse("mocked.jwt.token", "Bearer", 86400, userResponse);

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer invalid.expired.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mocked.jwt.token"));
    }
}
