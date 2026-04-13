package com.url.shortify.services;

import java.util.Collection;
import java.util.Collections;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.url.shortify.models.User;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserDetailsImpl implements UserDetails{

    private static final long serialVersionUID=1L;
    
    private Collection<? extends GrantedAuthority> authorities;

    private String email;
    private Long id;
    private String username;
    private String password;

    public UserDetailsImpl(Collection<? extends GrantedAuthority> authorities, String email, Long id, String username, String password){
        this.authorities = authorities;
        this.email = email;
        this.id = id;
        this.username = username;
        this.password = password;
    }

    public static UserDetailsImpl build(User user){
        GrantedAuthority authority = new SimpleGrantedAuthority(user.getRole());
        return new UserDetailsImpl(Collections.singletonList(authority), user.getEmail(), user.getId(), user.getUsername(), user.getPassword());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }
    
}
