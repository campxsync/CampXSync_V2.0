-- CampXSync ERP on Supabase (PostgreSQL 15+)
-- Generated from the design specs; edit the specs, not this file, if you regenerate.

create extension if not exists pgcrypto;
create extension if not exists pg_trgm;
create extension if not exists btree_gist;
-- Optional (enable in the Supabase dashboard): pg_cron, pgsodium/vault, pgmq
create schema if not exists iam;
create schema if not exists sys;
create schema if not exists core;
comment on schema core is 'Tenancy, organisation, people master, calendars, numbering, shared lookups';
create schema if not exists stu;
comment on schema stu is 'Student master anchor. ASSUMPTION: STU/IAM modules were not in the uploaded files';
create schema if not exists cfg;
comment on schema cfg is 'Settings cascade, feature flags, policies, retention';
create schema if not exists plat;
comment on schema plat is 'SaaS commercial + platform operations (CampSync operator plane). Tenants get read-only visibility of their own rows';
create schema if not exists wf;
comment on schema wf is 'Generic workflow / approval engine used by every module';
create schema if not exists doc;
comment on schema doc is 'Documents (Supabase Storage metadata), templates, verification, delivery';
create schema if not exists audit;
comment on schema audit is 'Row-level change log, semantic audit events, access logs, compliance & SoD, evidence, cases';
create schema if not exists acd;
comment on schema acd is 'Academic structure: programs, curricula, subjects, courses, offerings, batches, timetable, attendance, assessment schemes';
create schema if not exists lms;
comment on schema lms is 'Learning content, resources, lesson plans, assignments, virtual classrooms, progress, credentials';
create schema if not exists obe;
comment on schema obe is 'Outcome-based education: PO/PSO/CO/TLO, mappings, alignment, attainment (NBA/NAAC)';
create schema if not exists exm;
comment on schema exm is 'Examinations: registration, scheduling, hall tickets, valuation, marks, results, script logistics, reporting';
create schema if not exists fin;
comment on schema fin is 'Fees, collections, receipts, scholarships, vendors/expenses, disbursements, general ledger';
create schema if not exists tax;
comment on schema tax is 'Tax masters, transactions, returns and filings (GST/TDS/other)';
create schema if not exists bud;
comment on schema bud is 'Budgets, allocations, commitments, forecasts and scenarios';
create schema if not exists pay;
comment on schema pay is 'Payroll: salary structures, compensation, runs, payslips, statutory, loans';
create schema if not exists hrm;
comment on schema hrm is 'Employees, leave, attendance, performance, recruitment, training';
create schema if not exists cam;
comment on schema cam is 'Communications: audiences, campaigns/messages, unified delivery gateway (email/SMS/push/WhatsApp), surveys, emergency alerts';
create schema if not exists rpt;
comment on schema rpt is 'Report/dashboard/KPI definitions and executions shared by EXM, FIN, HRM, CAM; fact tables are views/materialised views';

-- API exposure: keep every ERP schema OFF the Data API 'exposed schemas' list except the ones you deliberately publish
-- (Dashboard > Settings > API). Safer default: expose none and use RPC functions / views in a thin 'api' schema.
revoke all on schema sys from anon, authenticated;

-- Functions are created with EXECUTE granted to PUBLIC by default. Turn that off for everything this migration role creates.
alter default privileges revoke execute on functions from public;
