package in.expensetrackerapp.user.service;

import in.expensetrackerapp.user.dto.UserResponse;
import in.expensetrackerapp.user.model.User;

public interface UserService {

    UserResponse getUserProfile(String email);

    UserResponse getUserProfileById(String userId);

    User findByEmail(String email);

    User findById(String id);
}
