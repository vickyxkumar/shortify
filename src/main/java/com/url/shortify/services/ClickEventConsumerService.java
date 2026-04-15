package com.url.shortify.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.url.shortify.dtos.ClickEventDTO;
import com.url.shortify.models.ClickEvent;
import com.url.shortify.repository.ClickEventRepository;

@Service
public class ClickEventConsumerService {

    @Autowired
    private ClickEventRepository clickEventRepository;

    List<ClickEventDTO> buffer = new ArrayList<>();

    private static final int BATCH_SIZE = 2;

    @KafkaListener(topics = "click-events", groupId = "analytics-group", containerFactory = "kafkaListenerContainerFactory")
    public void consume(ClickEventDTO event, Acknowledgment ack) {

        buffer.add(event);
        if (buffer.size() >= BATCH_SIZE) {
            processBatch();
            buffer.clear();
        }

        ack.acknowledge();

    }

    private void processBatch() {
        List<ClickEvent> events = new ArrayList<>();

        for (ClickEventDTO dto : buffer) {

            ClickEvent click = new ClickEvent();
            click.setClickDate(dto.getTimestamp());
            click.setShortUrl(dto.getShortUrl());

            events.add(click);

        }

        // Bulk insert click events
        clickEventRepository.saveAll(events);
    }

}
