package com.url.shortify.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.url.shortify.utils.SnowflakeGenerator;

@Configuration
public class SnowflakeConfig {

    @Value("${MACHINE_ID}")
    private long machineId;

    @Bean
    public SnowflakeGenerator snowflakeGenerator() {
        return new SnowflakeGenerator(machineId);
    }

}
