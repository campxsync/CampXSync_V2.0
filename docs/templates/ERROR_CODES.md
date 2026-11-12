# Error Code Catalog Entry: {ERROR_CODE}

## Error Metadata
- **Error Code:** `{ERROR_CODE}`
- **HTTP Status:** `{400 | 401 | 403 | 404 | 409 | 422 | 500 | 503}`
- **Standard Title:** `{Title per RFC 7807}`
- **Component / Service:** `{service-name}`

## Trigger Conditions
- Condition 1: What input or state triggers this error.
- Condition 2: Lifecycle or concurrency conflict.

## Remediation / Client Guidance
- What action the user or frontend should take to correct the request.

## Example Response (application/problem+json)
```json
{
  "type": "https://campxsync.io/errors/{ERROR_CODE}",
  "title": "{Standard Title}",
  "status": 400,
  "code": "{ERROR_CODE}",
  "message": "Human readable remediation message.",
  "traceId": "trace-uuid",
  "path": "/api/v1/{resource}"
}
```

