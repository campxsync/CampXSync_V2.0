# Disaster Recovery Runbook & Business Continuity Plan
## Service: CampXSync Curriculum Management Service (ACD-02) (`services/curriculum-service`)

### 1. Business Continuity Targets

| Metric | Target | Description |
| :--- | :--- | :--- |
| **Recovery Point Objective (RPO)** | **< 5 minutes** | Maximum acceptable data loss window during catastrophic storage failure. Supported by MongoDB continuous oplog streaming. |
| **Recovery Time Objective (RTO)** | **< 15 minutes** | Maximum allowable duration to restore full write and read availability after a multi-node cluster outage. |
| **High Availability Target** | **99.95%** | Redundant multi-AZ deployment with automated leader election and outbox relay recovery. |

---

### 2. Failure Scenarios & Recovery Procedures

#### Scenario 1: Primary MongoDB Node Failure (Replica Set Election)
- **Impact**: Transient write unavailability for 2-8 seconds during Raft/election cycle. Read traffic redirected to secondary nodes.
- **Automated Behavior**:
  1. MongoDB replica set members detect heartbeat loss from Primary after 10 seconds.
  2. Majority quorum elects the most up-to-date Secondary as the new Primary.
  3. CampXSync Curriculum Service connection pool automatically retries pending writes with exponential backoff (`RetryPolicy`).
- **Manual Verification**:
  ```bash
  mongosh "mongodb://mongo1:27017,mongo2:27017,mongo3:27017/campx_curriculum?replicaSet=rs0" --eval "rs.status()"
  ```
  Verify that one node transitioned to `PRIMARY` and healthy heartbeat exists across all nodes.

#### Scenario 2: Catastrophic Database Corruption / Region Outage (PITR Restore)
- **Target RTO**: < 15 minutes.
- **Target RPO**: < 5 minutes.
- **Recovery Procedure**:
  1. **Provision Target Cluster**: Provision new MongoDB cluster in the secondary failover cloud region or Kubernetes namespace.
  2. **Restore Snapshot Backup**: Restore the latest hourly EBS/volume snapshot:
     ```bash
     mongorestore --host mongo-failover.internal:27017 --drop /backup/snapshots/latest/
     ```
  3. **Replay Oplog to Target Point-in-Time**:
     Replay oplog entries up to the corruption timestamp $T$:
     ```bash
     mongorestore --host mongo-failover.internal:27017 --oplogReplay --oplogLimit "$T:1" /backup/oplog/
     ```
  4. **Update Connection Secrets & Restart Service**:
     Update Kubernetes config map `MONGODB_URI` and perform rolling restart of curriculum-service pods:
     ```bash
     kubectl rollout restart deployment curriculum-service -n campx-academic
     ```

#### Scenario 3: Event Broker Outage (Kafka / RabbitMQ) & Outbox Redrive
- **Impact**: REST API calls continue successfully; outbox events accumulate in `outbox_events` with status `PENDING`. No data is lost.
- **Automated Recovery**:
  1. When broker connectivity is restored, `OutboxRelayService` automatically resumes scanning `getPendingOutboxEvents()` every 2 seconds.
  2. Events are published sequentially and transitioned to `PUBLISHED`.
  3. Exponential backoff jitter protects the message broker from thundering herd issues.
- **Manual Backlog Redrive Check**:
  ```bash
  # Check pending backlog size
  curl -s http://localhost:8084/metrics | grep acd02_outbox_pending_count
  
  # Inspect pending events if backlog remains elevated
  curl -s http://localhost:8084/api/v1/curricula/events/outbox -H "X-User-Role: ACADEMIC_ADMIN"
  ```

#### Scenario 4: Split-Brain or Network Partition Resolution
- **Mitigation Architecture**:
  1. MongoDB replica set uses an odd member count (3 or 5 nodes) across separate availability zones.
  2. Write concern is set to `w: "majority", j: true` for all curriculum modifications.
  3. An isolated minority partition automatically steps down from Primary to prevent stale divergent writes.
- **Post-Recovery Data Reconciliation**:
  1. Inspect `dead_letter_events` for any messages rejected during reconnect.
  2. Reconcile audit history monotonic sequences (`sequenceNo`) in `audit_history` collection to verify no skipped sequences.

---

### 3. Step-by-Step Incident Checklist for On-Call Engineers

- [ ] **Step 1: Declare Incident & Severity**: Create Sev-1 incident room in Slack (`#incident-acd02`).
- [ ] **Step 2: Check Service Health**: `curl -f http://localhost:8084/actuator/health`.
- [ ] **Step 3: Check Metrics**: Inspect `GET /metrics` for error spikes and outbox backlog.
- [ ] **Step 4: Check Logs**: Review structured logs for `ACD2_DATABASE_UNAVAILABLE` or timeout stack traces.
- [ ] **Step 5: Verify Replica Set**: Confirm MongoDB primary election and cluster quorum status.
- [ ] **Step 6: Confirm Outbox Relay**: Ensure `OutboxRelayService` is draining pending events post-recovery.
- [ ] **Step 7: Verify Idempotency Cache**: Confirm duplicate client retries are serviced cleanly without duplicate records.
- [ ] **Step 8: Close Incident & Post-Mortem**: Document root cause, timeline, RPO/RTO metrics, and corrective actions.
