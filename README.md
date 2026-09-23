# CampXSync Platform V2.0

**CampXSync** is a high-performance College Enterprise Resource Planning (ERP) platform built on a clean multi-module microservice architecture targeting Java 1.8+.

## Modules Overview

- **[`api-gateway`](api-gateway/)**: Unified edge API Gateway with reverse-proxy routing, correlation header injection (`X-Trace-Id`), dynamic route resolution, and network resilience.
- **[`services/institute-admin-service`](services/institute-admin-service/)**: ADM-01 Institute Admin Service for platform-tier multi-tenant onboarding, college registry, provisioning state machine, and global configuration.
- **[`services/college-admin-service`](services/college-admin-service/)**: ADM-02 College Admin Service for college-level operational profiles, department/program lifecycle masters, bulk data ingestion, and document governance.
- **[`logger`](logger/)**: `campx-logger` enterprise logging framework with asynchronous background dispatching, microsecond step flow latency profiling (`FlowTracker`), distributed trace context propagation, automatic sensitive data masking, and compliance audit logging.

## Comprehensive Technical Documentation

For complete technical specifications, architectural diagrams, API contracts, error code mappings, and verification procedures, please refer to:
- **[Architecture & Technical Walkthrough](docs/architecture/WALKTHROUGH.md)**

## Quick Start & Build

### Prerequisites
- JDK 1.8 or higher
- Apache Maven 3.6+

### Build & Run Tests
To compile the entire multi-module reactor and run all unit and integration tests:

```bash
mvn clean test
```

### Package JARs
```bash
mvn clean package -DskipTests
```

## Branch & Repository Information
- **Branch**: `Dev1.0Branch`
- **Remote**: `https://github.com/campxsync/CampXSync_V2.0.git`
