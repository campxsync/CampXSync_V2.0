package com.campx.iadm.billing;

import com.campx.iadm.billing.payments.DevPaymentSimulator;
import com.campx.iadm.billing.payments.PaymentProviderAdapter;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * US-026 — DEV simulator outcomes and signature verification (no network).
 */
public class DevPaymentSimulatorTest {

    @Test
    public void providerIsLabelledDevSimulator() {
        assertEquals("DEV_SIMULATOR", new DevPaymentSimulator().providerName());
    }

    @Test
    public void initiatePendingProducesSignedCallbackHint() {
        DevPaymentSimulator sim = new DevPaymentSimulator("unit-test-secret");
        PaymentProviderAdapter.InitiateResult r = sim.initiate(
                new PaymentProviderAdapter.InitiateRequest(
                        "11111111-1111-1111-1111-111111111111",
                        "22222222-2222-2222-2222-222222222222",
                        50000L, "INR", "PENDING"));
        assertEquals("PENDING", r.outcome);
        assertNotNull(r.providerRef);
        assertNotNull(r.callbackHint);
        assertTrue(String.valueOf(r.callbackHint.get("labelled")).contains("DEV simulator"));
        String ts = String.valueOf(r.callbackHint.get("timestamp"));
        String sig = String.valueOf(r.callbackHint.get("signature"));
        String bodyJson = String.valueOf(r.callbackHint.get("bodyJson"));
        assertTrue(sim.verifySignature(ts, bodyJson, sig));
    }

    @Test
    public void initiateFailureDoesNotSettle() {
        DevPaymentSimulator sim = new DevPaymentSimulator("unit-test-secret");
        PaymentProviderAdapter.InitiateResult r = sim.initiate(
                new PaymentProviderAdapter.InitiateRequest(
                        "11111111-1111-1111-1111-111111111111",
                        "22222222-2222-2222-2222-222222222222",
                        100L, "INR", "FAILURE"));
        assertEquals("FAILURE", r.outcome);
    }

    @Test
    public void invalidSignatureRejected() {
        DevPaymentSimulator sim = new DevPaymentSimulator("unit-test-secret");
        PaymentProviderAdapter.SignedCallbackEnvelope env = sim.buildSignedCallback(
                new PaymentProviderAdapter.CallbackDraft(
                        "evt-1", "intent-1", "SIM-1", "SUCCESS", 100L, "INR"));
        assertTrue(sim.verifySignature(env.timestampEpochSeconds, env.bodyJson, env.signatureHex));
        assertFalse(sim.verifySignature(env.timestampEpochSeconds, env.bodyJson, "deadbeef"));
        assertFalse(sim.verifySignature(env.timestampEpochSeconds, env.bodyJson + " ", env.signatureHex));
    }

    @Test
    public void successHintIncludesCanonicalBodyJson() {
        DevPaymentSimulator sim = new DevPaymentSimulator("unit-test-secret");
        PaymentProviderAdapter.InitiateResult r = sim.initiate(
                new PaymentProviderAdapter.InitiateRequest(
                        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                        999L, "INR", "SUCCESS"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) r.callbackHint.get("body");
        assertEquals("SUCCESS", String.valueOf(body.get("settlementStatus")));
        assertEquals("999", String.valueOf(body.get("amountMinorUnits")));
    }
}
