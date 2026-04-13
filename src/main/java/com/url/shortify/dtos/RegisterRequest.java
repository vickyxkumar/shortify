package com.url.shortify.dtos;

import java.util.Set;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private Set<String> role;
    private String password;
    private String email;
}
