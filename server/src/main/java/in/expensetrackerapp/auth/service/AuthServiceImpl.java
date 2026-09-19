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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        // Application-level check
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException("An account with email " + normalizedEmail + " already exists");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User(request.getName().trim(), normalizedEmail, hashedPassword);

        // Concurrency-safe persistence check against database unique index
        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DuplicateKeyException e) {
            log.warn("Concurrent registration conflict detected for duplicate email: {}", normalizedEmail);
            throw new DuplicateEmailException("An account with email " + normalizedEmail + " already exists");
        }

        log.info("User registered successfully with email: {}", normalizedEmail);
        return UserResponse.fromUser(savedUser);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);

        log.info("User logged in successfully with email: {}", normalizedEmail);
        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationInSeconds(),
                UserResponse.fromUser(user)
        );
    }
}
