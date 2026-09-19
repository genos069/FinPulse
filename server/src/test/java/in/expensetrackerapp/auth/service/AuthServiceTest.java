package in.expensetrackerapp.auth.service;

import in.expensetrackerapp.auth.dto.AuthResponse;
import in.expensetrackerapp.auth.dto.LoginRequest;
import in.expensetrackerapp.auth.dto.RegisterRequest;
import in.expensetrackerapp.auth.security.JwtService;
import in.expensetrackerapp.common.exception.DuplicateEmailException;
import in.expensetrackerapp.common.exception.InvalidCredentialsException;
import in.expensetrackerapp.user.dto.UserResponse;
import in.expensetrackerapp.user.model.User;
import in.expensetrackerapp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void testRegister_Success() {
        RegisterRequest request = new RegisterRequest("Alex", "alex@example.com", "Password123");
        User savedUser = new User("id-1", "Alex", "alex@example.com", "hashed_pwd", Instant.now(), Instant.now());

        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("hashed_pwd");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("alex@example.com", response.getEmail());
        assertEquals("Alex", response.getName());
    }

    @Test
    void testRegister_DuplicateEmail_ApplicationCheck() {
        RegisterRequest request = new RegisterRequest("Alex", "alex@example.com", "Password123");

        when(userRepository.existsByEmail("alex@example.com")).thenReturn(true);

        DuplicateEmailException ex = assertThrows(DuplicateEmailException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("alex@example.com already exists"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testRegister_DuplicateEmail_ConcurrentDatabaseException() {
        RegisterRequest request = new RegisterRequest("Alex", "alex@example.com", "Password123");

        // Application check passes
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("hashed_pwd");
        // Concurrent race condition: another thread inserted first, so MongoDB throws DuplicateKeyException
        when(userRepository.save(any(User.class))).thenThrow(new DuplicateKeyException("E11000 duplicate key error"));

        DuplicateEmailException ex = assertThrows(DuplicateEmailException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("alex@example.com already exists"));
    }

    @Test
    void testLogin_Success() {
        LoginRequest request = new LoginRequest("alex@example.com", "Password123");
        User user = new User("id-1", "Alex", "alex@example.com", "hashed_pwd", Instant.now(), Instant.now());

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "hashed_pwd")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("mock.jwt.token");
        when(jwtService.getExpirationInSeconds()).thenReturn(86400L);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock.jwt.token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("alex@example.com", response.getUser().getEmail());
    }

    @Test
    void testLogin_UserNotFound() {
        LoginRequest request = new LoginRequest("unknown@example.com", "Password123");

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void testLogin_WrongPassword() {
        LoginRequest request = new LoginRequest("alex@example.com", "WrongPassword");
        User user = new User("id-1", "Alex", "alex@example.com", "hashed_pwd", Instant.now(), Instant.now());

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed_pwd")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
    }
}
