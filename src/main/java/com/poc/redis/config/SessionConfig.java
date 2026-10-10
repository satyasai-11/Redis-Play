//package com.poc.redis.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.serializer.support.SerializationDelegate;
//import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
//import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
//
//@Configuration
//@EnableRedisHttpSession
//public class SessionConfig {
//
//    @Bean
//    public SerializationDelegate springSessionDefaultRedisSerializer(
//            GenericJackson2JsonRedisSerializer serializer) {
//
//        return new SerializationDelegate(serializer);
//    }
//}