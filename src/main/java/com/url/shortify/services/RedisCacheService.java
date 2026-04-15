package com.url.shortify.services;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisCacheService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String URL_PREFIX = "url:";
    private static final String URL_CLICK_COUNT_PREFIX = "count:";
    private static final String CLICK_EVENT_PREFIX = "click_events:";

    public String getOriginalUrl(String shortUrl) {
        return redisTemplate.opsForValue().get(URL_PREFIX + shortUrl);
    }

    public void saveUrl(String shortUrl, String longUrl) {
        redisTemplate.opsForValue().set(URL_PREFIX + shortUrl, longUrl, Duration.ofHours(24));
    }

    public void increamentCount(String shortUrl) {
        redisTemplate.opsForValue().increment(URL_CLICK_COUNT_PREFIX + shortUrl);
    }

    public void pushClickEvent(String shortUrl) {
        String event = shortUrl + "|" + System.currentTimeMillis();
        redisTemplate.opsForList().rightPush(CLICK_EVENT_PREFIX, event);
    }
}
