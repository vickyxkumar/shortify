package com.url.shortify.dtos;

import java.time.LocalDate;

import lombok.Data;

@Data
public class ClickEventDTO {
    private Long count;
    private LocalDate clickDate;
}
