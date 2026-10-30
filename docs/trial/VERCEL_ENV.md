# Vercel Environment Variables Specification (Phase 10)

This document lists the required environment variable names for the CampXSync Vercel Preview and Production deployments. Per Rule 11 and V4 deployment safety rules, **variable names only** are documented here. All values must be configured through Vercel's secure project settings and target the approved Supabase Staging environment (`kpwdvvcschankgggitnj`).

---

## 1. Public Environment Variables
These variables are exposed to the browser via the Next.js client bundle.

| Variable Name | Scope | Description |
| :--- | :--- | :--- |
| `NEXT_PUBLIC_SUPABASE_URL` | Production, Preview, Development | Hosted Supabase project URL (Staging: `https://kpwdvvcschankgggitnj.supabase.co`) |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | Production, Preview, Development | Supabase anonymous publishable API key |

---

## 2. Server-Only & Secret Environment Variables
These variables are restricted to server-side Node.js execution (Server Components, Route Handlers, Edge Middleware) and are never exposed to the client bundle.

| Variable Name | Scope | Description |
| :--- | :--- | :--- |
| `SUPABASE_JDBC_URL` | Production, Preview, Development | JDBC connection string for Supabase PostgreSQL pooler |
| `SUPABASE_USER` | Production, Preview, Development | Database pooler username (`postgres.kpwdvvcschankgggitnj`) |
| `SUPABASE_PASSWORD` | Production, Preview, Development | Database pooler password |
| `CAMPX_TEST_ENV` | Production, Preview, Development | Target environment identifier (set to `staging`) |
| `ENABLE_TEST_SESSION` | Preview, Development | Enables test role switching session endpoints in Preview environments |
| `CAMPX_STAGING_TEST_PASSWORD` | Preview, Development | Password for staging account `campxsync@gmail.com` used in acceptance suites |

---

## 3. Strict Security Rules
- **No Production Target**: `ekspahbsbwdpraaqfqui` is strictly prohibited. Every Vercel environment targets Staging only.
- **No Service Role Key**: Neither `SUPABASE_SERVICE_ROLE_KEY` nor any service role tokens may be set as public variables or included in application code.
- **Vercel Protected Settings**: All secrets are managed exclusively within Vercel Project Settings > Environment Variables.
