package com.url.shortify.dtos;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class UrlMappingDTO {
    private Long id;
    private String originalUrl;
    private String shortUrl;
    private LocalDateTime createdAt;
    private String userName;
}
