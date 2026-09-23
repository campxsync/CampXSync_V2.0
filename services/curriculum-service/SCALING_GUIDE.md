# Scaling & Capacity Planning Guide
## Service: CampXSync Curriculum Management Service (ACD-02) (`services/curriculum-service`)

### 1. Architectural Scaling Model
The CampXSync Curriculum Management Service (ACD-02) is designed as a stateless microservice with asynchronous outbox relay workers. It supports horizontal auto-scaling behind the CampXSync API Gateway and an underlying clustered MongoDB replica set.

```
                          ┌─────────────────────────────┐
                          │   CampXSync API Gateway     │
                          │   (Route: /api/v1/curricula)│
                          └──────────────┬──────────────┘
                                         │ Round-Robin / Least Conn
                 ┌───────────────────────┼───────────────────────┐
                 ▼                       ▼                       ▼
         ┌───────────────┐       ┌───────────────┐       ┌───────────────┐
         │ Pod Replica 1 │       │ Pod Replica 2 │       │ Pod Replica N │
         │ (ACD-02 Core) │       │ (ACD-02 Core) │       │ (ACD-02 Core) │
         └───────┬───────┘       └───────┬───────┘       └───────┬───────┘
                 │                       │                       │
                 └───────────────────────┼───────────────────────┘
                                         ▼
                        ┌─────────────────────────────────┐
                        │   MongoDB Clustered Database    │
                        │   Primary (Writes) / Secondary  │
                        └─────────────────────────────────┘
```

---

### 2. Kubernetes Horizontal Pod Autoscaler (HPA)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: curriculum-service-hpa
  namespace: campx-academic
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: curriculum-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
        - type: Percent
          value: 100
          periodSeconds: 15
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 20
          periodSeconds: 60
```

---

### 3. Container Resource Sizing & JVM Tuning

| Parameter | Recommended Value | Description |
| :--- | :--- | :--- |
| **CPU Request** | `500m` (0.5 vCPU) | Minimum guaranteed CPU per pod |
| **CPU Limit** | `2000m` (2 vCPU) | Maximum burstable CPU per pod |
| **Memory Request** | `1024Mi` (1 GB) | Baseline reserved memory |
| **Memory Limit** | `2048Mi` (2 GB) | Hard OOM memory limit |
| **JVM Heap Options** | `-Xms1024m -Xmx1536m` | Prevents runtime heap resizing pauses |
| **Garbage Collector** | `-XX:+UseG1GC -XX:MaxGCPauseMillis=20` | Low-latency GC optimized for REST microservices |

---

### 4. Database Connection Pool & Read Scaling

1. **MongoDB Connection Pool Sizing**:
   - `minPoolSize`: 10 connections per container
   - `maxPoolSize`: 100 connections per container
   - `maxIdleTimeMS`: 60,000 (1 minute)
   - Total cluster connections across 10 replicas: $10 \times 100 = 1000$ connections (well within MongoDB 10,000 connection limit).

2. **Read-Preference Routing**:
   - Write endpoints (`POST`, `PUT`, `DELETE`): routed strictly to `Primary` (`readPreference=primary`).
   - High-volume read endpoints (`GET /api/v1/curricula/active`, `GET /metrics`): routed to `secondaryPreferred` to reduce load on the primary writer.

3. **Sharding Strategy for Multi-Tenant Scale**:
   - **Shard Key**: `{ tenantId: "hashed", courseId: 1 }`
   - **Rationale**: Distributes distinct tenants uniformly across shard nodes while preserving co-location for a tenant's course curricula.

---

### 5. Multi-Tenant Capacity Projections

| Scale Dimension | Current Baseline | 1-Year Projected | 3-Year Enterprise Scale |
| :--- | :--- | :--- | :--- |
| **Active Institutes / Tenants** | 10 tenants | 50 tenants | 250 tenants |
| **Curricula Aggregates** | 1,000 | 10,000 | 100,000 |
| **Curriculum Versions** | 2,500 | 30,000 | 300,000 |
| **Subject Mappings** | 25,000 | 300,000 | 3,000,000 |
| **Peak Read Throughput** | 100 rps | 500 rps | 2,500 rps |
| **Database Storage Footprint** | ~500 MB | ~5 GB | ~50 GB |
