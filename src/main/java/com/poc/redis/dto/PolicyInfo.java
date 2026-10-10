package com.poc.redis.dto;

import java.io.Serializable;

public class PolicyInfo implements Serializable {

    private String policyNo;
    private String policyType;

    public String getPolicyNo() {
        return policyNo;
    }

    public void setPolicyNo(String policyNo) {
        this.policyNo = policyNo;
    }

    public String getPolicyType() {
        return policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }
}