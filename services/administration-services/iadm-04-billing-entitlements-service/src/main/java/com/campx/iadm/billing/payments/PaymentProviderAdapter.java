package com.campx.iadm.billing.payments;

import java.util.Map;

/**
 * Payment-provider adapter (US-026). DEV ships a labelled simulator only —
 * no real provider credentials or network calls.
 */
public interface PaymentProviderAdapter {

    String providerName();

    /**
     * Called only AFTER intent + durable audit are committed.
     */
    InitiateResult initiate(InitiateRequest request);

    /**
     * Build a signed callback envelope the platform can accept (DEV simulator).
     */
    SignedCallbackEnvelope buildSignedCallback(CallbackDraft draft);

    /**
     * Verify provider signature over the canonical payload.
     */
    boolean verifySignature(String timestampEpochSeconds, String body, String signatureHex);

    final class InitiateRequest {
        public final String intentId;
        public final String invoiceId;
        public final long amountMinorUnits;
        public final String currencyCode;
        /** DEV only: PENDING | SUCCESS | FAILURE | DUPLICATE | INVALID_SIGNATURE */
        public final String simulatorOutcome;

        public InitiateRequest(String intentId, String invoiceId, long amountMinorUnits,
                               String currencyCode, String simulatorOutcome) {
            this.intentId = intentId;
            this.invoiceId = invoiceId;
            this.amountMinorUnits = amountMinorUnits;
            this.currencyCode = currencyCode;
            this.simulatorOutcome = simulatorOutcome;
        }
    }

    final class InitiateResult {
        public final String providerRef;
        public final String outcome;
        public final String detail;
        public final Map<String, Object> callbackHint;

        public InitiateResult(String providerRef, String outcome, String detail,
                              Map<String, Object> callbackHint) {
            this.providerRef = providerRef;
            this.outcome = outcome;
            this.detail = detail;
            this.callbackHint = callbackHint;
        }
    }

    final class CallbackDraft {
        public final String eventId;
        public final String intentId;
        public final String providerRef;
        public final String settlementStatus;
        public final long amountMinorUnits;
        public final String currencyCode;

        public CallbackDraft(String eventId, String intentId, String providerRef,
                             String settlementStatus, long amountMinorUnits, String currencyCode) {
            this.eventId = eventId;
            this.intentId = intentId;
            this.providerRef = providerRef;
            this.settlementStatus = settlementStatus;
            this.amountMinorUnits = amountMinorUnits;
            this.currencyCode = currencyCode;
        }
    }

    final class SignedCallbackEnvelope {
        public final String timestampEpochSeconds;
        public final String signatureHex;
        public final String bodyJson;
        public final Map<String, Object> bodyMap;

        public SignedCallbackEnvelope(String timestampEpochSeconds, String signatureHex,
                                      String bodyJson, Map<String, Object> bodyMap) {
            this.timestampEpochSeconds = timestampEpochSeconds;
            this.signatureHex = signatureHex;
            this.bodyJson = bodyJson;
            this.bodyMap = bodyMap;
        }
    }
}
