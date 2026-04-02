package com.example.moneymap.common.security;

import com.example.moneymap.features.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new RuntimeException("Authenticated user not found");
        }

        return user;
    }
}
