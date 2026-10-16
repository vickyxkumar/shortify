package com.url.shortify.models;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
// redirect cache misses look up by short_url; without this every miss is a full table scan
@Table(indexes = @Index(name = "idx_url_mapping_short_url", columnList = "short_url", unique = true))
public class UrlMapping {

    @Id
    private Long id;
    private String originalUrl;
    private String shortUrl;
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

}
