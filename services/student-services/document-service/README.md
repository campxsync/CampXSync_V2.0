# STM-06 Student Documents Service

Port: `8098` (loopback). Flow: UI → API Gateway `:8080` → this service → Supabase PostgreSQL (+ optional Supabase Storage for bytes).

## Storage (STM-06-EN-002 Phase A)

- Interface: `com.campx.document.storage.DocumentObjectStorage`
- Live client: `SupabaseStorageHttpClient` (JDK `HttpURLConnection` only; no new Maven deps)
- Test fake: `FakeDocumentObjectStorage`
- Relational tables store **metadata + object path only** (`storage_bucket`, `storage_path`, `size_bytes`, `checksum_sha256`, `mime_type`)

### Config (server-side only)

| Env var | Default |
|---|---|
| `CAMPX_SUPABASE_URL` | (required for live storage) |
| `CAMPX_SUPABASE_SERVICE_ROLE_KEY` | (required for live storage; never log) |
| `CAMPX_STORAGE_BUCKET` | `student-documents` |

When URL + service-role key are absent, the service runs in **metadata-only** profile (local/unit tests).

### Upload

`POST /api/v1/documents/submissions` JSON may include optional `contentBase64`.

- Storage configured → `contentBase64` **required**; decoded length must equal `sizeBytes`; SHA-256 must match `checksumSha256`; magic-byte check vs allowlisted MIME (`application/pdf`, `image/jpeg`, `image/png`, `image/webp`); max size **10 MB**.
- Storage not configured → metadata-only allowed.

### Download

1. `POST /api/v1/documents/{id}/signed-url` issues a short-lived HMAC token (EN-003).
2. `GET /api/v1/documents/download?token=...` streams **bytes through this service** after token + RBAC checks. Does **not** return Supabase signed URLs to clients.
3. `?mode=metadata` returns the EN-003 JSON metadata shape for diagnostics.

## Registrar R31 (D10 / D12)

- Canonical role: `R31` / `REGISTRAR` with assigned campus (`X-College-Id` → `Actor.collegeId`).
- Approve/reject requires active delegation via `DelegationPort` (`STM-06.DOCUMENTS.A`). Default runtime port is fail-closed.
- `CAMPX_STM_LEGACY_REGISTRAR_STANDIN` (default OFF): when ON, `COLLEGE_ADMIN` / `INSTITUTION_ADMIN` may satisfy Registrar checks — **staging only**, temporary until first real college onboarding.
- Catalogue/config (document types, stage requirements) remains **College Admin**; Registrar does not inherit those rights.
- Staging IAM seed: `docs/runbooks/R31_REGISTRAR_STAGING_SETUP.md` (prepare-only).

## Open gaps

- Malware: controlled lifecycle (`PENDING_SCAN`→`SCANNING`→`CLEAN`/`INFECTED`/`SCAN_FAILED`) via `MalwareScanPort` / `ControlledMalwareScanAdapter` (content EICAR + fail-closed). Live commercial AV: OUT OF SCOPE until owner-approved new tool (`liveEndpoint=false`).
- Staging bucket/policies: prepared in `docs/runbooks/STM-06-EN-002_STAGING_STORAGE_SETUP.md` — not applied until Phase B approval.
- Live IAM delegation adapter (cross-schema): fail-closed until Phase B / owner design.

## Tests (no DB / no live Supabase)

```bash
mvn test -pl services/student-services/document-service -Dtest=DocumentDomainServiceTest
```
