package in.expensetrackerapp.user.service;

import in.expensetrackerapp.common.exception.ResourceNotFoundException;
import in.expensetrackerapp.user.dto.UserResponse;
import in.expensetrackerapp.user.model.User;
import in.expensetrackerapp.user.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserResponse getUserProfile(String email) {
        User user = findByEmail(email);
        return UserResponse.fromUser(user);
    }

    @Override
    public UserResponse getUserProfileById(String userId) {
        User user = findById(userId);
        return UserResponse.fromUser(user);
    }

    @Override
    public User findByEmail(String email) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Override
    public User findById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }
}
