# CampXSync — System Architecture Diagrams & End-to-End Workflows

**Document Identifier**: `docs/audit/CAMPXSYNC_ARCHITECTURE_FLOWS.md`  
**Audit Date**: October 19, 2026  
**Auditor**: Antigravity Autonomous Agent (Google DeepMind)  
**Governing Baseline**: Git HEAD (`ReviewBranch` commit `b307149`)  
**Cross-Reference Evidence Index**: [CAMPXSYNC_EVIDENCE_INDEX.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_EVIDENCE_INDEX.md)

---

## 1. Executive Summary & Diagram Conventions

This document provides editable text-based diagrams using **Mermaid** representing the actual, empirical architecture and end-to-end workflows of CampXSync.

To avoid presenting hypothetical designs as working code:
- **Solid lines & filled boxes** represent `CODE VERIFIED` and `STAGING VERIFIED` components.
- **Dotted lines (`-.->`) & hollow boxes** represent `PLANNED`, `DOCUMENTED ONLY`, or inactive historical prototypes.
- Security boundaries, tenant isolation checkpoints, and known RLS bypass paths are explicitly highlighted.

---

## 2. Diagram A: System Context Diagram

```mermaid
graph TD
    subgraph Users [Institutional Stakeholders]
        PlatUser[Platform Admin Operator]
        CollegeUser[College Administrator]
        FacultyUser[Teaching Faculty Member]
        StudentUser[Enrolled Student]
    end

    subgraph BrowserClient [Client Browser Layer]
        AppUI[Next.js 15 Enterprise Client UI - React 19]
        CookieStorage[HTTP-Only Secure Cookie Storage]
    end

    subgraph VercelEdge [Vercel Cloud Hosting Ingress]
        EdgeMW[Next.js Edge Middleware - Route Guards & Fast-Fail]
        NextApp[Next.js App Router - Server Components & Route Handlers]
        DirectPool[Serverless PostgreSQL Connection Pool - pg.Pool]
    end

    subgraph SupabasePlatform [Supabase Managed Cloud - AWS ap-southeast-1]
        GoTrueAuth[Supabase GoTrue Auth Service - /auth/v1]
        TokenHook[Custom Access Token Hook - iam.custom_access_token_hook]
        PostgREST[PostgREST Engine - /rest/v1 - Public Schema Only]
        PGSQL[(PostgreSQL 17.11 Database - 20 Schemas, 458 Tables)]
    end

    subgraph ExternalBoundary [External Integrations]
        VercelBypass[Vercel Automation Protection]
    end

    PlatUser -->|HTTPS| AppUI
    CollegeUser -->|HTTPS| AppUI
    FacultyUser -->|HTTPS| AppUI
    StudentUser -->|HTTPS| AppUI

    AppUI <-->|Session Cookies| CookieStorage
    AppUI -->|Page Requests & API Mutations| EdgeMW
    EdgeMW -->|Authorized Ingress| NextApp
    AppUI -->|Direct Password Login| GoTrueAuth
    GoTrueAuth -.->|Invokes| TokenHook
    NextApp -->|Verify JWT / Session| GoTrueAuth
    NextApp -->|Direct Parameterized SQL| DirectPool
    DirectPool -->|Direct TCP Port 5432 - Superuser Owner| PGSQL
    VercelBypass -.->|Bypass Header| EdgeMW

    classDef active fill:#22c55e,stroke:#15803d,color:#ffffff;
    classDef warning fill:#f59e0b,stroke:#b45309,color:#ffffff;
    classDef danger fill:#ef4444,stroke:#b91c1c,color:#ffffff;
    class PlatUser,CollegeUser,FacultyUser active;
    class StudentUser warning;
    class DirectPool danger;
```

**Explanation**: Shows the active cloud MVP. Browser users interact with the Next.js 15 UI on Vercel. Authenticated requests pass through Edge Middleware to Server Components and Route Handlers. Direct database queries use `pg.Pool` directly connecting to PostgreSQL on port 5432, bypassing PostgREST.

---

## 3. Diagram B: Deployment & Runtime Architecture

```mermaid
graph TB
    subgraph PublicInternet [Public Internet]
        ClientDevice[Web Browser / Automation Probes]
    end

    subgraph VercelRuntime [Vercel Deployment Baseline - sin1 Singapore Region]
        subgraph VercelEdgeLayer [Edge Network]
            EdgeFirewall[Vercel Deployment Protection & Firewall]
            EdgeFunction[Next.js Edge Middleware]
        end
        subgraph VercelServerless [Node.js 20 Serverless Runtime]
            PageRenderer[Server Component SSR Pages]
            APIHandlers[API Route Handlers - /api/*]
            DBDriver[pg Connection Pool - Max 10 Conn]
        end
    end

    subgraph SupabaseStaging [Supabase Staging Project: kpwdvvcschankgggitnj]
        AuthContainer[GoTrue Authentication Engine]
        DBPooler[AWS ap-southeast-1 Transaction Pooler - Port 5432]
        PostgresCore[(PostgreSQL 17.11 Instance)]
        StorageBucket[Supabase Storage - S3 Compatible]
    end

    subgraph ProductionIsolation [Supabase Production: ekspahbsbwdpraaqfqui]
        ProdDatabase[(STRICTLY ISOLATED - ZERO CONNECTIONS)]
    end

    ClientDevice -->|HTTPS / DNS: frontend-murex-one-58.vercel.app| EdgeFirewall
    EdgeFirewall -->|Valid Token / Public Access| EdgeFunction
    EdgeFunction -->|Route Match| PageRenderer
    EdgeFunction -->|API Call| APIHandlers
    PageRenderer --> DBDriver
    APIHandlers --> DBDriver
    ClientDevice -->|Direct Auth POST /auth/v1| AuthContainer
    DBDriver -->|TCP Connection - postgres.kpwdvvcschankgggitnj| DBPooler
    DBPooler --> PostgresCore

    EdgeFirewall -.->|STRICT FORBIDDEN RULE 11| ProdDatabase
    DBDriver -.->|STRICT FORBIDDEN RULE 11| ProdDatabase

    classDef isolated fill:#ef4444,stroke:#b91c1c,color:#ffffff;
    class ProdDatabase isolated;
```

**Explanation**: Illustrates the Vercel cloud serverless boundary and staging database target. The application is isolated from production Supabase (`ekspahbsbwdpraaqfqui`) via strict `TestEnvironmentGuard` checks in code and environment configurations.

---

## 4. Diagram C: Authentication & Authorization Sequence Flow

```mermaid
sequenceDiagram
    autonumber
    actor User as User / Browser
    participant LoginUI as /login (Next.js)
    participant GoTrue as Supabase Auth (/auth/v1)
    participant EdgeMW as Edge Middleware
    participant Resolve as resolveUserRole()
    participant DirectDB as Direct PostgreSQL Pool
    participant Dashboard as Role Dashboard (/college, /platform)

    User->>LoginUI: Enter Email & Password
    LoginUI->>GoTrue: POST /auth/v1/token?grant_type=password
    GoTrue-->>LoginUI: 200 OK (Set-Cookie: sb-*-auth-token, access_token)
    LoginUI->>User: Full Page Navigation -> window.location.href = '/'
    
    User->>EdgeMW: GET / (Carries sb-*-auth-token)
    EdgeMW->>GoTrue: supabase.auth.getUser()
    GoTrue-->>EdgeMW: Return Authenticated User Object
    
    alt User has raw role in JWT claim
        EdgeMW->>User: HTTP 307 Redirect to /college, /platform, /faculty
    else Missing role claim in JWT (Current Staging Reality)
        EdgeMW->>Dashboard: Allow request to proceed to Server Component
    end

    Dashboard->>Resolve: resolveUserRole(supabase)
    Resolve->>DirectDB: Query plat.platform_admins WHERE user_id = $1
    alt Is Platform Admin
        DirectDB-->>Resolve: Returns Platform Admin Row
        Resolve-->>Dashboard: { success: true, role: 'platform_admin' }
    else Tenant Member
        Resolve->>DirectDB: Query iam.user_profiles & iam.role_assignments
        DirectDB-->>Resolve: Returns Profile & Active Roles
        Resolve->>DirectDB: Query core.tenants (Verify Status != SUSPENDED)
        DirectDB-->>Resolve: Tenant ACTIVE
        Resolve-->>Dashboard: { success: true, role: 'college_admin' / 'faculty' }
    end

    Dashboard-->>User: Render Authorized Dashboard HTML (200 OK)
```

**Explanation**: Details the actual two-phase authentication flow. Because the Supabase Custom Access Token hook was observed to omit roles during staging tests, Next.js Server Components invoke `resolveUserRole()` which authoritatively queries `plat.platform_admins`, `iam.user_profiles`, `iam.role_assignments`, and `core.tenants` directly over PostgreSQL.

---

## 5. Diagram D: Multi-Tenant Request & Data Isolation Flow

```mermaid
flowchart TD
    Req[Incoming HTTP Request from Client] --> MW[Edge Middleware: Extract Session]
    MW --> FastFail{Membership / Tenant Status Checks}
    
    FastFail -->|status == INACTIVE| DenyInactive[Redirect: /access-denied?reason=inactive_membership]
    FastFail -->|tenant_status == SUSPENDED| DenySuspended[Redirect: /access-denied?reason=tenant_suspended]
    FastFail -->|no_profile == true| DenyNoProfile[Redirect: /access-denied?reason=no_profile]
    FastFail -->|Valid Membership| RoleGate{Path Role Verification}
    
    RoleGate -->|Violates Role Prefix e.g. Student -> /college| DenyForbidden[HTTP 403 / Redirect /access-denied?reason=forbidden]
    RoleGate -->|Authorized Role| Handler[Next.js Server Component or API Route]
    
    Handler --> ResolveRole[Authoritative resolveUserRole: Resolves tenant_id]
    ResolveRole --> SQLGen[Construct Parameterized SQL Query]
    
    subgraph RLSBypassReality [DATABASE ISOLATION BOUNDARY]
        SQLGen -->|Explicit Query Predicate: WHERE tenant_id = $1| DBPool[pg.Pool - postgres superuser]
        DBPool -->|Bypasses PostgreSQL Kernel RLS| Table[(PostgreSQL Base Tables)]
    end
    
    Table --> Result[Tenant-Scoped Relational Result]
    Result --> JSONResp[RFC 7807 JSON Response or Rendered DOM]

    classDef danger fill:#ef4444,stroke:#b91c1c,color:#ffffff;
    class DBPool danger;
```

**Explanation**: Demonstrates tenant isolation enforcement. While application-level checks and query predicates (`WHERE tenant_id = $1`) strictly filter data, database kernel RLS is bypassed because the connection pool executes under the `postgres` table-owner role.

---

## 6. Diagram E: API Ingress & Service Call Flow

```mermaid
graph LR
    subgraph Clients
        Browser[Web Browser]
        MobileApp[Mobile App - Future]
    end

    subgraph ActiveCloudIngress [Active Vercel Cloud Architecture]
        VercelEdge[Next.js Edge Middleware]
        APIRoutes[Next.js API Routes - 13 Endpoints]
        DirectPG[Direct pg.Pool Connection]
    end

    subgraph InactiveJavaCluster [Legacy Inactive Java Services - Inactive on Cloud]
        JavaGW[Java API Gateway :8080 - Superseded]
        ADM01[Institute Admin :8081]
        ADM02[College Admin :8082]
        ACD01[Course Mgmt :8083]
        ACD_Other[ACD 02..10 Microservices :8084-8092 - In-Memory State]
    end

    subgraph DatabaseBoundary [Supabase Staging Database]
        PG[(PostgreSQL 17.11 Instance - 20 Schemas)]
    end

    Browser -->|Active Cloud Ingress| VercelEdge
    VercelEdge --> APIRoutes
    APIRoutes --> DirectPG
    DirectPG -->|Direct SQL| PG

    Browser -.->|Local Dev Only| JavaGW
    JavaGW -.-> ADM01
    JavaGW -.-> ADM02
    JavaGW -.-> ACD01
    JavaGW -.-> ACD_Other
    ADM01 -.->|JDBC| PG
    ADM02 -.->|JDBC (Disabled by Default)| PG
    ACD01 -.->|JDBC (Disabled by Default)| PG

    classDef inactive fill:#6b7280,stroke:#374151,color:#ffffff;
    class JavaGW,ADM01,ADM02,ACD01,ACD_Other inactive;
```

**Explanation**: Directly compares the active Next.js cloud ingress against the legacy Java microservices. The Java microservices are inactive on Vercel and do not participate in cloud request handling.

---

## 7. Diagram F: Tenant Provisioning & Migration Data Flow

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Platform Admin
    participant Wizard as /platform/institutes/new
    participant API as /api/platform/onboard-tenant
    participant DB as Supabase PostgreSQL
    participant Trigger as Trigger: trg_seed_roles

    Admin->>Wizard: Complete 5-Step Saga (Institute, Admin, Academics, Plan)
    Wizard->>API: POST /api/platform/onboard-tenant
    
    alt Dry-Run Simulation (Default on Cloud Trial)
        API-->>Wizard: Return Simulated Success (Zero DB writes)
    else Gated Live Provisioning (Requires 'x-provision-approval: GO PROVISION-TENANT')
        API->>DB: INSERT INTO core.tenants (id, name, code, status)
        DB->>Trigger: Fires iam.on_tenant_created()
        Trigger->>DB: Seed 30 Role Templates into iam.roles
        Trigger->>DB: Seed 2,138 Role-Permissions into iam.role_permissions
        API->>DB: INSERT INTO core.colleges (tenant_id, name, code)
        API->>DB: INSERT INTO core.departments (tenant_id, college_id, code)
        API->>DB: INSERT INTO core.persons & iam.user_profiles (Admin Account)
        API->>DB: INSERT INTO iam.role_assignments (Bind COLLEGE_ADMIN Role)
        API->>DB: INSERT INTO audit.events (action = 'TENANT_ONBOARDED')
        API-->>Wizard: 201 Created (Tenant ID & College Provisioned)
    end
```

**Explanation**: Shows the multi-step transactional tenant provisioning saga. Live execution creates the tenant in `core.tenants`, which automatically triggers `trg_seed_roles` to instantiate standard roles and permission bindings.

---

## 8. Diagram G: Major Business Workflows

### Attendance Capture & Audited Correction Workflow
```mermaid
stateDiagram-v2
    [*] --> SlotSelection: Faculty selects scheduled timetable slot
    SlotSelection --> DeduplicationCheck: Check acd.attendance_sessions
    
    DeduplicationCheck --> ExistingSession: Session exists -> Open read-only view
    DeduplicationCheck --> NewSession: Session missing -> Initialize OPEN session
    
    NewSession --> RosterMarking: Faculty marks Present/Absent/Leave via keyboard
    RosterMarking --> ModalConfirmation: Click 'Submit to Ledger'
    ModalConfirmation --> LedgerLocked: Permanent status = 'CLOSED' (v1)
    
    LedgerLocked --> ShortageRecalculation: acd.v_attendance_summary updates
    ShortageRecalculation --> ShortageAlert: Cumulative attendance < 75% flags shortage
    
    LedgerLocked --> AdministrativeReview: College Admin opens /college/attendance
    AdministrativeReview --> CorrectionDrawer: Slide-over drawer with optimistic lock
    CorrectionDrawer --> ConcurrencyCheck: Check expectedVersion == row_version
    
    ConcurrencyCheck --> VersionConflict: Mismatch -> HTTP 409 Conflict
    ConcurrencyCheck --> ValidUpdate: Version match & reason >= 5 chars
    
    ValidUpdate --> LedgerIncrement: Update record status & increment row_version (v1 -> v2)
    LedgerIncrement --> AuditLog: Write immutable audit.change_log & audit.events
    AuditLog --> [*]
```

**Explanation**: Documents the full lifecycle of faculty attendance marking, ledger locking, statutory 75% shortage calculation, and audited administrative corrections.

---

## 9. Diagram H: Deployment & Validation Pipeline

```mermaid
graph TD
    subgraph LocalDev [Local Development & Regression Testing]
        GitRepo[Local Git: ReviewBranch] --> TypeCheck[npx tsc --noEmit - 0 Errors]
        TypeCheck --> BuildCheck[npm run build - 34 Routes Compiled]
        BuildCheck --> UnitTests[Regression Suites: verify_login_hardening.mjs - 58/58 Pass]
    end

    subgraph SecurityGate [Approval & Security Gates]
        UnitTests --> Rule11[Rule 11 Staging Assertion: Zero Prod References]
        Rule11 --> GatedWrite{Database Write Approval Phrase?}
        GatedWrite -->|No| SafeSimulation[Read-Only / Simulation Mode]
        GatedWrite -->|Yes: GO PROVISION-TENANT| LiveStagingWrite[Staging DB Write]
    end

    subgraph VercelPipeline [Vercel Cloud Deployment]
        LiveStagingWrite --> PreviewDeploy[Vercel Preview Deployment - dpl_7oE8yDDTT6TAKJyDkkg8aAmpWdfe]
        PreviewDeploy --> LiveProbes[scripts/verify_phase10_deployed_browser.mjs - 10/10 Pass]
        LiveProbes --> LiveSecurity[scripts/verify_phase9_security_browser.mjs - 36/36 Pass]
        LiveSecurity --> AliasPromotion[Vercel Live Alias: frontend-murex-one-58.vercel.app]
    end

    subgraph ProductionCutover [Production Release Gate]
        AliasPromotion --> ProdBlocker{Production Ready?}
        ProdBlocker -->|NO: Direct SQL RLS Bypass & Secret Exposure| BlockedRelease[PRODUCTION RELEASE BLOCKED]
    end

    classDef pass fill:#22c55e,stroke:#15803d,color:#ffffff;
    classDef blocked fill:#ef4444,stroke:#b91c1c,color:#ffffff;
    class TypeCheck,BuildCheck,UnitTests,Rule11,PreviewDeploy,LiveProbes,LiveSecurity,AliasPromotion pass;
    class BlockedRelease blocked;
```

**Explanation**: Documents the existing validation, staging promotion, and deployment gates leading up to the production blocker verdict.
