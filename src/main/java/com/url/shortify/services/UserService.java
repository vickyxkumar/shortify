package com.url.shortify.services;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.url.shortify.dtos.LoginRequest;
import com.url.shortify.models.User;
import com.url.shortify.repository.UserRepository;
import com.url.shortify.security.jwt.JwtAuthenticationResponse;
import com.url.shortify.security.jwt.JwtUtils;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor

public class UserService {
    
    private PasswordEncoder passwordEncoded;
    private UserRepository userRepository;
    private AuthenticationManager authenticationManager;
    private JwtUtils jwtUtils;

    public User registerUser(User registerUserDetails){
        registerUserDetails.setPassword(passwordEncoded.encode(registerUserDetails.getPassword()));
        return userRepository.save(registerUserDetails);
    }

    public JwtAuthenticationResponse authenticateUser(LoginRequest loginRequest){
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        String jwt =jwtUtils.generateToken(userDetails);
        return new JwtAuthenticationResponse(jwt);
    }
}
