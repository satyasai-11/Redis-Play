package com.poc.redis.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poc.redis.cache.SessionCacheService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/policy")
public class PolicyController {

    private final SessionCacheService
            sessionCacheService;

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    public PolicyController(
            SessionCacheService sessionCacheService) {

        this.sessionCacheService =
                sessionCacheService;
    }

    // Same for user
    @GetMapping("/card")
    public JsonNode getPolicyCard(
            HttpSession session) {

        ObjectNode request =
                objectMapper.createObjectNode();

        return sessionCacheService.getResponse(
                session,
                "getPolicyCard",
                "",
                request,
                true
        );
    }

    // Same API but different policy number
    @GetMapping("/details/{policyNo}")
    public JsonNode getPolicyDetails(
            @PathVariable String policyNo,
            HttpSession session) {

        ObjectNode request =
                objectMapper.createObjectNode();

        request.put("policyNo", policyNo);

        return sessionCacheService.getResponse(
                session,
                "getPolicyDetails",
                policyNo,
                request,
                true
        );
    }
}