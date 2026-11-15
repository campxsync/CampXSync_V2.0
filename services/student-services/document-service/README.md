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

## Open gaps

- Live malware scanner: OUT OF SCOPE (would require a new tool). Heuristic `ControlledMalwareScanAdapter` remains (`liveEndpoint=false`).
- Staging bucket/policies: prepared in `docs/runbooks/STM-06-EN-002_STAGING_STORAGE_SETUP.md` — not applied until Phase B approval.

## Tests (no DB / no live Supabase)

```bash
mvn test -pl services/student-services/document-service -Dtest=DocumentDomainServiceTest
```
