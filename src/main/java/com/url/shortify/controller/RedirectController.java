package com.url.shortify.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.url.shortify.services.RateLimiterService;
import com.url.shortify.services.UrlMappingService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;

@RestController
@AllArgsConstructor
public class RedirectController {
    
    private UrlMappingService urlMappingService;
    private RateLimiterService rateLimiterService;

    @GetMapping("/{shortCode}")
    public ResponseEntity<?> redirect(@PathVariable String shortCode, HttpServletRequest httpRequest){
        String userIpAdd = httpRequest.getRemoteAddr();
        String key = "ip:" + userIpAdd + ":" + shortCode;
        if (!rateLimiterService.isAllowed(key)) {
            return ResponseEntity.status(429).body("Too many requests");
        }
    
        String originalUrl = urlMappingService.getOriginalUrl(shortCode);
        if(originalUrl != null){
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add("Location", originalUrl);
            return ResponseEntity.status(302).headers(httpHeaders).build();
        }else{
            return ResponseEntity.notFound().build();
        }

    }
}
