package com.url.shortify.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.url.shortify.models.User;
import com.url.shortify.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService{

    @Autowired
    UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found with the username:" + username));
        return UserDetailsImpl.build(user);
    }
    
}
