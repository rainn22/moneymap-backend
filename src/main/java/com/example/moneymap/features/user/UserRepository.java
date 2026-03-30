package com.example.moneymap.features.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    default Optional<User> findByUsernameOrEmail(String login) {
        Optional<User> byUsername = findByUsername(login);
        return byUsername.isPresent() ? byUsername : findByEmail(login);
    }

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}