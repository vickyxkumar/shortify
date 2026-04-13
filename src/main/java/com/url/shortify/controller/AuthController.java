package com.url.shortify.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.url.shortify.dtos.LoginRequest;
import com.url.shortify.dtos.RegisterRequest;
import com.url.shortify.models.User;
import com.url.shortify.services.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/public/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest loginRequest){
        return ResponseEntity.ok(userService.authenticateUser(loginRequest));
    }
    
    @PostMapping("/public/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerBody){
        User user = new User();
        user.setEmail(registerBody.getEmail());
        user.setUsername(registerBody.getUsername());
        user.setPassword(registerBody.getPassword());
        user.setRole("USER");
        userService.registerUser(user);
        return ResponseEntity.ok("User register successfully");
    }

}
