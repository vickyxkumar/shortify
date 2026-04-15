package com.url.shortify.services;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

// this rate limiter is based on sliding window
@Service
public class RateLimiterService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final int LIMIT = 10;
    private static final long WINDOW_SIZE = 60_000;

    // key: rate_limit:{identifier}
    // value:
    // score = timestamp
    // member = unique request id
    // the above is the data structure for the redis based rate limiter

    public boolean isAllowed(String key) {

        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_SIZE;

        String redisKey = "rate_limit:" + key;

        ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();

        // 1. Remove old requests
        zSet.removeRangeByScore(redisKey, 0, windowStart);

        // 2. Get current count
        Long count = zSet.zCard(redisKey);

        if (count != null && count >= LIMIT) {
            return false;
        }

        // 3. Add current request
        zSet.add(redisKey, String.valueOf(now) + ThreadLocalRandom.current().nextInt(), now);

        // 4. Set TTL
        redisTemplate.expire(redisKey, Duration.ofSeconds(60));

        return true;
    }
}