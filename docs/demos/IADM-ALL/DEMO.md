# IADM-ALL Browser Demo Desk

**Date:** 2026-11-21  
**URL:** http://localhost:3000/platform/iadm  
**Session:** Platform Admin via `/api/auth/test-session?role=platform_admin&returnTo=/platform/iadm`

## What it shows
- All **57** IADM user stories across IADM-01…08 (56 completed, 1 deferred: IADM-05-US-007 under-18)
- Live Gateway health probe for all 8 Java services (UI → BFF → Gateway → `:8099`–`:8106`)
- Probe treats HTTP 401 as **UP (HMAC)** — same semantics as `check_stack.ps1` AcceptAuthChallenge

## Live run evidence (this session)
- Stack started; IADM-01…08 listening
- Probe result: **8/8 up**
- Expanded IADM-03 shows US-008 break-glass + US-009 two-person as completed

## Approvals recorded
- `APPROVED IADM-03-US-009`
- `APPROVED IADM-03-US-008`
