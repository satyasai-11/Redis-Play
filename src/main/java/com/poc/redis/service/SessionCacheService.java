package com.poc.redis.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.redis.service.ApiCallerService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class  SessionCacheService{

    private final ApiCallerService apiCallerService;
    private final ObjectMapper objectMapper;

    public SessionCacheService(
            ApiCallerService apiCallerService, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        this.apiCallerService = apiCallerService;
    }

    public JsonNode getResponse(
            HttpSession session,
            String apiName,
            String identifier,
            JsonNode requestBody,
            boolean useCache) {

        String cacheKey =
                generateKey(apiName, identifier);

        // Skip cache
        if (!useCache) {

            return apiCallerService.callApi(
                    apiName,
                    requestBody
            );
        }

        // Check cache
        Object cached =
                session.getAttribute(cacheKey);

        if (cached != null) {

            System.out.println("CACHE HIT");

            return objectMapper.valueToTree(cached);
        }

        System.out.println("CACHE MISS");

        JsonNode response =
                apiCallerService.callApi(
                        apiName,
                        requestBody
                );

        // Store in session
        session.setAttribute(cacheKey, response);

        return response;
    }

    private String generateKey(
            String apiName,
            String identifier) {

        // Example:
        // api:getPolicyCard
        if (identifier == null ||
                identifier.isBlank()) {

            return "api:" + apiName;
        }

        // Example:
        // api:getPolicyDetails:POL123
        return "api:" + apiName + ":" + identifier;
    }
}