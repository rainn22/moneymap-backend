package com.example.moneymap.features.user.repository;

import java.util.Optional;

import com.example.moneymap.features.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
