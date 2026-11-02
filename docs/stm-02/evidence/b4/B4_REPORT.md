# STM-02 Phase B — B4 Handover Resilience & Intelligence Layer Report

**Status:** PARTIAL / BLOCKED ON LIVE BROKER (Same approval dependency as B3)  
**Governing Standard:** `.agent/rules/campxsync-wave-control.md` & Phase B instructions.

---

## 1. Handover Idempotency & Duplicate Prevention (Item a)
- **Design & Contract:** Handover downstream calls enforce uniqueness on `uq_admission_handovers_idempotency` `(tenant_id, idempotency_key)`.
- **Test Evidence:** `HandoverOrchestrationServiceTest.testIdempotentRepeatedHandoverDoesNotReCallDownstream` verified:
  - First invocation executes downstream student creation.
  - Second invocation with identical idempotency key detects existing handover record in `stu.admission_handovers`, returns existing `created_student_id` and `enrolment_status`, and increments downstream call counter 0 times (`downstreamCalls.get() == 1`).
- **Live Dev DB Execution Status:** BLOCKED pending B3 fixture provisioning approval (no test tenant or user exists).

---

## 2. Failure and Retry Lifecycle (Item b)
- **Mechanism in Code (`HandoverOrchestrationService.java` lines 86–122):**
  - If downstream `StudentServiceAdapter` throws `RuntimeException` (e.g., target URL unreachable / timeout):
    - Handover record status is marked `FAILED`.
    - `retry_count` is incremented.
    - `last_error` is recorded in `stu.admission_handovers`.
    - Application status remains at `HANDOVER_PENDING` (does not transition to `HANDED_OVER`).
  - Upon retry with healthy downstream service, transition completes to `STUDENT_CREATED` and `stu.applications` transitions to `HANDED_OVER`.
- **Live Dev DB Execution Status:** BLOCKED pending B3 fixture provisioning approval.

---

## 3. Intelligence Layer Events (Item c)

Per Phase B instructions: *"show each of the five event types (a) emitted by the service and (b) received/processed on the consumer side, with evidence for each. If only (a) can be shown, mark PARTIAL."*

### (a) Emitted by Admissions Service:
All 5 governed event streams are implemented in `AdmissionsIntelligenceService.java` and verified via `AdmissionsIntelligenceIntegrationTest`:
1. **Admissions Funnel:** `AdmissionsFunnelCalculated.v1` (Schema `1.0.0`)
2. **Application Demand:** `ApplicationDemandAnalyzed.v1` (Schema `1.0.0`)
3. **Operational Workload:** `AdmissionsOperationsAnalyzed.v1` (Schema `1.0.0`)
4. **Decision Outcomes:** `AdmissionsDecisionAnalyzed.v1` (Schema `1.0.0`)
5. **Enrolment Conversion:** `EnrolmentConversionCalculated.v1` (Schema `1.0.0`)

Each event is validated by `IntelligenceContractValidator` against multi-tenant isolation (payload contains zero cross-tenant fields and zero raw PII).

### (b) Received / Processed on Consumer Side:
- Live external CampXSync Intelligence Layer broker/consumer service is not a standing local endpoint.
- In accordance with rule 18 of Persistent Rules (*"If the real Intelligence Layer endpoint is unavailable, test the contract with a controlled adapter and mark live delivery as pending"*), live consumer delivery remains pending.

### Overall Item c Evaluation:
**PARTIAL** (Emitted events 5/5 verified; live consumer processing pending external broker).

