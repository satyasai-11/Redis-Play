package com.poc.redis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

@Service
public class ApiCallerService {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    public JsonNode callApi(
            String apiName,
            JsonNode requestBody) {

        System.out.println(
                "ACTUAL API CALL EXECUTED");

        ObjectNode response =
                objectMapper.createObjectNode();

        if ("getPolicyCard".equals(apiName)) {

            response.put("policyName",
                    "Life Insurance");

            response.put("premium",
                    25000);

            response.put("status",
                    "ACTIVE");

            return response;
        }

        if ("getPolicyDetails".equals(apiName)) {

            response.put(
                    "policyNo",
                    requestBody.get("policyNo")
                            .asText()
            );

            response.put(
                    "customerName",
                    "Satya Sai"
            );

            response.put(
                    "sumAssured",
                    1000000
            );

            return response;
        }

        response.put("message",
                "Default Response");

        return response;
    }
}