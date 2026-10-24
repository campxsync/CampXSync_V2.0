# CampXSync — Dashboard Metric Definitions & Aggregation Rules (Wave 8)

**Document Reference:** `docs/reports/wave-8/METRIC_DEFINITIONS.md`  
**Governing Standard:** ACD-10 (Reporting & Analytics Service) / ACD-06 (Attendance Management)  
**Authoritative Source of Truth:** Supabase PostgreSQL transactional tables  
**Date:** 2026-10-23  

---

## 1. Executive Summary & Principles

1. **Transactional Source of Truth:** All dashboard metrics are derived directly or via read-projections from authoritative transactional PostgreSQL tables. Dashboards are consumer read models and never act as a secondary source of truth.
2. **Role-Appropriate Isolation:** Dashboards enforce strict Role-Based and Attribute-Based Access Control (RBAC/ABAC). Users see only metrics within their authorized tenant, college, and department/faculty assignment scope.
3. **Audit & Correction Reconcilability:** Any correction approved under Wave 7 (`acd.attendance_corrections`) updates the underlying `acd.attendance_records` status and version, immediately reflecting in dashboard metrics without stale cache or double counting.
4. **Deterministic Calculation:** Zero ambiguity in denominators, rounding, or status handling. All formulas are mathematically closed and handle empty datasets gracefully.

---

## 2. Status Classification & Attendance Rules

### 2.1 Attendance Status Domain
The domain allows the following attendance statuses: `PRESENT`, `ABSENT`, `LATE`, `ON_DUTY`, `LEAVE`, `EXCUSED`.

| Status | Counted as Attended? | Included in Total Denominator? | Description |
|---|:---:|:---:|---|
| `PRESENT` | **Yes** | Yes | Student present and marked during session roll call |
| `ABSENT` | No | Yes | Student unexcused absent |
| `LATE` | No (by default) | Yes | Student arrived after initial marking window |
| `ON_DUTY` | **Yes** (Institutional) | Yes | Approved college duty (sports, conference, authorized activity) |
| `LEAVE` | No | Yes | Excused medical or personal leave with approved slip |
| `EXCUSED` | No | Yes | Administrative exemption |

### 2.2 Handling of Corrected Records
- Under Wave 7 rules, an approved correction transitions `acd.attendance_corrections.status = 'APPROVED'` and updates `acd.attendance_records.status = new_status` while incrementing `row_version`.
- Dashboard queries aggregate over `acd.attendance_records` directly (filtering `deleted_at IS NULL`).
- Consequently, an approved correction from `ABSENT` to `PRESENT` immediately increments attended count and percentage on the next read.
- Rejected corrections (`status = 'REJECTED'`) leave `acd.attendance_records` unchanged.

### 2.3 Student Lifecycle & Inactive Filtering
- Active student records: `stu.students.deleted_at IS NULL AND stu.students.status = 'ACTIVE'`.
- Withdrawn or graduated students: excluded from active shortage alerts, but historical attendance records for completed terms are preserved for transcript auditability.

---

## 3. Mathematical Metric Definitions

### 3.1 Overall Attendance Percentage
$$\text{Overall Attendance \%} = \begin{cases} 
\text{ROUND}\left(\frac{\sum \text{PRESENT Records}}{\sum \text{Total Active Records}} \times 100, 1\right), & \text{if Total Records} > 0 \\
0.0, & \text{otherwise}
\end{cases}$$

### 3.2 Statutory Shortage Threshold
Per statutory higher education regulations (ACD-06 / DEC-14):
$$\text{Shortage Flag} = \text{TRUE} \iff \frac{\text{Attended Sessions}}{\text{Total Sessions Held}} < 0.75 \quad (75.0\%)$$
- Shortage students are sorted ascending by percentage (lowest attendance first) to prioritize academic intervention.

### 3.3 14-Day Attendance Trend
- Date Range: $\text{Current Date} - 13 \text{ days} \le \text{session\_date} \le \text{Current Date}$.
- Daily aggregate:
$$\text{Daily Attendance \%} = \frac{\text{Present Count for Day}}{\text{Total Records for Day}} \times 100$$
- Missing days (e.g. Sundays or institutional holidays) are omitted or represented with 0 scheduled sessions.

### 3.4 Batch & Subject Breakdown
- Grouped by `course_sections.section_code`, `subjects.code`, `subjects.name`.
- Metric calculations:
  - `sessions_held`: count of distinct sessions in `CLOSED` or active state.
  - `total_records`: sum of records across held sessions.
  - `present_records`: sum of `PRESENT` records.
  - `percentage`: $\frac{\text{present\_records}}{\text{total\_records}} \times 100$.

### 3.5 Missed / Pending Sessions
- Defined as attendance sessions where `session_date <= CURRENT_DATE` and `status = 'OPEN'`.
- Indicates faculty has opened or scheduled a session but attendance has not been completed/closed.

---

## 4. Role-Specific Dashboard Views

### 4.1 College Admin Dashboard
- **Scope:** Entire College / Tenant (`core.current_tenant_id()`).
- **Read Model Components:**
  1. **Headline KPIs:** `studentCount`, `facultyCount`, `sessionCount`, `totalRecords`, `presentRecords`, `overallPercentage`.
  2. **Shortage Alerts:** List of students with $< 75\%$ attendance.
  3. **14-Day Trend:** Daily historical attendance trajectory.
  4. **Curriculum/Batch Matrix:** Subject and section attendance breakdown.
  5. **Operational Alerts:** Missed / open attendance sessions requiring dean/HOD follow-up.

### 4.2 Faculty Dashboard
- **Scope:** Assigned sections and courses only (`hrm.employees.id = s.employee_id` or section faculty mapping).
- **Read Model Components:**
  1. **Today's Schedule:** Timetable slots matching `day_of_week = CURRENT_ISO_DAY` with joined session state:
     - `NOT_STARTED`: session not yet created.
     - `OPEN`: session created and ready for roll call.
     - `SUBMITTED` (`CLOSED`): attendance marked and ledger submitted.
  2. **Upcoming Slots:** Scheduled slots on future days or later today.
  3. **Quick Action Stats:** `submittedCount`, `openCount`.

### 4.3 Head of Department (HOD) Dashboard
- **Scope:** Department-scoped courses, faculty, and student batches (`core.departments.id = $departmentId`).
- **Read Model Components:**
  1. Department attendance average and shortage count.
  2. Faculty session submission compliance rate.
  3. Pending attendance corrections within department.

### 4.4 Platform Admin Dashboard
- **Scope:** Cross-tenant platform metrics.
- **Privacy Barrier:** High-level tenant/college counts and user totals only (`core.tenants`, `core.colleges`, `iam.user_profiles`). Never exposes individual student or faculty personal data.

---

## 5. Performance and Latency Budget

To resolve historical login and dashboard latency bottlenecks:
- **p50 Latency Budget:** $< 100\text{ ms}$ for aggregate dashboard loads.
- **p95 Latency Budget:** $< 250\text{ ms}$ under concurrent query load.
- **Optimization Strategy:** Single-pass relational queries, index utilization on `(tenant_id, session_date)` and `(tenant_id, session_id)`, eliminating N+1 frontend query cascades.
