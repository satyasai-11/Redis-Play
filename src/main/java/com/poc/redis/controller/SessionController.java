package com.poc.redis.controller;

import com.poc.redis.dto.ClientInfo;
import com.poc.redis.dto.LoginUser;
import com.poc.redis.dto.PolicyInfo;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {

    @GetMapping("/login")
    public String login(HttpSession session) {

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(Long.valueOf(101L));
        loginUser.setUsername("satya");
        loginUser.setRole("ADMIN");

        ClientInfo clientInfo = new ClientInfo();
        clientInfo.setClientId("WEB001");
        clientInfo.setChannel("PORTAL");
        clientInfo.setDevice("CHROME");

        PolicyInfo policyInfo = new PolicyInfo();
        policyInfo.setPolicyNo("POL123");
        policyInfo.setPolicyType("LIFE");

        session.setAttribute("loginUser", loginUser);
        session.setAttribute("clientInfo", clientInfo);
        session.setAttribute("policyInfo", policyInfo);

        return "Session Created";
    }
}