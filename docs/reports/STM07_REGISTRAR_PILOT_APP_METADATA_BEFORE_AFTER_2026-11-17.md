# Pilot Registrar (R31) Auth sign-in metadata — before/after

**Date:** 2026-11-17  
**Project confirmed:** `azulbaxszsqxymqmlwfe` (CampXSync-Architecture-Dev) — matched before write  
**Account touched:** one only — Auth user `c31a0000-0000-4000-8000-000000000031` / `registrar.pilot.campx@gmail.com`  
**Not changed:** password, email, Auth user id, `user_metadata` (except left as-is), no new accounts

## Before (`auth.users.raw_app_meta_data` / `raw_user_meta_data`)

| Store | Fields present (no secrets) |
|---|---|
| App metadata (`raw_app_meta_data`) | `provider=email`, `providers=[email]` — **no** `tenant_id`, **no** `college_id`, **no** `role` |
| User metadata (`raw_user_meta_data`) | `full_name=Pilot Registrar`, `tenant_id=a0000000-0000-0000-0000-000000000001` |

## After

| Store | Fields present (no secrets) |
|---|---|
| App metadata (`raw_app_meta_data`) | `provider=email`, `providers=[email]`, **`tenant_id=a0000000-0000-0000-0000-000000000001`**, **`college_id=a0000000-0000-0000-0000-000000000002`**, **`role=REGISTRAR`** |
| User metadata (`raw_user_meta_data`) | unchanged (`full_name`, existing `tenant_id`) |

## Field notes

- Owner approved updating **college ID** and **role** on this existing pilot.
- API Gateway requires **`app_metadata.tenant_id`** (UUID) or it returns `GATEWAY_MALFORMED_JWT`. That value already existed on this same account under **user metadata**; it was copied into **app metadata** so the gateway can accept the login token. No new tenant invented.
- College id = pilot campus `a0000000-0000-0000-0000-000000000002` (from Registrar pilot runbook).
- Role = `REGISTRAR` (Registrar R31).
