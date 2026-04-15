package com.url.shortify.dtos;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ClickEventDTO {
    private String shortUrl;
    private LocalDateTime timestamp;
}
