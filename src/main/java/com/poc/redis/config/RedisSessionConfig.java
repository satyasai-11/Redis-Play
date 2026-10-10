package com.poc.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

@Configuration
public class RedisSessionConfig {

    @Bean(name = "springSessionDefaultRedisSerializer")
    public GenericJackson2JsonRedisSerializer
    springSessionDefaultRedisSerializer(
            ObjectMapper objectMapper) {

        return new GenericJackson2JsonRedisSerializer(
                objectMapper
        );
    }
}