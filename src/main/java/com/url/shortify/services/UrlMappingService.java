package com.url.shortify.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.url.shortify.dtos.ClickEventDTO;
import com.url.shortify.dtos.UrlMappingDTO;
import com.url.shortify.models.ClickEvent;
import com.url.shortify.models.UrlMapping;
import com.url.shortify.models.User;
import com.url.shortify.repository.ClickEventRepository;
import com.url.shortify.repository.UrlMappingRepository;
import com.url.shortify.utils.EncodeBase62;
import com.url.shortify.utils.SnowflakeGenerator;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class UrlMappingService {

    private UrlMappingRepository urlMappingRepository;
    private ClickEventRepository clickEventRepository;
    private RedisCacheService redisCacheService;
    private SnowflakeGenerator snowflakeGenerator;
    private EncodeBase62 encodingUtil;
    private KafkaProducerService kafkaProducerService;

    public UrlMappingDTO createShortUrl(String originalUrl, User user) {

        long id = snowflakeGenerator.nextId();

        String shortCode = encodingUtil.encodeBase62(id);
        UrlMapping urlMapping = new UrlMapping();
        urlMapping.setId(id);
        urlMapping.setOriginalUrl(originalUrl);
        urlMapping.setUser(user);
        urlMapping.setCreatedAt(LocalDateTime.now());
        urlMapping.setShortUrl(shortCode);

        UrlMapping savedUrlMapping = urlMappingRepository.save(urlMapping);

        redisCacheService.saveUrl(shortCode, originalUrl);
        return convertToDto(savedUrlMapping);
    }

    private UrlMappingDTO convertToDto(UrlMapping urlMapping) {
        UrlMappingDTO urlMappingDTO = new UrlMappingDTO();
        urlMappingDTO.setCreatedAt(urlMapping.getCreatedAt());
        urlMappingDTO.setId(urlMapping.getId());
        urlMappingDTO.setOriginalUrl(urlMapping.getOriginalUrl());
        urlMappingDTO.setShortUrl(urlMapping.getShortUrl());
        urlMappingDTO.setUserName(urlMapping.getUser().getUsername());

        return urlMappingDTO;
    }

    public List<UrlMappingDTO> getUrlsByUser(User user) {
        return urlMappingRepository.findByUser(user).stream().map(this::convertToDto).toList();
    }

    // public List<ClickEventDTO> getUrlAnalytics(String shortUrl, LocalDateTime
    // startDate, LocalDateTime endDate) {
    // UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
    // if (urlMapping != null) {
    // return clickEventRepository.findByUrlMappingAndClickDateBetween(urlMapping,
    // startDate, endDate).stream()
    // .collect(Collectors.groupingBy(click -> click.getClickDate().toLocalDate(),
    // Collectors.counting()))
    // .entrySet().stream().map(entry -> {
    // ClickEventDTO clickEventDTO = new ClickEventDTO();
    // clickEventDTO.setClickDate(entry.getKey());
    // clickEventDTO.setCount(entry.getValue());
    // return clickEventDTO;
    // }).collect(Collectors.toList());
    // }

    // return null;

    // }

    // public Map<LocalDate, Long> getTotalClicksByUserAndDate(User user, LocalDate
    // start, LocalDate end) {

    // // get all the url mapping from that user
    // List<UrlMapping> urlMappings = urlMappingRepository.findByUser(user);

    // // get all the clicks on that date for all the urls
    // List<ClickEvent> clickEvents =
    // clickEventRepository.findByUrlMappingInAndClickDateBetween(urlMappings,
    // start.atStartOfDay(), end.plusDays(1).atStartOfDay());

    // return clickEvents.stream()
    // .collect(Collectors.groupingBy(click -> click.getClickDate().toLocalDate(),
    // Collectors.counting()));
    // }

    public String getOriginalUrl(String shortUrl) {

        String cachedOriginalUrl = redisCacheService.getOriginalUrl(shortUrl);
        if (cachedOriginalUrl == null) {
            // Fallback to datebase
            UrlMapping urlMapping = urlMappingRepository.findByShortUrl(shortUrl);
            if (urlMapping == null) {
                throw new RuntimeException("URL not found");
            }
            cachedOriginalUrl = urlMapping.getOriginalUrl();

            redisCacheService.saveUrl(shortUrl, cachedOriginalUrl);

        }

        ClickEventDTO event = new ClickEventDTO();
        event.setShortUrl(shortUrl);
        event.setTimestamp(LocalDateTime.now());

        kafkaProducerService.sendClickEvent(event);

        return cachedOriginalUrl;
    }
}
