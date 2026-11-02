package com.campx.admissions.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentWebhookRequest {

    private UUID applicationId;
    private String paymentReference;
    private BigDecimal amount;
    private String status; // PAID, FAILED

    public PaymentWebhookRequest() {}

    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

