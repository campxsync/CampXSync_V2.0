# Log Event Catalog Entry: {EVENT_NAME}

## Event Metadata
- **Event Name:** `{domain}.{resource}.{action}`
- **Log Level:** `{INFO | WARN | ERROR | AUDIT}`
- **Service:** `{service-name}`
- **Flow Identifier:** `{flow-name}`

## Log Payload Structure
```json
{
  "timestamp": "2026-11-12T14:30:00.000Z",
  "level": "INFO",
  "service": "{service-name}",
  "traceId": "c0000000-0000-0000-0000-000000000001",
  "tenantId": "a0000000-0000-0000-0000-000000000001",
  "userId": "u0000000-0000-0000-0000-000000000001",
  "action": "{action_name}",
  "resourceId": "uuid",
  "durationMs": 42
}
```

## Security & PII Redaction
- **Masked Fields:** Passwords, tokens, national IDs, PII.
- **Retained Context:** Entity UUIDs, Tenant UUID, Correlation Trace UUID.

