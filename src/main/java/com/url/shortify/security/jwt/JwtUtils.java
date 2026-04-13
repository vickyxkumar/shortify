package com.url.shortify.security.jwt;

import java.security.Key;
import java.util.Date;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;

import com.url.shortify.services.UserDetailsImpl;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;

public class JwtUtils {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private int jwtExpirationMs;

    public String getTokenFromHeader(HttpServletRequest request){
        String bearerToken = request.getHeader("Authoriation");
        if(bearerToken != null && bearerToken.startsWith("Bearer ")){
            return bearerToken.substring(7);
        }
        return null;
    }

    public String generateToken(UserDetailsImpl userDetails){
        String userName = userDetails.getUsername();
        String role = userDetails.getAuthorities().stream().map(authority -> authority.getAuthority()).collect(Collectors.joining(","));

        return Jwts.builder().subject(userName).claim("role", role).issuedAt(new Date()).expiration(new Date(new Date().getTime() + jwtExpirationMs)).signWith(key()).compact();
    }

    private Key key(){
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public String getUserNameFromToken(String token){
        return Jwts.parser().verifyWith((SecretKey) key()).build().parseSignedClaims(token).getPayload().getSubject();
    }
    
    public boolean validateToken(String authToken){
        try {
            Jwts.parser().verifyWith((SecretKey) key()).build().parseSignedClaims(authToken);
            return true;
            
        }catch(JwtException e){
            throw new RuntimeException(e);
        }
         catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }
}
