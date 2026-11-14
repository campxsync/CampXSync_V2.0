# STM-CROSS-EN-001 — Supabase/PostgreSQL Schema Audit

**Environment:** Development ref `azulbaxszsqxymqmlwfe`  
**Date:** 2026-11-14  
**Scope:** Existing schemas, migrations, RLS flags, grants (read-only; no DDL applied)

## Migrations present (remote)

| Version | Name |
|---|---|
| 20261107094852 | create_stu_enrollment_tables |
| 20261107094906 | rbac_stu_catalogue_addendum |
| 20261107095620 | create_stu_enrollment_catalogue_tables |
| 20261107101138 | create_stu_lifecycle_tables |
| 20261107101141 | rbac_stu04_catalogue_addendum |
| 20261107113930 | add_lifecycle_request_effective_date |
| 20261107122141 | persons_insert_null_photo_pii_write |
| 20261112045208 | create_doc_stage_requirements |

## Domain table RLS snapshot (selected)

| Table | RLS | FORCE |
|---|:---:|:---:|
| `stu.students` | yes | **yes** |
| `stu.applications` | yes | **yes** |
| `stu.enrolments` | yes | **yes** |
| `stu.enrolment_courses` | yes | **yes** |
| `stu.admission_offers` | yes | **yes** |
| `stu.guardianships` | yes | **yes** |
| `stu.lifecycle_status` | yes | **yes** |
| `stu.lifecycle_requests` | yes | **yes** |
| `stu.lifecycle_history` | yes | **yes** |
| `doc.stage_document_requirements` | yes | **yes** |
| `doc.documents` | yes | no |
| `doc.document_types` | yes | no |
| `doc.document_versions` | yes | no |
| `core.persons` | yes | **yes** |
| `core.person_emergency_contacts` | yes | no |
| `core.colleges` | yes | no |
| `acd.programs` | yes | no |
| `sys.outbox_events` | yes | no |

## Grants (role_table_grants counts)

| Grantee | Schema | Grant rows |
|---|---|---|
| authenticated | stu | 95 |
| authenticated | doc | 56 |
| service_role | stu | 21 |
| service_role | doc | 91 |

## Findings / open gaps (no DDL this story)

1. **FORCE missing** on `doc.documents`, `doc.document_types`, `doc.document_versions` — requires explicit DDL approval.
2. **FORCE missing** on `core.person_emergency_contacts`, `core.colleges`, `acd.programs`, `sys.outbox_events`.
3. Cross-tenant penetration suite still not run (phrase: RLS enabled; suite pending).
4. Do not invent Kafka topics or new ownership tables in this audit.

## Evidence

- Headed demo: `docs/demos/STM-CROSS-EN-001/`
- Demo story: `docs/stories/STM-CROSS-EN-001.demo.json`
