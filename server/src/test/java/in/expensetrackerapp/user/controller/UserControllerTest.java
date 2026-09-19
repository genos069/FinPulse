package in.expensetrackerapp.user.controller;

import in.expensetrackerapp.auth.security.JwtAuthenticationEntryPoint;
import in.expensetrackerapp.auth.security.JwtAuthenticationFilter;
import in.expensetrackerapp.auth.security.JwtService;
import in.expensetrackerapp.auth.security.SecurityConfig;
import in.expensetrackerapp.common.exception.GlobalExceptionHandler;
import in.expensetrackerapp.config.CorsConfig;
import in.expensetrackerapp.user.dto.UserResponse;
import in.expensetrackerapp.user.service.UserService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, CorsConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void testGetCurrentUser_Authenticated() throws Exception {
        String token = "valid.jwt.token";
        String userId = "user-123";
        UserResponse response = new UserResponse(userId, "Test User", "test@example.com", Instant.now(), Instant.now());

        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(jwtService.isTokenValid(token, userId)).thenReturn(true);
        when(userService.getUserProfileById(userId)).thenReturn(response);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void testGetCurrentUser_Unauthenticated() throws Exception {
        // Without Authorization header
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Unauthorized: Authentication token is missing, invalid, or expired"));
    }

    @Test
    void testGetCurrentUser_InvalidToken() throws Exception {
        String invalidToken = "invalid.jwt.token";

        when(jwtService.extractUserId(invalidToken)).thenThrow(new JwtException("Invalid token signature"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Unauthorized: Authentication token is missing, invalid, or expired"));
    }

    @Test
    void testGetUsersMe_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Unauthorized: Authentication token is missing, invalid, or expired"));
    }

    @Test
    void testGetUsersMe_InvalidToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Unauthorized: Authentication token is missing, invalid, or expired"));
    }
}
