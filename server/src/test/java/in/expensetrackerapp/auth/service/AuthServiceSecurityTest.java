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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Uses the REAL BCrypt encoder (AuthServiceTest mocks it), so hashing itself is exercised. */
@ExtendWith(MockitoExtension.class)
class AuthServiceSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepository, encoder, jwtService);
    }

    @Test
    void passwordIsStoredAsASaltedBCryptHashNeverAsPlaintext() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(new RegisterRequest("Asha", "a@example.com", "Sup3r-Secret!"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        String hash = saved.getValue().getPasswordHash();
        assertThat(hash).startsWith("$2").hasSize(60).doesNotContain("Sup3r-Secret!");
        assertThat(encoder.matches("Sup3r-Secret!", hash)).isTrue();
        assertThat(encoder.matches("sup3r-secret!", hash)).isFalse();
    }

    @Test
    void samePasswordGivesDifferentHashesBecauseOfPerUserSalt() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register(new RegisterRequest("One", "one@example.com", "SamePassword1"));
        service.register(new RegisterRequest("Two", "two@example.com", "SamePassword1"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getPasswordHash()).isNotEqualTo(saved.getAllValues().get(1).getPasswordHash());
    }

    @Test
    void emailsAreNormalisedSoDifferentSpellingsAreTheSameAccount() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Asha", "  USER@Example.COM ", "Password123")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository).existsByEmail("user@example.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginNormalisesTheEmailBeforeLookingItUp() {
        User user = new User("Asha", "user@example.com", encoder.encode("Password123"));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("token");
        when(jwtService.getExpirationInSeconds()).thenReturn(3600L);

        AuthResponse response = service.login(new LoginRequest(" USER@example.com", "Password123"));

        assertThat(response.getAccessToken()).isEqualTo("token");
    }

    @Test
    void unknownEmailAndWrongPasswordAreIndistinguishableToTheCaller() {
        User user = new User("Asha", "user@example.com", encoder.encode("Password123"));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        Throwable unknown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.login(new LoginRequest("nobody@example.com", "Password123")));
        Throwable wrong = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.login(new LoginRequest("user@example.com", "WrongPassword9")));

        assertThat(unknown).isInstanceOf(InvalidCredentialsException.class);
        assertThat(wrong).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknown.getMessage()).isEqualTo(wrong.getMessage());
    }

    @Test
    void userResponseCannotCarryAPasswordOrHash() {
        User user = new User("Asha", "a@example.com", encoder.encode("Password123"));
        UserResponse response = UserResponse.fromUser(user);

        assertThat(Arrays.stream(UserResponse.class.getDeclaredFields()).map(Field::getName))
                .noneMatch(name -> name.toLowerCase().contains("pass") || name.toLowerCase().contains("hash"));
        assertThat(response.toString()).doesNotContain("$2");
    }
}
