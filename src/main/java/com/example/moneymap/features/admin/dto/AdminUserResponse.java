package com.example.moneymap.features.admin.dto;

import com.example.moneymap.features.user.entity.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUserResponse {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private boolean enabled;
}
