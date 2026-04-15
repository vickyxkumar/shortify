package com.url.shortify.services;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.url.shortify.models.ClickEvent;
import com.url.shortify.repository.ClickEventRepository;

@Service
public class AnalyticsWorker {

    @Autowired
    private ClickEventRepository clickEventRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Scheduled(fixedRate = 60000)
    public void processClickEvent() {
        List<ClickEvent> batch = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            String event = redisTemplate.opsForList().leftPop("click_events:");
            if (event == null)
                break;

            String[] parts = event.split("\\|");
            ClickEvent ce = new ClickEvent();
            ce.setShortUrl(parts[0]);
            ce.setClickDate(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(Long.parseLong(parts[1])),
                    ZoneId.systemDefault()));

            batch.add(ce);
        }

        if(!batch.isEmpty()){
            clickEventRepository.saveAll(batch);
        }

    }
}
