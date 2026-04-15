package com.url.shortify.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.url.shortify.dtos.ClickEventDTO;

@Service
public class KafkaProducerService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "click-events";

    public void sendClickEvent(ClickEventDTO event) {
        kafkaTemplate.send(TOPIC, event);
    }

}
