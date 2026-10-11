# Alerting Rules Specification & Runbook Reference
## Service: CampXSync Curriculum Management Service (ACD-02) (`services/curriculum-service`)

### 1. Overview
This document specifies the Prometheus alerting thresholds, severity classifications, and operational remediation runbooks for the CampXSync Curriculum Management Service (ACD-02). Metrics are exposed via standard Prometheus format at `GET /metrics` and mapped through the API Gateway at `GET /api/v1/curricula/metrics`.

---

### 2. Alert Definitions & Trigger Thresholds

| Alert Name | Metric Expression | Condition / Duration | Severity | Notification Channel |
| :--- | :--- | :--- | :--- | :--- |
| **ServiceDown** | `up{job="curriculum-service"} == 0` | 0 reachable instances for > 1m | `CRITICAL` | PagerDuty (SRE Team), Slack `#alerts-academic-sev1` |
| **HighErrorRate** | `sum(rate(acd02_error_total[5m])) / sum(rate(acd02_request_total[5m]))` | > 5% failure rate for > 5m | `CRITICAL` | PagerDuty, Slack `#alerts-academic` |
| **LatencyP99Breach** | `sum(rate(acd02_request_duration_seconds[5m])) / sum(rate(acd02_request_total[5m]))` | > 1.5s avg latency for > 5m | `WARNING` | Slack `#alerts-academic` |
| **OutboxBacklogHigh** | `acd02_outbox_pending_count` | > 1000 pending events for > 10m | `CRITICAL` | PagerDuty, Slack `#alerts-academic-sev1` |
| **DeadLetterSpike** | `increase(acd02_dlq_count[5m])` | > 10 failed events in 5m | `WARNING` | Slack `#alerts-academic` |
| **PrerequisiteCycleDetected** | `acd02_validation_failure_total{type="PREREQUISITE_CYCLE"}` | > 0 in 1m | `WARNING` | Slack `#alerts-academic` |

---

### 3. Triage & Remediation Runbooks

#### 3.1 ServiceDown
1. **Verification**: Check pod/process health via `GET /actuator/health`.
2. **Logs**: Inspect JVM crashes or OutOfMemory errors using structured logs:
   ```bash
   kubectl logs -n campx-academic -l app=curriculum-service --tail=200
   ```
3. **Remediation**: Check node resources, restart stalled pods, verify database connectivity to MongoDB replica set.

#### 3.2 HighErrorRate (> 5% 5xx/4xx)
1. **Verification**: Query `acd02_error_total` grouped by `errorCode`:
   ```promql
   sum by (errorCode) (rate(acd02_error_total[5m]))
   ```
2. **Identification**:
   - `ACD2_DATABASE_UNAVAILABLE`: Check MongoDB connectivity or connection pool saturation.
   - `ACD2_DUPLICATE_MAPPING`: Check upstream clients submitting duplicate concurrent requests without idempotency keys.
   - `ACD2_DOWNSTREAM_UNAVAILABLE`: Check ACD-01 (Course Management) or ACD-03 (Subject Management) health.
3. **Mitigation**: Scale horizontal replicas, restart unhealthy pods, or engage upstream service owners.

#### 3.3 OutboxBacklogHigh (> 1,000 Pending Events)
1. **Verification**: Query pending outbox backlog:
   ```bash
   curl -s http://localhost:8084/metrics | grep acd02_outbox_pending_count
   ```
2. **Investigation**:
   - Check status of event broker (Kafka / RabbitMQ).
   - Check `OutboxRelayService` thread activity in logs: `logger.info("OutboxRelay published...")`.
   - Verify network egress permissions to message broker cluster.
3. **Remediation**:
   - Once broker connectivity is restored, `OutboxRelayService` will automatically resume polling every 2 seconds with exponential backoff retry.
   - For stuck events, inspect the `GET /api/v1/curricula/events/outbox` endpoint.

#### 3.4 DeadLetterSpike (> 10 DLQ Events)
1. **Inspection**: Fetch failed events from Dead Letter Queue:
   ```bash
   curl -s http://localhost:8084/api/v1/curricula/events/dead-letter -H "X-User-Role: ACADEMIC_ADMIN"
   ```
2. **Diagnosis**: Examine `failureReason` field (e.g., broker schema validation error, deserialization mismatch).
3. **Reprocessing**: Correct schema/payload issue and re-publish via manual administrative re-drive script.

#### 3.5 PrerequisiteCycleDetected
1. **Inspection**: Check structured audit logs for `actorId` and `curriculumId` associated with the cyclic prerequisite submission.
2. **Investigation**: The DAG cycle detection algorithm (`CurriculumDomainService.detectCycle`) successfully prevented circular dependencies. Notify the department head or academic curriculum coordinator to correct their prerequisite configuration.

---

### 4. Escalation Matrix
- **Tier 1 (0-15 mins)**: On-Call Academic Platform SRE (`#alerts-academic`).
- **Tier 2 (15-30 mins)**: Curriculum Lead Engineer & Core Database DBA.
- **Tier 3 (30+ mins)**: Engineering Director & Chief Architect.
