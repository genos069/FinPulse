package in.expensetrackerapp.auth.service;

import in.expensetrackerapp.auth.dto.AuthResponse;
import in.expensetrackerapp.auth.dto.LoginRequest;
import in.expensetrackerapp.auth.dto.RegisterRequest;
import in.expensetrackerapp.user.dto.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
