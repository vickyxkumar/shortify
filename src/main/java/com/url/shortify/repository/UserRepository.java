package com.url.shortify.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.url.shortify.models.User;

public interface UserRepository extends JpaRepository<User, Long>{
    Optional<User> findByUsername(String username);
}
