package com.example.moneymap.features.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /**
     * Get current user info
     */
    @GetMapping("/me")
    public UserDto getCurrentUser(@AuthenticationPrincipal User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                "ROLE_USER",
                user.getFirstName(),
                user.getLastName()
        );
    }

    /**
     * Update optional profile fields: firstName and lastName only
     */
    @PatchMapping("/me/profile")
    public UserDto updateProfile(@AuthenticationPrincipal User user,
                                 @RequestBody Map<String, String> updates) {

        if (updates.containsKey("firstName")) {
            user.setFirstName(updates.get("firstName"));
        }
        if (updates.containsKey("lastName")) {
            user.setLastName(updates.get("lastName"));
        }

        User updatedUser = userRepository.save(user);

        return new UserDto(
                updatedUser.getId(),
                updatedUser.getUsername(),
                updatedUser.getEmail(),
                "ROLE_USER",
                updatedUser.getFirstName(),
                updatedUser.getLastName()
        );
    }
}