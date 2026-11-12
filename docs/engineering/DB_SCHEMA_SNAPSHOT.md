# Database Schema Snapshot (Dev: azulbaxszsqxymqmlwfe)

*Generated automatically on 2026-11-12T09:02:54.449Z via `scripts/dev/dump_schema.mjs`*
*RULE: Consult this document before writing any JDBC repository queries or migrations.*

## Schema: `doc`

### Table: `doc.document_deliveries`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `document_id` | `uuid` | **NO** | — |
| `recipient_person_id` | `uuid` | YES | — |
| `channel` | `text` | **NO** | — |
| `status` | `text` | **NO** | `'PENDING'::text` |
| `delivered_at` | `timestamp with time zone` | YES | — |
| `client_ip` | `inet` | YES | — |
| `delivery_id` | `uuid` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_document_deliveries_channel` | CHECK | `CHECK ((channel = ANY (ARRAY['EMAIL'::text, 'SMS'::text, 'PORTAL'::text, 'DOWNLOAD'::text, 'PRINT'::text, 'COURIER'::text])))` |
| `ck_document_deliveries_status` | CHECK | `CHECK ((status = ANY (ARRAY['PENDING'::text, 'SENT'::text, 'DELIVERED'::text, 'FAILED'::text, 'DOWNLOADED'::text])))` |
| `fk_document_deliveries_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT` |
| `fk_document_deliveries_recipient_person_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, recipient_person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT` |
| `fk_document_deliveries_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `document_deliveries_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_document_deliveries_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `document_deliveries_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.manage'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.manage'::text, true) AS sc_doc_documents))))` |
| `document_deliveries_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.write'::text, true) AS sc_doc_documents))))` |
| `document_deliveries_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.view'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.view'::text, false) AS sc_doc_documents))))` |
| `document_deliveries_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.write'::text, true) AS sc_doc_documents))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `document_deliveries_pkey` | `CREATE UNIQUE INDEX document_deliveries_pkey ON doc.document_deliveries USING btree (id)` |
| `ix_document_deliveries_recipient_person_id` | `CREATE INDEX ix_document_deliveries_recipient_person_id ON doc.document_deliveries USING btree (tenant_id, recipient_person_id)` |
| `ix_document_deliveries_tenant_id_document_id_channel` | `CREATE INDEX ix_document_deliveries_tenant_id_document_id_channel ON doc.document_deliveries USING btree (tenant_id, document_id, channel)` |
| `uq_document_deliveries_tenant_id` | `CREATE UNIQUE INDEX uq_document_deliveries_tenant_id ON doc.document_deliveries USING btree (tenant_id, id)` |

---

### Table: `doc.document_links`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `document_id` | `uuid` | **NO** | — |
| `entity_type` | `text` | **NO** | — |
| `entity_id` | `uuid` | **NO** | — |
| `relation` | `text` | **NO** | `'ATTACHMENT'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_document_links_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE CASCADE` |
| `fk_document_links_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `document_links_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_document_links_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `document_links_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.manage'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.manage'::text, true) AS sc_doc_documents))))` |
| `document_links_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.write'::text, true) AS sc_doc_documents))))` |
| `document_links_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.view'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.view'::text, false) AS sc_doc_documents))))` |
| `document_links_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.write'::text, true) AS sc_doc_documents))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `document_links_pkey` | `CREATE UNIQUE INDEX document_links_pkey ON doc.document_links USING btree (id)` |
| `ix_document_links_tenant_id_entity_type_entity_id` | `CREATE INDEX ix_document_links_tenant_id_entity_type_entity_id ON doc.document_links USING btree (tenant_id, entity_type, entity_id)` |
| `uq_document_links_tenant_id` | `CREATE UNIQUE INDEX uq_document_links_tenant_id ON doc.document_links USING btree (tenant_id, id)` |
| `uq_document_links_tenant_id_document_id_entity_typ_4949eedf` | `CREATE UNIQUE INDEX uq_document_links_tenant_id_document_id_entity_typ_4949eedf ON doc.document_links USING btree (tenant_id, document_id, entity_type, entity_id, relation)` |

---

### Table: `doc.document_renditions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `document_id` | `uuid` | **NO** | — |
| `rendition_type` | `text` | **NO** | — |
| `storage_path` | `text` | **NO** | — |
| `size_bytes` | `bigint` | YES | — |
| `status` | `text` | **NO** | `'READY'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_document_renditions_rendition_type` | CHECK | `CHECK ((rendition_type = ANY (ARRAY['THUMBNAIL'::text, 'PREVIEW'::text, 'TRANSCODE'::text, 'OCR'::text, 'PDF'::text])))` |
| `ck_document_renditions_status` | CHECK | `CHECK ((status = ANY (ARRAY['PENDING'::text, 'READY'::text, 'FAILED'::text])))` |
| `fk_document_renditions_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE CASCADE` |
| `fk_document_renditions_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `document_renditions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_document_renditions_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `document_renditions_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.manage'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.manage'::text, true) AS sc_doc_documents))))` |
| `document_renditions_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.write'::text, true) AS sc_doc_documents))))` |
| `document_renditions_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.view'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.view'::text, false) AS sc_doc_documents))))` |
| `document_renditions_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.write'::text, true) AS sc_doc_documents))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `document_renditions_pkey` | `CREATE UNIQUE INDEX document_renditions_pkey ON doc.document_renditions USING btree (id)` |
| `ix_document_renditions_document_id` | `CREATE INDEX ix_document_renditions_document_id ON doc.document_renditions USING btree (tenant_id, document_id)` |
| `uq_document_renditions_tenant_id` | `CREATE UNIQUE INDEX uq_document_renditions_tenant_id ON doc.document_renditions USING btree (tenant_id, id)` |
| `uq_document_renditions_tenant_id_document_id_rendition_type` | `CREATE UNIQUE INDEX uq_document_renditions_tenant_id_document_id_rendition_type ON doc.document_renditions USING btree (tenant_id, document_id, rendition_type) WHERE (deleted_at IS NULL)` |

---

### Table: `doc.document_types`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `category` | `text` | YES | — |
| `module` | `text` | YES | — |
| `default_classification` | `text` | **NO** | `'INTERNAL'::text` |
| `requires_signature` | `boolean` | **NO** | `false` |
| `retention_policy_code` | `text` | YES | — |
| `is_active` | `boolean` | **NO** | `true` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_document_types_default_classification` | CHECK | `CHECK ((default_classification = ANY (ARRAY['PUBLIC'::text, 'INTERNAL'::text, 'CONFIDENTIAL'::text, 'RESTRICTED'::text])))` |
| `fk_document_types_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `document_types_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_document_types_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `document_types_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('doc.admin'::text) AS has_permission))` |
| `document_types_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('doc.admin'::text) AS has_permission))` |
| `document_types_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('doc.view'::text) AS has_permission))` |
| `document_types_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('doc.admin'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `document_types_pkey` | `CREATE UNIQUE INDEX document_types_pkey ON doc.document_types USING btree (id)` |
| `uq_document_types_tenant_id` | `CREATE UNIQUE INDEX uq_document_types_tenant_id ON doc.document_types USING btree (tenant_id, id)` |
| `uq_document_types_tenant_id_code` | `CREATE UNIQUE INDEX uq_document_types_tenant_id_code ON doc.document_types USING btree (tenant_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `doc.document_versions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `document_id` | `uuid` | **NO** | — |
| `version_no` | `integer` | **NO** | — |
| `storage_path` | `text` | **NO** | — |
| `size_bytes` | `bigint` | YES | — |
| `checksum_sha256` | `text` | YES | — |
| `change_note` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_document_versions_version_no` | CHECK | `CHECK ((version_no > 0))` |
| `fk_document_versions_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE CASCADE` |
| `fk_document_versions_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `document_versions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_document_versions_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_immutable` | BEFORE | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_immutable` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `document_versions_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.write'::text, true) AS sc_doc_documents))))` |
| `document_versions_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.view'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.view'::text, false) AS sc_doc_documents))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `document_versions_pkey` | `CREATE UNIQUE INDEX document_versions_pkey ON doc.document_versions USING btree (id)` |
| `uq_document_versions_tenant_id` | `CREATE UNIQUE INDEX uq_document_versions_tenant_id ON doc.document_versions USING btree (tenant_id, id)` |
| `uq_document_versions_tenant_id_document_id_version_no` | `CREATE UNIQUE INDEX uq_document_versions_tenant_id_document_id_version_no ON doc.document_versions USING btree (tenant_id, document_id, version_no)` |

---

### Table: `doc.documents`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `college_id` | `uuid` | YES | — |
| `document_type_id` | `uuid` | YES | — |
| `title` | `text` | **NO** | — |
| `classification` | `text` | **NO** | `'INTERNAL'::text` |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `owner_id` | `uuid` | YES | — |
| `storage_bucket` | `text` | YES | — |
| `storage_path` | `text` | YES | — |
| `mime_type` | `text` | YES | — |
| `size_bytes` | `bigint` | YES | — |
| `checksum_sha256` | `text` | YES | — |
| `current_version_no` | `integer` | **NO** | `1` |
| `published_at` | `timestamp with time zone` | YES | — |
| `expires_at` | `timestamp with time zone` | YES | — |
| `workflow_instance_id` | `uuid` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_documents_classification` | CHECK | `CHECK ((classification = ANY (ARRAY['PUBLIC'::text, 'INTERNAL'::text, 'CONFIDENTIAL'::text, 'RESTRICTED'::text])))` |
| `ck_documents_size_bytes` | CHECK | `CHECK ((size_bytes >= 0))` |
| `ck_documents_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SUBMITTED'::text, 'APPROVED'::text, 'REJECTED'::text, 'PUBLISHED'::text, 'ARCHIVED'::text, 'CANCELLED'::text])))` |
| `fk_documents_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_documents_document_type_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, document_type_id) REFERENCES doc.document_types(tenant_id, id) ON DELETE RESTRICT` |
| `fk_documents_owner_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, owner_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_documents_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_documents_workflow_instance_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT` |
| `documents_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_documents_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `documents_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.manage'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('doc.manage'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('doc.manage'::text) AS has_permission)))))` |
| `documents_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('doc.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('doc.write'::text) AS has_permission)))))` |
| `documents_rfilter` | `SELECT` | `{authenticated}` | `(( SELECT iam.has_permission('doc.view.all'::text) AS has_permission) OR ((classification = ANY (ARRAY['PUBLIC'::text, 'INTERNAL'::text])) OR (owner_id = ( SELECT auth.uid() AS uid)) OR ( SELECT iam.has_permission('doc.view.confidential'::text) AS has_permission)))` |
| `documents_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.view'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('doc.view'::text)) AS unnest)) OR ((NOT false) AND (college_id IS NULL) AND ( SELECT iam.has_permission('doc.view'::text) AS has_permission))) OR (owner_id = ( SELECT auth.uid() AS uid))))` |
| `documents_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('doc.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('doc.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `documents_pkey` | `CREATE UNIQUE INDEX documents_pkey ON doc.documents USING btree (id)` |
| `ix_documents_college_id` | `CREATE INDEX ix_documents_college_id ON doc.documents USING btree (tenant_id, college_id)` |
| `ix_documents_tenant_id_document_type_id_status` | `CREATE INDEX ix_documents_tenant_id_document_type_id_status ON doc.documents USING btree (tenant_id, document_type_id, status)` |
| `ix_documents_tenant_id_owner_id` | `CREATE INDEX ix_documents_tenant_id_owner_id ON doc.documents USING btree (tenant_id, owner_id)` |
| `ix_documents_workflow_instance_id` | `CREATE INDEX ix_documents_workflow_instance_id ON doc.documents USING btree (tenant_id, workflow_instance_id)` |
| `uq_documents_tenant_id` | `CREATE UNIQUE INDEX uq_documents_tenant_id ON doc.documents USING btree (tenant_id, id)` |

---

### Table: `doc.lifecycle_requests`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `document_id` | `uuid` | YES | — |
| `entity_type` | `text` | YES | — |
| `entity_id` | `uuid` | YES | — |
| `request_type` | `text` | **NO** | — |
| `reason` | `text` | **NO** | — |
| `status` | `text` | **NO** | `'PENDING'::text` |
| `requested_by` | `uuid` | YES | — |
| `workflow_instance_id` | `uuid` | YES | — |
| `new_document_id` | `uuid` | YES | — |
| `completed_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_lifecycle_requests_request_type` | CHECK | `CHECK ((request_type = ANY (ARRAY['REISSUE'::text, 'CANCEL'::text, 'CORRECTION'::text])))` |
| `ck_lifecycle_requests_status` | CHECK | `CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text, 'COMPLETED'::text])))` |
| `fk_lifecycle_requests_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT` |
| `fk_lifecycle_requests_new_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, new_document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT` |
| `fk_lifecycle_requests_requested_by` | FOREIGN KEY | `FOREIGN KEY (tenant_id, requested_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_lifecycle_requests_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_lifecycle_requests_workflow_instance_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT` |
| `lifecycle_requests_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_lifecycle_requests_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `lifecycle_requests_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.manage'::text) AS perm_all) OR ((document_id IN ( SELECT iam.sc_doc_documents('doc.manage'::text, true) AS sc_doc_documents)) OR ((NOT true) AND (document_id IS NULL) AND ( SELECT iam.has_permission('doc.manage'::text) AS has_permission)))))` |
| `lifecycle_requests_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR ((document_id IN ( SELECT iam.sc_doc_documents('doc.write'::text, true) AS sc_doc_documents)) OR ((NOT true) AND (document_id IS NULL) AND ( SELECT iam.has_permission('doc.write'::text) AS has_permission)))))` |
| `lifecycle_requests_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.view'::text) AS perm_all) OR ((document_id IN ( SELECT iam.sc_doc_documents('doc.view'::text, false) AS sc_doc_documents)) OR ((NOT false) AND (document_id IS NULL) AND ( SELECT iam.has_permission('doc.view'::text) AS has_permission)))))` |
| `lifecycle_requests_student_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (entity_type = 'STUDENT'::text) AND (entity_id IN ( SELECT core.my_student_ids() AS my_student_ids)) AND (request_type = 'CORRECTION'::text) AND (requested_by = auth.uid()) AND (status = 'PENDING'::text) AND (completed_at IS NULL) AND (workflow_instance_id IS NULL))` |
| `lifecycle_requests_student_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (entity_type = 'STUDENT'::text) AND (entity_id IN ( SELECT core.my_student_ids() AS my_student_ids)) AND (request_type = 'CORRECTION'::text))` |
| `lifecycle_requests_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR ((document_id IN ( SELECT iam.sc_doc_documents('doc.write'::text, true) AS sc_doc_documents)) OR ((NOT true) AND (document_id IS NULL) AND ( SELECT iam.has_permission('doc.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_lifecycle_requests_document_id` | `CREATE INDEX ix_lifecycle_requests_document_id ON doc.lifecycle_requests USING btree (tenant_id, document_id)` |
| `ix_lifecycle_requests_new_document_id` | `CREATE INDEX ix_lifecycle_requests_new_document_id ON doc.lifecycle_requests USING btree (tenant_id, new_document_id)` |
| `ix_lifecycle_requests_requested_by` | `CREATE INDEX ix_lifecycle_requests_requested_by ON doc.lifecycle_requests USING btree (tenant_id, requested_by)` |
| `ix_lifecycle_requests_workflow_instance_id` | `CREATE INDEX ix_lifecycle_requests_workflow_instance_id ON doc.lifecycle_requests USING btree (tenant_id, workflow_instance_id)` |
| `lifecycle_requests_pkey` | `CREATE UNIQUE INDEX lifecycle_requests_pkey ON doc.lifecycle_requests USING btree (id)` |
| `uq_lifecycle_requests_tenant_id` | `CREATE UNIQUE INDEX uq_lifecycle_requests_tenant_id ON doc.lifecycle_requests USING btree (tenant_id, id)` |

---

### Table: `doc.print_job_items`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `print_job_id` | `uuid` | **NO** | — |
| `document_id` | `uuid` | YES | — |
| `seq` | `integer` | **NO** | `1` |
| `status` | `text` | **NO** | `'QUEUED'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_print_job_items_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT` |
| `fk_print_job_items_print_job_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, print_job_id) REFERENCES doc.print_jobs(tenant_id, id) ON DELETE CASCADE` |
| `fk_print_job_items_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `print_job_items_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_print_job_items_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `print_job_items_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.manage'::text) AS perm_all) OR (print_job_id IN ( SELECT iam.sc_doc_print_jobs('doc.manage'::text, true) AS sc_doc_print_jobs))))` |
| `print_job_items_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR (print_job_id IN ( SELECT iam.sc_doc_print_jobs('doc.write'::text, true) AS sc_doc_print_jobs))))` |
| `print_job_items_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.view'::text) AS perm_all) OR (print_job_id IN ( SELECT iam.sc_doc_print_jobs('doc.view'::text, false) AS sc_doc_print_jobs))))` |
| `print_job_items_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR (print_job_id IN ( SELECT iam.sc_doc_print_jobs('doc.write'::text, true) AS sc_doc_print_jobs))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_print_job_items_document_id` | `CREATE INDEX ix_print_job_items_document_id ON doc.print_job_items USING btree (tenant_id, document_id)` |
| `ix_print_job_items_print_job_id` | `CREATE INDEX ix_print_job_items_print_job_id ON doc.print_job_items USING btree (tenant_id, print_job_id)` |
| `print_job_items_pkey` | `CREATE UNIQUE INDEX print_job_items_pkey ON doc.print_job_items USING btree (id)` |
| `uq_print_job_items_tenant_id` | `CREATE UNIQUE INDEX uq_print_job_items_tenant_id ON doc.print_job_items USING btree (tenant_id, id)` |

---

### Table: `doc.print_jobs`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `document_id` | `uuid` | YES | — |
| `entity_type` | `text` | YES | — |
| `entity_id` | `uuid` | YES | — |
| `requested_by` | `uuid` | YES | — |
| `copies` | `integer` | **NO** | `1` |
| `status` | `text` | **NO** | `'QUEUED'::text` |
| `printed_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_print_jobs_status` | CHECK | `CHECK ((status = ANY (ARRAY['QUEUED'::text, 'PRINTING'::text, 'DONE'::text, 'FAILED'::text])))` |
| `fk_print_jobs_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT` |
| `fk_print_jobs_requested_by` | FOREIGN KEY | `FOREIGN KEY (tenant_id, requested_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_print_jobs_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `print_jobs_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_print_jobs_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `print_jobs_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.manage'::text) AS perm_all) OR ((document_id IN ( SELECT iam.sc_doc_documents('doc.manage'::text, true) AS sc_doc_documents)) OR ((NOT true) AND (document_id IS NULL) AND ( SELECT iam.has_permission('doc.manage'::text) AS has_permission)))))` |
| `print_jobs_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR ((document_id IN ( SELECT iam.sc_doc_documents('doc.write'::text, true) AS sc_doc_documents)) OR ((NOT true) AND (document_id IS NULL) AND ( SELECT iam.has_permission('doc.write'::text) AS has_permission)))))` |
| `print_jobs_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.view'::text) AS perm_all) OR ((document_id IN ( SELECT iam.sc_doc_documents('doc.view'::text, false) AS sc_doc_documents)) OR ((NOT false) AND (document_id IS NULL) AND ( SELECT iam.has_permission('doc.view'::text) AS has_permission)))))` |
| `print_jobs_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR ((document_id IN ( SELECT iam.sc_doc_documents('doc.write'::text, true) AS sc_doc_documents)) OR ((NOT true) AND (document_id IS NULL) AND ( SELECT iam.has_permission('doc.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_print_jobs_document_id` | `CREATE INDEX ix_print_jobs_document_id ON doc.print_jobs USING btree (tenant_id, document_id)` |
| `ix_print_jobs_requested_by` | `CREATE INDEX ix_print_jobs_requested_by ON doc.print_jobs USING btree (tenant_id, requested_by)` |
| `print_jobs_pkey` | `CREATE UNIQUE INDEX print_jobs_pkey ON doc.print_jobs USING btree (id)` |
| `uq_print_jobs_tenant_id` | `CREATE UNIQUE INDEX uq_print_jobs_tenant_id ON doc.print_jobs USING btree (tenant_id, id)` |

---

### Table: `doc.signatures`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `document_id` | `uuid` | **NO** | — |
| `signer_id` | `uuid` | YES | — |
| `signature_type` | `text` | **NO** | — |
| `signed_at` | `timestamp with time zone` | **NO** | — |
| `certificate_ref` | `text` | YES | — |
| `signature_hash` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_signatures_signature_type` | CHECK | `CHECK ((signature_type = ANY (ARRAY['DIGITAL'::text, 'ESIGN'::text, 'STAMP'::text])))` |
| `fk_signatures_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE CASCADE` |
| `fk_signatures_signer_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, signer_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_signatures_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `signatures_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_signatures_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_immutable` | BEFORE | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_immutable` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `signatures_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.write'::text, true) AS sc_doc_documents))))` |
| `signatures_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.view'::text) AS perm_all) OR (document_id IN ( SELECT iam.sc_doc_documents('doc.view'::text, false) AS sc_doc_documents))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_signatures_document_id` | `CREATE INDEX ix_signatures_document_id ON doc.signatures USING btree (tenant_id, document_id)` |
| `ix_signatures_signer_id` | `CREATE INDEX ix_signatures_signer_id ON doc.signatures USING btree (tenant_id, signer_id)` |
| `signatures_pkey` | `CREATE UNIQUE INDEX signatures_pkey ON doc.signatures USING btree (id)` |
| `uq_signatures_tenant_id` | `CREATE UNIQUE INDEX uq_signatures_tenant_id ON doc.signatures USING btree (tenant_id, id)` |

---

### Table: `doc.stage_document_requirements`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `program_id` | `uuid` | YES | — |
| `academic_stage` | `text` | **NO** | — |
| `document_type_id` | `uuid` | **NO** | — |
| `is_mandatory` | `boolean` | **NO** | `true` |
| `min_count` | `integer` | **NO** | `1` |
| `max_count` | `integer` | YES | `1` |
| `submission_deadline_days` | `integer` | YES | — |
| `description` | `text` | YES | — |
| `is_active` | `boolean` | **NO** | `true` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_stage_document_requirements_counts` | CHECK | `CHECK (((min_count >= 0) AND ((max_count IS NULL) OR (max_count >= min_count))))` |
| `ck_stage_document_requirements_stage` | CHECK | `CHECK ((academic_stage = ANY (ARRAY['ADMISSION'::text, 'ENROLLMENT'::text, 'ANNUAL_PROGRESSION'::text, 'EXAMINATION'::text, 'GRADUATION'::text])))` |
| `stage_document_requirements_document_type_id_fkey` | FOREIGN KEY | `FOREIGN KEY (document_type_id) REFERENCES doc.document_types(id) ON DELETE RESTRICT` |
| `stage_document_requirements_program_id_fkey` | FOREIGN KEY | `FOREIGN KEY (program_id) REFERENCES acd.programs(id) ON DELETE RESTRICT` |
| `stage_document_requirements_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_stage_document_requirements_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `stage_document_requirements_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('doc.admin'::text) AS has_permission))` |
| `stage_document_requirements_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('doc.admin'::text) AS has_permission))` |
| `stage_document_requirements_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('doc.view'::text) AS has_permission))` |
| `stage_document_requirements_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('doc.admin'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_stage_document_requirements_doctype` | `CREATE INDEX ix_stage_document_requirements_doctype ON doc.stage_document_requirements USING btree (tenant_id, document_type_id)` |
| `ix_stage_document_requirements_stage_prog` | `CREATE INDEX ix_stage_document_requirements_stage_prog ON doc.stage_document_requirements USING btree (tenant_id, academic_stage, program_id)` |
| `stage_document_requirements_pkey` | `CREATE UNIQUE INDEX stage_document_requirements_pkey ON doc.stage_document_requirements USING btree (id)` |
| `uq_stage_document_requirements_rule` | `CREATE UNIQUE INDEX uq_stage_document_requirements_rule ON doc.stage_document_requirements USING btree (tenant_id, COALESCE(program_id, '00000000-0000-0000-0000-000000000000'::uuid), academic_stage, document_type_id) WHERE (deleted_at IS NULL)` |
| `uq_stage_document_requirements_tenant_id` | `CREATE UNIQUE INDEX uq_stage_document_requirements_tenant_id ON doc.stage_document_requirements USING btree (tenant_id, id)` |

---

### Table: `doc.template_versions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `template_id` | `uuid` | **NO** | — |
| `version_no` | `integer` | **NO** | — |
| `subject` | `text` | YES | — |
| `body` | `text` | **NO** | — |
| `variables` | `jsonb` | YES | `'{}'::jsonb` |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `published_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_template_versions_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text, 'RETIRED'::text])))` |
| `ck_template_versions_version_no` | CHECK | `CHECK ((version_no > 0))` |
| `fk_template_versions_template_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, template_id) REFERENCES doc.templates(tenant_id, id) ON DELETE CASCADE` |
| `fk_template_versions_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `template_versions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_template_versions_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `template_versions_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.admin'::text) AS perm_all) OR (template_id IN ( SELECT iam.sc_doc_templates('doc.admin'::text, true) AS sc_doc_templates))))` |
| `template_versions_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.admin'::text) AS perm_all) OR (template_id IN ( SELECT iam.sc_doc_templates('doc.admin'::text, true) AS sc_doc_templates))))` |
| `template_versions_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.view'::text) AS perm_all) OR (template_id IN ( SELECT iam.sc_doc_templates('doc.view'::text, false) AS sc_doc_templates))))` |
| `template_versions_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.admin'::text) AS perm_all) OR (template_id IN ( SELECT iam.sc_doc_templates('doc.admin'::text, true) AS sc_doc_templates))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_template_versions_template_id` | `CREATE INDEX ix_template_versions_template_id ON doc.template_versions USING btree (tenant_id, template_id)` |
| `template_versions_pkey` | `CREATE UNIQUE INDEX template_versions_pkey ON doc.template_versions USING btree (id)` |
| `uq_template_versions_tenant_id` | `CREATE UNIQUE INDEX uq_template_versions_tenant_id ON doc.template_versions USING btree (tenant_id, id)` |
| `uq_template_versions_tenant_id_template_id_version_no` | `CREATE UNIQUE INDEX uq_template_versions_tenant_id_template_id_version_no ON doc.template_versions USING btree (tenant_id, template_id, version_no) WHERE (deleted_at IS NULL)` |

---

### Table: `doc.templates`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `college_id` | `uuid` | YES | — |
| `template_kind` | `text` | **NO** | — |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `channel` | `text` | YES | — |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `active_version_no` | `integer` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_templates_channel` | CHECK | `CHECK ((channel = ANY (ARRAY['EMAIL'::text, 'SMS'::text, 'PUSH'::text, 'WHATSAPP'::text, 'IN_APP'::text, 'PRINT'::text])))` |
| `ck_templates_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])))` |
| `ck_templates_template_kind` | CHECK | `CHECK ((template_kind = ANY (ARRAY['DOCUMENT'::text, 'HALL_TICKET'::text, 'RECEIPT'::text, 'PAYSLIP'::text, 'MESSAGE'::text, 'REPORT'::text, 'CERTIFICATE'::text])))` |
| `fk_templates_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_templates_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `templates_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_templates_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `templates_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.admin'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('doc.admin'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('doc.admin'::text) AS has_permission)))))` |
| `templates_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.admin'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('doc.admin'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('doc.admin'::text) AS has_permission)))))` |
| `templates_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.view'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('doc.view'::text)) AS unnest)) OR ((NOT false) AND (college_id IS NULL) AND ( SELECT iam.has_permission('doc.view'::text) AS has_permission)))))` |
| `templates_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.admin'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('doc.admin'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('doc.admin'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_templates_college_id` | `CREATE INDEX ix_templates_college_id ON doc.templates USING btree (tenant_id, college_id)` |
| `templates_pkey` | `CREATE UNIQUE INDEX templates_pkey ON doc.templates USING btree (id)` |
| `uq_templates_tenant_id` | `CREATE UNIQUE INDEX uq_templates_tenant_id ON doc.templates USING btree (tenant_id, id)` |
| `uq_templates_tenant_id_template_kind_code` | `CREATE UNIQUE INDEX uq_templates_tenant_id_template_kind_code ON doc.templates USING btree (tenant_id, template_kind, code) WHERE (deleted_at IS NULL)` |

---

### Table: `doc.verifications`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `document_id` | `uuid` | YES | — |
| `entity_type` | `text` | YES | — |
| `entity_id` | `uuid` | YES | — |
| `verification_code` | `text` | **NO** | — |
| `result` | `text` | **NO** | — |
| `verified_at` | `timestamp with time zone` | **NO** | `now()` |
| `client_ip` | `inet` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_verifications_result` | CHECK | `CHECK ((result = ANY (ARRAY['VALID'::text, 'INVALID'::text, 'EXPIRED'::text, 'REVOKED'::text])))` |
| `fk_verifications_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT` |
| `fk_verifications_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `verifications_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_verifications_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `verifications_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.write'::text) AS perm_all) OR ((document_id IN ( SELECT iam.sc_doc_documents('doc.write'::text, true) AS sc_doc_documents)) OR ((NOT true) AND (document_id IS NULL) AND ( SELECT iam.has_permission('doc.write'::text) AS has_permission)))))` |
| `verifications_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('doc.view'::text) AS perm_all) OR ((document_id IN ( SELECT iam.sc_doc_documents('doc.view'::text, false) AS sc_doc_documents)) OR ((NOT false) AND (document_id IS NULL) AND ( SELECT iam.has_permission('doc.view'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_verifications_document_id` | `CREATE INDEX ix_verifications_document_id ON doc.verifications USING btree (tenant_id, document_id)` |
| `ix_verifications_tenant_id_entity_type_entity_id` | `CREATE INDEX ix_verifications_tenant_id_entity_type_entity_id ON doc.verifications USING btree (tenant_id, entity_type, entity_id)` |
| `ix_verifications_tenant_id_verification_code` | `CREATE INDEX ix_verifications_tenant_id_verification_code ON doc.verifications USING btree (tenant_id, verification_code)` |
| `uq_verifications_tenant_id` | `CREATE UNIQUE INDEX uq_verifications_tenant_id ON doc.verifications USING btree (tenant_id, id)` |
| `verifications_pkey` | `CREATE UNIQUE INDEX verifications_pkey ON doc.verifications USING btree (id)` |

---

## Schema: `stu`

### Table: `stu.academic_terms`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `year_code` | `character varying(16)` | **NO** | — |
| `term_code` | `character varying(32)` | **NO** | — |
| `name` | `character varying(120)` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `academic_terms_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `pk_academic_terms` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |
| `uq_academic_terms_code` | UNIQUE | `UNIQUE (tenant_id, year_code, term_code)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_academic_terms` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_academic_terms` | `CREATE UNIQUE INDEX pk_academic_terms ON stu.academic_terms USING btree (tenant_id, id)` |
| `uq_academic_terms_code` | `CREATE UNIQUE INDEX uq_academic_terms_code ON stu.academic_terms USING btree (tenant_id, year_code, term_code)` |

---

### Table: `stu.admission_decisions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `application_id` | `uuid` | **NO** | — |
| `decision_type` | `character varying(32)` | **NO** | — |
| `decision_reason_code` | `character varying(64)` | **NO** | — |
| `decision_notes` | `text` | YES | — |
| `decided_by` | `uuid` | **NO** | — |
| `decided_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `row_version` | `integer` | **NO** | `1` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `admission_decisions_decision_type_check` | CHECK | `CHECK (((decision_type)::text = ANY ((ARRAY['OFFER_APPROVED'::character varying, 'REJECTED'::character varying, 'DEFERRED'::character varying, 'WAITLISTED'::character varying])::text[])))` |
| `admission_decisions_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_decisions_application` | FOREIGN KEY | `FOREIGN KEY (tenant_id, application_id) REFERENCES stu.applications(tenant_id, id) ON DELETE CASCADE` |
| `pk_admission_decisions` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_admission_decisions` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_admission_decisions_app` | `CREATE INDEX ix_admission_decisions_app ON stu.admission_decisions USING btree (tenant_id, application_id)` |
| `pk_admission_decisions` | `CREATE UNIQUE INDEX pk_admission_decisions ON stu.admission_decisions USING btree (tenant_id, id)` |

---

### Table: `stu.admission_eligibility_rules`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `intake_id` | `uuid` | **NO** | — |
| `version` | `character varying(32)` | **NO** | — |
| `min_qualification_grade` | `numeric` | **NO** | `0.00` |
| `min_age` | `integer` | **NO** | `16` |
| `max_age` | `integer` | YES | — |
| `required_prerequisites` | `jsonb` | **NO** | `'[]'::jsonb` |
| `rule_definitions` | `jsonb` | **NO** | `'{}'::jsonb` |
| `is_active` | `boolean` | **NO** | `true` |
| `effective_from` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `effective_to` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `updated_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `created_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `admission_eligibility_rules_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_eligibility_intake` | FOREIGN KEY | `FOREIGN KEY (tenant_id, intake_id) REFERENCES stu.admission_intakes(tenant_id, id) ON DELETE RESTRICT` |
| `pk_admission_eligibility_rules` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |
| `uq_eligibility_rules_version` | UNIQUE | `UNIQUE (tenant_id, intake_id, version)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_admission_eligibility` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_admission_eligibility_intake` | `CREATE INDEX ix_admission_eligibility_intake ON stu.admission_eligibility_rules USING btree (tenant_id, intake_id, is_active)` |
| `pk_admission_eligibility_rules` | `CREATE UNIQUE INDEX pk_admission_eligibility_rules ON stu.admission_eligibility_rules USING btree (tenant_id, id)` |
| `uq_eligibility_rules_version` | `CREATE UNIQUE INDEX uq_eligibility_rules_version ON stu.admission_eligibility_rules USING btree (tenant_id, intake_id, version)` |

---

### Table: `stu.admission_handovers`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `application_id` | `uuid` | **NO** | — |
| `offer_id` | `uuid` | **NO** | — |
| `idempotency_key` | `character varying(128)` | **NO** | — |
| `created_student_id` | `uuid` | YES | — |
| `enrolment_status` | `character varying(32)` | **NO** | `'PENDING'::character varying` |
| `retry_count` | `integer` | **NO** | `0` |
| `last_error` | `text` | YES | — |
| `handover_completed_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `updated_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `row_version` | `integer` | **NO** | `1` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `admission_handovers_enrolment_status_check` | CHECK | `CHECK (((enrolment_status)::text = ANY ((ARRAY['PENDING'::character varying, 'STUDENT_CREATED'::character varying, 'ENROLMENT_COMPLETED'::character varying, 'FAILED'::character varying])::text[])))` |
| `admission_handovers_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_handovers_application` | FOREIGN KEY | `FOREIGN KEY (tenant_id, application_id) REFERENCES stu.applications(tenant_id, id) ON DELETE RESTRICT` |
| `fk_handovers_offer` | FOREIGN KEY | `FOREIGN KEY (tenant_id, offer_id) REFERENCES stu.admission_offers(tenant_id, id) ON DELETE RESTRICT` |
| `pk_admission_handovers` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |
| `uq_admission_handovers_idempotency` | UNIQUE | `UNIQUE (tenant_id, idempotency_key)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_admission_handovers` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_admission_handovers_status` | `CREATE INDEX ix_admission_handovers_status ON stu.admission_handovers USING btree (tenant_id, enrolment_status) WHERE ((enrolment_status)::text = ANY ((ARRAY['PENDING'::character varying, 'FAILED'::character varying])::text[]))` |
| `pk_admission_handovers` | `CREATE UNIQUE INDEX pk_admission_handovers ON stu.admission_handovers USING btree (tenant_id, id)` |
| `uq_admission_handovers_idempotency` | `CREATE UNIQUE INDEX uq_admission_handovers_idempotency ON stu.admission_handovers USING btree (tenant_id, idempotency_key)` |

---

### Table: `stu.admission_intakes`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `college_id` | `uuid` | **NO** | — |
| `program_id` | `uuid` | **NO** | — |
| `academic_year_id` | `uuid` | **NO** | — |
| `intake_name` | `character varying(120)` | **NO** | — |
| `intake_code` | `character varying(32)` | **NO** | — |
| `application_window_start` | `timestamp with time zone` | **NO** | — |
| `application_window_end` | `timestamp with time zone` | **NO** | — |
| `capacity` | `integer` | **NO** | — |
| `admitted_count` | `integer` | **NO** | `0` |
| `is_active` | `boolean` | **NO** | `true` |
| `fee_required` | `boolean` | **NO** | `false` |
| `fee_amount` | `numeric` | **NO** | `0.00` |
| `currency` | `character varying(3)` | **NO** | `'INR'::character varying` |
| `fee_timing` | `character varying(32)` | **NO** | `'AT_SUBMISSION'::character varying` |
| `scoring_rubric` | `jsonb` | **NO** | `'{}'::jsonb` |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `updated_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `created_by` | `uuid` | YES | — |
| `updated_by` | `uuid` | YES | — |
| `deleted_at` | `timestamp with time zone` | YES | — |
| `row_version` | `integer` | **NO** | `1` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `admission_intakes_admitted_count_check` | CHECK | `CHECK ((admitted_count >= 0))` |
| `admission_intakes_capacity_check` | CHECK | `CHECK ((capacity > 0))` |
| `admission_intakes_fee_amount_check` | CHECK | `CHECK ((fee_amount >= 0.00))` |
| `admission_intakes_fee_timing_check` | CHECK | `CHECK (((fee_timing)::text = ANY ((ARRAY['AT_SUBMISSION'::character varying, 'POST_ELIGIBILITY'::character varying, 'UPON_OFFER_ACCEPTANCE'::character varying, 'NONE'::character varying])::text[])))` |
| `admission_intakes_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `pk_admission_intakes` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |
| `uq_admission_intakes_code` | UNIQUE | `UNIQUE (tenant_id, academic_year_id, intake_code)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_admission_intakes` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_admission_intakes_tenant_prog_year` | `CREATE INDEX ix_admission_intakes_tenant_prog_year ON stu.admission_intakes USING btree (tenant_id, program_id, academic_year_id)` |
| `ix_admission_intakes_window` | `CREATE INDEX ix_admission_intakes_window ON stu.admission_intakes USING btree (tenant_id, application_window_start, application_window_end) WHERE ((is_active = true) AND (deleted_at IS NULL))` |
| `pk_admission_intakes` | `CREATE UNIQUE INDEX pk_admission_intakes ON stu.admission_intakes USING btree (tenant_id, id)` |
| `uq_admission_intakes_code` | `CREATE UNIQUE INDEX uq_admission_intakes_code ON stu.admission_intakes USING btree (tenant_id, academic_year_id, intake_code)` |

---

### Table: `stu.admission_offers`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `application_id` | `uuid` | **NO** | — |
| `decision_id` | `uuid` | **NO** | — |
| `offer_code` | `character varying(64)` | **NO** | — |
| `offer_terms_version` | `character varying(32)` | **NO** | — |
| `valid_from` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `valid_to` | `timestamp with time zone` | **NO** | — |
| `status` | `character varying(32)` | **NO** | `'ISSUED'::character varying` |
| `accepted_at` | `timestamp with time zone` | YES | — |
| `declined_at` | `timestamp with time zone` | YES | — |
| `decline_reason` | `text` | YES | — |
| `extension_granted` | `boolean` | **NO** | `false` |
| `extension_granted_by` | `uuid` | YES | — |
| `extended_valid_to` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `updated_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `created_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `admission_offers_status_check` | CHECK | `CHECK (((status)::text = ANY ((ARRAY['ISSUED'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying, 'EXPIRED'::character varying, 'REVOKED'::character varying])::text[])))` |
| `admission_offers_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_offers_application` | FOREIGN KEY | `FOREIGN KEY (tenant_id, application_id) REFERENCES stu.applications(tenant_id, id) ON DELETE RESTRICT` |
| `fk_offers_decision` | FOREIGN KEY | `FOREIGN KEY (tenant_id, decision_id) REFERENCES stu.admission_decisions(tenant_id, id) ON DELETE RESTRICT` |
| `pk_admission_offers` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |
| `uq_admission_offers_code` | UNIQUE | `UNIQUE (tenant_id, offer_code)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_admission_offers` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_admission_offers_app` | `CREATE INDEX ix_admission_offers_app ON stu.admission_offers USING btree (tenant_id, application_id)` |
| `ix_admission_offers_expiry` | `CREATE INDEX ix_admission_offers_expiry ON stu.admission_offers USING btree (tenant_id, status, valid_to) WHERE ((status)::text = 'ISSUED'::text)` |
| `pk_admission_offers` | `CREATE UNIQUE INDEX pk_admission_offers ON stu.admission_offers USING btree (tenant_id, id)` |
| `uq_admission_offers_code` | `CREATE UNIQUE INDEX uq_admission_offers_code ON stu.admission_offers USING btree (tenant_id, offer_code)` |

---

### Table: `stu.application_documents`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `application_id` | `uuid` | **NO** | — |
| `document_id` | `uuid` | **NO** | — |
| `document_type` | `character varying(64)` | **NO** | — |
| `storage_path` | `character varying(512)` | **NO** | — |
| `file_name` | `character varying(256)` | **NO** | — |
| `mime_type` | `character varying(128)` | **NO** | — |
| `file_size_bytes` | `bigint` | **NO** | — |
| `verification_status` | `character varying(32)` | **NO** | `'PENDING'::character varying` |
| `verified_by` | `uuid` | YES | — |
| `verified_at` | `timestamp with time zone` | YES | — |
| `rejection_reason` | `text` | YES | — |
| `is_fraudulent` | `boolean` | **NO** | `false` |
| `fraud_notes` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `updated_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `row_version` | `integer` | **NO** | `1` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `application_documents_verification_status_check` | CHECK | `CHECK (((verification_status)::text = ANY ((ARRAY['PENDING'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying, 'WAIVED'::character varying])::text[])))` |
| `application_documents_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_app_documents_application` | FOREIGN KEY | `FOREIGN KEY (tenant_id, application_id) REFERENCES stu.applications(tenant_id, id) ON DELETE CASCADE` |
| `pk_application_documents` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `app_documents_staff_all` | `ALL` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ((auth.uid() IS NULL) OR ( SELECT iam.has_permission('core.write'::text) AS has_permission) OR ( SELECT iam.has_permission('adm01.manage'::text) AS has_permission) OR ( SELECT iam.has_permission('adm01.write'::text) AS has_permission) OR ( SELECT iam.has_permission('adm01.view'::text) AS has_permission)))` |
| `app_documents_student_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (application_id IN ( SELECT a.id
   FROM (stu.applications a
     JOIN stu.admission_intakes i ON (((i.id = a.intake_id) AND (i.tenant_id = a.tenant_id))))
  WHERE ((a.applicant_person_id = auth.uid()) AND (a.tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ((a.status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying, 'UNDER_REVIEW'::character varying])::text[])) AND (i.is_active = true) AND ((i.application_window_start IS NULL) OR (now() >= i.application_window_start)) AND ((i.application_window_end IS NULL) OR (now() <= i.application_window_end))))) AND ((verification_status)::text = 'PENDING'::text) AND (verified_by IS NULL) AND (verified_at IS NULL) AND (rejection_reason IS NULL) AND (is_fraudulent = false) AND (fraud_notes IS NULL))` |
| `app_documents_student_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (application_id IN ( SELECT applications.id
   FROM stu.applications
  WHERE ((applications.applicant_person_id = auth.uid()) AND (applications.tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id))))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_app_documents_app` | `CREATE INDEX ix_app_documents_app ON stu.application_documents USING btree (tenant_id, application_id)` |
| `ix_app_documents_doc` | `CREATE INDEX ix_app_documents_doc ON stu.application_documents USING btree (tenant_id, document_id)` |
| `pk_application_documents` | `CREATE UNIQUE INDEX pk_application_documents ON stu.application_documents USING btree (tenant_id, id)` |

---

### Table: `stu.application_reviews`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `application_id` | `uuid` | **NO** | — |
| `reviewer_id` | `uuid` | **NO** | — |
| `rubric_version` | `character varying(32)` | **NO** | — |
| `rubric_scores` | `jsonb` | **NO** | `'{}'::jsonb` |
| `total_score` | `numeric` | **NO** | `0.00` |
| `recommendation` | `character varying(32)` | **NO** | — |
| `confidential_comments` | `text` | YES | — |
| `applicant_facing_remarks` | `text` | YES | — |
| `submitted_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `updated_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `row_version` | `integer` | **NO** | `1` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `application_reviews_recommendation_check` | CHECK | `CHECK (((recommendation)::text = ANY ((ARRAY['RECOMMEND_OFFER'::character varying, 'RECOMMEND_REJECT'::character varying, 'WAITLIST'::character varying, 'REQUEST_INFORMATION'::character varying])::text[])))` |
| `application_reviews_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_reviews_application` | FOREIGN KEY | `FOREIGN KEY (tenant_id, application_id) REFERENCES stu.applications(tenant_id, id) ON DELETE CASCADE` |
| `pk_application_reviews` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_app_reviews` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_app_reviews_app` | `CREATE INDEX ix_app_reviews_app ON stu.application_reviews USING btree (tenant_id, application_id)` |
| `ix_app_reviews_reviewer` | `CREATE INDEX ix_app_reviews_reviewer ON stu.application_reviews USING btree (tenant_id, reviewer_id)` |
| `pk_application_reviews` | `CREATE UNIQUE INDEX pk_application_reviews ON stu.application_reviews USING btree (tenant_id, id)` |

---

### Table: `stu.application_status_history`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `application_id` | `uuid` | **NO** | — |
| `from_status` | `character varying(32)` | **NO** | — |
| `to_status` | `character varying(32)` | **NO** | — |
| `transition_action` | `character varying(64)` | **NO** | — |
| `transition_reason` | `text` | YES | — |
| `actor_id` | `uuid` | **NO** | — |
| `actor_role` | `character varying(64)` | **NO** | — |
| `correlation_id` | `character varying(64)` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `application_status_history_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_status_history_application` | FOREIGN KEY | `FOREIGN KEY (tenant_id, application_id) REFERENCES stu.applications(tenant_id, id) ON DELETE CASCADE` |
| `pk_application_status_history` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_app_status_history` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_app_status_history_app` | `CREATE INDEX ix_app_status_history_app ON stu.application_status_history USING btree (tenant_id, application_id, created_at DESC)` |
| `pk_application_status_history` | `CREATE UNIQUE INDEX pk_application_status_history ON stu.application_status_history USING btree (tenant_id, id)` |

---

### Table: `stu.applications`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `application_number` | `character varying(64)` | **NO** | — |
| `intake_id` | `uuid` | **NO** | — |
| `applicant_person_id` | `uuid` | **NO** | — |
| `assigned_department_id` | `uuid` | YES | — |
| `assigned_reviewer_id` | `uuid` | YES | — |
| `status` | `character varying(32)` | **NO** | `'DRAFT'::character varying` |
| `document_verification_status` | `character varying(32)` | **NO** | `'PENDING'::character varying` |
| `payment_status` | `character varying(32)` | **NO** | `'PENDING'::character varying` |
| `fee_obligation_id` | `uuid` | YES | — |
| `payment_reference` | `character varying(128)` | YES | — |
| `payment_amount` | `numeric` | YES | `0.00` |
| `submission_timestamp` | `timestamp with time zone` | YES | — |
| `eligibility_rule_version` | `character varying(32)` | YES | — |
| `eligibility_result` | `character varying(32)` | YES | — |
| `eligibility_evaluation_detail` | `jsonb` | YES | `'{}'::jsonb` |
| `eligibility_overridden` | `boolean` | **NO** | `false` |
| `eligibility_override_reason` | `text` | YES | — |
| `eligibility_override_by` | `uuid` | YES | — |
| `late_submission_exception_granted` | `boolean` | **NO** | `false` |
| `late_submission_exception_by` | `uuid` | YES | — |
| `declaration_accepted` | `boolean` | **NO** | `false` |
| `declaration_timestamp` | `timestamp with time zone` | YES | — |
| `privacy_consent_accepted` | `boolean` | **NO** | `false` |
| `applicant_data` | `jsonb` | **NO** | `'{}'::jsonb` |
| `review_comments_summary` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `updated_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `created_by` | `uuid` | YES | — |
| `updated_by` | `uuid` | YES | — |
| `deleted_at` | `timestamp with time zone` | YES | — |
| `row_version` | `integer` | **NO** | `1` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `applications_document_verification_status_check` | CHECK | `CHECK (((document_verification_status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_PROGRESS'::character varying, 'VERIFIED'::character varying, 'REJECTED'::character varying, 'WAIVED'::character varying])::text[])))` |
| `applications_payment_status_check` | CHECK | `CHECK (((payment_status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'WAIVED'::character varying, 'EXEMPT'::character varying, 'FAILED'::character varying])::text[])))` |
| `applications_status_check` | CHECK | `CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SUBMITTED'::character varying, 'RETURNED_FOR_INFORMATION'::character varying, 'ELIGIBLE'::character varying, 'UNDER_REVIEW'::character varying, 'OFFERED'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying, 'EXPIRED'::character varying, 'REJECTED'::character varying, 'WITHDRAWN'::character varying, 'HANDOVER_PENDING'::character varying, 'HANDED_OVER'::character varying])::text[])))` |
| `applications_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_application_intake` | FOREIGN KEY | `FOREIGN KEY (tenant_id, intake_id) REFERENCES stu.admission_intakes(tenant_id, id) ON DELETE RESTRICT` |
| `pk_applications` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |
| `uq_applications_number` | UNIQUE | `UNIQUE (tenant_id, intake_id, application_number)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `applications_staff_all` | `ALL` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ((auth.uid() IS NULL) OR ( SELECT iam.has_permission('core.write'::text) AS has_permission) OR ( SELECT iam.has_permission('adm01.manage'::text) AS has_permission) OR ( SELECT iam.has_permission('adm01.write'::text) AS has_permission) OR ( SELECT iam.has_permission('adm01.view'::text) AS has_permission)))` |
| `applications_student_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (applicant_person_id = auth.uid()))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_applications_dept` | `CREATE INDEX ix_applications_dept ON stu.applications USING btree (tenant_id, assigned_department_id) WHERE (assigned_department_id IS NOT NULL)` |
| `ix_applications_reviewer` | `CREATE INDEX ix_applications_reviewer ON stu.applications USING btree (tenant_id, assigned_reviewer_id) WHERE (assigned_reviewer_id IS NOT NULL)` |
| `ix_applications_tenant_applicant` | `CREATE INDEX ix_applications_tenant_applicant ON stu.applications USING btree (tenant_id, applicant_person_id)` |
| `ix_applications_tenant_status` | `CREATE INDEX ix_applications_tenant_status ON stu.applications USING btree (tenant_id, status)` |
| `pk_applications` | `CREATE UNIQUE INDEX pk_applications ON stu.applications USING btree (tenant_id, id)` |
| `uq_applications_number` | `CREATE UNIQUE INDEX uq_applications_number ON stu.applications USING btree (tenant_id, intake_id, application_number)` |
| `uq_applications_prevent_duplicates` | `CREATE UNIQUE INDEX uq_applications_prevent_duplicates ON stu.applications USING btree (tenant_id, intake_id, applicant_person_id) WHERE ((deleted_at IS NULL) AND ((status)::text <> ALL ((ARRAY['WITHDRAWN'::character varying, 'REJECTED'::character varying])::text[])))` |

---

### Table: `stu.enrolment_courses`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `program_id` | `uuid` | **NO** | — |
| `term_id` | `uuid` | **NO** | — |
| `code` | `character varying(32)` | **NO** | — |
| `credits` | `integer` | **NO** | — |
| `is_core` | `boolean` | **NO** | `false` |
| `prerequisites` | `ARRAY` | **NO** | `'{}'::uuid[]` |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_enrolment_courses_credits` | CHECK | `CHECK ((credits > 0))` |
| `enrolment_courses_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_enrolment_courses_term` | FOREIGN KEY | `FOREIGN KEY (tenant_id, term_id) REFERENCES stu.academic_terms(tenant_id, id)` |
| `pk_enrolment_courses` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |
| `uq_enrolment_courses_code` | UNIQUE | `UNIQUE (tenant_id, program_id, term_id, code)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_enrolment_courses` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_enrolment_courses` | `CREATE UNIQUE INDEX pk_enrolment_courses ON stu.enrolment_courses USING btree (tenant_id, id)` |
| `uq_enrolment_courses_code` | `CREATE UNIQUE INDEX uq_enrolment_courses_code ON stu.enrolment_courses USING btree (tenant_id, program_id, term_id, code)` |

---

### Table: `stu.enrolment_sections`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `course_id` | `uuid` | **NO** | — |
| `capacity` | `integer` | **NO** | — |
| `enrolled_count` | `integer` | **NO** | `0` |
| `faculty_user_id` | `uuid` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_enrolment_sections_capacity` | CHECK | `CHECK (((capacity > 0) AND (enrolled_count >= 0) AND (enrolled_count <= capacity)))` |
| `enrolment_sections_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_enrolment_sections_course` | FOREIGN KEY | `FOREIGN KEY (tenant_id, course_id) REFERENCES stu.enrolment_courses(tenant_id, id)` |
| `pk_enrolment_sections` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_enrolment_sections` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_enrolment_sections` | `CREATE UNIQUE INDEX pk_enrolment_sections ON stu.enrolment_sections USING btree (tenant_id, id)` |

---

### Table: `stu.enrolment_selections`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `enrolment_id` | `uuid` | **NO** | — |
| `course_id` | `uuid` | **NO** | — |
| `section_id` | `uuid` | **NO** | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `enrolment_selections_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_enrolment_selections` | FOREIGN KEY | `FOREIGN KEY (tenant_id, enrolment_id) REFERENCES stu.enrolments(tenant_id, id)` |
| `pk_enrolment_selections` | PRIMARY KEY | `PRIMARY KEY (tenant_id, enrolment_id, course_id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_enrolment_selections` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_enrolment_selections` | `CREATE UNIQUE INDEX pk_enrolment_selections ON stu.enrolment_selections USING btree (tenant_id, enrolment_id, course_id)` |

---

### Table: `stu.enrolment_status_history`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `enrolment_id` | `uuid` | **NO** | — |
| `status` | `character varying(32)` | **NO** | — |
| `action` | `character varying(64)` | **NO** | — |
| `actor_role` | `character varying(64)` | **NO** | — |
| `occurred_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `enrolment_status_history_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_enrolment_status_history_enrolment` | FOREIGN KEY | `FOREIGN KEY (tenant_id, enrolment_id) REFERENCES stu.enrolments(tenant_id, id)` |
| `pk_enrolment_status_history` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_enrolment_status_history` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_enrolment_status_history` | `CREATE UNIQUE INDEX pk_enrolment_status_history ON stu.enrolment_status_history USING btree (tenant_id, id)` |

---

### Table: `stu.enrolments`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `application_id` | `uuid` | **NO** | — |
| `student_id` | `uuid` | **NO** | — |
| `program_id` | `uuid` | **NO** | — |
| `term_id` | `uuid` | **NO** | — |
| `status` | `character varying(32)` | **NO** | — |
| `total_credits` | `integer` | **NO** | `0` |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `enrolments_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `pk_enrolments` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_enrolments` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_enrolments` | `CREATE UNIQUE INDEX pk_enrolments ON stu.enrolments USING btree (tenant_id, id)` |
| `uq_enrolments_application` | `CREATE UNIQUE INDEX uq_enrolments_application ON stu.enrolments USING btree (tenant_id, application_id)` |
| `uq_enrolments_student_term_active` | `CREATE UNIQUE INDEX uq_enrolments_student_term_active ON stu.enrolments USING btree (tenant_id, student_id, term_id) WHERE ((status)::text <> 'REJECTED'::text)` |

---

### Table: `stu.graduation_records`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `student_id` | `uuid` | **NO** | — |
| `program_id` | `uuid` | **NO** | — |
| `graduated_on` | `date` | **NO** | — |
| `notes` | `text` | YES | — |
| `recorded_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_graduation_student` | FOREIGN KEY | `FOREIGN KEY (tenant_id, student_id) REFERENCES stu.lifecycle_status(tenant_id, student_id)` |
| `graduation_records_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `pk_graduation_records` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |
| `uq_graduation_student_program` | UNIQUE | `UNIQUE (tenant_id, student_id, program_id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_graduation_records` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_graduation_records` | `CREATE UNIQUE INDEX pk_graduation_records ON stu.graduation_records USING btree (tenant_id, id)` |
| `uq_graduation_student_program` | `CREATE UNIQUE INDEX uq_graduation_student_program ON stu.graduation_records USING btree (tenant_id, student_id, program_id)` |

---

### Table: `stu.guardianships`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `student_id` | `uuid` | **NO** | — |
| `guardian_person_id` | `uuid` | **NO** | — |
| `relationship` | `text` | **NO** | — |
| `is_primary` | `boolean` | **NO** | `false` |
| `can_view_academics` | `boolean` | **NO** | `true` |
| `can_view_fees` | `boolean` | **NO** | `true` |
| `can_pay_fees` | `boolean` | **NO** | `false` |
| `valid_from` | `date` | YES | — |
| `valid_to` | `date` | YES | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_guardianships_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'ENDED'::text])))` |
| `fk_guardianships_guardian_person_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, guardian_person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT` |
| `fk_guardianships_student_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE CASCADE` |
| `fk_guardianships_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `guardianships_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_guardianships_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `guardianships_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd06.manage'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd06.manage'::text, true) AS students_in_scope))))` |
| `guardianships_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd06.write'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd06.write'::text, true) AS students_in_scope))))` |
| `guardianships_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd06.view.all'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd06.view.all'::text, false) AS students_in_scope)) OR (guardian_person_id = ( SELECT core.current_person_id() AS current_person_id))))` |
| `guardianships_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd06.write'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd06.write'::text, true) AS students_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `guardianships_pkey` | `CREATE UNIQUE INDEX guardianships_pkey ON stu.guardianships USING btree (id)` |
| `ix_guardianships_student_id` | `CREATE INDEX ix_guardianships_student_id ON stu.guardianships USING btree (tenant_id, student_id)` |
| `ix_guardianships_tenant_id_guardian_person_id` | `CREATE INDEX ix_guardianships_tenant_id_guardian_person_id ON stu.guardianships USING btree (tenant_id, guardian_person_id)` |
| `uq_guardianships_tenant_id` | `CREATE UNIQUE INDEX uq_guardianships_tenant_id ON stu.guardianships USING btree (tenant_id, id)` |
| `uq_guardianships_tenant_id_student_id_guardian_person_id` | `CREATE UNIQUE INDEX uq_guardianships_tenant_id_student_id_guardian_person_id ON stu.guardianships USING btree (tenant_id, student_id, guardian_person_id) WHERE (deleted_at IS NULL)` |

---

### Table: `stu.lifecycle_history`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `student_id` | `uuid` | **NO** | — |
| `status` | `character varying(32)` | **NO** | — |
| `action` | `character varying(64)` | **NO** | — |
| `actor_role` | `character varying(64)` | **NO** | — |
| `occurred_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_lifecycle_history_student` | FOREIGN KEY | `FOREIGN KEY (tenant_id, student_id) REFERENCES stu.lifecycle_status(tenant_id, student_id)` |
| `lifecycle_history_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `pk_lifecycle_history` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_lifecycle_history` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_lifecycle_history` | `CREATE UNIQUE INDEX pk_lifecycle_history ON stu.lifecycle_history USING btree (tenant_id, id)` |

---

### Table: `stu.lifecycle_requests`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `student_id` | `uuid` | **NO** | — |
| `request_type` | `character varying(32)` | **NO** | — |
| `status` | `character varying(32)` | **NO** | `'PENDING'::character varying` |
| `reason` | `text` | YES | — |
| `decided_by` | `uuid` | YES | — |
| `decided_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `effective_date` | `date` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_lifecycle_request_status` | CHECK | `CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))` |
| `ck_lifecycle_request_type` | CHECK | `CHECK (((request_type)::text = ANY ((ARRAY['LEAVE'::character varying, 'REINSTATE'::character varying, 'TRANSFER'::character varying, 'WITHDRAWAL'::character varying])::text[])))` |
| `fk_lifecycle_requests_student` | FOREIGN KEY | `FOREIGN KEY (tenant_id, student_id) REFERENCES stu.lifecycle_status(tenant_id, student_id)` |
| `lifecycle_requests_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `pk_lifecycle_requests` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_lifecycle_requests` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_lifecycle_requests` | `CREATE UNIQUE INDEX pk_lifecycle_requests ON stu.lifecycle_requests USING btree (tenant_id, id)` |

---

### Table: `stu.lifecycle_status`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `student_id` | `uuid` | **NO** | — |
| `status` | `character varying(32)` | **NO** | — |
| `effective_from` | `timestamp with time zone` | **NO** | `clock_timestamp()` |
| `updated_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_lifecycle_status` | CHECK | `CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'ON_LEAVE'::character varying, 'TRANSFERRED'::character varying, 'WITHDRAWN'::character varying, 'GRADUATED'::character varying, 'SUSPENDED'::character varying])::text[])))` |
| `lifecycle_status_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `pk_lifecycle_status` | PRIMARY KEY | `PRIMARY KEY (tenant_id, student_id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_lifecycle_status` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_lifecycle_status` | `CREATE UNIQUE INDEX pk_lifecycle_status ON stu.lifecycle_status USING btree (tenant_id, student_id)` |

---

### Table: `stu.lifecycle_transition_rules`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `from_status` | `character varying(32)` | **NO** | — |
| `to_status` | `character varying(32)` | **NO** | — |
| `requires_approval` | `boolean` | **NO** | `true` |
| `active` | `boolean` | **NO** | `true` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `lifecycle_transition_rules_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `pk_lifecycle_transition_rules` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |
| `uq_lifecycle_transition_rules` | UNIQUE | `UNIQUE (tenant_id, from_status, to_status)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_lifecycle_transition_rules` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_lifecycle_transition_rules` | `CREATE UNIQUE INDEX pk_lifecycle_transition_rules ON stu.lifecycle_transition_rules USING btree (tenant_id, id)` |
| `uq_lifecycle_transition_rules` | `CREATE UNIQUE INDEX uq_lifecycle_transition_rules ON stu.lifecycle_transition_rules USING btree (tenant_id, from_status, to_status)` |

---

### Table: `stu.registration_windows`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `term_id` | `uuid` | **NO** | — |
| `opens_at` | `timestamp with time zone` | **NO** | — |
| `closes_at` | `timestamp with time zone` | **NO** | — |
| `min_credits` | `integer` | **NO** | — |
| `max_credits` | `integer` | **NO** | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_registration_credits` | CHECK | `CHECK (((min_credits >= 0) AND (max_credits >= min_credits)))` |
| `ck_registration_window_order` | CHECK | `CHECK ((closes_at > opens_at))` |
| `fk_registration_windows_term` | FOREIGN KEY | `FOREIGN KEY (tenant_id, term_id) REFERENCES stu.academic_terms(tenant_id, id)` |
| `registration_windows_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `pk_registration_windows` | PRIMARY KEY | `PRIMARY KEY (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_registration_windows` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_registration_windows` | `CREATE UNIQUE INDEX pk_registration_windows ON stu.registration_windows USING btree (tenant_id, id)` |

---

### Table: `stu.section_waitlist`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `section_id` | `uuid` | **NO** | — |
| `student_id` | `uuid` | **NO** | — |
| `position` | `integer` | **NO** | — |
| `joined_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_section_waitlist_section` | FOREIGN KEY | `FOREIGN KEY (tenant_id, section_id) REFERENCES stu.enrolment_sections(tenant_id, id)` |
| `section_waitlist_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `pk_section_waitlist` | PRIMARY KEY | `PRIMARY KEY (tenant_id, section_id, student_id)` |
| `uq_section_waitlist_position` | UNIQUE | `UNIQUE (tenant_id, section_id, "position")` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_section_waitlist` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_section_waitlist` | `CREATE UNIQUE INDEX pk_section_waitlist ON stu.section_waitlist USING btree (tenant_id, section_id, student_id)` |
| `uq_section_waitlist_position` | `CREATE UNIQUE INDEX uq_section_waitlist_position ON stu.section_waitlist USING btree (tenant_id, section_id, "position")` |

---

### Table: `stu.student_cleared_courses`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `tenant_id` | `uuid` | **NO** | — |
| `student_id` | `uuid` | **NO** | — |
| `course_id` | `uuid` | **NO** | — |
| `cleared_at` | `timestamp with time zone` | **NO** | `clock_timestamp()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `student_cleared_courses_tenant_id_fkey` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `pk_student_cleared_courses` | PRIMARY KEY | `PRIMARY KEY (tenant_id, student_id, course_id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_isolation_student_cleared_courses` | `ALL` | `{authenticated}` | `(tenant_id = (((current_setting('request.jwt.claims'::text, true))::jsonb ->> 'tenant_id'::text))::uuid)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `pk_student_cleared_courses` | `CREATE UNIQUE INDEX pk_student_cleared_courses ON stu.student_cleared_courses USING btree (tenant_id, student_id, course_id)` |

---

### Table: `stu.students`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `person_id` | `uuid` | **NO** | — |
| `college_id` | `uuid` | **NO** | — |
| `program_id` | `uuid` | YES | — |
| `enrollment_no` | `text` | **NO** | — |
| `admission_no` | `text` | YES | — |
| `admission_date` | `date` | YES | — |
| `admission_academic_year_id` | `uuid` | YES | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_students_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'ALUMNI'::text, 'SUSPENDED'::text, 'WITHDRAWN'::text, 'DETAINED'::text, 'GRADUATED'::text])))` |
| `fk_students_admission_academic_year_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, admission_academic_year_id) REFERENCES core.academic_years(tenant_id, id) ON DELETE RESTRICT` |
| `fk_students_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_students_person_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT` |
| `fk_students_program_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, program_id) REFERENCES acd.programs(tenant_id, id) ON DELETE RESTRICT` |
| `fk_students_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `students_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_students_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `students_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd06.manage'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd06.manage'::text)) AS unnest)) OR ((program_id IN ( SELECT core.programs_in_scope('acd06.manage'::text, true) AS programs_in_scope)) OR ((NOT true) AND (program_id IS NULL) AND ( SELECT iam.has_permission('acd06.manage'::text) AS has_permission)))))` |
| `students_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd06.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd06.write'::text)) AS unnest)) OR ((program_id IN ( SELECT core.programs_in_scope('acd06.write'::text, true) AS programs_in_scope)) OR ((NOT true) AND (program_id IS NULL) AND ( SELECT iam.has_permission('acd06.write'::text) AS has_permission)))))` |
| `students_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd06.view.all'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd06.view.all'::text)) AS unnest)) OR ((program_id IN ( SELECT core.programs_in_scope('acd06.view.all'::text, false) AS programs_in_scope)) OR ((NOT false) AND (program_id IS NULL) AND ( SELECT iam.has_permission('acd06.view.all'::text) AS has_permission))) OR (( SELECT iam.has_permission('acd06.view.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd06.view.own'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd06.view.own'::text)) AS unnest)) OR ((program_id IN ( SELECT core.programs_in_scope('acd06.view.own'::text, false) AS programs_in_scope)) OR ((NOT false) AND (program_id IS NULL) AND ( SELECT iam.has_permission('acd06.view.own'::text) AS has_permission)))) AND (id IN ( SELECT core.faculty_student_ids() AS faculty_student_ids))) OR (id IN ( SELECT core.my_student_ids() AS my_student_ids))))` |
| `students_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd06.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd06.write'::text)) AS unnest)) OR ((program_id IN ( SELECT core.programs_in_scope('acd06.write'::text, true) AS programs_in_scope)) OR ((NOT true) AND (program_id IS NULL) AND ( SELECT iam.has_permission('acd06.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_students_admission_academic_year_id` | `CREATE INDEX ix_students_admission_academic_year_id ON stu.students USING btree (tenant_id, admission_academic_year_id)` |
| `ix_students_person_id` | `CREATE INDEX ix_students_person_id ON stu.students USING btree (tenant_id, person_id)` |
| `ix_students_program_id` | `CREATE INDEX ix_students_program_id ON stu.students USING btree (tenant_id, program_id)` |
| `ix_students_tenant_id_college_id_program_id_status` | `CREATE INDEX ix_students_tenant_id_college_id_program_id_status ON stu.students USING btree (tenant_id, college_id, program_id, status)` |
| `students_pkey` | `CREATE UNIQUE INDEX students_pkey ON stu.students USING btree (id)` |
| `uq_students_tenant_id` | `CREATE UNIQUE INDEX uq_students_tenant_id ON stu.students USING btree (tenant_id, id)` |
| `uq_students_tenant_id_enrollment_no` | `CREATE UNIQUE INDEX uq_students_tenant_id_enrollment_no ON stu.students USING btree (tenant_id, enrollment_no) WHERE (deleted_at IS NULL)` |
| `uq_students_tenant_id_person_id_college_id` | `CREATE UNIQUE INDEX uq_students_tenant_id_person_id_college_id ON stu.students USING btree (tenant_id, person_id, college_id) WHERE (deleted_at IS NULL)` |

---

## Schema: `acd`

### Table: `acd.assessment_components`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `structure_id` | `uuid` | **NO** | — |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `component_type` | `text` | **NO** | — |
| `is_external` | `boolean` | **NO** | `false` |
| `max_marks` | `numeric` | **NO** | — |
| `min_marks` | `numeric` | YES | — |
| `weightage_pct` | `numeric` | YES | — |
| `sequence` | `smallint` | **NO** | `1` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_assessment_components_component_type` | CHECK | `CHECK ((component_type = ANY (ARRAY['INTERNAL_TEST'::text, 'ASSIGNMENT'::text, 'LAB'::text, 'VIVA'::text, 'PROJECT'::text, 'ATTENDANCE'::text, 'END_SEMESTER'::text, 'OTHER'::text])))` |
| `ck_assessment_components_max_marks` | CHECK | `CHECK ((max_marks >= (0)::numeric))` |
| `ck_assessment_components_min_marks` | CHECK | `CHECK ((min_marks >= (0)::numeric))` |
| `ck_assessment_components_weightage_pct` | CHECK | `CHECK (((weightage_pct >= (0)::numeric) AND (weightage_pct <= (100)::numeric)))` |
| `fk_assessment_components_structure_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, structure_id) REFERENCES acd.assessment_structures(tenant_id, id) ON DELETE CASCADE` |
| `fk_assessment_components_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `assessment_components_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_assessment_components_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `assessment_components_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.manage'::text) AS perm_all) OR (structure_id IN ( SELECT iam.sc_acd_assessment_structures('acd09.manage'::text, true) AS sc_acd_assessment_structures))))` |
| `assessment_components_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR (structure_id IN ( SELECT iam.sc_acd_assessment_structures('acd09.write'::text, true) AS sc_acd_assessment_structures))))` |
| `assessment_components_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.view'::text) AS perm_all) OR (structure_id IN ( SELECT iam.sc_acd_assessment_structures('acd09.view'::text, false) AS sc_acd_assessment_structures))))` |
| `assessment_components_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR (structure_id IN ( SELECT iam.sc_acd_assessment_structures('acd09.write'::text, true) AS sc_acd_assessment_structures))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `assessment_components_pkey` | `CREATE UNIQUE INDEX assessment_components_pkey ON acd.assessment_components USING btree (id)` |
| `ix_assessment_components_structure_id` | `CREATE INDEX ix_assessment_components_structure_id ON acd.assessment_components USING btree (tenant_id, structure_id)` |
| `uq_assessment_components_tenant_id` | `CREATE UNIQUE INDEX uq_assessment_components_tenant_id ON acd.assessment_components USING btree (tenant_id, id)` |
| `uq_assessment_components_tenant_id_structure_id_code` | `CREATE UNIQUE INDEX uq_assessment_components_tenant_id_structure_id_code ON acd.assessment_components USING btree (tenant_id, structure_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.assessment_structures`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `subject_id` | `uuid` | YES | — |
| `curriculum_version_id` | `uuid` | YES | — |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `total_marks` | `numeric` | **NO** | — |
| `pass_marks` | `numeric` | YES | — |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_assessment_structures_pass_marks` | CHECK | `CHECK ((pass_marks >= (0)::numeric))` |
| `ck_assessment_structures_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])))` |
| `ck_assessment_structures_total_marks` | CHECK | `CHECK ((total_marks >= (0)::numeric))` |
| `fk_assessment_structures_curriculum_version_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, curriculum_version_id) REFERENCES acd.curriculum_versions(tenant_id, id) ON DELETE RESTRICT` |
| `fk_assessment_structures_subject_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT` |
| `fk_assessment_structures_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `assessment_structures_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_assessment_structures_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `assessment_structures_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.manage'::text) AS perm_all) OR ((subject_id IN ( SELECT core.subjects_in_scope('acd09.manage'::text, true) AS subjects_in_scope)) OR ((NOT true) AND (subject_id IS NULL) AND ( SELECT iam.has_permission('acd09.manage'::text) AS has_permission)))))` |
| `assessment_structures_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR ((subject_id IN ( SELECT core.subjects_in_scope('acd09.write'::text, true) AS subjects_in_scope)) OR ((NOT true) AND (subject_id IS NULL) AND ( SELECT iam.has_permission('acd09.write'::text) AS has_permission)))))` |
| `assessment_structures_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.view'::text) AS perm_all) OR ((subject_id IN ( SELECT core.subjects_in_scope('acd09.view'::text, false) AS subjects_in_scope)) OR ((NOT false) AND (subject_id IS NULL) AND ( SELECT iam.has_permission('acd09.view'::text) AS has_permission)))))` |
| `assessment_structures_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR ((subject_id IN ( SELECT core.subjects_in_scope('acd09.write'::text, true) AS subjects_in_scope)) OR ((NOT true) AND (subject_id IS NULL) AND ( SELECT iam.has_permission('acd09.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `assessment_structures_pkey` | `CREATE UNIQUE INDEX assessment_structures_pkey ON acd.assessment_structures USING btree (id)` |
| `ix_assessment_structures_curriculum_version_id` | `CREATE INDEX ix_assessment_structures_curriculum_version_id ON acd.assessment_structures USING btree (tenant_id, curriculum_version_id)` |
| `ix_assessment_structures_subject_id` | `CREATE INDEX ix_assessment_structures_subject_id ON acd.assessment_structures USING btree (tenant_id, subject_id)` |
| `uq_assessment_structures_tenant_id` | `CREATE UNIQUE INDEX uq_assessment_structures_tenant_id ON acd.assessment_structures USING btree (tenant_id, id)` |
| `uq_assessment_structures_tenant_id_code` | `CREATE UNIQUE INDEX uq_assessment_structures_tenant_id_code ON acd.assessment_structures USING btree (tenant_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.attendance_corrections`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `record_id` | `uuid` | **NO** | — |
| `old_status` | `text` | **NO** | — |
| `new_status` | `text` | **NO** | — |
| `reason` | `text` | **NO** | — |
| `requested_by` | `uuid` | YES | — |
| `approved_by` | `uuid` | YES | — |
| `workflow_instance_id` | `uuid` | YES | — |
| `status` | `text` | **NO** | `'PENDING'::text` |
| `decided_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_attendance_corrections_new_status` | CHECK | `CHECK ((new_status = ANY (ARRAY['PRESENT'::text, 'ABSENT'::text, 'LATE'::text, 'ON_DUTY'::text, 'LEAVE'::text, 'EXCUSED'::text])))` |
| `ck_attendance_corrections_status` | CHECK | `CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text])))` |
| `fk_attendance_corrections_approved_by` | FOREIGN KEY | `FOREIGN KEY (tenant_id, approved_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_attendance_corrections_record_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, record_id) REFERENCES acd.attendance_records(tenant_id, id) ON DELETE RESTRICT` |
| `fk_attendance_corrections_requested_by` | FOREIGN KEY | `FOREIGN KEY (tenant_id, requested_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_attendance_corrections_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_attendance_corrections_workflow_instance_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT` |
| `attendance_corrections_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_attendance_corrections_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_b_transition` | BEFORE | INSERT | Standard |
| `trg_b_transition` | BEFORE | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |
| `trg_z_transition_log` | AFTER | INSERT | Standard |
| `trg_z_transition_log` | AFTER | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `attendance_corrections_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd08.manage'::text) AS perm_all) OR (record_id IN ( SELECT iam.sc_acd_attendance_records('acd08.manage'::text, true) AS sc_acd_attendance_records)) OR (( SELECT iam.has_permission('acd08.manage.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd08.manage.own'::text) AS perm_all) OR (record_id IN ( SELECT iam.sc_acd_attendance_records('acd08.manage.own'::text, true) AS sc_acd_attendance_records))) AND (record_id IN ( SELECT core.own_acd_attendance_records() AS own_acd_attendance_records)))))` |
| `attendance_corrections_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd08.write'::text) AS perm_all) OR (record_id IN ( SELECT iam.sc_acd_attendance_records('acd08.write'::text, true) AS sc_acd_attendance_records)) OR (( SELECT iam.has_permission('acd08.write.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd08.write.own'::text) AS perm_all) OR (record_id IN ( SELECT iam.sc_acd_attendance_records('acd08.write.own'::text, true) AS sc_acd_attendance_records))) AND (record_id IN ( SELECT core.own_acd_attendance_records() AS own_acd_attendance_records)))))` |
| `attendance_corrections_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd08.view'::text) AS perm_all) OR (record_id IN ( SELECT iam.sc_acd_attendance_records('acd08.view'::text, false) AS sc_acd_attendance_records)) OR (( SELECT iam.has_permission('acd08.view.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd08.view.own'::text) AS perm_all) OR (record_id IN ( SELECT iam.sc_acd_attendance_records('acd08.view.own'::text, false) AS sc_acd_attendance_records))) AND (record_id IN ( SELECT core.own_acd_attendance_records() AS own_acd_attendance_records)))))` |
| `attendance_corrections_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd08.write'::text) AS perm_all) OR (record_id IN ( SELECT iam.sc_acd_attendance_records('acd08.write'::text, true) AS sc_acd_attendance_records)) OR (( SELECT iam.has_permission('acd08.write.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd08.write.own'::text) AS perm_all) OR (record_id IN ( SELECT iam.sc_acd_attendance_records('acd08.write.own'::text, true) AS sc_acd_attendance_records))) AND (record_id IN ( SELECT core.own_acd_attendance_records() AS own_acd_attendance_records))) OR (( SELECT iam.perm_all('acd08.review'::text) AS perm_all) OR (record_id IN ( SELECT iam.sc_acd_attendance_records('acd08.review'::text, true) AS sc_acd_attendance_records))) OR (( SELECT iam.perm_all('acd08.manage'::text) AS perm_all) OR (record_id IN ( SELECT iam.sc_acd_attendance_records('acd08.manage'::text, true) AS sc_acd_attendance_records)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `attendance_corrections_pkey` | `CREATE UNIQUE INDEX attendance_corrections_pkey ON acd.attendance_corrections USING btree (id)` |
| `ix_attendance_corrections_approved_by` | `CREATE INDEX ix_attendance_corrections_approved_by ON acd.attendance_corrections USING btree (tenant_id, approved_by)` |
| `ix_attendance_corrections_record_id` | `CREATE INDEX ix_attendance_corrections_record_id ON acd.attendance_corrections USING btree (tenant_id, record_id)` |
| `ix_attendance_corrections_requested_by` | `CREATE INDEX ix_attendance_corrections_requested_by ON acd.attendance_corrections USING btree (tenant_id, requested_by)` |
| `ix_attendance_corrections_workflow_instance_id` | `CREATE INDEX ix_attendance_corrections_workflow_instance_id ON acd.attendance_corrections USING btree (tenant_id, workflow_instance_id)` |
| `uq_attendance_corrections_tenant_id` | `CREATE UNIQUE INDEX uq_attendance_corrections_tenant_id ON acd.attendance_corrections USING btree (tenant_id, id)` |

---

### Table: `acd.attendance_records`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `session_id` | `uuid` | **NO** | — |
| `student_id` | `uuid` | **NO** | — |
| `status` | `text` | **NO** | — |
| `method` | `text` | **NO** | `'MANUAL'::text` |
| `marked_by` | `uuid` | YES | — |
| `marked_at` | `timestamp with time zone` | **NO** | `now()` |
| `remarks` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_attendance_records_method` | CHECK | `CHECK ((method = ANY (ARRAY['MANUAL'::text, 'QR'::text, 'BIOMETRIC'::text, 'GEOFENCE'::text, 'IMPORT'::text])))` |
| `ck_attendance_records_status` | CHECK | `CHECK ((status = ANY (ARRAY['PRESENT'::text, 'ABSENT'::text, 'LATE'::text, 'ON_DUTY'::text, 'LEAVE'::text, 'EXCUSED'::text])))` |
| `fk_attendance_records_marked_by` | FOREIGN KEY | `FOREIGN KEY (tenant_id, marked_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_attendance_records_session_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, session_id) REFERENCES acd.attendance_sessions(tenant_id, id) ON DELETE CASCADE` |
| `fk_attendance_records_student_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT` |
| `fk_attendance_records_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `attendance_records_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_attendance_records_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `attendance_records_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd08.manage'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd08.manage'::text, true) AS students_in_scope)) OR (( SELECT iam.has_permission('acd08.manage.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd08.manage.own'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd08.manage.own'::text, true) AS students_in_scope))) AND (session_id IN ( SELECT core.own_acd_attendance_sessions() AS own_acd_attendance_sessions)))))` |
| `attendance_records_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd08.write'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd08.write'::text, true) AS students_in_scope)) OR (( SELECT iam.has_permission('acd08.write.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd08.write.own'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd08.write.own'::text, true) AS students_in_scope))) AND (session_id IN ( SELECT core.own_acd_attendance_sessions() AS own_acd_attendance_sessions)))))` |
| `attendance_records_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd08.view.all'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd08.view.all'::text, false) AS students_in_scope)) OR (( SELECT iam.has_permission('acd08.view.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd08.view.own'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd08.view.own'::text, false) AS students_in_scope))) AND (session_id IN ( SELECT core.own_acd_attendance_sessions() AS own_acd_attendance_sessions))) OR (student_id IN ( SELECT core.my_student_ids() AS my_student_ids))))` |
| `attendance_records_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd08.write'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd08.write'::text, true) AS students_in_scope)) OR (( SELECT iam.has_permission('acd08.write.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd08.write.own'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd08.write.own'::text, true) AS students_in_scope))) AND (session_id IN ( SELECT core.own_acd_attendance_sessions() AS own_acd_attendance_sessions)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `attendance_records_pkey` | `CREATE UNIQUE INDEX attendance_records_pkey ON acd.attendance_records USING btree (id)` |
| `ix_attendance_records_marked_by` | `CREATE INDEX ix_attendance_records_marked_by ON acd.attendance_records USING btree (tenant_id, marked_by)` |
| `ix_attendance_records_session_id` | `CREATE INDEX ix_attendance_records_session_id ON acd.attendance_records USING btree (tenant_id, session_id)` |
| `ix_attendance_records_tenant_id_student_id_status` | `CREATE INDEX ix_attendance_records_tenant_id_student_id_status ON acd.attendance_records USING btree (tenant_id, student_id, status)` |
| `uq_attendance_records_tenant_id` | `CREATE UNIQUE INDEX uq_attendance_records_tenant_id ON acd.attendance_records USING btree (tenant_id, id)` |
| `uq_attendance_records_tenant_id_session_id_student_id` | `CREATE UNIQUE INDEX uq_attendance_records_tenant_id_session_id_student_id ON acd.attendance_records USING btree (tenant_id, session_id, student_id) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.attendance_sessions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `section_id` | `uuid` | **NO** | — |
| `timetable_entry_id` | `uuid` | YES | — |
| `employee_id` | `uuid` | YES | — |
| `session_date` | `date` | **NO** | — |
| `start_time` | `time without time zone` | YES | — |
| `end_time` | `time without time zone` | YES | — |
| `status` | `text` | **NO** | `'SCHEDULED'::text` |
| `locked_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_attendance_sessions_status` | CHECK | `CHECK ((status = ANY (ARRAY['SCHEDULED'::text, 'OPEN'::text, 'CLOSED'::text, 'CANCELLED'::text])))` |
| `fk_attendance_sessions_employee_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT` |
| `fk_attendance_sessions_section_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, section_id) REFERENCES acd.course_sections(tenant_id, id) ON DELETE RESTRICT` |
| `fk_attendance_sessions_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_attendance_sessions_timetable_entry_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, timetable_entry_id) REFERENCES acd.timetable_entries(tenant_id, id) ON DELETE RESTRICT` |
| `attendance_sessions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_attendance_sessions_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `attendance_sessions_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd08.manage'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd08.manage'::text, true) AS sections_in_scope)) OR ((employee_id IN ( SELECT core.employees_in_scope('acd08.manage'::text, true) AS employees_in_scope)) OR ((NOT true) AND (employee_id IS NULL) AND ( SELECT iam.has_permission('acd08.manage'::text) AS has_permission))) OR (( SELECT iam.has_permission('acd08.manage.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd08.manage.own'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd08.manage.own'::text, true) AS sections_in_scope)) OR ((employee_id IN ( SELECT core.employees_in_scope('acd08.manage.own'::text, true) AS employees_in_scope)) OR ((NOT true) AND (employee_id IS NULL) AND ( SELECT iam.has_permission('acd08.manage.own'::text) AS has_permission)))) AND (employee_id = ( SELECT core.current_employee_id() AS current_employee_id)))))` |
| `attendance_sessions_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd08.write'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd08.write'::text, true) AS sections_in_scope)) OR ((employee_id IN ( SELECT core.employees_in_scope('acd08.write'::text, true) AS employees_in_scope)) OR ((NOT true) AND (employee_id IS NULL) AND ( SELECT iam.has_permission('acd08.write'::text) AS has_permission))) OR (( SELECT iam.has_permission('acd08.write.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd08.write.own'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd08.write.own'::text, true) AS sections_in_scope)) OR ((employee_id IN ( SELECT core.employees_in_scope('acd08.write.own'::text, true) AS employees_in_scope)) OR ((NOT true) AND (employee_id IS NULL) AND ( SELECT iam.has_permission('acd08.write.own'::text) AS has_permission)))) AND (employee_id = ( SELECT core.current_employee_id() AS current_employee_id)))))` |
| `attendance_sessions_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd08.view.all'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd08.view.all'::text, false) AS sections_in_scope)) OR ((employee_id IN ( SELECT core.employees_in_scope('acd08.view.all'::text, false) AS employees_in_scope)) OR ((NOT false) AND (employee_id IS NULL) AND ( SELECT iam.has_permission('acd08.view.all'::text) AS has_permission))) OR (( SELECT iam.has_permission('acd08.view.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd08.view.own'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd08.view.own'::text, false) AS sections_in_scope)) OR ((employee_id IN ( SELECT core.employees_in_scope('acd08.view.own'::text, false) AS employees_in_scope)) OR ((NOT false) AND (employee_id IS NULL) AND ( SELECT iam.has_permission('acd08.view.own'::text) AS has_permission)))) AND (employee_id = ( SELECT core.current_employee_id() AS current_employee_id))) OR (employee_id = ( SELECT core.current_employee_id() AS current_employee_id))))` |
| `attendance_sessions_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd08.write'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd08.write'::text, true) AS sections_in_scope)) OR ((employee_id IN ( SELECT core.employees_in_scope('acd08.write'::text, true) AS employees_in_scope)) OR ((NOT true) AND (employee_id IS NULL) AND ( SELECT iam.has_permission('acd08.write'::text) AS has_permission))) OR (( SELECT iam.has_permission('acd08.write.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd08.write.own'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd08.write.own'::text, true) AS sections_in_scope)) OR ((employee_id IN ( SELECT core.employees_in_scope('acd08.write.own'::text, true) AS employees_in_scope)) OR ((NOT true) AND (employee_id IS NULL) AND ( SELECT iam.has_permission('acd08.write.own'::text) AS has_permission)))) AND (employee_id = ( SELECT core.current_employee_id() AS current_employee_id)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `attendance_sessions_pkey` | `CREATE UNIQUE INDEX attendance_sessions_pkey ON acd.attendance_sessions USING btree (id)` |
| `ix_attendance_sessions_section_id` | `CREATE INDEX ix_attendance_sessions_section_id ON acd.attendance_sessions USING btree (tenant_id, section_id)` |
| `ix_attendance_sessions_tenant_id_employee_id_session_date` | `CREATE INDEX ix_attendance_sessions_tenant_id_employee_id_session_date ON acd.attendance_sessions USING btree (tenant_id, employee_id, session_date)` |
| `ix_attendance_sessions_tenant_id_session_date` | `CREATE INDEX ix_attendance_sessions_tenant_id_session_date ON acd.attendance_sessions USING btree (tenant_id, session_date)` |
| `ix_attendance_sessions_timetable_entry_id` | `CREATE INDEX ix_attendance_sessions_timetable_entry_id ON acd.attendance_sessions USING btree (tenant_id, timetable_entry_id)` |
| `uq_attendance_sessions_tenant_id` | `CREATE UNIQUE INDEX uq_attendance_sessions_tenant_id ON acd.attendance_sessions USING btree (tenant_id, id)` |
| `uq_attendance_sessions_tenant_id_section_id_sessio_f3a84572` | `CREATE UNIQUE INDEX uq_attendance_sessions_tenant_id_section_id_sessio_f3a84572 ON acd.attendance_sessions USING btree (tenant_id, section_id, session_date, start_time) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.batch_capacity_overrides`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `batch_id` | `uuid` | **NO** | — |
| `term_id` | `uuid` | YES | — |
| `capacity` | `integer` | **NO** | — |
| `reason` | `text` | YES | — |
| `approved_by` | `uuid` | YES | — |
| `valid_from` | `date` | YES | — |
| `valid_to` | `date` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_batch_capacity_overrides_capacity` | CHECK | `CHECK ((capacity >= 0))` |
| `fk_batch_capacity_overrides_approved_by` | FOREIGN KEY | `FOREIGN KEY (tenant_id, approved_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_batch_capacity_overrides_batch_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, batch_id) REFERENCES acd.batches(tenant_id, id) ON DELETE CASCADE` |
| `fk_batch_capacity_overrides_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_batch_capacity_overrides_term_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, term_id) REFERENCES acd.calendar_terms(tenant_id, id) ON DELETE RESTRICT` |
| `batch_capacity_overrides_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_batch_capacity_overrides_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `batch_capacity_overrides_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.manage'::text) AS perm_all) OR (batch_id IN ( SELECT iam.sc_acd_batches('acd07.manage'::text, true) AS sc_acd_batches))))` |
| `batch_capacity_overrides_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (batch_id IN ( SELECT iam.sc_acd_batches('acd07.write'::text, true) AS sc_acd_batches))))` |
| `batch_capacity_overrides_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.view'::text) AS perm_all) OR (batch_id IN ( SELECT iam.sc_acd_batches('acd07.view'::text, false) AS sc_acd_batches))))` |
| `batch_capacity_overrides_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (batch_id IN ( SELECT iam.sc_acd_batches('acd07.write'::text, true) AS sc_acd_batches))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `batch_capacity_overrides_pkey` | `CREATE UNIQUE INDEX batch_capacity_overrides_pkey ON acd.batch_capacity_overrides USING btree (id)` |
| `ix_batch_capacity_overrides_approved_by` | `CREATE INDEX ix_batch_capacity_overrides_approved_by ON acd.batch_capacity_overrides USING btree (tenant_id, approved_by)` |
| `ix_batch_capacity_overrides_batch_id` | `CREATE INDEX ix_batch_capacity_overrides_batch_id ON acd.batch_capacity_overrides USING btree (tenant_id, batch_id)` |
| `ix_batch_capacity_overrides_term_id` | `CREATE INDEX ix_batch_capacity_overrides_term_id ON acd.batch_capacity_overrides USING btree (tenant_id, term_id)` |
| `uq_batch_capacity_overrides_tenant_id` | `CREATE UNIQUE INDEX uq_batch_capacity_overrides_tenant_id ON acd.batch_capacity_overrides USING btree (tenant_id, id)` |

---

### Table: `acd.batch_students`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `batch_id` | `uuid` | **NO** | — |
| `student_id` | `uuid` | **NO** | — |
| `roll_no` | `text` | YES | — |
| `joined_on` | `date` | YES | — |
| `left_on` | `date` | YES | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_batch_students_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'TRANSFERRED'::text, 'DETAINED'::text, 'COMPLETED'::text, 'DROPPED'::text])))` |
| `fk_batch_students_batch_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, batch_id) REFERENCES acd.batches(tenant_id, id) ON DELETE CASCADE` |
| `fk_batch_students_student_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT` |
| `fk_batch_students_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `batch_students_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_batch_students_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `batch_students_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.manage'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd07.manage'::text, true) AS students_in_scope))))` |
| `batch_students_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd07.write'::text, true) AS students_in_scope))))` |
| `batch_students_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.view.all'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd07.view.all'::text, false) AS students_in_scope)) OR (student_id IN ( SELECT core.my_student_ids() AS my_student_ids))))` |
| `batch_students_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (student_id IN ( SELECT core.students_in_scope('acd07.write'::text, true) AS students_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `batch_students_pkey` | `CREATE UNIQUE INDEX batch_students_pkey ON acd.batch_students USING btree (id)` |
| `ix_batch_students_batch_id` | `CREATE INDEX ix_batch_students_batch_id ON acd.batch_students USING btree (tenant_id, batch_id)` |
| `ix_batch_students_tenant_id_student_id` | `CREATE INDEX ix_batch_students_tenant_id_student_id ON acd.batch_students USING btree (tenant_id, student_id)` |
| `uq_batch_students_tenant_id` | `CREATE UNIQUE INDEX uq_batch_students_tenant_id ON acd.batch_students USING btree (tenant_id, id)` |
| `uq_batch_students_tenant_id_batch_id_roll_no` | `CREATE UNIQUE INDEX uq_batch_students_tenant_id_batch_id_roll_no ON acd.batch_students USING btree (tenant_id, batch_id, roll_no) WHERE ((deleted_at IS NULL) AND (roll_no IS NOT NULL))` |
| `uq_batch_students_tenant_id_batch_id_student_id` | `CREATE UNIQUE INDEX uq_batch_students_tenant_id_batch_id_student_id ON acd.batch_students USING btree (tenant_id, batch_id, student_id) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.batches`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `college_id` | `uuid` | **NO** | — |
| `program_id` | `uuid` | **NO** | — |
| `curriculum_version_id` | `uuid` | YES | — |
| `admission_year_id` | `uuid` | YES | — |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `current_term_no` | `smallint` | YES | — |
| `capacity` | `integer` | YES | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_batches_capacity` | CHECK | `CHECK ((capacity >= 0))` |
| `ck_batches_status` | CHECK | `CHECK ((status = ANY (ARRAY['PLANNED'::text, 'ACTIVE'::text, 'GRADUATED'::text, 'CLOSED'::text])))` |
| `fk_batches_admission_year_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, admission_year_id) REFERENCES core.academic_years(tenant_id, id) ON DELETE RESTRICT` |
| `fk_batches_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_batches_curriculum_version_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, curriculum_version_id) REFERENCES acd.curriculum_versions(tenant_id, id) ON DELETE RESTRICT` |
| `fk_batches_program_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, program_id) REFERENCES acd.programs(tenant_id, id) ON DELETE RESTRICT` |
| `fk_batches_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `batches_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_batches_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `batches_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.manage'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd07.manage'::text)) AS unnest)) OR (program_id IN ( SELECT core.programs_in_scope('acd07.manage'::text, true) AS programs_in_scope))))` |
| `batches_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd07.write'::text)) AS unnest)) OR (program_id IN ( SELECT core.programs_in_scope('acd07.write'::text, true) AS programs_in_scope))))` |
| `batches_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.view'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd07.view'::text)) AS unnest)) OR (program_id IN ( SELECT core.programs_in_scope('acd07.view'::text, false) AS programs_in_scope)) OR (id IN ( SELECT batch_students.batch_id
   FROM acd.batch_students
  WHERE (batch_students.student_id IN ( SELECT core.my_student_ids() AS my_student_ids))))))` |
| `batches_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd07.write'::text)) AS unnest)) OR (program_id IN ( SELECT core.programs_in_scope('acd07.write'::text, true) AS programs_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `batches_pkey` | `CREATE UNIQUE INDEX batches_pkey ON acd.batches USING btree (id)` |
| `ix_batches_admission_year_id` | `CREATE INDEX ix_batches_admission_year_id ON acd.batches USING btree (tenant_id, admission_year_id)` |
| `ix_batches_college_id` | `CREATE INDEX ix_batches_college_id ON acd.batches USING btree (tenant_id, college_id)` |
| `ix_batches_curriculum_version_id` | `CREATE INDEX ix_batches_curriculum_version_id ON acd.batches USING btree (tenant_id, curriculum_version_id)` |
| `ix_batches_tenant_id_program_id_status` | `CREATE INDEX ix_batches_tenant_id_program_id_status ON acd.batches USING btree (tenant_id, program_id, status)` |
| `uq_batches_tenant_id` | `CREATE UNIQUE INDEX uq_batches_tenant_id ON acd.batches USING btree (tenant_id, id)` |
| `uq_batches_tenant_id_college_id_code` | `CREATE UNIQUE INDEX uq_batches_tenant_id_college_id_code ON acd.batches USING btree (tenant_id, college_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.calendar_terms`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `calendar_id` | `uuid` | **NO** | — |
| `term_no` | `smallint` | **NO** | — |
| `name` | `text` | **NO** | — |
| `term_type` | `text` | **NO** | `'SEMESTER'::text` |
| `start_date` | `date` | **NO** | — |
| `end_date` | `date` | **NO** | — |
| `status` | `text` | **NO** | `'PLANNED'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_calendar_terms_status` | CHECK | `CHECK ((status = ANY (ARRAY['PLANNED'::text, 'ACTIVE'::text, 'CLOSED'::text])))` |
| `ck_calendar_terms_term_no` | CHECK | `CHECK ((term_no > 0))` |
| `ck_calendar_terms_term_type` | CHECK | `CHECK ((term_type = ANY (ARRAY['SEMESTER'::text, 'TRIMESTER'::text, 'QUARTER'::text, 'SUMMER'::text, 'WINTER'::text])))` |
| `ck_calendar_terms_x1` | CHECK | `CHECK ((end_date > start_date))` |
| `fk_calendar_terms_calendar_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, calendar_id) REFERENCES core.calendars(tenant_id, id) ON DELETE CASCADE` |
| `fk_calendar_terms_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `calendar_terms_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_calendar_terms_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `calendar_terms_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.manage'::text) AS perm_all) OR (calendar_id IN ( SELECT iam.sc_core_calendars('acd09.manage'::text, true) AS sc_core_calendars))))` |
| `calendar_terms_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR (calendar_id IN ( SELECT iam.sc_core_calendars('acd09.write'::text, true) AS sc_core_calendars))))` |
| `calendar_terms_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.view'::text) AS perm_all) OR (calendar_id IN ( SELECT iam.sc_core_calendars('acd09.view'::text, false) AS sc_core_calendars))))` |
| `calendar_terms_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR (calendar_id IN ( SELECT iam.sc_core_calendars('acd09.write'::text, true) AS sc_core_calendars))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `calendar_terms_pkey` | `CREATE UNIQUE INDEX calendar_terms_pkey ON acd.calendar_terms USING btree (id)` |
| `ix_calendar_terms_calendar_id` | `CREATE INDEX ix_calendar_terms_calendar_id ON acd.calendar_terms USING btree (tenant_id, calendar_id)` |
| `uq_calendar_terms_tenant_id` | `CREATE UNIQUE INDEX uq_calendar_terms_tenant_id ON acd.calendar_terms USING btree (tenant_id, id)` |
| `uq_calendar_terms_tenant_id_calendar_id_term_no` | `CREATE UNIQUE INDEX uq_calendar_terms_tenant_id_calendar_id_term_no ON acd.calendar_terms USING btree (tenant_id, calendar_id, term_no) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.course_offerings`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `college_id` | `uuid` | **NO** | — |
| `course_id` | `uuid` | **NO** | — |
| `term_id` | `uuid` | **NO** | — |
| `start_date` | `date` | YES | — |
| `end_date` | `date` | YES | — |
| `capacity` | `integer` | YES | — |
| `status` | `text` | **NO** | `'PLANNED'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_course_offerings_capacity` | CHECK | `CHECK ((capacity >= 0))` |
| `ck_course_offerings_status` | CHECK | `CHECK ((status = ANY (ARRAY['PLANNED'::text, 'OPEN'::text, 'RUNNING'::text, 'COMPLETED'::text, 'CANCELLED'::text])))` |
| `fk_course_offerings_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_course_offerings_course_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE RESTRICT` |
| `fk_course_offerings_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_course_offerings_term_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, term_id) REFERENCES acd.calendar_terms(tenant_id, id) ON DELETE RESTRICT` |
| `course_offerings_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_course_offerings_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `course_offerings_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.manage'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd07.manage'::text)) AS unnest)) OR (course_id IN ( SELECT core.courses_in_scope('acd07.manage'::text, true) AS courses_in_scope))))` |
| `course_offerings_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd07.write'::text)) AS unnest)) OR (course_id IN ( SELECT core.courses_in_scope('acd07.write'::text, true) AS courses_in_scope))))` |
| `course_offerings_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.view'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd07.view'::text)) AS unnest)) OR (course_id IN ( SELECT core.courses_in_scope('acd07.view'::text, false) AS courses_in_scope)) OR (( SELECT iam.has_permission('acd07.view.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd07.view.own'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd07.view.own'::text)) AS unnest)) OR (course_id IN ( SELECT core.courses_in_scope('acd07.view.own'::text, false) AS courses_in_scope))) AND (id IN ( SELECT course_sections.offering_id
   FROM acd.course_sections
  WHERE (course_sections.id IN ( SELECT core.faculty_section_ids() AS faculty_section_ids))))) OR (id IN ( SELECT course_sections.offering_id
   FROM acd.course_sections
  WHERE (course_sections.id IN ( SELECT core.my_section_ids() AS my_section_ids))))))` |
| `course_offerings_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd07.write'::text)) AS unnest)) OR (course_id IN ( SELECT core.courses_in_scope('acd07.write'::text, true) AS courses_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `course_offerings_pkey` | `CREATE UNIQUE INDEX course_offerings_pkey ON acd.course_offerings USING btree (id)` |
| `ix_course_offerings_college_id` | `CREATE INDEX ix_course_offerings_college_id ON acd.course_offerings USING btree (tenant_id, college_id)` |
| `ix_course_offerings_course_id` | `CREATE INDEX ix_course_offerings_course_id ON acd.course_offerings USING btree (tenant_id, course_id)` |
| `ix_course_offerings_tenant_id_term_id_status` | `CREATE INDEX ix_course_offerings_tenant_id_term_id_status ON acd.course_offerings USING btree (tenant_id, term_id, status)` |
| `uq_course_offerings_tenant_id` | `CREATE UNIQUE INDEX uq_course_offerings_tenant_id ON acd.course_offerings USING btree (tenant_id, id)` |
| `uq_course_offerings_tenant_id_course_id_term_id_college_id` | `CREATE UNIQUE INDEX uq_course_offerings_tenant_id_course_id_term_id_college_id ON acd.course_offerings USING btree (tenant_id, course_id, term_id, college_id) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.course_prerequisites`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `course_id` | `uuid` | **NO** | — |
| `prerequisite_course_id` | `uuid` | **NO** | — |
| `relation` | `text` | **NO** | `'PREREQUISITE'::text` |
| `min_grade` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_course_prerequisites_relation` | CHECK | `CHECK ((relation = ANY (ARRAY['PREREQUISITE'::text, 'COREQUISITE'::text])))` |
| `ck_course_prerequisites_x1` | CHECK | `CHECK ((course_id <> prerequisite_course_id))` |
| `fk_course_prerequisites_course_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE CASCADE` |
| `fk_course_prerequisites_prerequisite_course_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, prerequisite_course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE RESTRICT` |
| `fk_course_prerequisites_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `course_prerequisites_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_course_prerequisites_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `course_prerequisites_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.manage'::text) AS perm_all) OR (course_id IN ( SELECT core.courses_in_scope('acd03.manage'::text, true) AS courses_in_scope))))` |
| `course_prerequisites_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR (course_id IN ( SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope))))` |
| `course_prerequisites_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.view'::text) AS perm_all) OR (course_id IN ( SELECT core.courses_in_scope('acd03.view'::text, false) AS courses_in_scope))))` |
| `course_prerequisites_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR (course_id IN ( SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `course_prerequisites_pkey` | `CREATE UNIQUE INDEX course_prerequisites_pkey ON acd.course_prerequisites USING btree (id)` |
| `ix_course_prerequisites_course_id` | `CREATE INDEX ix_course_prerequisites_course_id ON acd.course_prerequisites USING btree (tenant_id, course_id)` |
| `ix_course_prerequisites_prerequisite_course_id` | `CREATE INDEX ix_course_prerequisites_prerequisite_course_id ON acd.course_prerequisites USING btree (tenant_id, prerequisite_course_id)` |
| `uq_course_prerequisites_tenant_id` | `CREATE UNIQUE INDEX uq_course_prerequisites_tenant_id ON acd.course_prerequisites USING btree (tenant_id, id)` |
| `uq_course_prerequisites_tenant_id_course_id_prereq_741c9219` | `CREATE UNIQUE INDEX uq_course_prerequisites_tenant_id_course_id_prereq_741c9219 ON acd.course_prerequisites USING btree (tenant_id, course_id, prerequisite_course_id, relation) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.course_program_map`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `course_id` | `uuid` | **NO** | — |
| `program_id` | `uuid` | **NO** | — |
| `term_no` | `smallint` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_course_program_map_course_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE CASCADE` |
| `fk_course_program_map_program_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, program_id) REFERENCES acd.programs(tenant_id, id) ON DELETE CASCADE` |
| `fk_course_program_map_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `course_program_map_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_course_program_map_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `course_program_map_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.manage'::text) AS perm_all) OR (program_id IN ( SELECT core.programs_in_scope('acd01.manage'::text, true) AS programs_in_scope))))` |
| `course_program_map_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND core.is_active_course_in_tenant(course_id) AND (( SELECT iam.perm_all('acd01.write'::text) AS perm_all) OR (program_id IN ( SELECT core.programs_in_scope('acd01.write'::text, true) AS programs_in_scope))))` |
| `course_program_map_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.view'::text) AS perm_all) OR (program_id IN ( SELECT core.programs_in_scope('acd01.view'::text, false) AS programs_in_scope)) OR (course_id IN ( SELECT core.courses_in_scope('acd01.view'::text, false) AS courses_in_scope))))` |
| `course_program_map_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.write'::text) AS perm_all) OR (program_id IN ( SELECT core.programs_in_scope('acd01.write'::text, true) AS programs_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `course_program_map_pkey` | `CREATE UNIQUE INDEX course_program_map_pkey ON acd.course_program_map USING btree (id)` |
| `ix_course_program_map_program_id` | `CREATE INDEX ix_course_program_map_program_id ON acd.course_program_map USING btree (tenant_id, program_id)` |
| `uq_course_program_map_tenant_id` | `CREATE UNIQUE INDEX uq_course_program_map_tenant_id ON acd.course_program_map USING btree (tenant_id, id)` |
| `uq_course_program_map_tenant_id_course_id_program_id` | `CREATE UNIQUE INDEX uq_course_program_map_tenant_id_course_id_program_id ON acd.course_program_map USING btree (tenant_id, course_id, program_id)` |

---

### Table: `acd.course_sections`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `offering_id` | `uuid` | **NO** | — |
| `batch_id` | `uuid` | YES | — |
| `section_code` | `text` | **NO** | — |
| `capacity` | `integer` | YES | — |
| `default_room_id` | `uuid` | YES | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_course_sections_capacity` | CHECK | `CHECK ((capacity >= 0))` |
| `ck_course_sections_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'CLOSED'::text, 'CANCELLED'::text])))` |
| `fk_course_sections_batch_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, batch_id) REFERENCES acd.batches(tenant_id, id) ON DELETE RESTRICT` |
| `fk_course_sections_default_room_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, default_room_id) REFERENCES core.rooms(tenant_id, id) ON DELETE RESTRICT` |
| `fk_course_sections_offering_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, offering_id) REFERENCES acd.course_offerings(tenant_id, id) ON DELETE CASCADE` |
| `fk_course_sections_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `course_sections_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_course_sections_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `course_sections_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.manage'::text) AS perm_all) OR (offering_id IN ( SELECT iam.sc_acd_course_offerings('acd07.manage'::text, true) AS sc_acd_course_offerings))))` |
| `course_sections_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (offering_id IN ( SELECT iam.sc_acd_course_offerings('acd07.write'::text, true) AS sc_acd_course_offerings))))` |
| `course_sections_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.view'::text) AS perm_all) OR (offering_id IN ( SELECT iam.sc_acd_course_offerings('acd07.view'::text, false) AS sc_acd_course_offerings)) OR (( SELECT iam.has_permission('acd07.view.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd07.view.own'::text) AS perm_all) OR (offering_id IN ( SELECT iam.sc_acd_course_offerings('acd07.view.own'::text, false) AS sc_acd_course_offerings))) AND (id IN ( SELECT core.faculty_section_ids() AS faculty_section_ids))) OR (id IN ( SELECT core.my_section_ids() AS my_section_ids))))` |
| `course_sections_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (offering_id IN ( SELECT iam.sc_acd_course_offerings('acd07.write'::text, true) AS sc_acd_course_offerings))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `course_sections_pkey` | `CREATE UNIQUE INDEX course_sections_pkey ON acd.course_sections USING btree (id)` |
| `ix_course_sections_batch_id` | `CREATE INDEX ix_course_sections_batch_id ON acd.course_sections USING btree (tenant_id, batch_id)` |
| `ix_course_sections_default_room_id` | `CREATE INDEX ix_course_sections_default_room_id ON acd.course_sections USING btree (tenant_id, default_room_id)` |
| `ix_course_sections_offering_id` | `CREATE INDEX ix_course_sections_offering_id ON acd.course_sections USING btree (tenant_id, offering_id)` |
| `uq_course_sections_tenant_id` | `CREATE UNIQUE INDEX uq_course_sections_tenant_id ON acd.course_sections USING btree (tenant_id, id)` |
| `uq_course_sections_tenant_id_offering_id_section_code` | `CREATE UNIQUE INDEX uq_course_sections_tenant_id_offering_id_section_code ON acd.course_sections USING btree (tenant_id, offering_id, section_code) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.course_versions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `course_id` | `uuid` | **NO** | — |
| `version_no` | `integer` | **NO** | — |
| `effective_from` | `date` | YES | — |
| `effective_to` | `date` | YES | — |
| `content_outline` | `jsonb` | YES | `'{}'::jsonb` |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_course_versions_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'APPROVED'::text, 'ACTIVE'::text, 'SUPERSEDED'::text])))` |
| `ck_course_versions_version_no` | CHECK | `CHECK ((version_no > 0))` |
| `fk_course_versions_course_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE CASCADE` |
| `fk_course_versions_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `course_versions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_course_versions_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `course_versions_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.manage'::text) AS perm_all) OR (course_id IN ( SELECT core.courses_in_scope('acd03.manage'::text, true) AS courses_in_scope))))` |
| `course_versions_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR (course_id IN ( SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope))))` |
| `course_versions_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.view'::text) AS perm_all) OR (course_id IN ( SELECT core.courses_in_scope('acd03.view'::text, false) AS courses_in_scope))))` |
| `course_versions_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR (course_id IN ( SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `course_versions_pkey` | `CREATE UNIQUE INDEX course_versions_pkey ON acd.course_versions USING btree (id)` |
| `ix_course_versions_course_id` | `CREATE INDEX ix_course_versions_course_id ON acd.course_versions USING btree (tenant_id, course_id)` |
| `uq_course_versions_tenant_id` | `CREATE UNIQUE INDEX uq_course_versions_tenant_id ON acd.course_versions USING btree (tenant_id, id)` |
| `uq_course_versions_tenant_id_course_id_version_no` | `CREATE UNIQUE INDEX uq_course_versions_tenant_id_course_id_version_no ON acd.course_versions USING btree (tenant_id, course_id, version_no) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.courses`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `department_id` | `uuid` | YES | — |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `description` | `text` | YES | — |
| `level` | `text` | YES | — |
| `credits` | `numeric` | YES | — |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `metadata` | `jsonb` | YES | `'{}'::jsonb` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_courses_credits` | CHECK | `CHECK ((credits >= (0)::numeric))` |
| `ck_courses_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])))` |
| `fk_courses_department_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT` |
| `fk_courses_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `courses_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_courses_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `courses_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.manage'::text) AS perm_all) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('acd03.manage'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('acd03.manage'::text) AS has_permission)))))` |
| `courses_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('acd03.write'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('acd03.write'::text) AS has_permission)))))` |
| `courses_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.view'::text) AS perm_all) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('acd03.view'::text)) AS unnest)) OR ((NOT false) AND (department_id IS NULL) AND ( SELECT iam.has_permission('acd03.view'::text) AS has_permission)))))` |
| `courses_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('acd03.write'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('acd03.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `courses_pkey` | `CREATE UNIQUE INDEX courses_pkey ON acd.courses USING btree (id)` |
| `ix_courses_department_id` | `CREATE INDEX ix_courses_department_id ON acd.courses USING btree (tenant_id, department_id)` |
| `uq_courses_tenant_id` | `CREATE UNIQUE INDEX uq_courses_tenant_id ON acd.courses USING btree (tenant_id, id)` |
| `uq_courses_tenant_id_code` | `CREATE UNIQUE INDEX uq_courses_tenant_id_code ON acd.courses USING btree (tenant_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.curricula`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `program_id` | `uuid` | **NO** | — |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `regulation_year` | `smallint` | YES | — |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_curricula_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])))` |
| `fk_curricula_program_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, program_id) REFERENCES acd.programs(tenant_id, id) ON DELETE RESTRICT` |
| `fk_curricula_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `curricula_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_curricula_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `curricula_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.manage'::text) AS perm_all) OR (program_id IN ( SELECT core.programs_in_scope('acd03.manage'::text, true) AS programs_in_scope))))` |
| `curricula_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR (program_id IN ( SELECT core.programs_in_scope('acd03.write'::text, true) AS programs_in_scope))))` |
| `curricula_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.view'::text) AS perm_all) OR (program_id IN ( SELECT core.programs_in_scope('acd03.view'::text, false) AS programs_in_scope))))` |
| `curricula_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR (program_id IN ( SELECT core.programs_in_scope('acd03.write'::text, true) AS programs_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `curricula_pkey` | `CREATE UNIQUE INDEX curricula_pkey ON acd.curricula USING btree (id)` |
| `ix_curricula_program_id` | `CREATE INDEX ix_curricula_program_id ON acd.curricula USING btree (tenant_id, program_id)` |
| `uq_curricula_tenant_id` | `CREATE UNIQUE INDEX uq_curricula_tenant_id ON acd.curricula USING btree (tenant_id, id)` |
| `uq_curricula_tenant_id_code` | `CREATE UNIQUE INDEX uq_curricula_tenant_id_code ON acd.curricula USING btree (tenant_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.curriculum_subjects`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `curriculum_version_id` | `uuid` | **NO** | — |
| `subject_id` | `uuid` | **NO** | — |
| `term_no` | `smallint` | **NO** | — |
| `category` | `text` | **NO** | `'CORE'::text` |
| `credits` | `numeric` | YES | — |
| `elective_group` | `text` | YES | — |
| `display_order` | `smallint` | **NO** | `0` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_curriculum_subjects_category` | CHECK | `CHECK ((category = ANY (ARRAY['CORE'::text, 'ELECTIVE'::text, 'OPEN_ELECTIVE'::text, 'MANDATORY_NON_CREDIT'::text, 'PROJECT'::text])))` |
| `ck_curriculum_subjects_credits` | CHECK | `CHECK ((credits >= (0)::numeric))` |
| `ck_curriculum_subjects_term_no` | CHECK | `CHECK ((term_no > 0))` |
| `fk_curriculum_subjects_curriculum_version_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, curriculum_version_id) REFERENCES acd.curriculum_versions(tenant_id, id) ON DELETE CASCADE` |
| `fk_curriculum_subjects_subject_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT` |
| `fk_curriculum_subjects_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `curriculum_subjects_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_curriculum_subjects_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `curriculum_subjects_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.manage'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd03.manage'::text, true) AS subjects_in_scope))))` |
| `curriculum_subjects_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd03.write'::text, true) AS subjects_in_scope))))` |
| `curriculum_subjects_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.view'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd03.view'::text, false) AS subjects_in_scope))))` |
| `curriculum_subjects_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd03.write'::text, true) AS subjects_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `curriculum_subjects_pkey` | `CREATE UNIQUE INDEX curriculum_subjects_pkey ON acd.curriculum_subjects USING btree (id)` |
| `ix_curriculum_subjects_subject_id` | `CREATE INDEX ix_curriculum_subjects_subject_id ON acd.curriculum_subjects USING btree (tenant_id, subject_id)` |
| `ix_curriculum_subjects_tenant_id_curriculum_versio_2e60e8e2` | `CREATE INDEX ix_curriculum_subjects_tenant_id_curriculum_versio_2e60e8e2 ON acd.curriculum_subjects USING btree (tenant_id, curriculum_version_id, term_no)` |
| `uq_curriculum_subjects_tenant_id` | `CREATE UNIQUE INDEX uq_curriculum_subjects_tenant_id ON acd.curriculum_subjects USING btree (tenant_id, id)` |
| `uq_curriculum_subjects_tenant_id_curriculum_versio_b5a60d64` | `CREATE UNIQUE INDEX uq_curriculum_subjects_tenant_id_curriculum_versio_b5a60d64 ON acd.curriculum_subjects USING btree (tenant_id, curriculum_version_id, subject_id) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.curriculum_versions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `curriculum_id` | `uuid` | **NO** | — |
| `version_no` | `integer` | **NO** | — |
| `effective_from` | `date` | **NO** | — |
| `effective_to` | `date` | YES | — |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `total_credits` | `numeric` | YES | — |
| `workflow_instance_id` | `uuid` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_curriculum_versions_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'APPROVED'::text, 'ACTIVE'::text, 'SUPERSEDED'::text, 'RETIRED'::text])))` |
| `ck_curriculum_versions_total_credits` | CHECK | `CHECK ((total_credits >= (0)::numeric))` |
| `ck_curriculum_versions_version_no` | CHECK | `CHECK ((version_no > 0))` |
| `ck_curriculum_versions_x1` | CHECK | `CHECK (((effective_to IS NULL) OR (effective_to >= effective_from)))` |
| `fk_curriculum_versions_curriculum_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, curriculum_id) REFERENCES acd.curricula(tenant_id, id) ON DELETE CASCADE` |
| `fk_curriculum_versions_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_curriculum_versions_workflow_instance_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT` |
| `curriculum_versions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_curriculum_versions_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_b_transition` | BEFORE | INSERT | Standard |
| `trg_b_transition` | BEFORE | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |
| `trg_z_transition_log` | AFTER | INSERT | Standard |
| `trg_z_transition_log` | AFTER | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `curriculum_versions_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.manage'::text) AS perm_all) OR (curriculum_id IN ( SELECT iam.sc_acd_curricula('acd03.manage'::text, true) AS sc_acd_curricula))))` |
| `curriculum_versions_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR (curriculum_id IN ( SELECT iam.sc_acd_curricula('acd03.write'::text, true) AS sc_acd_curricula))))` |
| `curriculum_versions_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.view'::text) AS perm_all) OR (curriculum_id IN ( SELECT iam.sc_acd_curricula('acd03.view'::text, false) AS sc_acd_curricula))))` |
| `curriculum_versions_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR (curriculum_id IN ( SELECT iam.sc_acd_curricula('acd03.write'::text, true) AS sc_acd_curricula)) OR (( SELECT iam.perm_all('acd03.approve'::text) AS perm_all) OR (curriculum_id IN ( SELECT iam.sc_acd_curricula('acd03.approve'::text, true) AS sc_acd_curricula)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `curriculum_versions_pkey` | `CREATE UNIQUE INDEX curriculum_versions_pkey ON acd.curriculum_versions USING btree (id)` |
| `ix_curriculum_versions_curriculum_id` | `CREATE INDEX ix_curriculum_versions_curriculum_id ON acd.curriculum_versions USING btree (tenant_id, curriculum_id)` |
| `ix_curriculum_versions_workflow_instance_id` | `CREATE INDEX ix_curriculum_versions_workflow_instance_id ON acd.curriculum_versions USING btree (tenant_id, workflow_instance_id)` |
| `uq_curriculum_versions_tenant_id` | `CREATE UNIQUE INDEX uq_curriculum_versions_tenant_id ON acd.curriculum_versions USING btree (tenant_id, id)` |
| `uq_curriculum_versions_tenant_id_curriculum_id_version_no` | `CREATE UNIQUE INDEX uq_curriculum_versions_tenant_id_curriculum_id_version_no ON acd.curriculum_versions USING btree (tenant_id, curriculum_id, version_no) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.enrollment_rules`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `offering_id` | `uuid` | **NO** | — |
| `rule_type` | `text` | **NO** | — |
| `params` | `jsonb` | **NO** | `'{}'::jsonb` |
| `is_active` | `boolean` | **NO** | `true` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_enrollment_rules_offering_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, offering_id) REFERENCES acd.course_offerings(tenant_id, id) ON DELETE CASCADE` |
| `fk_enrollment_rules_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `enrollment_rules_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_enrollment_rules_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `enrollment_rules_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.manage'::text) AS perm_all) OR (offering_id IN ( SELECT iam.sc_acd_course_offerings('acd07.manage'::text, true) AS sc_acd_course_offerings))))` |
| `enrollment_rules_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (offering_id IN ( SELECT iam.sc_acd_course_offerings('acd07.write'::text, true) AS sc_acd_course_offerings))))` |
| `enrollment_rules_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.view'::text) AS perm_all) OR (offering_id IN ( SELECT iam.sc_acd_course_offerings('acd07.view'::text, false) AS sc_acd_course_offerings))))` |
| `enrollment_rules_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (offering_id IN ( SELECT iam.sc_acd_course_offerings('acd07.write'::text, true) AS sc_acd_course_offerings))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `enrollment_rules_pkey` | `CREATE UNIQUE INDEX enrollment_rules_pkey ON acd.enrollment_rules USING btree (id)` |
| `ix_enrollment_rules_offering_id` | `CREATE INDEX ix_enrollment_rules_offering_id ON acd.enrollment_rules USING btree (tenant_id, offering_id)` |
| `uq_enrollment_rules_tenant_id` | `CREATE UNIQUE INDEX uq_enrollment_rules_tenant_id ON acd.enrollment_rules USING btree (tenant_id, id)` |

---

### Table: `acd.programs`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `college_id` | `uuid` | **NO** | — |
| `department_id` | `uuid` | **NO** | — |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `level` | `text` | **NO** | — |
| `duration_years` | `numeric` | **NO** | — |
| `total_terms` | `smallint` | YES | — |
| `published` | `boolean` | **NO** | `false` |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_programs_duration_years` | CHECK | `CHECK ((duration_years > (0)::numeric))` |
| `ck_programs_level` | CHECK | `CHECK ((level = ANY (ARRAY['CERTIFICATE'::text, 'DIPLOMA'::text, 'UG'::text, 'PG'::text, 'PHD'::text, 'INTEGRATED'::text])))` |
| `ck_programs_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'DISCONTINUED'::text, 'DRAFT'::text])))` |
| `fk_programs_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_programs_department_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT` |
| `fk_programs_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `programs_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_programs_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `programs_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd02.manage'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd02.manage'::text)) AS unnest)) OR (department_id IN ( SELECT unnest(iam.perm_department_ids('acd02.manage'::text)) AS unnest))))` |
| `programs_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd02.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd02.write'::text)) AS unnest)) OR (department_id IN ( SELECT unnest(iam.perm_department_ids('acd02.write'::text)) AS unnest))))` |
| `programs_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd02.view'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd02.view'::text)) AS unnest)) OR (department_id IN ( SELECT unnest(iam.perm_department_ids('acd02.view'::text)) AS unnest))))` |
| `programs_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd02.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd02.write'::text)) AS unnest)) OR (department_id IN ( SELECT unnest(iam.perm_department_ids('acd02.write'::text)) AS unnest))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_programs_college_id` | `CREATE INDEX ix_programs_college_id ON acd.programs USING btree (tenant_id, college_id)` |
| `ix_programs_tenant_id_department_id` | `CREATE INDEX ix_programs_tenant_id_department_id ON acd.programs USING btree (tenant_id, department_id)` |
| `programs_pkey` | `CREATE UNIQUE INDEX programs_pkey ON acd.programs USING btree (id)` |
| `uq_programs_tenant_id` | `CREATE UNIQUE INDEX uq_programs_tenant_id ON acd.programs USING btree (tenant_id, id)` |
| `uq_programs_tenant_id_college_id_code` | `CREATE UNIQUE INDEX uq_programs_tenant_id_college_id_code ON acd.programs USING btree (tenant_id, college_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.section_enrollments`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `section_id` | `uuid` | **NO** | — |
| `student_id` | `uuid` | **NO** | — |
| `status` | `text` | **NO** | `'ENROLLED'::text` |
| `enrolled_at` | `timestamp with time zone` | **NO** | `now()` |
| `dropped_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_section_enrollments_status` | CHECK | `CHECK ((status = ANY (ARRAY['ENROLLED'::text, 'DROPPED'::text, 'WITHDRAWN'::text, 'COMPLETED'::text, 'AUDIT'::text])))` |
| `fk_section_enrollments_section_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, section_id) REFERENCES acd.course_sections(tenant_id, id) ON DELETE RESTRICT` |
| `fk_section_enrollments_student_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT` |
| `fk_section_enrollments_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `section_enrollments_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_section_enrollments_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `section_enrollments_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.manage'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd07.manage'::text, true) AS sections_in_scope)) OR (student_id IN ( SELECT core.students_in_scope('acd07.manage'::text, true) AS students_in_scope))))` |
| `section_enrollments_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd07.write'::text, true) AS sections_in_scope)) OR (student_id IN ( SELECT core.students_in_scope('acd07.write'::text, true) AS students_in_scope))))` |
| `section_enrollments_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.view.all'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd07.view.all'::text, false) AS sections_in_scope)) OR (student_id IN ( SELECT core.students_in_scope('acd07.view.all'::text, false) AS students_in_scope)) OR (( SELECT iam.has_permission('acd07.view.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd07.view.own'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd07.view.own'::text, false) AS sections_in_scope)) OR (student_id IN ( SELECT core.students_in_scope('acd07.view.own'::text, false) AS students_in_scope))) AND (section_id IN ( SELECT core.faculty_section_ids() AS faculty_section_ids))) OR (student_id IN ( SELECT core.my_student_ids() AS my_student_ids))))` |
| `section_enrollments_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd07.write'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd07.write'::text, true) AS sections_in_scope)) OR (student_id IN ( SELECT core.students_in_scope('acd07.write'::text, true) AS students_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_section_enrollments_section_id` | `CREATE INDEX ix_section_enrollments_section_id ON acd.section_enrollments USING btree (tenant_id, section_id)` |
| `ix_section_enrollments_tenant_id_student_id_status` | `CREATE INDEX ix_section_enrollments_tenant_id_student_id_status ON acd.section_enrollments USING btree (tenant_id, student_id, status)` |
| `section_enrollments_pkey` | `CREATE UNIQUE INDEX section_enrollments_pkey ON acd.section_enrollments USING btree (id)` |
| `uq_section_enrollments_tenant_id` | `CREATE UNIQUE INDEX uq_section_enrollments_tenant_id ON acd.section_enrollments USING btree (tenant_id, id)` |
| `uq_section_enrollments_tenant_id_section_id_student_id` | `CREATE UNIQUE INDEX uq_section_enrollments_tenant_id_section_id_student_id ON acd.section_enrollments USING btree (tenant_id, section_id, student_id) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.section_faculty`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `section_id` | `uuid` | **NO** | — |
| `employee_id` | `uuid` | **NO** | — |
| `role` | `text` | **NO** | `'PRIMARY'::text` |
| `valid_from` | `date` | YES | — |
| `valid_to` | `date` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_section_faculty_role` | CHECK | `CHECK ((role = ANY (ARRAY['PRIMARY'::text, 'CO_INSTRUCTOR'::text, 'LAB_INSTRUCTOR'::text, 'TEACHING_ASSISTANT'::text, 'MENTOR'::text])))` |
| `fk_section_faculty_employee_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT` |
| `fk_section_faculty_section_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, section_id) REFERENCES acd.course_sections(tenant_id, id) ON DELETE CASCADE` |
| `fk_section_faculty_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `section_faculty_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_section_faculty_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `section_faculty_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd05.manage'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd05.manage'::text, true) AS sections_in_scope)) OR (employee_id IN ( SELECT core.employees_in_scope('acd05.manage'::text, true) AS employees_in_scope))))` |
| `section_faculty_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd05.write'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd05.write'::text, true) AS sections_in_scope)) OR (employee_id IN ( SELECT core.employees_in_scope('acd05.write'::text, true) AS employees_in_scope))))` |
| `section_faculty_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd05.view.all'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd05.view.all'::text, false) AS sections_in_scope)) OR (employee_id IN ( SELECT core.employees_in_scope('acd05.view.all'::text, false) AS employees_in_scope)) OR (employee_id = ( SELECT core.current_employee_id() AS current_employee_id))))` |
| `section_faculty_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd05.write'::text) AS perm_all) OR (section_id IN ( SELECT core.sections_in_scope('acd05.write'::text, true) AS sections_in_scope)) OR (employee_id IN ( SELECT core.employees_in_scope('acd05.write'::text, true) AS employees_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_section_faculty_section_id` | `CREATE INDEX ix_section_faculty_section_id ON acd.section_faculty USING btree (tenant_id, section_id)` |
| `ix_section_faculty_tenant_id_employee_id` | `CREATE INDEX ix_section_faculty_tenant_id_employee_id ON acd.section_faculty USING btree (tenant_id, employee_id)` |
| `section_faculty_pkey` | `CREATE UNIQUE INDEX section_faculty_pkey ON acd.section_faculty USING btree (id)` |
| `uq_section_faculty_tenant_id` | `CREATE UNIQUE INDEX uq_section_faculty_tenant_id ON acd.section_faculty USING btree (tenant_id, id)` |
| `uq_section_faculty_tenant_id_section_id_employee_id_role` | `CREATE UNIQUE INDEX uq_section_faculty_tenant_id_section_id_employee_id_role ON acd.section_faculty USING btree (tenant_id, section_id, employee_id, role) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.subject_change_requests`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `subject_id` | `uuid` | **NO** | — |
| `change_type` | `text` | **NO** | — |
| `proposed_changes` | `jsonb` | **NO** | `'{}'::jsonb` |
| `status` | `text` | **NO** | `'PENDING'::text` |
| `requested_by` | `uuid` | YES | — |
| `workflow_instance_id` | `uuid` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_subject_change_requests_status` | CHECK | `CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text, 'APPLIED'::text])))` |
| `fk_subject_change_requests_requested_by` | FOREIGN KEY | `FOREIGN KEY (tenant_id, requested_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_subject_change_requests_subject_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT` |
| `fk_subject_change_requests_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_subject_change_requests_workflow_instance_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT` |
| `subject_change_requests_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_subject_change_requests_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_b_transition` | BEFORE | INSERT | Standard |
| `trg_b_transition` | BEFORE | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |
| `trg_z_transition_log` | AFTER | INSERT | Standard |
| `trg_z_transition_log` | AFTER | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `subject_change_requests_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.manage'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.manage'::text, true) AS subjects_in_scope))))` |
| `subject_change_requests_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.write'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.write'::text, true) AS subjects_in_scope))))` |
| `subject_change_requests_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.view'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.view'::text, false) AS subjects_in_scope))))` |
| `subject_change_requests_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.write'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.write'::text, true) AS subjects_in_scope)) OR (( SELECT iam.perm_all('acd04.manage'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.manage'::text, true) AS subjects_in_scope)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_subject_change_requests_requested_by` | `CREATE INDEX ix_subject_change_requests_requested_by ON acd.subject_change_requests USING btree (tenant_id, requested_by)` |
| `ix_subject_change_requests_subject_id` | `CREATE INDEX ix_subject_change_requests_subject_id ON acd.subject_change_requests USING btree (tenant_id, subject_id)` |
| `ix_subject_change_requests_workflow_instance_id` | `CREATE INDEX ix_subject_change_requests_workflow_instance_id ON acd.subject_change_requests USING btree (tenant_id, workflow_instance_id)` |
| `subject_change_requests_pkey` | `CREATE UNIQUE INDEX subject_change_requests_pkey ON acd.subject_change_requests USING btree (id)` |
| `uq_subject_change_requests_tenant_id` | `CREATE UNIQUE INDEX uq_subject_change_requests_tenant_id ON acd.subject_change_requests USING btree (tenant_id, id)` |

---

### Table: `acd.subject_course_map`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `subject_id` | `uuid` | **NO** | — |
| `course_id` | `uuid` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_subject_course_map_course_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE CASCADE` |
| `fk_subject_course_map_subject_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE CASCADE` |
| `fk_subject_course_map_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `subject_course_map_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_subject_course_map_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `subject_course_map_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.manage'::text) AS perm_all) OR (course_id IN ( SELECT core.courses_in_scope('acd03.manage'::text, true) AS courses_in_scope)) OR (subject_id IN ( SELECT core.subjects_in_scope('acd03.manage'::text, true) AS subjects_in_scope))))` |
| `subject_course_map_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR (course_id IN ( SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope)) OR (subject_id IN ( SELECT core.subjects_in_scope('acd03.write'::text, true) AS subjects_in_scope))))` |
| `subject_course_map_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.view'::text) AS perm_all) OR (course_id IN ( SELECT core.courses_in_scope('acd03.view'::text, false) AS courses_in_scope)) OR (subject_id IN ( SELECT core.subjects_in_scope('acd03.view'::text, false) AS subjects_in_scope))))` |
| `subject_course_map_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd03.write'::text) AS perm_all) OR (course_id IN ( SELECT core.courses_in_scope('acd03.write'::text, true) AS courses_in_scope)) OR (subject_id IN ( SELECT core.subjects_in_scope('acd03.write'::text, true) AS subjects_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_subject_course_map_course_id` | `CREATE INDEX ix_subject_course_map_course_id ON acd.subject_course_map USING btree (tenant_id, course_id)` |
| `subject_course_map_pkey` | `CREATE UNIQUE INDEX subject_course_map_pkey ON acd.subject_course_map USING btree (id)` |
| `uq_subject_course_map_tenant_id` | `CREATE UNIQUE INDEX uq_subject_course_map_tenant_id ON acd.subject_course_map USING btree (tenant_id, id)` |
| `uq_subject_course_map_tenant_id_subject_id_course_id` | `CREATE UNIQUE INDEX uq_subject_course_map_tenant_id_subject_id_course_id ON acd.subject_course_map USING btree (tenant_id, subject_id, course_id)` |

---

### Table: `acd.subject_equivalencies`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `subject_id` | `uuid` | **NO** | — |
| `equivalent_subject_id` | `uuid` | **NO** | — |
| `valid_from` | `date` | YES | — |
| `valid_to` | `date` | YES | — |
| `note` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_subject_equivalencies_x1` | CHECK | `CHECK ((subject_id <> equivalent_subject_id))` |
| `fk_subject_equivalencies_equivalent_subject_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, equivalent_subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE CASCADE` |
| `fk_subject_equivalencies_subject_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE CASCADE` |
| `fk_subject_equivalencies_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `subject_equivalencies_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_subject_equivalencies_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `subject_equivalencies_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.manage'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.manage'::text, true) AS subjects_in_scope))))` |
| `subject_equivalencies_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.write'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.write'::text, true) AS subjects_in_scope))))` |
| `subject_equivalencies_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.view'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.view'::text, false) AS subjects_in_scope))))` |
| `subject_equivalencies_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.write'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.write'::text, true) AS subjects_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_subject_equivalencies_equivalent_subject_id` | `CREATE INDEX ix_subject_equivalencies_equivalent_subject_id ON acd.subject_equivalencies USING btree (tenant_id, equivalent_subject_id)` |
| `ix_subject_equivalencies_subject_id` | `CREATE INDEX ix_subject_equivalencies_subject_id ON acd.subject_equivalencies USING btree (tenant_id, subject_id)` |
| `subject_equivalencies_pkey` | `CREATE UNIQUE INDEX subject_equivalencies_pkey ON acd.subject_equivalencies USING btree (id)` |
| `uq_subject_equivalencies_tenant_id` | `CREATE UNIQUE INDEX uq_subject_equivalencies_tenant_id ON acd.subject_equivalencies USING btree (tenant_id, id)` |
| `uq_subject_equivalencies_tenant_id_subject_id_equi_f2246f78` | `CREATE UNIQUE INDEX uq_subject_equivalencies_tenant_id_subject_id_equi_f2246f78 ON acd.subject_equivalencies USING btree (tenant_id, subject_id, equivalent_subject_id) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.subject_prerequisites`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `subject_id` | `uuid` | **NO** | — |
| `prerequisite_subject_id` | `uuid` | **NO** | — |
| `relation` | `text` | **NO** | `'PREREQUISITE'::text` |
| `min_grade` | `text` | YES | — |
| `curriculum_version_id` | `uuid` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_subject_prerequisites_relation` | CHECK | `CHECK ((relation = ANY (ARRAY['PREREQUISITE'::text, 'COREQUISITE'::text])))` |
| `ck_subject_prerequisites_x1` | CHECK | `CHECK ((subject_id <> prerequisite_subject_id))` |
| `fk_subject_prerequisites_curriculum_version_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, curriculum_version_id) REFERENCES acd.curriculum_versions(tenant_id, id) ON DELETE RESTRICT` |
| `fk_subject_prerequisites_prerequisite_subject_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, prerequisite_subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT` |
| `fk_subject_prerequisites_subject_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE CASCADE` |
| `fk_subject_prerequisites_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `subject_prerequisites_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_subject_prerequisites_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `subject_prerequisites_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.manage'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.manage'::text, true) AS subjects_in_scope))))` |
| `subject_prerequisites_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.write'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.write'::text, true) AS subjects_in_scope))))` |
| `subject_prerequisites_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.view'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.view'::text, false) AS subjects_in_scope))))` |
| `subject_prerequisites_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.write'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.write'::text, true) AS subjects_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_subject_prerequisites_curriculum_version_id` | `CREATE INDEX ix_subject_prerequisites_curriculum_version_id ON acd.subject_prerequisites USING btree (tenant_id, curriculum_version_id)` |
| `ix_subject_prerequisites_prerequisite_subject_id` | `CREATE INDEX ix_subject_prerequisites_prerequisite_subject_id ON acd.subject_prerequisites USING btree (tenant_id, prerequisite_subject_id)` |
| `ix_subject_prerequisites_subject_id` | `CREATE INDEX ix_subject_prerequisites_subject_id ON acd.subject_prerequisites USING btree (tenant_id, subject_id)` |
| `subject_prerequisites_pkey` | `CREATE UNIQUE INDEX subject_prerequisites_pkey ON acd.subject_prerequisites USING btree (id)` |
| `uq_subject_prerequisites_tenant_id` | `CREATE UNIQUE INDEX uq_subject_prerequisites_tenant_id ON acd.subject_prerequisites USING btree (tenant_id, id)` |
| `uq_subject_prerequisites_tenant_id_subject_id_prer_af55c281` | `CREATE UNIQUE INDEX uq_subject_prerequisites_tenant_id_subject_id_prer_af55c281 ON acd.subject_prerequisites USING btree (tenant_id, subject_id, prerequisite_subject_id, relation, curriculum_version_id) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.subject_versions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `subject_id` | `uuid` | **NO** | — |
| `version_no` | `integer` | **NO** | — |
| `effective_from` | `date` | YES | — |
| `effective_to` | `date` | YES | — |
| `credits` | `numeric` | YES | — |
| `syllabus` | `jsonb` | YES | `'{}'::jsonb` |
| `reference_books` | `jsonb` | YES | `'{}'::jsonb` |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_subject_versions_credits` | CHECK | `CHECK ((credits >= (0)::numeric))` |
| `ck_subject_versions_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'APPROVED'::text, 'ACTIVE'::text, 'SUPERSEDED'::text])))` |
| `ck_subject_versions_version_no` | CHECK | `CHECK ((version_no > 0))` |
| `fk_subject_versions_subject_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE CASCADE` |
| `fk_subject_versions_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `subject_versions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_subject_versions_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `subject_versions_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.manage'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.manage'::text, true) AS subjects_in_scope))))` |
| `subject_versions_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.write'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.write'::text, true) AS subjects_in_scope))))` |
| `subject_versions_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.view'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.view'::text, false) AS subjects_in_scope))))` |
| `subject_versions_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.write'::text) AS perm_all) OR (subject_id IN ( SELECT core.subjects_in_scope('acd04.write'::text, true) AS subjects_in_scope))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_subject_versions_subject_id` | `CREATE INDEX ix_subject_versions_subject_id ON acd.subject_versions USING btree (tenant_id, subject_id)` |
| `subject_versions_pkey` | `CREATE UNIQUE INDEX subject_versions_pkey ON acd.subject_versions USING btree (id)` |
| `uq_subject_versions_tenant_id` | `CREATE UNIQUE INDEX uq_subject_versions_tenant_id ON acd.subject_versions USING btree (tenant_id, id)` |
| `uq_subject_versions_tenant_id_subject_id_version_no` | `CREATE UNIQUE INDEX uq_subject_versions_tenant_id_subject_id_version_no ON acd.subject_versions USING btree (tenant_id, subject_id, version_no) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.subjects`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `department_id` | `uuid` | YES | — |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `subject_type` | `text` | **NO** | `'THEORY'::text` |
| `category_id` | `uuid` | YES | — |
| `credits` | `numeric` | YES | — |
| `lecture_hours` | `smallint` | YES | — |
| `tutorial_hours` | `smallint` | YES | — |
| `practical_hours` | `smallint` | YES | — |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `metadata` | `jsonb` | YES | `'{}'::jsonb` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_subjects_credits` | CHECK | `CHECK ((credits >= (0)::numeric))` |
| `ck_subjects_lecture_hours` | CHECK | `CHECK ((lecture_hours >= 0))` |
| `ck_subjects_practical_hours` | CHECK | `CHECK ((practical_hours >= 0))` |
| `ck_subjects_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])))` |
| `ck_subjects_subject_type` | CHECK | `CHECK ((subject_type = ANY (ARRAY['THEORY'::text, 'LAB'::text, 'PROJECT'::text, 'SEMINAR'::text, 'INTERNSHIP'::text, 'MOOC'::text, 'AUDIT'::text])))` |
| `ck_subjects_tutorial_hours` | CHECK | `CHECK ((tutorial_hours >= 0))` |
| `fk_subjects_category_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, category_id) REFERENCES core.lookup_values(tenant_id, id) ON DELETE RESTRICT` |
| `fk_subjects_department_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT` |
| `fk_subjects_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `subjects_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_subjects_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `subjects_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.manage'::text) AS perm_all) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('acd04.manage'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('acd04.manage'::text) AS has_permission)))))` |
| `subjects_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.write'::text) AS perm_all) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('acd04.write'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('acd04.write'::text) AS has_permission)))))` |
| `subjects_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.view'::text) AS perm_all) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('acd04.view'::text)) AS unnest)) OR ((NOT false) AND (department_id IS NULL) AND ( SELECT iam.has_permission('acd04.view'::text) AS has_permission)))))` |
| `subjects_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd04.write'::text) AS perm_all) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('acd04.write'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('acd04.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_subjects_category_id` | `CREATE INDEX ix_subjects_category_id ON acd.subjects USING btree (tenant_id, category_id)` |
| `ix_subjects_tenant_id_department_id_status` | `CREATE INDEX ix_subjects_tenant_id_department_id_status ON acd.subjects USING btree (tenant_id, department_id, status)` |
| `subjects_pkey` | `CREATE UNIQUE INDEX subjects_pkey ON acd.subjects USING btree (id)` |
| `uq_subjects_tenant_id` | `CREATE UNIQUE INDEX uq_subjects_tenant_id ON acd.subjects USING btree (tenant_id, id)` |
| `uq_subjects_tenant_id_code` | `CREATE UNIQUE INDEX uq_subjects_tenant_id_code ON acd.subjects USING btree (tenant_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.timetable_conflicts`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `timetable_version_id` | `uuid` | **NO** | — |
| `conflict_type` | `text` | **NO** | — |
| `entry_a_id` | `uuid` | YES | — |
| `entry_b_id` | `uuid` | YES | — |
| `severity` | `text` | **NO** | `'ERROR'::text` |
| `detail` | `jsonb` | YES | — |
| `resolved_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_timetable_conflicts_conflict_type` | CHECK | `CHECK ((conflict_type = ANY (ARRAY['FACULTY'::text, 'ROOM'::text, 'BATCH'::text, 'CAPACITY'::text, 'OTHER'::text])))` |
| `ck_timetable_conflicts_severity` | CHECK | `CHECK ((severity = ANY (ARRAY['WARNING'::text, 'ERROR'::text])))` |
| `fk_timetable_conflicts_entry_a_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, entry_a_id) REFERENCES acd.timetable_entries(tenant_id, id) ON DELETE RESTRICT` |
| `fk_timetable_conflicts_entry_b_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, entry_b_id) REFERENCES acd.timetable_entries(tenant_id, id) ON DELETE RESTRICT` |
| `fk_timetable_conflicts_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_timetable_conflicts_timetable_version_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, timetable_version_id) REFERENCES acd.timetable_versions(tenant_id, id) ON DELETE CASCADE` |
| `timetable_conflicts_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_timetable_conflicts_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `timetable_conflicts_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.manage'::text) AS perm_all) OR (timetable_version_id IN ( SELECT iam.sc_acd_timetable_versions('acd09.manage'::text, true) AS sc_acd_timetable_versions))))` |
| `timetable_conflicts_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR (timetable_version_id IN ( SELECT iam.sc_acd_timetable_versions('acd09.write'::text, true) AS sc_acd_timetable_versions))))` |
| `timetable_conflicts_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.view'::text) AS perm_all) OR (timetable_version_id IN ( SELECT iam.sc_acd_timetable_versions('acd09.view'::text, false) AS sc_acd_timetable_versions))))` |
| `timetable_conflicts_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR (timetable_version_id IN ( SELECT iam.sc_acd_timetable_versions('acd09.write'::text, true) AS sc_acd_timetable_versions))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_timetable_conflicts_entry_a_id` | `CREATE INDEX ix_timetable_conflicts_entry_a_id ON acd.timetable_conflicts USING btree (tenant_id, entry_a_id)` |
| `ix_timetable_conflicts_entry_b_id` | `CREATE INDEX ix_timetable_conflicts_entry_b_id ON acd.timetable_conflicts USING btree (tenant_id, entry_b_id)` |
| `ix_timetable_conflicts_timetable_version_id` | `CREATE INDEX ix_timetable_conflicts_timetable_version_id ON acd.timetable_conflicts USING btree (tenant_id, timetable_version_id)` |
| `timetable_conflicts_pkey` | `CREATE UNIQUE INDEX timetable_conflicts_pkey ON acd.timetable_conflicts USING btree (id)` |
| `uq_timetable_conflicts_tenant_id` | `CREATE UNIQUE INDEX uq_timetable_conflicts_tenant_id ON acd.timetable_conflicts USING btree (tenant_id, id)` |

---

### Table: `acd.timetable_entries`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `timetable_version_id` | `uuid` | **NO** | — |
| `section_id` | `uuid` | YES | — |
| `subject_id` | `uuid` | YES | — |
| `employee_id` | `uuid` | YES | — |
| `room_id` | `uuid` | YES | — |
| `day_of_week` | `smallint` | **NO** | — |
| `start_time` | `time without time zone` | **NO** | — |
| `end_time` | `time without time zone` | **NO** | — |
| `entry_type` | `text` | **NO** | `'LECTURE'::text` |
| `valid_from` | `date` | YES | — |
| `valid_to` | `date` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_timetable_entries_day_of_week` | CHECK | `CHECK ((day_of_week >= 0))` |
| `ck_timetable_entries_entry_type` | CHECK | `CHECK ((entry_type = ANY (ARRAY['LECTURE'::text, 'LAB'::text, 'TUTORIAL'::text, 'SEMINAR'::text, 'BREAK'::text])))` |
| `ck_timetable_entries_x1` | CHECK | `CHECK ((end_time > start_time))` |
| `ck_timetable_entries_x2` | CHECK | `CHECK (((day_of_week >= 0) AND (day_of_week <= 6)))` |
| `fk_timetable_entries_employee_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT` |
| `fk_timetable_entries_room_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, room_id) REFERENCES core.rooms(tenant_id, id) ON DELETE RESTRICT` |
| `fk_timetable_entries_section_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, section_id) REFERENCES acd.course_sections(tenant_id, id) ON DELETE RESTRICT` |
| `fk_timetable_entries_subject_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT` |
| `fk_timetable_entries_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_timetable_entries_timetable_version_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, timetable_version_id) REFERENCES acd.timetable_versions(tenant_id, id) ON DELETE CASCADE` |
| `timetable_entries_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_timetable_entries_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |
| `ex_tte_employee` | x | `EXCLUDE USING gist (tenant_id WITH =, timetable_version_id WITH =, employee_id WITH =, day_of_week WITH =, int4range((EXTRACT(epoch FROM start_time))::integer, (EXTRACT(epoch FROM end_time))::integer) WITH &&) WHERE (((employee_id IS NOT NULL) AND (deleted_at IS NULL) AND (entry_type <> 'BREAK'::text)))` |
| `ex_tte_room` | x | `EXCLUDE USING gist (tenant_id WITH =, timetable_version_id WITH =, room_id WITH =, day_of_week WITH =, int4range((EXTRACT(epoch FROM start_time))::integer, (EXTRACT(epoch FROM end_time))::integer) WITH &&) WHERE (((room_id IS NOT NULL) AND (deleted_at IS NULL) AND (entry_type <> 'BREAK'::text)))` |
| `ex_tte_section` | x | `EXCLUDE USING gist (tenant_id WITH =, timetable_version_id WITH =, section_id WITH =, day_of_week WITH =, int4range((EXTRACT(epoch FROM start_time))::integer, (EXTRACT(epoch FROM end_time))::integer) WITH &&) WHERE (((section_id IS NOT NULL) AND (deleted_at IS NULL) AND (entry_type <> 'BREAK'::text)))` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_guard_tt_entry` | BEFORE | INSERT | Standard |
| `trg_guard_tt_entry` | BEFORE | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `timetable_entries_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.manage'::text) AS perm_all) OR ((section_id IN ( SELECT core.sections_in_scope('acd09.manage'::text, true) AS sections_in_scope)) OR ((NOT true) AND (section_id IS NULL) AND ( SELECT iam.has_permission('acd09.manage'::text) AS has_permission))) OR ((subject_id IN ( SELECT core.subjects_in_scope('acd09.manage'::text, true) AS subjects_in_scope)) OR ((NOT true) AND (subject_id IS NULL) AND ( SELECT iam.has_permission('acd09.manage'::text) AS has_permission))) OR ((employee_id IN ( SELECT core.employees_in_scope('acd09.manage'::text, true) AS employees_in_scope)) OR ((NOT true) AND (employee_id IS NULL) AND ( SELECT iam.has_permission('acd09.manage'::text) AS has_permission)))))` |
| `timetable_entries_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR ((section_id IN ( SELECT core.sections_in_scope('acd09.write'::text, true) AS sections_in_scope)) OR ((NOT true) AND (section_id IS NULL) AND ( SELECT iam.has_permission('acd09.write'::text) AS has_permission))) OR ((subject_id IN ( SELECT core.subjects_in_scope('acd09.write'::text, true) AS subjects_in_scope)) OR ((NOT true) AND (subject_id IS NULL) AND ( SELECT iam.has_permission('acd09.write'::text) AS has_permission))) OR ((employee_id IN ( SELECT core.employees_in_scope('acd09.write'::text, true) AS employees_in_scope)) OR ((NOT true) AND (employee_id IS NULL) AND ( SELECT iam.has_permission('acd09.write'::text) AS has_permission)))))` |
| `timetable_entries_rfilter` | `SELECT` | `{authenticated}` | `(( SELECT iam.has_permission('acd09.view.all'::text) AS has_permission) OR ((timetable_version_id IN ( SELECT timetable_versions.id
   FROM acd.timetable_versions
  WHERE (timetable_versions.status = 'PUBLISHED'::text))) OR ((section_id IN ( SELECT core.faculty_section_ids() AS faculty_section_ids)) OR (employee_id = ( SELECT core.current_employee_id() AS current_employee_id)))))` |
| `timetable_entries_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.view'::text) AS perm_all) OR ((section_id IN ( SELECT core.sections_in_scope('acd09.view'::text, false) AS sections_in_scope)) OR ((NOT false) AND (section_id IS NULL) AND ( SELECT iam.has_permission('acd09.view'::text) AS has_permission))) OR ((subject_id IN ( SELECT core.subjects_in_scope('acd09.view'::text, false) AS subjects_in_scope)) OR ((NOT false) AND (subject_id IS NULL) AND ( SELECT iam.has_permission('acd09.view'::text) AS has_permission))) OR ((employee_id IN ( SELECT core.employees_in_scope('acd09.view'::text, false) AS employees_in_scope)) OR ((NOT false) AND (employee_id IS NULL) AND ( SELECT iam.has_permission('acd09.view'::text) AS has_permission))) OR (( SELECT iam.has_permission('acd09.view.own'::text) AS has_permission) AND (( SELECT iam.perm_all('acd09.view.own'::text) AS perm_all) OR ((section_id IN ( SELECT core.sections_in_scope('acd09.view.own'::text, false) AS sections_in_scope)) OR ((NOT false) AND (section_id IS NULL) AND ( SELECT iam.has_permission('acd09.view.own'::text) AS has_permission))) OR ((subject_id IN ( SELECT core.subjects_in_scope('acd09.view.own'::text, false) AS subjects_in_scope)) OR ((NOT false) AND (subject_id IS NULL) AND ( SELECT iam.has_permission('acd09.view.own'::text) AS has_permission))) OR ((employee_id IN ( SELECT core.employees_in_scope('acd09.view.own'::text, false) AS employees_in_scope)) OR ((NOT false) AND (employee_id IS NULL) AND ( SELECT iam.has_permission('acd09.view.own'::text) AS has_permission)))) AND ((section_id IN ( SELECT core.faculty_section_ids() AS faculty_section_ids)) OR (employee_id = ( SELECT core.current_employee_id() AS current_employee_id)))) OR ((section_id IN ( SELECT core.my_section_ids() AS my_section_ids)) AND (timetable_version_id IN ( SELECT timetable_versions.id
   FROM acd.timetable_versions
  WHERE (timetable_versions.status = 'PUBLISHED'::text))))))` |
| `timetable_entries_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR ((section_id IN ( SELECT core.sections_in_scope('acd09.write'::text, true) AS sections_in_scope)) OR ((NOT true) AND (section_id IS NULL) AND ( SELECT iam.has_permission('acd09.write'::text) AS has_permission))) OR ((subject_id IN ( SELECT core.subjects_in_scope('acd09.write'::text, true) AS subjects_in_scope)) OR ((NOT true) AND (subject_id IS NULL) AND ( SELECT iam.has_permission('acd09.write'::text) AS has_permission))) OR ((employee_id IN ( SELECT core.employees_in_scope('acd09.write'::text, true) AS employees_in_scope)) OR ((NOT true) AND (employee_id IS NULL) AND ( SELECT iam.has_permission('acd09.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ex_tte_employee` | `CREATE INDEX ex_tte_employee ON acd.timetable_entries USING gist (tenant_id, timetable_version_id, employee_id, day_of_week, int4range((EXTRACT(epoch FROM start_time))::integer, (EXTRACT(epoch FROM end_time))::integer)) WHERE ((employee_id IS NOT NULL) AND (deleted_at IS NULL) AND (entry_type <> 'BREAK'::text))` |
| `ex_tte_room` | `CREATE INDEX ex_tte_room ON acd.timetable_entries USING gist (tenant_id, timetable_version_id, room_id, day_of_week, int4range((EXTRACT(epoch FROM start_time))::integer, (EXTRACT(epoch FROM end_time))::integer)) WHERE ((room_id IS NOT NULL) AND (deleted_at IS NULL) AND (entry_type <> 'BREAK'::text))` |
| `ex_tte_section` | `CREATE INDEX ex_tte_section ON acd.timetable_entries USING gist (tenant_id, timetable_version_id, section_id, day_of_week, int4range((EXTRACT(epoch FROM start_time))::integer, (EXTRACT(epoch FROM end_time))::integer)) WHERE ((section_id IS NOT NULL) AND (deleted_at IS NULL) AND (entry_type <> 'BREAK'::text))` |
| `ix_timetable_entries_subject_id` | `CREATE INDEX ix_timetable_entries_subject_id ON acd.timetable_entries USING btree (tenant_id, subject_id)` |
| `ix_timetable_entries_tenant_id_employee_id_day_of__af7e4356` | `CREATE INDEX ix_timetable_entries_tenant_id_employee_id_day_of__af7e4356 ON acd.timetable_entries USING btree (tenant_id, employee_id, day_of_week, start_time)` |
| `ix_timetable_entries_tenant_id_room_id_day_of_week_4f241504` | `CREATE INDEX ix_timetable_entries_tenant_id_room_id_day_of_week_4f241504 ON acd.timetable_entries USING btree (tenant_id, room_id, day_of_week, start_time)` |
| `ix_timetable_entries_tenant_id_section_id` | `CREATE INDEX ix_timetable_entries_tenant_id_section_id ON acd.timetable_entries USING btree (tenant_id, section_id)` |
| `ix_timetable_entries_timetable_version_id` | `CREATE INDEX ix_timetable_entries_timetable_version_id ON acd.timetable_entries USING btree (tenant_id, timetable_version_id)` |
| `timetable_entries_pkey` | `CREATE UNIQUE INDEX timetable_entries_pkey ON acd.timetable_entries USING btree (id)` |
| `uq_timetable_entries_tenant_id` | `CREATE UNIQUE INDEX uq_timetable_entries_tenant_id ON acd.timetable_entries USING btree (tenant_id, id)` |

---

### Table: `acd.timetable_versions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `timetable_id` | `uuid` | **NO** | — |
| `version_no` | `integer` | **NO** | — |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `published_at` | `timestamp with time zone` | YES | — |
| `change_note` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_timetable_versions_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text, 'SUPERSEDED'::text])))` |
| `ck_timetable_versions_version_no` | CHECK | `CHECK ((version_no > 0))` |
| `fk_timetable_versions_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_timetable_versions_timetable_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, timetable_id) REFERENCES acd.timetables(tenant_id, id) ON DELETE CASCADE` |
| `timetable_versions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_timetable_versions_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `timetable_versions_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.manage'::text) AS perm_all) OR (timetable_id IN ( SELECT iam.sc_acd_timetables('acd09.manage'::text, true) AS sc_acd_timetables))))` |
| `timetable_versions_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR (timetable_id IN ( SELECT iam.sc_acd_timetables('acd09.write'::text, true) AS sc_acd_timetables))))` |
| `timetable_versions_rfilter` | `SELECT` | `{authenticated}` | `(( SELECT iam.has_permission('acd09.view.all'::text) AS has_permission) OR (status = 'PUBLISHED'::text))` |
| `timetable_versions_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.view'::text) AS perm_all) OR (timetable_id IN ( SELECT iam.sc_acd_timetables('acd09.view'::text, false) AS sc_acd_timetables))))` |
| `timetable_versions_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR (timetable_id IN ( SELECT iam.sc_acd_timetables('acd09.write'::text, true) AS sc_acd_timetables))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_timetable_versions_timetable_id` | `CREATE INDEX ix_timetable_versions_timetable_id ON acd.timetable_versions USING btree (tenant_id, timetable_id)` |
| `timetable_versions_pkey` | `CREATE UNIQUE INDEX timetable_versions_pkey ON acd.timetable_versions USING btree (id)` |
| `uq_timetable_versions_tenant_id` | `CREATE UNIQUE INDEX uq_timetable_versions_tenant_id ON acd.timetable_versions USING btree (tenant_id, id)` |
| `uq_timetable_versions_tenant_id_timetable_id_version_no` | `CREATE UNIQUE INDEX uq_timetable_versions_tenant_id_timetable_id_version_no ON acd.timetable_versions USING btree (tenant_id, timetable_id, version_no) WHERE (deleted_at IS NULL)` |

---

### Table: `acd.timetables`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `college_id` | `uuid` | **NO** | — |
| `term_id` | `uuid` | **NO** | — |
| `batch_id` | `uuid` | YES | — |
| `name` | `text` | **NO** | — |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_timetables_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text, 'ARCHIVED'::text])))` |
| `fk_timetables_batch_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, batch_id) REFERENCES acd.batches(tenant_id, id) ON DELETE RESTRICT` |
| `fk_timetables_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_timetables_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_timetables_term_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, term_id) REFERENCES acd.calendar_terms(tenant_id, id) ON DELETE RESTRICT` |
| `timetables_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_timetables_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `timetables_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.manage'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd09.manage'::text)) AS unnest))))` |
| `timetables_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd09.write'::text)) AS unnest))))` |
| `timetables_rfilter` | `SELECT` | `{authenticated}` | `(( SELECT iam.has_permission('acd09.view.all'::text) AS has_permission) OR (status = 'PUBLISHED'::text))` |
| `timetables_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.view'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd09.view'::text)) AS unnest))))` |
| `timetables_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd09.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('acd09.write'::text)) AS unnest))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_timetables_batch_id` | `CREATE INDEX ix_timetables_batch_id ON acd.timetables USING btree (tenant_id, batch_id)` |
| `ix_timetables_college_id` | `CREATE INDEX ix_timetables_college_id ON acd.timetables USING btree (tenant_id, college_id)` |
| `ix_timetables_term_id` | `CREATE INDEX ix_timetables_term_id ON acd.timetables USING btree (tenant_id, term_id)` |
| `timetables_pkey` | `CREATE UNIQUE INDEX timetables_pkey ON acd.timetables USING btree (id)` |
| `uq_timetables_tenant_id` | `CREATE UNIQUE INDEX uq_timetables_tenant_id ON acd.timetables USING btree (tenant_id, id)` |

---

## Schema: `core`

### Table: `core.academic_years`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `code` | `text` | **NO** | — |
| `start_date` | `date` | **NO** | — |
| `end_date` | `date` | **NO** | — |
| `is_current` | `boolean` | **NO** | `false` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_academic_years_x1` | CHECK | `CHECK ((end_date > start_date))` |
| `fk_academic_years_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `academic_years_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_academic_years_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `academic_years_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('acd01.manage'::text) AS has_permission))` |
| `academic_years_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('acd01.write'::text) AS has_permission))` |
| `academic_years_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('acd01.view'::text) AS has_permission))` |
| `academic_years_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('acd01.write'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `academic_years_pkey` | `CREATE UNIQUE INDEX academic_years_pkey ON core.academic_years USING btree (id)` |
| `uq_academic_years_tenant_id` | `CREATE UNIQUE INDEX uq_academic_years_tenant_id ON core.academic_years USING btree (tenant_id, id)` |
| `uq_academic_years_tenant_id_code` | `CREATE UNIQUE INDEX uq_academic_years_tenant_id_code ON core.academic_years USING btree (tenant_id, code) WHERE (deleted_at IS NULL)` |
| `uq_academic_years_tenant_id_is_current` | `CREATE UNIQUE INDEX uq_academic_years_tenant_id_is_current ON core.academic_years USING btree (tenant_id, is_current) WHERE ((deleted_at IS NULL) AND is_current)` |

---

### Table: `core.addresses`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `line1` | `text` | **NO** | — |
| `line2` | `text` | YES | — |
| `landmark` | `text` | YES | — |
| `city` | `text` | YES | — |
| `district` | `text` | YES | — |
| `state_code` | `text` | YES | — |
| `postal_code` | `text` | YES | — |
| `country_code` | `character(3)` | **NO** | `'IND'::bpchar` |
| `latitude` | `numeric` | YES | — |
| `longitude` | `numeric` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_addresses_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `addresses_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_addresses_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `addresses_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |
| `addresses_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |
| `addresses_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.pii.read'::text) AS has_permission))` |
| `addresses_student_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (created_by = auth.uid()))` |
| `addresses_student_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ((created_by = auth.uid()) OR (id IN ( SELECT persons.permanent_address_id
   FROM core.persons
  WHERE (persons.id = ( SELECT core.current_person_id() AS current_person_id))
UNION
 SELECT persons.current_address_id
   FROM core.persons
  WHERE (persons.id = ( SELECT core.current_person_id() AS current_person_id))))))` |
| `addresses_student_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ((created_by = auth.uid()) OR (id IN ( SELECT persons.permanent_address_id
   FROM core.persons
  WHERE (persons.id = ( SELECT core.current_person_id() AS current_person_id))
UNION
 SELECT persons.current_address_id
   FROM core.persons
  WHERE (persons.id = ( SELECT core.current_person_id() AS current_person_id))))))` |
| `addresses_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `addresses_pkey` | `CREATE UNIQUE INDEX addresses_pkey ON core.addresses USING btree (id)` |
| `uq_addresses_tenant_id` | `CREATE UNIQUE INDEX uq_addresses_tenant_id ON core.addresses USING btree (tenant_id, id)` |

---

### Table: `core.calendar_events`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `calendar_id` | `uuid` | **NO** | — |
| `event_type` | `text` | **NO** | — |
| `title` | `text` | **NO** | — |
| `description` | `text` | YES | — |
| `start_date` | `date` | **NO** | — |
| `end_date` | `date` | **NO** | — |
| `is_holiday` | `boolean` | **NO** | `false` |
| `applies_to` | `jsonb` | YES | `'{}'::jsonb` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_calendar_events_event_type` | CHECK | `CHECK ((event_type = ANY (ARRAY['HOLIDAY'::text, 'TERM_START'::text, 'TERM_END'::text, 'EXAM'::text, 'COMMENCEMENT'::text, 'EVENT'::text, 'DEADLINE'::text, 'OTHER'::text])))` |
| `ck_calendar_events_x1` | CHECK | `CHECK ((end_date >= start_date))` |
| `fk_calendar_events_calendar_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, calendar_id) REFERENCES core.calendars(tenant_id, id) ON DELETE CASCADE` |
| `fk_calendar_events_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `calendar_events_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_calendar_events_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `calendar_events_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.manage'::text) AS perm_all) OR (calendar_id IN ( SELECT iam.sc_core_calendars('acd01.manage'::text, true) AS sc_core_calendars))))` |
| `calendar_events_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.write'::text) AS perm_all) OR (calendar_id IN ( SELECT iam.sc_core_calendars('acd01.write'::text, true) AS sc_core_calendars))))` |
| `calendar_events_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.view'::text) AS perm_all) OR (calendar_id IN ( SELECT iam.sc_core_calendars('acd01.view'::text, false) AS sc_core_calendars))))` |
| `calendar_events_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.write'::text) AS perm_all) OR (calendar_id IN ( SELECT iam.sc_core_calendars('acd01.write'::text, true) AS sc_core_calendars))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `calendar_events_pkey` | `CREATE UNIQUE INDEX calendar_events_pkey ON core.calendar_events USING btree (id)` |
| `ix_calendar_events_tenant_id_calendar_id_start_date` | `CREATE INDEX ix_calendar_events_tenant_id_calendar_id_start_date ON core.calendar_events USING btree (tenant_id, calendar_id, start_date)` |
| `ix_calendar_events_tenant_id_start_date_end_date` | `CREATE INDEX ix_calendar_events_tenant_id_start_date_end_date ON core.calendar_events USING btree (tenant_id, start_date, end_date)` |
| `uq_calendar_events_tenant_id` | `CREATE UNIQUE INDEX uq_calendar_events_tenant_id ON core.calendar_events USING btree (tenant_id, id)` |

---

### Table: `core.calendar_working_days`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `calendar_id` | `uuid` | **NO** | — |
| `day_of_week` | `smallint` | **NO** | — |
| `is_working` | `boolean` | **NO** | `true` |
| `is_half_day` | `boolean` | **NO** | `false` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_calendar_working_days_day_of_week` | CHECK | `CHECK ((day_of_week >= 0))` |
| `ck_calendar_working_days_x1` | CHECK | `CHECK (((day_of_week >= 0) AND (day_of_week <= 6)))` |
| `fk_calendar_working_days_calendar_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, calendar_id) REFERENCES core.calendars(tenant_id, id) ON DELETE CASCADE` |
| `fk_calendar_working_days_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `calendar_working_days_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_calendar_working_days_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `calendar_working_days_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.manage'::text) AS perm_all) OR (calendar_id IN ( SELECT iam.sc_core_calendars('acd01.manage'::text, true) AS sc_core_calendars))))` |
| `calendar_working_days_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.write'::text) AS perm_all) OR (calendar_id IN ( SELECT iam.sc_core_calendars('acd01.write'::text, true) AS sc_core_calendars))))` |
| `calendar_working_days_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.view'::text) AS perm_all) OR (calendar_id IN ( SELECT iam.sc_core_calendars('acd01.view'::text, false) AS sc_core_calendars))))` |
| `calendar_working_days_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.write'::text) AS perm_all) OR (calendar_id IN ( SELECT iam.sc_core_calendars('acd01.write'::text, true) AS sc_core_calendars))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `calendar_working_days_pkey` | `CREATE UNIQUE INDEX calendar_working_days_pkey ON core.calendar_working_days USING btree (id)` |
| `ix_calendar_working_days_calendar_id` | `CREATE INDEX ix_calendar_working_days_calendar_id ON core.calendar_working_days USING btree (tenant_id, calendar_id)` |
| `uq_calendar_working_days_tenant_id` | `CREATE UNIQUE INDEX uq_calendar_working_days_tenant_id ON core.calendar_working_days USING btree (tenant_id, id)` |
| `uq_calendar_working_days_tenant_id_calendar_id_day_of_week` | `CREATE UNIQUE INDEX uq_calendar_working_days_tenant_id_calendar_id_day_of_week ON core.calendar_working_days USING btree (tenant_id, calendar_id, day_of_week) WHERE (deleted_at IS NULL)` |

---

### Table: `core.calendars`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `college_id` | `uuid` | YES | — |
| `academic_year_id` | `uuid` | YES | — |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `calendar_type` | `text` | **NO** | — |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `published_at` | `timestamp with time zone` | YES | — |
| `week_start_day` | `smallint` | **NO** | `1` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_calendars_calendar_type` | CHECK | `CHECK ((calendar_type = ANY (ARRAY['ACADEMIC'::text, 'HOLIDAY'::text, 'EXAM'::text, 'ADMINISTRATIVE'::text])))` |
| `ck_calendars_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text, 'ARCHIVED'::text])))` |
| `fk_calendars_academic_year_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, academic_year_id) REFERENCES core.academic_years(tenant_id, id) ON DELETE RESTRICT` |
| `fk_calendars_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_calendars_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `calendars_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_calendars_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `calendars_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.manage'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('acd01.manage'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('acd01.manage'::text) AS has_permission)))))` |
| `calendars_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('acd01.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('acd01.write'::text) AS has_permission)))))` |
| `calendars_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.view'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('acd01.view'::text)) AS unnest)) OR ((NOT false) AND (college_id IS NULL) AND ( SELECT iam.has_permission('acd01.view'::text) AS has_permission)))))` |
| `calendars_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('acd01.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('acd01.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('acd01.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `calendars_pkey` | `CREATE UNIQUE INDEX calendars_pkey ON core.calendars USING btree (id)` |
| `ix_calendars_academic_year_id` | `CREATE INDEX ix_calendars_academic_year_id ON core.calendars USING btree (tenant_id, academic_year_id)` |
| `ix_calendars_tenant_id_college_id_calendar_type_ac_6cbad949` | `CREATE INDEX ix_calendars_tenant_id_college_id_calendar_type_ac_6cbad949 ON core.calendars USING btree (tenant_id, college_id, calendar_type, academic_year_id)` |
| `uq_calendars_tenant_id` | `CREATE UNIQUE INDEX uq_calendars_tenant_id ON core.calendars USING btree (tenant_id, id)` |
| `uq_calendars_tenant_id_code` | `CREATE UNIQUE INDEX uq_calendars_tenant_id_code ON core.calendars USING btree (tenant_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `core.campuses`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `college_id` | `uuid` | **NO** | — |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `address_id` | `uuid` | YES | — |
| `is_primary` | `boolean` | **NO** | `false` |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_campuses_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'INACTIVE'::text, 'ARCHIVED'::text])))` |
| `fk_campuses_address_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, address_id) REFERENCES core.addresses(tenant_id, id) ON DELETE RESTRICT` |
| `fk_campuses_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE CASCADE` |
| `fk_campuses_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `campuses_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_campuses_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `campuses_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm02.manage'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('adm02.manage'::text)) AS unnest))))` |
| `campuses_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm02.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('adm02.write'::text)) AS unnest))))` |
| `campuses_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm02.view'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('adm02.view'::text)) AS unnest))))` |
| `campuses_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm02.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('adm02.write'::text)) AS unnest))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `campuses_pkey` | `CREATE UNIQUE INDEX campuses_pkey ON core.campuses USING btree (id)` |
| `ix_campuses_address_id` | `CREATE INDEX ix_campuses_address_id ON core.campuses USING btree (tenant_id, address_id)` |
| `ix_campuses_college_id` | `CREATE INDEX ix_campuses_college_id ON core.campuses USING btree (tenant_id, college_id)` |
| `uq_campuses_tenant_id` | `CREATE UNIQUE INDEX uq_campuses_tenant_id ON core.campuses USING btree (tenant_id, id)` |
| `uq_campuses_tenant_id_college_id_code` | `CREATE UNIQUE INDEX uq_campuses_tenant_id_college_id_code ON core.campuses USING btree (tenant_id, college_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `core.college_accreditations`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `college_id` | `uuid` | **NO** | — |
| `body` | `text` | **NO** | — |
| `grade_or_status` | `text` | YES | — |
| `score` | `numeric` | YES | — |
| `reference_no` | `text` | YES | — |
| `valid_from` | `date` | YES | — |
| `valid_to` | `date` | YES | — |
| `certificate_document_id` | `uuid` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_college_accreditations_body` | CHECK | `CHECK ((body = ANY (ARRAY['NAAC'::text, 'NBA'::text, 'UGC'::text, 'AICTE'::text, 'NIRF'::text, 'ISO'::text, 'OTHER'::text])))` |
| `ck_college_accreditations_x1` | CHECK | `CHECK (((valid_to IS NULL) OR (valid_from IS NULL) OR (valid_to >= valid_from)))` |
| `fk_college_accreditations_certificate_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, certificate_document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT` |
| `fk_college_accreditations_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE CASCADE` |
| `fk_college_accreditations_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `college_accreditations_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_college_accreditations_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `college_accreditations_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm06.manage'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('adm06.manage'::text)) AS unnest))))` |
| `college_accreditations_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm06.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('adm06.write'::text)) AS unnest))))` |
| `college_accreditations_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm06.view'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('adm06.view'::text)) AS unnest))))` |
| `college_accreditations_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm06.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('adm06.write'::text)) AS unnest))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `college_accreditations_pkey` | `CREATE UNIQUE INDEX college_accreditations_pkey ON core.college_accreditations USING btree (id)` |
| `ix_college_accreditations_certificate_document_id` | `CREATE INDEX ix_college_accreditations_certificate_document_id ON core.college_accreditations USING btree (tenant_id, certificate_document_id)` |
| `ix_college_accreditations_tenant_id_college_id_body` | `CREATE INDEX ix_college_accreditations_tenant_id_college_id_body ON core.college_accreditations USING btree (tenant_id, college_id, body)` |
| `uq_college_accreditations_tenant_id` | `CREATE UNIQUE INDEX uq_college_accreditations_tenant_id ON core.college_accreditations USING btree (tenant_id, id)` |

---

### Table: `core.colleges`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `legal_name` | `text` | YES | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `provisioning_status` | `text` | **NO** | `'REQUESTED'::text` |
| `affiliating_university` | `text` | YES | — |
| `established_year` | `smallint` | YES | — |
| `address_id` | `uuid` | YES | — |
| `email` | `text` | YES | — |
| `phone` | `text` | YES | — |
| `website` | `text` | YES | — |
| `profile` | `jsonb` | YES | `'{}'::jsonb` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_colleges_provisioning_status` | CHECK | `CHECK ((provisioning_status = ANY (ARRAY['REQUESTED'::text, 'VALIDATED'::text, 'PROVISIONING'::text, 'ACTIVE'::text, 'SUSPENDED'::text, 'FAILED'::text])))` |
| `ck_colleges_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'INACTIVE'::text, 'ARCHIVED'::text])))` |
| `ck_colleges_x1` | CHECK | `CHECK ((code ~ '^[A-Z0-9_-]{2,30}$'::text))` |
| `fk_colleges_address_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, address_id) REFERENCES core.addresses(tenant_id, id) ON DELETE RESTRICT` |
| `fk_colleges_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `colleges_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_colleges_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `colleges_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm06.manage'::text) AS perm_all) OR (id IN ( SELECT unnest(iam.perm_colleges('adm06.manage'::text)) AS unnest))))` |
| `colleges_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm06.write'::text) AS perm_all) OR (id IN ( SELECT unnest(iam.perm_colleges('adm06.write'::text)) AS unnest))))` |
| `colleges_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm06.view'::text) AS perm_all) OR (id IN ( SELECT unnest(iam.perm_colleges('adm06.view'::text)) AS unnest))))` |
| `colleges_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm06.write'::text) AS perm_all) OR (id IN ( SELECT unnest(iam.perm_colleges('adm06.write'::text)) AS unnest))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `colleges_pkey` | `CREATE UNIQUE INDEX colleges_pkey ON core.colleges USING btree (id)` |
| `ix_colleges_address_id` | `CREATE INDEX ix_colleges_address_id ON core.colleges USING btree (tenant_id, address_id)` |
| `uq_colleges_tenant_id` | `CREATE UNIQUE INDEX uq_colleges_tenant_id ON core.colleges USING btree (tenant_id, id)` |
| `uq_colleges_tenant_id_code` | `CREATE UNIQUE INDEX uq_colleges_tenant_id_code ON core.colleges USING btree (tenant_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `core.comments`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `entity_type` | `text` | **NO** | — |
| `entity_id` | `uuid` | **NO** | — |
| `body` | `text` | **NO** | — |
| `is_internal` | `boolean` | **NO** | `true` |
| `parent_comment_id` | `uuid` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_comments_parent_comment_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, parent_comment_id) REFERENCES core.comments(tenant_id, id) ON DELETE RESTRICT` |
| `fk_comments_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `comments_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_comments_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `comments_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.comment'::text) AS has_permission))` |
| `comments_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.comment'::text) AS has_permission))` |
| `comments_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND true)` |
| `comments_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.comment'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `comments_pkey` | `CREATE UNIQUE INDEX comments_pkey ON core.comments USING btree (id)` |
| `ix_comments_parent_comment_id` | `CREATE INDEX ix_comments_parent_comment_id ON core.comments USING btree (tenant_id, parent_comment_id)` |
| `ix_comments_tenant_id_entity_type_entity_id_created_at` | `CREATE INDEX ix_comments_tenant_id_entity_type_entity_id_created_at ON core.comments USING btree (tenant_id, entity_type, entity_id, created_at)` |
| `uq_comments_tenant_id` | `CREATE UNIQUE INDEX uq_comments_tenant_id ON core.comments USING btree (tenant_id, id)` |

---

### Table: `core.departments`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `college_id` | `uuid` | **NO** | — |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `parent_department_id` | `uuid` | YES | — |
| `head_employee_id` | `uuid` | YES | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_departments_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'RETIRED'::text])))` |
| `fk_departments_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_departments_head_employee_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, head_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT` |
| `fk_departments_parent_department_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, parent_department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT` |
| `fk_departments_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `departments_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_departments_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `departments_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm07.manage'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('adm07.manage'::text)) AS unnest))))` |
| `departments_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm07.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('adm07.write'::text)) AS unnest))))` |
| `departments_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm07.view'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('adm07.view'::text)) AS unnest))))` |
| `departments_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm07.write'::text) AS perm_all) OR (college_id IN ( SELECT unnest(iam.perm_colleges('adm07.write'::text)) AS unnest))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `departments_pkey` | `CREATE UNIQUE INDEX departments_pkey ON core.departments USING btree (id)` |
| `ix_departments_head_employee_id` | `CREATE INDEX ix_departments_head_employee_id ON core.departments USING btree (tenant_id, head_employee_id)` |
| `ix_departments_parent_department_id` | `CREATE INDEX ix_departments_parent_department_id ON core.departments USING btree (tenant_id, parent_department_id)` |
| `ix_departments_tenant_id_college_id_parent_department_id` | `CREATE INDEX ix_departments_tenant_id_college_id_parent_department_id ON core.departments USING btree (tenant_id, college_id, parent_department_id)` |
| `uq_departments_tenant_id` | `CREATE UNIQUE INDEX uq_departments_tenant_id ON core.departments USING btree (tenant_id, id)` |
| `uq_departments_tenant_id_college_id_code` | `CREATE UNIQUE INDEX uq_departments_tenant_id_college_id_code ON core.departments USING btree (tenant_id, college_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `core.entity_tags`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `tag_id` | `uuid` | **NO** | — |
| `entity_type` | `text` | **NO** | — |
| `entity_id` | `uuid` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_entity_tags_tag_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, tag_id) REFERENCES core.tags(tenant_id, id) ON DELETE CASCADE` |
| `fk_entity_tags_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `entity_tags_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_entity_tags_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `entity_tags_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |
| `entity_tags_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |
| `entity_tags_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND true)` |
| `entity_tags_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `entity_tags_pkey` | `CREATE UNIQUE INDEX entity_tags_pkey ON core.entity_tags USING btree (id)` |
| `ix_entity_tags_tenant_id_entity_type_entity_id` | `CREATE INDEX ix_entity_tags_tenant_id_entity_type_entity_id ON core.entity_tags USING btree (tenant_id, entity_type, entity_id)` |
| `uq_entity_tags_tenant_id` | `CREATE UNIQUE INDEX uq_entity_tags_tenant_id ON core.entity_tags USING btree (tenant_id, id)` |
| `uq_entity_tags_tenant_id_tag_id_entity_type_entity_id` | `CREATE UNIQUE INDEX uq_entity_tags_tenant_id_tag_id_entity_type_entity_id ON core.entity_tags USING btree (tenant_id, tag_id, entity_type, entity_id)` |

---

### Table: `core.entity_versions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `entity_type` | `text` | **NO** | — |
| `entity_id` | `uuid` | **NO** | — |
| `version_no` | `integer` | **NO** | — |
| `status` | `text` | YES | — |
| `effective_from` | `timestamp with time zone` | YES | — |
| `effective_to` | `timestamp with time zone` | YES | — |
| `change_reason` | `text` | YES | — |
| `snapshot` | `jsonb` | **NO** | `'{}'::jsonb` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_entity_versions_version_no` | CHECK | `CHECK ((version_no > 0))` |
| `fk_entity_versions_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `entity_versions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_entity_versions_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_immutable` | BEFORE | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_immutable` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `entity_versions_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('audit.read'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `entity_versions_pkey` | `CREATE UNIQUE INDEX entity_versions_pkey ON core.entity_versions USING btree (id)` |
| `gx_entity_versions_snapshot` | `CREATE INDEX gx_entity_versions_snapshot ON core.entity_versions USING gin (snapshot)` |
| `ix_entity_versions_tenant_id_entity_type_entity_id_7502a837` | `CREATE INDEX ix_entity_versions_tenant_id_entity_type_entity_id_7502a837 ON core.entity_versions USING btree (tenant_id, entity_type, entity_id, version_no DESC)` |
| `uq_entity_versions_tenant_id` | `CREATE UNIQUE INDEX uq_entity_versions_tenant_id ON core.entity_versions USING btree (tenant_id, id)` |

---

### Table: `core.lookup_types`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `is_system` | `boolean` | **NO** | `false` |
| `description` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_lookup_types_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `lookup_types_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_lookup_types_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `lookup_types_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |
| `lookup_types_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |
| `lookup_types_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND true)` |
| `lookup_types_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `lookup_types_pkey` | `CREATE UNIQUE INDEX lookup_types_pkey ON core.lookup_types USING btree (id)` |
| `uq_lookup_types_tenant_id` | `CREATE UNIQUE INDEX uq_lookup_types_tenant_id ON core.lookup_types USING btree (tenant_id, id)` |
| `uq_lookup_types_tenant_id_code` | `CREATE UNIQUE INDEX uq_lookup_types_tenant_id_code ON core.lookup_types USING btree (tenant_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `core.lookup_values`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `lookup_type_id` | `uuid` | **NO** | — |
| `code` | `text` | **NO** | — |
| `label` | `text` | **NO** | — |
| `sort_order` | `integer` | **NO** | `0` |
| `is_active` | `boolean` | **NO** | `true` |
| `attrs` | `jsonb` | YES | `'{}'::jsonb` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_lookup_values_lookup_type_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, lookup_type_id) REFERENCES core.lookup_types(tenant_id, id) ON DELETE CASCADE` |
| `fk_lookup_values_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `lookup_values_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_lookup_values_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `lookup_values_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |
| `lookup_values_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |
| `lookup_values_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND true)` |
| `lookup_values_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_lookup_values_lookup_type_id` | `CREATE INDEX ix_lookup_values_lookup_type_id ON core.lookup_values USING btree (tenant_id, lookup_type_id)` |
| `lookup_values_pkey` | `CREATE UNIQUE INDEX lookup_values_pkey ON core.lookup_values USING btree (id)` |
| `uq_lookup_values_tenant_id` | `CREATE UNIQUE INDEX uq_lookup_values_tenant_id ON core.lookup_values USING btree (tenant_id, id)` |
| `uq_lookup_values_tenant_id_lookup_type_id_code` | `CREATE UNIQUE INDEX uq_lookup_values_tenant_id_lookup_type_id_code ON core.lookup_values USING btree (tenant_id, lookup_type_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `core.number_sequences`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `scope_key` | `text` | **NO** | — |
| `prefix` | `text` | YES | — |
| `suffix` | `text` | YES | — |
| `next_value` | `bigint` | **NO** | `1` |
| `padding` | `smallint` | **NO** | `6` |
| `reset_policy` | `text` | **NO** | `'NEVER'::text` |
| `last_reset_on` | `date` | YES | — |
| `required_permission` | `text` | YES | — |
| `college_id` | `uuid` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_number_sequences_next_value` | CHECK | `CHECK ((next_value >= 0))` |
| `ck_number_sequences_reset_policy` | CHECK | `CHECK ((reset_policy = ANY (ARRAY['NEVER'::text, 'YEARLY'::text, 'MONTHLY'::text, 'ACADEMIC_YEAR'::text])))` |
| `fk_number_sequences_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_number_sequences_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `number_sequences_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_number_sequences_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `number_sequences_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm06.manage'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm06.manage'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm06.manage'::text) AS has_permission)))))` |
| `number_sequences_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm06.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm06.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm06.write'::text) AS has_permission)))))` |
| `number_sequences_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm06.view'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm06.view'::text)) AS unnest)) OR ((NOT false) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm06.view'::text) AS has_permission)))))` |
| `number_sequences_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm06.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm06.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm06.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_number_sequences_college_id` | `CREATE INDEX ix_number_sequences_college_id ON core.number_sequences USING btree (tenant_id, college_id)` |
| `number_sequences_pkey` | `CREATE UNIQUE INDEX number_sequences_pkey ON core.number_sequences USING btree (id)` |
| `uq_number_sequences_tenant_id` | `CREATE UNIQUE INDEX uq_number_sequences_tenant_id ON core.number_sequences USING btree (tenant_id, id)` |
| `uq_number_sequences_tenant_id_scope_key_college_id` | `CREATE UNIQUE INDEX uq_number_sequences_tenant_id_scope_key_college_id ON core.number_sequences USING btree (tenant_id, scope_key, college_id) WHERE (deleted_at IS NULL)` |

---

### Table: `core.person_contacts`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `person_id` | `uuid` | **NO** | — |
| `contact_type` | `text` | **NO** | — |
| `value` | `text` | **NO** | — |
| `is_primary` | `boolean` | **NO** | `false` |
| `is_verified` | `boolean` | **NO** | `false` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_person_contacts_contact_type` | CHECK | `CHECK ((contact_type = ANY (ARRAY['EMAIL'::text, 'PHONE'::text, 'ALT_PHONE'::text, 'WHATSAPP'::text])))` |
| `fk_person_contacts_person_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE` |
| `fk_person_contacts_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `person_contacts_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_person_contacts_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `person_contacts_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |
| `person_contacts_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |
| `person_contacts_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.read'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.read'::text, false) AS sc_core_persons)) OR (person_id = ( SELECT core.current_person_id() AS current_person_id))))` |
| `person_contacts_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_person_contacts_person_id` | `CREATE INDEX ix_person_contacts_person_id ON core.person_contacts USING btree (tenant_id, person_id)` |
| `person_contacts_pkey` | `CREATE UNIQUE INDEX person_contacts_pkey ON core.person_contacts USING btree (id)` |
| `uq_person_contacts_tenant_id` | `CREATE UNIQUE INDEX uq_person_contacts_tenant_id ON core.person_contacts USING btree (tenant_id, id)` |
| `uq_person_contacts_tenant_id_person_id_contact_type_value` | `CREATE UNIQUE INDEX uq_person_contacts_tenant_id_person_id_contact_type_value ON core.person_contacts USING btree (tenant_id, person_id, contact_type, value) WHERE (deleted_at IS NULL)` |

---

### Table: `core.person_emergency_contacts`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `person_id` | `uuid` | **NO** | — |
| `name` | `text` | **NO** | — |
| `relationship` | `text` | **NO** | — |
| `phone` | `text` | **NO** | — |
| `alt_phone` | `text` | YES | — |
| `priority` | `smallint` | **NO** | `1` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_person_emergency_contacts_person_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE` |
| `fk_person_emergency_contacts_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `person_emergency_contacts_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_person_emergency_contacts_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `person_emergency_contacts_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |
| `person_emergency_contacts_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |
| `person_emergency_contacts_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.read'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.read'::text, false) AS sc_core_persons)) OR (person_id = ( SELECT core.current_person_id() AS current_person_id))))` |
| `person_emergency_contacts_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_person_emergency_contacts_person_id` | `CREATE INDEX ix_person_emergency_contacts_person_id ON core.person_emergency_contacts USING btree (tenant_id, person_id)` |
| `person_emergency_contacts_pkey` | `CREATE UNIQUE INDEX person_emergency_contacts_pkey ON core.person_emergency_contacts USING btree (id)` |
| `uq_person_emergency_contacts_tenant_id` | `CREATE UNIQUE INDEX uq_person_emergency_contacts_tenant_id ON core.person_emergency_contacts USING btree (tenant_id, id)` |

---

### Table: `core.person_experience`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `person_id` | `uuid` | **NO** | — |
| `organisation` | `text` | **NO** | — |
| `designation` | `text` | YES | — |
| `from_date` | `date` | YES | — |
| `to_date` | `date` | YES | — |
| `is_teaching` | `boolean` | **NO** | `false` |
| `responsibilities` | `text` | YES | — |
| `document_id` | `uuid` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_person_experience_x1` | CHECK | `CHECK (((to_date IS NULL) OR (from_date IS NULL) OR (to_date >= from_date)))` |
| `fk_person_experience_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT` |
| `fk_person_experience_person_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE` |
| `fk_person_experience_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `person_experience_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_person_experience_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `person_experience_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |
| `person_experience_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |
| `person_experience_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.read'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.read'::text, false) AS sc_core_persons)) OR (person_id = ( SELECT core.current_person_id() AS current_person_id))))` |
| `person_experience_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_person_experience_document_id` | `CREATE INDEX ix_person_experience_document_id ON core.person_experience USING btree (tenant_id, document_id)` |
| `ix_person_experience_person_id` | `CREATE INDEX ix_person_experience_person_id ON core.person_experience USING btree (tenant_id, person_id)` |
| `person_experience_pkey` | `CREATE UNIQUE INDEX person_experience_pkey ON core.person_experience USING btree (id)` |
| `uq_person_experience_tenant_id` | `CREATE UNIQUE INDEX uq_person_experience_tenant_id ON core.person_experience USING btree (tenant_id, id)` |

---

### Table: `core.person_identifiers`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `person_id` | `uuid` | **NO** | — |
| `id_type` | `text` | **NO** | — |
| `masked_value` | `text` | YES | — |
| `value_ciphertext` | `bytea` | YES | — |
| `value_hash` | `bytea` | YES | — |
| `issuing_authority` | `text` | YES | — |
| `valid_from` | `date` | YES | — |
| `valid_to` | `date` | YES | — |
| `is_verified` | `boolean` | **NO** | `false` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_person_identifiers_id_type` | CHECK | `CHECK ((id_type = ANY (ARRAY['AADHAAR'::text, 'PAN'::text, 'PASSPORT'::text, 'DRIVING_LICENSE'::text, 'VOTER_ID'::text, 'UAN'::text, 'ESI'::text, 'APAAR'::text, 'OTHER'::text])))` |
| `fk_person_identifiers_person_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE` |
| `fk_person_identifiers_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `person_identifiers_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_person_identifiers_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `person_identifiers_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |
| `person_identifiers_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |
| `person_identifiers_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.read'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.read'::text, false) AS sc_core_persons))))` |
| `person_identifiers_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_person_identifiers_person_id` | `CREATE INDEX ix_person_identifiers_person_id ON core.person_identifiers USING btree (tenant_id, person_id)` |
| `person_identifiers_pkey` | `CREATE UNIQUE INDEX person_identifiers_pkey ON core.person_identifiers USING btree (id)` |
| `uq_person_identifiers_tenant_id` | `CREATE UNIQUE INDEX uq_person_identifiers_tenant_id ON core.person_identifiers USING btree (tenant_id, id)` |
| `uq_person_identifiers_tenant_id_id_type_value_hash` | `CREATE UNIQUE INDEX uq_person_identifiers_tenant_id_id_type_value_hash ON core.person_identifiers USING btree (tenant_id, id_type, value_hash) WHERE ((deleted_at IS NULL) AND (value_hash IS NOT NULL))` |
| `uq_person_identifiers_tenant_id_person_id_id_type` | `CREATE UNIQUE INDEX uq_person_identifiers_tenant_id_person_id_id_type ON core.person_identifiers USING btree (tenant_id, person_id, id_type) WHERE (deleted_at IS NULL)` |

---

### Table: `core.person_qualifications`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `person_id` | `uuid` | **NO** | — |
| `level_code` | `text` | **NO** | — |
| `degree` | `text` | **NO** | — |
| `specialization` | `text` | YES | — |
| `institution` | `text` | YES | — |
| `board_or_university` | `text` | YES | — |
| `year_of_passing` | `smallint` | YES | — |
| `grade_or_percentage` | `text` | YES | — |
| `document_id` | `uuid` | YES | — |
| `is_verified` | `boolean` | **NO** | `false` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_person_qualifications_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT` |
| `fk_person_qualifications_person_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE` |
| `fk_person_qualifications_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `person_qualifications_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_person_qualifications_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `person_qualifications_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |
| `person_qualifications_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |
| `person_qualifications_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.read'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.read'::text, false) AS sc_core_persons)) OR (person_id = ( SELECT core.current_person_id() AS current_person_id))))` |
| `person_qualifications_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR (person_id IN ( SELECT iam.sc_core_persons('core.pii.write'::text, true) AS sc_core_persons))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_person_qualifications_document_id` | `CREATE INDEX ix_person_qualifications_document_id ON core.person_qualifications USING btree (tenant_id, document_id)` |
| `ix_person_qualifications_tenant_id_person_id_level_code` | `CREATE INDEX ix_person_qualifications_tenant_id_person_id_level_code ON core.person_qualifications USING btree (tenant_id, person_id, level_code)` |
| `person_qualifications_pkey` | `CREATE UNIQUE INDEX person_qualifications_pkey ON core.person_qualifications USING btree (id)` |
| `uq_person_qualifications_tenant_id` | `CREATE UNIQUE INDEX uq_person_qualifications_tenant_id ON core.person_qualifications USING btree (tenant_id, id)` |

---

### Table: `core.persons`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `first_name` | `text` | **NO** | — |
| `middle_name` | `text` | YES | — |
| `last_name` | `text` | YES | — |
| `display_name` | `text` | **NO** | — |
| `date_of_birth` | `date` | YES | — |
| `gender` | `text` | YES | — |
| `blood_group` | `text` | YES | — |
| `nationality` | `text` | YES | — |
| `marital_status` | `text` | YES | — |
| `primary_email` | `text` | YES | — |
| `primary_phone` | `text` | YES | — |
| `photo_document_id` | `uuid` | YES | — |
| `permanent_address_id` | `uuid` | YES | — |
| `current_address_id` | `uuid` | YES | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_persons_gender` | CHECK | `CHECK ((gender = ANY (ARRAY['M'::text, 'F'::text, 'O'::text, 'U'::text])))` |
| `ck_persons_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'INACTIVE'::text])))` |
| `fk_persons_current_address_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, current_address_id) REFERENCES core.addresses(tenant_id, id) ON DELETE RESTRICT` |
| `fk_persons_permanent_address_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, permanent_address_id) REFERENCES core.addresses(tenant_id, id) ON DELETE RESTRICT` |
| `fk_persons_photo_document_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, photo_document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT` |
| `fk_persons_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `persons_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_persons_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_guard_student_person_update` | BEFORE | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `persons_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR ((photo_document_id IN ( SELECT iam.sc_doc_documents('core.pii.write'::text, true) AS sc_doc_documents)) OR ((NOT true) AND (photo_document_id IS NULL) AND ( SELECT iam.has_permission('core.pii.write'::text) AS has_permission)))))` |
| `persons_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR ((photo_document_id IN ( SELECT iam.sc_doc_documents('core.pii.write'::text, true) AS sc_doc_documents)) OR ((photo_document_id IS NULL) AND ( SELECT iam.has_permission('core.pii.write'::text) AS has_permission)))))` |
| `persons_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.read'::text) AS perm_all) OR ((photo_document_id IN ( SELECT iam.sc_doc_documents('core.pii.read'::text, false) AS sc_doc_documents)) OR ((NOT false) AND (photo_document_id IS NULL) AND ( SELECT iam.has_permission('core.pii.read'::text) AS has_permission))) OR (id = ( SELECT core.current_person_id() AS current_person_id))))` |
| `persons_student_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (id = ( SELECT core.current_person_id() AS current_person_id)))` |
| `persons_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.pii.write'::text) AS perm_all) OR ((photo_document_id IN ( SELECT iam.sc_doc_documents('core.pii.write'::text, true) AS sc_doc_documents)) OR ((NOT true) AND (photo_document_id IS NULL) AND ( SELECT iam.has_permission('core.pii.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_persons_current_address_id` | `CREATE INDEX ix_persons_current_address_id ON core.persons USING btree (tenant_id, current_address_id)` |
| `ix_persons_permanent_address_id` | `CREATE INDEX ix_persons_permanent_address_id ON core.persons USING btree (tenant_id, permanent_address_id)` |
| `ix_persons_photo_document_id` | `CREATE INDEX ix_persons_photo_document_id ON core.persons USING btree (tenant_id, photo_document_id)` |
| `ix_persons_tenant_id_display_name` | `CREATE INDEX ix_persons_tenant_id_display_name ON core.persons USING btree (tenant_id, display_name)` |
| `ix_persons_tenant_id_primary_email` | `CREATE INDEX ix_persons_tenant_id_primary_email ON core.persons USING btree (tenant_id, primary_email)` |
| `ix_persons_tenant_id_primary_phone` | `CREATE INDEX ix_persons_tenant_id_primary_phone ON core.persons USING btree (tenant_id, primary_phone)` |
| `persons_pkey` | `CREATE UNIQUE INDEX persons_pkey ON core.persons USING btree (id)` |
| `uq_persons_tenant_id` | `CREATE UNIQUE INDEX uq_persons_tenant_id ON core.persons USING btree (tenant_id, id)` |

---

### Table: `core.publications`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `entity_type` | `text` | **NO** | — |
| `entity_id` | `uuid` | **NO** | — |
| `version_no` | `integer` | **NO** | `1` |
| `status` | `text` | **NO** | `'PUBLISHED'::text` |
| `audience` | `jsonb` | YES | `'{}'::jsonb` |
| `published_by` | `uuid` | YES | — |
| `published_at` | `timestamp with time zone` | YES | — |
| `unpublished_at` | `timestamp with time zone` | YES | — |
| `notes` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_publications_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SCHEDULED'::text, 'PUBLISHED'::text, 'UNPUBLISHED'::text, 'WITHDRAWN'::text])))` |
| `fk_publications_published_by` | FOREIGN KEY | `FOREIGN KEY (tenant_id, published_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_publications_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `publications_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_publications_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `publications_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.write'::text) AS perm_all) OR ((published_by IN ( SELECT iam.sc_iam_user_profiles('core.write'::text, true) AS sc_iam_user_profiles)) OR ((NOT true) AND (published_by IS NULL) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission)))))` |
| `publications_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.write'::text) AS perm_all) OR ((published_by IN ( SELECT iam.sc_iam_user_profiles('core.write'::text, true) AS sc_iam_user_profiles)) OR ((NOT true) AND (published_by IS NULL) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission)))))` |
| `publications_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND true)` |
| `publications_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('core.write'::text) AS perm_all) OR ((published_by IN ( SELECT iam.sc_iam_user_profiles('core.write'::text, true) AS sc_iam_user_profiles)) OR ((NOT true) AND (published_by IS NULL) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_publications_published_by` | `CREATE INDEX ix_publications_published_by ON core.publications USING btree (tenant_id, published_by)` |
| `ix_publications_tenant_id_entity_type_entity_id_published_at` | `CREATE INDEX ix_publications_tenant_id_entity_type_entity_id_published_at ON core.publications USING btree (tenant_id, entity_type, entity_id, published_at DESC)` |
| `publications_pkey` | `CREATE UNIQUE INDEX publications_pkey ON core.publications USING btree (id)` |
| `uq_publications_tenant_id` | `CREATE UNIQUE INDEX uq_publications_tenant_id ON core.publications USING btree (tenant_id, id)` |

---

### Table: `core.rooms`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `campus_id` | `uuid` | **NO** | — |
| `building` | `text` | YES | — |
| `code` | `text` | **NO** | — |
| `name` | `text` | YES | — |
| `room_type` | `text` | **NO** | `'CLASSROOM'::text` |
| `capacity` | `integer` | YES | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_rooms_capacity` | CHECK | `CHECK ((capacity >= 0))` |
| `ck_rooms_room_type` | CHECK | `CHECK ((room_type = ANY (ARRAY['CLASSROOM'::text, 'LAB'::text, 'SEMINAR'::text, 'AUDITORIUM'::text, 'EXAM_HALL'::text, 'OFFICE'::text, 'OTHER'::text])))` |
| `ck_rooms_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'INACTIVE'::text, 'MAINTENANCE'::text])))` |
| `fk_rooms_campus_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, campus_id) REFERENCES core.campuses(tenant_id, id) ON DELETE RESTRICT` |
| `fk_rooms_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `rooms_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_rooms_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `rooms_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm02.manage'::text) AS perm_all) OR (campus_id IN ( SELECT iam.sc_core_campuses('adm02.manage'::text, true) AS sc_core_campuses))))` |
| `rooms_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm02.write'::text) AS perm_all) OR (campus_id IN ( SELECT iam.sc_core_campuses('adm02.write'::text, true) AS sc_core_campuses))))` |
| `rooms_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm02.view'::text) AS perm_all) OR (campus_id IN ( SELECT iam.sc_core_campuses('adm02.view'::text, false) AS sc_core_campuses))))` |
| `rooms_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm02.write'::text) AS perm_all) OR (campus_id IN ( SELECT iam.sc_core_campuses('adm02.write'::text, true) AS sc_core_campuses))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_rooms_campus_id` | `CREATE INDEX ix_rooms_campus_id ON core.rooms USING btree (tenant_id, campus_id)` |
| `rooms_pkey` | `CREATE UNIQUE INDEX rooms_pkey ON core.rooms USING btree (id)` |
| `uq_rooms_tenant_id` | `CREATE UNIQUE INDEX uq_rooms_tenant_id ON core.rooms USING btree (tenant_id, id)` |
| `uq_rooms_tenant_id_campus_id_code` | `CREATE UNIQUE INDEX uq_rooms_tenant_id_campus_id_code ON core.rooms USING btree (tenant_id, campus_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `core.tags`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `code` | `text` | **NO** | — |
| `label` | `text` | **NO** | — |
| `color` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_tags_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `tags_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_tags_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tags_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |
| `tags_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |
| `tags_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND true)` |
| `tags_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('core.write'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `tags_pkey` | `CREATE UNIQUE INDEX tags_pkey ON core.tags USING btree (id)` |
| `uq_tags_tenant_id` | `CREATE UNIQUE INDEX uq_tags_tenant_id ON core.tags USING btree (tenant_id, id)` |
| `uq_tags_tenant_id_code` | `CREATE UNIQUE INDEX uq_tags_tenant_id_code ON core.tags USING btree (tenant_id, code) WHERE (deleted_at IS NULL)` |

---

### Table: `core.tenants`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `legal_name` | `text` | YES | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `currency_code` | `character(3)` | **NO** | `'INR'::bpchar` |
| `timezone` | `text` | **NO** | `'Asia/Kolkata'::text` |
| `locale` | `text` | **NO** | `'en-IN'::text` |
| `profile` | `jsonb` | YES | `'{}'::jsonb` |
| `onboarded_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_tenants_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'INACTIVE'::text, 'ARCHIVED'::text])))` |
| `ck_tenants_x1` | CHECK | `CHECK ((code ~ '^[A-Z0-9_-]{2,30}$'::text))` |
| `tenants_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_seed_roles` | AFTER | INSERT | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenants_platform` | `ALL` | `{authenticated}` | `( SELECT iam.is_platform_admin() AS is_platform_admin)` |
| `tenants_read` | `SELECT` | `{authenticated}` | `((id = ( SELECT core.current_tenant_id() AS current_tenant_id)) OR ( SELECT iam.is_platform_admin() AS is_platform_admin))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `tenants_pkey` | `CREATE UNIQUE INDEX tenants_pkey ON core.tenants USING btree (id)` |
| `uq_tenants_code` | `CREATE UNIQUE INDEX uq_tenants_code ON core.tenants USING btree (code) WHERE (deleted_at IS NULL)` |

---

## Schema: `iam`

### Table: `iam.access_review_items`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `review_id` | `uuid` | **NO** | — |
| `role_assignment_id` | `uuid` | **NO** | — |
| `decision` | `text` | YES | — |
| `decided_at` | `timestamp with time zone` | YES | — |
| `note` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_access_review_items_decision` | CHECK | `CHECK ((decision = ANY (ARRAY['KEEP'::text, 'REVOKE'::text, 'MODIFY'::text])))` |
| `fk_access_review_items_review_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, review_id) REFERENCES iam.access_reviews(tenant_id, id) ON DELETE CASCADE` |
| `fk_access_review_items_role_assignment_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, role_assignment_id) REFERENCES iam.role_assignments(tenant_id, id) ON DELETE CASCADE` |
| `fk_access_review_items_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `access_review_items_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_access_review_items_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `access_review_items_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.manage'::text) AS perm_all) OR (review_id IN ( SELECT iam.sc_iam_access_reviews('adm09.manage'::text, true) AS sc_iam_access_reviews))))` |
| `access_review_items_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR (review_id IN ( SELECT iam.sc_iam_access_reviews('adm09.write'::text, true) AS sc_iam_access_reviews))))` |
| `access_review_items_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.view'::text) AS perm_all) OR (review_id IN ( SELECT iam.sc_iam_access_reviews('adm09.view'::text, false) AS sc_iam_access_reviews))))` |
| `access_review_items_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR (review_id IN ( SELECT iam.sc_iam_access_reviews('adm09.write'::text, true) AS sc_iam_access_reviews))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `access_review_items_pkey` | `CREATE UNIQUE INDEX access_review_items_pkey ON iam.access_review_items USING btree (id)` |
| `ix_access_review_items_review_id` | `CREATE INDEX ix_access_review_items_review_id ON iam.access_review_items USING btree (tenant_id, review_id)` |
| `ix_access_review_items_role_assignment_id` | `CREATE INDEX ix_access_review_items_role_assignment_id ON iam.access_review_items USING btree (tenant_id, role_assignment_id)` |
| `uq_access_review_items_tenant_id` | `CREATE UNIQUE INDEX uq_access_review_items_tenant_id ON iam.access_review_items USING btree (tenant_id, id)` |
| `uq_access_review_items_tenant_id_review_id_role_as_c7696a57` | `CREATE UNIQUE INDEX uq_access_review_items_tenant_id_review_id_role_as_c7696a57 ON iam.access_review_items USING btree (tenant_id, review_id, role_assignment_id) WHERE (deleted_at IS NULL)` |

---

### Table: `iam.access_reviews`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `cycle_name` | `text` | **NO** | — |
| `reviewer_id` | `uuid` | YES | — |
| `status` | `text` | **NO** | `'PENDING'::text` |
| `due_on` | `date` | YES | — |
| `completed_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_access_reviews_status` | CHECK | `CHECK ((status = ANY (ARRAY['PENDING'::text, 'IN_PROGRESS'::text, 'COMPLETED'::text, 'EXPIRED'::text])))` |
| `fk_access_reviews_reviewer_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, reviewer_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_access_reviews_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `access_reviews_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_access_reviews_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `access_reviews_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.manage'::text) AS perm_all) OR ((reviewer_id IN ( SELECT iam.sc_iam_user_profiles('adm09.manage'::text, true) AS sc_iam_user_profiles)) OR ((NOT true) AND (reviewer_id IS NULL) AND ( SELECT iam.has_permission('adm09.manage'::text) AS has_permission)))))` |
| `access_reviews_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR ((reviewer_id IN ( SELECT iam.sc_iam_user_profiles('adm09.write'::text, true) AS sc_iam_user_profiles)) OR ((NOT true) AND (reviewer_id IS NULL) AND ( SELECT iam.has_permission('adm09.write'::text) AS has_permission)))))` |
| `access_reviews_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.view'::text) AS perm_all) OR ((reviewer_id IN ( SELECT iam.sc_iam_user_profiles('adm09.view'::text, false) AS sc_iam_user_profiles)) OR ((NOT false) AND (reviewer_id IS NULL) AND ( SELECT iam.has_permission('adm09.view'::text) AS has_permission)))))` |
| `access_reviews_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR ((reviewer_id IN ( SELECT iam.sc_iam_user_profiles('adm09.write'::text, true) AS sc_iam_user_profiles)) OR ((NOT true) AND (reviewer_id IS NULL) AND ( SELECT iam.has_permission('adm09.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `access_reviews_pkey` | `CREATE UNIQUE INDEX access_reviews_pkey ON iam.access_reviews USING btree (id)` |
| `ix_access_reviews_reviewer_id` | `CREATE INDEX ix_access_reviews_reviewer_id ON iam.access_reviews USING btree (tenant_id, reviewer_id)` |
| `uq_access_reviews_tenant_id` | `CREATE UNIQUE INDEX uq_access_reviews_tenant_id ON iam.access_reviews USING btree (tenant_id, id)` |

---

### Table: `iam.api_client_grants`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `client_id` | `uuid` | **NO** | — |
| `permission_id` | `uuid` | **NO** | — |
| `college_id` | `uuid` | YES | — |
| `department_id` | `uuid` | YES | — |
| `resource_scope` | `jsonb` | YES | `'{}'::jsonb` |
| `valid_to` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_api_client_grants_client_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, client_id) REFERENCES iam.api_clients(tenant_id, id) ON DELETE CASCADE` |
| `fk_api_client_grants_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_api_client_grants_department_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT` |
| `fk_api_client_grants_permission_id` | FOREIGN KEY | `FOREIGN KEY (permission_id) REFERENCES iam.permissions(id) ON DELETE RESTRICT` |
| `fk_api_client_grants_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `api_client_grants_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_api_client_grants_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `api_client_grants_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm03.manage'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm03.manage'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm03.manage'::text) AS has_permission))) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('adm03.manage'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('adm03.manage'::text) AS has_permission)))))` |
| `api_client_grants_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm03.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm03.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm03.write'::text) AS has_permission))) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('adm03.write'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('adm03.write'::text) AS has_permission)))))` |
| `api_client_grants_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm03.view'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm03.view'::text)) AS unnest)) OR ((NOT false) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm03.view'::text) AS has_permission))) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('adm03.view'::text)) AS unnest)) OR ((NOT false) AND (department_id IS NULL) AND ( SELECT iam.has_permission('adm03.view'::text) AS has_permission)))))` |
| `api_client_grants_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm03.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm03.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm03.write'::text) AS has_permission))) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('adm03.write'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('adm03.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `api_client_grants_pkey` | `CREATE UNIQUE INDEX api_client_grants_pkey ON iam.api_client_grants USING btree (id)` |
| `ix_api_client_grants_college_id` | `CREATE INDEX ix_api_client_grants_college_id ON iam.api_client_grants USING btree (tenant_id, college_id)` |
| `ix_api_client_grants_department_id` | `CREATE INDEX ix_api_client_grants_department_id ON iam.api_client_grants USING btree (tenant_id, department_id)` |
| `ix_api_client_grants_permission_id` | `CREATE INDEX ix_api_client_grants_permission_id ON iam.api_client_grants USING btree (permission_id)` |
| `ix_api_client_grants_tenant_id_client_id` | `CREATE INDEX ix_api_client_grants_tenant_id_client_id ON iam.api_client_grants USING btree (tenant_id, client_id)` |
| `uq_api_client_grants_tenant_id` | `CREATE UNIQUE INDEX uq_api_client_grants_tenant_id ON iam.api_client_grants USING btree (tenant_id, id)` |

---

### Table: `iam.api_clients`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `user_id` | `uuid` | **NO** | — |
| `name` | `text` | **NO** | — |
| `owner_user_id` | `uuid` | YES | — |
| `description` | `text` | YES | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `allowed_ips` | `ARRAY` | YES | — |
| `expires_at` | `timestamp with time zone` | YES | — |
| `last_used_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_api_clients_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'REVOKED'::text])))` |
| `fk_api_clients_owner_user_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, owner_user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_api_clients_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_api_clients_user_id` | FOREIGN KEY | `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE RESTRICT` |
| `api_clients_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_api_clients_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `api_clients_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm03.manage'::text) AS perm_all) OR ((owner_user_id IN ( SELECT iam.sc_iam_user_profiles('adm03.manage'::text, true) AS sc_iam_user_profiles)) OR ((NOT true) AND (owner_user_id IS NULL) AND ( SELECT iam.has_permission('adm03.manage'::text) AS has_permission)))))` |
| `api_clients_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm03.write'::text) AS perm_all) OR ((owner_user_id IN ( SELECT iam.sc_iam_user_profiles('adm03.write'::text, true) AS sc_iam_user_profiles)) OR ((NOT true) AND (owner_user_id IS NULL) AND ( SELECT iam.has_permission('adm03.write'::text) AS has_permission)))))` |
| `api_clients_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm03.view'::text) AS perm_all) OR ((owner_user_id IN ( SELECT iam.sc_iam_user_profiles('adm03.view'::text, false) AS sc_iam_user_profiles)) OR ((NOT false) AND (owner_user_id IS NULL) AND ( SELECT iam.has_permission('adm03.view'::text) AS has_permission)))))` |
| `api_clients_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm03.write'::text) AS perm_all) OR ((owner_user_id IN ( SELECT iam.sc_iam_user_profiles('adm03.write'::text, true) AS sc_iam_user_profiles)) OR ((NOT true) AND (owner_user_id IS NULL) AND ( SELECT iam.has_permission('adm03.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `api_clients_pkey` | `CREATE UNIQUE INDEX api_clients_pkey ON iam.api_clients USING btree (id)` |
| `ix_api_clients_owner_user_id` | `CREATE INDEX ix_api_clients_owner_user_id ON iam.api_clients USING btree (tenant_id, owner_user_id)` |
| `ix_api_clients_user_id` | `CREATE INDEX ix_api_clients_user_id ON iam.api_clients USING btree (user_id)` |
| `uq_api_clients_tenant_id` | `CREATE UNIQUE INDEX uq_api_clients_tenant_id ON iam.api_clients USING btree (tenant_id, id)` |
| `uq_api_clients_tenant_id_user_id` | `CREATE UNIQUE INDEX uq_api_clients_tenant_id_user_id ON iam.api_clients USING btree (tenant_id, user_id) WHERE (deleted_at IS NULL)` |

---

### Table: `iam.delegations`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `from_user_id` | `uuid` | **NO** | — |
| `to_user_id` | `uuid` | **NO** | — |
| `role_id` | `uuid` | **NO** | — |
| `valid_from` | `timestamp with time zone` | **NO** | — |
| `valid_to` | `timestamp with time zone` | **NO** | — |
| `reason` | `text` | YES | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_delegations_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'REVOKED'::text, 'EXPIRED'::text])))` |
| `ck_delegations_x1` | CHECK | `CHECK ((valid_to > valid_from))` |
| `fk_delegations_from_user_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, from_user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_delegations_role_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, role_id) REFERENCES iam.roles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_delegations_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_delegations_to_user_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, to_user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `delegations_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_delegations_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_guard_delegation` | BEFORE | INSERT | Standard |
| `trg_guard_delegation` | BEFORE | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `delegations_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.manage'::text) AS perm_all) OR (from_user_id IN ( SELECT iam.sc_iam_user_profiles('adm09.manage'::text, true) AS sc_iam_user_profiles))))` |
| `delegations_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR (from_user_id IN ( SELECT iam.sc_iam_user_profiles('adm09.write'::text, true) AS sc_iam_user_profiles)) OR (( SELECT iam.has_permission('adm09.write.own'::text) AS has_permission) AND (( SELECT iam.perm_all('adm09.write.own'::text) AS perm_all) OR (from_user_id IN ( SELECT iam.sc_iam_user_profiles('adm09.write.own'::text, true) AS sc_iam_user_profiles))) AND (from_user_id = ( SELECT auth.uid() AS uid)))))` |
| `delegations_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.view'::text) AS perm_all) OR (from_user_id IN ( SELECT iam.sc_iam_user_profiles('adm09.view'::text, false) AS sc_iam_user_profiles)) OR (to_user_id = ( SELECT auth.uid() AS uid)) OR (from_user_id = ( SELECT auth.uid() AS uid))))` |
| `delegations_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR (from_user_id IN ( SELECT iam.sc_iam_user_profiles('adm09.write'::text, true) AS sc_iam_user_profiles)) OR (( SELECT iam.has_permission('adm09.write.own'::text) AS has_permission) AND (( SELECT iam.perm_all('adm09.write.own'::text) AS perm_all) OR (from_user_id IN ( SELECT iam.sc_iam_user_profiles('adm09.write.own'::text, true) AS sc_iam_user_profiles))) AND (from_user_id = ( SELECT auth.uid() AS uid)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `delegations_pkey` | `CREATE UNIQUE INDEX delegations_pkey ON iam.delegations USING btree (id)` |
| `ix_delegations_from_user_id` | `CREATE INDEX ix_delegations_from_user_id ON iam.delegations USING btree (tenant_id, from_user_id)` |
| `ix_delegations_role_id` | `CREATE INDEX ix_delegations_role_id ON iam.delegations USING btree (tenant_id, role_id)` |
| `ix_delegations_tenant_id_to_user_id_status` | `CREATE INDEX ix_delegations_tenant_id_to_user_id_status ON iam.delegations USING btree (tenant_id, to_user_id, status)` |
| `uq_delegations_tenant_id` | `CREATE UNIQUE INDEX uq_delegations_tenant_id ON iam.delegations USING btree (tenant_id, id)` |

---

### Table: `iam.department_access`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `user_id` | `uuid` | **NO** | — |
| `department_id` | `uuid` | **NO** | — |
| `access_level` | `text` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_department_access_access_level` | CHECK | `CHECK ((access_level = ANY (ARRAY['READ'::text, 'WRITE'::text, 'ADMIN'::text])))` |
| `fk_department_access_department_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE CASCADE` |
| `fk_department_access_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_department_access_user_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE CASCADE` |
| `department_access_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_department_access_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `department_access_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.manage'::text) AS perm_all) OR (department_id IN ( SELECT unnest(iam.perm_department_ids('adm09.manage'::text)) AS unnest))))` |
| `department_access_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR (department_id IN ( SELECT unnest(iam.perm_department_ids('adm09.write'::text)) AS unnest))))` |
| `department_access_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.view'::text) AS perm_all) OR (department_id IN ( SELECT unnest(iam.perm_department_ids('adm09.view'::text)) AS unnest))))` |
| `department_access_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR (department_id IN ( SELECT unnest(iam.perm_department_ids('adm09.write'::text)) AS unnest))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `department_access_pkey` | `CREATE UNIQUE INDEX department_access_pkey ON iam.department_access USING btree (id)` |
| `ix_department_access_department_id` | `CREATE INDEX ix_department_access_department_id ON iam.department_access USING btree (tenant_id, department_id)` |
| `ix_department_access_user_id` | `CREATE INDEX ix_department_access_user_id ON iam.department_access USING btree (tenant_id, user_id)` |
| `uq_department_access_tenant_id` | `CREATE UNIQUE INDEX uq_department_access_tenant_id ON iam.department_access USING btree (tenant_id, id)` |
| `uq_department_access_tenant_id_user_id_department_id` | `CREATE UNIQUE INDEX uq_department_access_tenant_id_user_id_department_id ON iam.department_access USING btree (tenant_id, user_id, department_id) WHERE (deleted_at IS NULL)` |

---

### Table: `iam.permissions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `code` | `text` | **NO** | — |
| `module` | `text` | **NO** | — |
| `resource` | `text` | YES | — |
| `action` | `text` | **NO** | — |
| `description` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `permissions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `permissions_platform` | `ALL` | `{authenticated}` | `( SELECT iam.is_platform_admin() AS is_platform_admin)` |
| `permissions_read` | `SELECT` | `{authenticated}` | `true` |

#### Indexes
| Index Name | Definition |
|---|---|
| `permissions_pkey` | `CREATE UNIQUE INDEX permissions_pkey ON iam.permissions USING btree (id)` |
| `uq_permissions_code` | `CREATE UNIQUE INDEX uq_permissions_code ON iam.permissions USING btree (code) WHERE (deleted_at IS NULL)` |

---

### Table: `iam.role_assignments`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `user_id` | `uuid` | YES | — |
| `group_id` | `uuid` | YES | — |
| `role_id` | `uuid` | **NO** | — |
| `college_id` | `uuid` | YES | — |
| `department_id` | `uuid` | YES | — |
| `valid_from` | `date` | YES | — |
| `valid_to` | `date` | YES | — |
| `granted_by` | `uuid` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_role_assignments_x1` | CHECK | `CHECK (((user_id IS NULL) <> (group_id IS NULL)))` |
| `ck_role_assignments_x2` | CHECK | `CHECK (((valid_to IS NULL) OR (valid_from IS NULL) OR (valid_to >= valid_from)))` |
| `fk_role_assignments_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_role_assignments_department_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT` |
| `fk_role_assignments_granted_by` | FOREIGN KEY | `FOREIGN KEY (tenant_id, granted_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_role_assignments_group_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, group_id) REFERENCES iam.user_groups(tenant_id, id) ON DELETE CASCADE` |
| `fk_role_assignments_role_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, role_id) REFERENCES iam.roles(tenant_id, id) ON DELETE RESTRICT` |
| `fk_role_assignments_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_role_assignments_user_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE CASCADE` |
| `role_assignments_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_role_assignments_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_guard_role_assignment` | BEFORE | INSERT | Standard |
| `trg_guard_role_assignment` | BEFORE | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_guard_role_assignment` | BEFORE | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `role_assignments_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.manage'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm09.manage'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm09.manage'::text) AS has_permission))) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('adm09.manage'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('adm09.manage'::text) AS has_permission)))))` |
| `role_assignments_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm09.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm09.write'::text) AS has_permission))) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('adm09.write'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('adm09.write'::text) AS has_permission)))))` |
| `role_assignments_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.view'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm09.view'::text)) AS unnest)) OR ((NOT false) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm09.view'::text) AS has_permission))) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('adm09.view'::text)) AS unnest)) OR ((NOT false) AND (department_id IS NULL) AND ( SELECT iam.has_permission('adm09.view'::text) AS has_permission)))))` |
| `role_assignments_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm09.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm09.write'::text) AS has_permission))) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('adm09.write'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('adm09.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_role_assignments_department_id` | `CREATE INDEX ix_role_assignments_department_id ON iam.role_assignments USING btree (tenant_id, department_id)` |
| `ix_role_assignments_granted_by` | `CREATE INDEX ix_role_assignments_granted_by ON iam.role_assignments USING btree (tenant_id, granted_by)` |
| `ix_role_assignments_role_id` | `CREATE INDEX ix_role_assignments_role_id ON iam.role_assignments USING btree (tenant_id, role_id)` |
| `ix_role_assignments_tenant_id_college_id` | `CREATE INDEX ix_role_assignments_tenant_id_college_id ON iam.role_assignments USING btree (tenant_id, college_id)` |
| `ix_role_assignments_tenant_id_group_id_role_id` | `CREATE INDEX ix_role_assignments_tenant_id_group_id_role_id ON iam.role_assignments USING btree (tenant_id, group_id, role_id)` |
| `ix_role_assignments_tenant_id_user_id_role_id` | `CREATE INDEX ix_role_assignments_tenant_id_user_id_role_id ON iam.role_assignments USING btree (tenant_id, user_id, role_id)` |
| `role_assignments_pkey` | `CREATE UNIQUE INDEX role_assignments_pkey ON iam.role_assignments USING btree (id)` |
| `uq_role_assignments_tenant_id` | `CREATE UNIQUE INDEX uq_role_assignments_tenant_id ON iam.role_assignments USING btree (tenant_id, id)` |

---

### Table: `iam.role_grant_rules`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `grantor_role` | `text` | **NO** | — |
| `grantable_role` | `text` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `role_grant_rules_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `role_grant_rules_platform` | `ALL` | `{authenticated}` | `( SELECT iam.is_platform_admin() AS is_platform_admin)` |
| `role_grant_rules_read` | `SELECT` | `{authenticated}` | `true` |

#### Indexes
| Index Name | Definition |
|---|---|
| `role_grant_rules_pkey` | `CREATE UNIQUE INDEX role_grant_rules_pkey ON iam.role_grant_rules USING btree (id)` |
| `uq_role_grant_rules_grantor_role_grantable_role` | `CREATE UNIQUE INDEX uq_role_grant_rules_grantor_role_grantable_role ON iam.role_grant_rules USING btree (grantor_role, grantable_role) WHERE (deleted_at IS NULL)` |

---

### Table: `iam.role_hierarchy`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `parent_role` | `text` | **NO** | — |
| `child_role` | `text` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `role_hierarchy_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `role_hierarchy_platform` | `ALL` | `{authenticated}` | `( SELECT iam.is_platform_admin() AS is_platform_admin)` |
| `role_hierarchy_read` | `SELECT` | `{authenticated}` | `true` |

#### Indexes
| Index Name | Definition |
|---|---|
| `role_hierarchy_pkey` | `CREATE UNIQUE INDEX role_hierarchy_pkey ON iam.role_hierarchy USING btree (id)` |
| `uq_role_hierarchy_parent_role_child_role` | `CREATE UNIQUE INDEX uq_role_hierarchy_parent_role_child_role ON iam.role_hierarchy USING btree (parent_role, child_role) WHERE (deleted_at IS NULL)` |

---

### Table: `iam.role_permissions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `role_id` | `uuid` | **NO** | — |
| `permission_id` | `uuid` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_role_permissions_permission_id` | FOREIGN KEY | `FOREIGN KEY (permission_id) REFERENCES iam.permissions(id) ON DELETE RESTRICT` |
| `fk_role_permissions_role_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, role_id) REFERENCES iam.roles(tenant_id, id) ON DELETE CASCADE` |
| `fk_role_permissions_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `role_permissions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_role_permissions_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_guard_role_permission` | BEFORE | INSERT | Standard |
| `trg_guard_role_permission` | BEFORE | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_guard_role_permission` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `role_permissions_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.manage'::text) AS perm_all) OR (role_id IN ( SELECT iam.sc_iam_roles('adm09.manage'::text, true) AS sc_iam_roles))))` |
| `role_permissions_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR (role_id IN ( SELECT iam.sc_iam_roles('adm09.write'::text, true) AS sc_iam_roles))))` |
| `role_permissions_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.view'::text) AS perm_all) OR (role_id IN ( SELECT iam.sc_iam_roles('adm09.view'::text, false) AS sc_iam_roles))))` |
| `role_permissions_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR (role_id IN ( SELECT iam.sc_iam_roles('adm09.write'::text, true) AS sc_iam_roles))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_role_permissions_permission_id` | `CREATE INDEX ix_role_permissions_permission_id ON iam.role_permissions USING btree (permission_id)` |
| `role_permissions_pkey` | `CREATE UNIQUE INDEX role_permissions_pkey ON iam.role_permissions USING btree (id)` |
| `uq_role_permissions_tenant_id` | `CREATE UNIQUE INDEX uq_role_permissions_tenant_id ON iam.role_permissions USING btree (tenant_id, id)` |
| `uq_role_permissions_tenant_id_role_id_permission_id` | `CREATE UNIQUE INDEX uq_role_permissions_tenant_id_role_id_permission_id ON iam.role_permissions USING btree (tenant_id, role_id, permission_id)` |

---

### Table: `iam.role_template_permissions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `role_code` | `text` | **NO** | — |
| `permission_code` | `text` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `role_template_permissions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `role_template_permissions_platform` | `ALL` | `{authenticated}` | `( SELECT iam.is_platform_admin() AS is_platform_admin)` |
| `role_template_permissions_read` | `SELECT` | `{authenticated}` | `true` |

#### Indexes
| Index Name | Definition |
|---|---|
| `role_template_permissions_pkey` | `CREATE UNIQUE INDEX role_template_permissions_pkey ON iam.role_template_permissions USING btree (id)` |
| `uq_role_template_permissions_role_code_permission_code` | `CREATE UNIQUE INDEX uq_role_template_permissions_role_code_permission_code ON iam.role_template_permissions USING btree (role_code, permission_code) WHERE (deleted_at IS NULL)` |

---

### Table: `iam.role_templates`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `catalogue_id` | `text` | YES | — |
| `kind` | `text` | YES | — |
| `scope_note` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `role_templates_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `role_templates_platform` | `ALL` | `{authenticated}` | `( SELECT iam.is_platform_admin() AS is_platform_admin)` |
| `role_templates_read` | `SELECT` | `{authenticated}` | `true` |

#### Indexes
| Index Name | Definition |
|---|---|
| `role_templates_pkey` | `CREATE UNIQUE INDEX role_templates_pkey ON iam.role_templates USING btree (id)` |
| `uq_role_templates_code` | `CREATE UNIQUE INDEX uq_role_templates_code ON iam.role_templates USING btree (code) WHERE (deleted_at IS NULL)` |

---

### Table: `iam.roles`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `college_id` | `uuid` | YES | — |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `description` | `text` | YES | — |
| `is_system` | `boolean` | **NO** | `false` |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_roles_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'INACTIVE'::text])))` |
| `fk_roles_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_roles_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `roles_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_roles_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_guard_role` | BEFORE | INSERT | Standard |
| `trg_guard_role` | BEFORE | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_guard_role` | BEFORE | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `roles_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.manage'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm09.manage'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm09.manage'::text) AS has_permission)))))` |
| `roles_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm09.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm09.write'::text) AS has_permission)))))` |
| `roles_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.view'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm09.view'::text)) AS unnest)) OR ((NOT false) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm09.view'::text) AS has_permission)))))` |
| `roles_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm09.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm09.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm09.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_roles_college_id` | `CREATE INDEX ix_roles_college_id ON iam.roles USING btree (tenant_id, college_id)` |
| `roles_pkey` | `CREATE UNIQUE INDEX roles_pkey ON iam.roles USING btree (id)` |
| `uq_roles_tenant_id` | `CREATE UNIQUE INDEX uq_roles_tenant_id ON iam.roles USING btree (tenant_id, id)` |
| `uq_roles_tenant_id_code` | `CREATE UNIQUE INDEX uq_roles_tenant_id_code ON iam.roles USING btree (tenant_id, code) WHERE ((deleted_at IS NULL) AND (college_id IS NULL))` |
| `uq_roles_tenant_id_college_id_code` | `CREATE UNIQUE INDEX uq_roles_tenant_id_college_id_code ON iam.roles USING btree (tenant_id, college_id, code) WHERE ((deleted_at IS NULL) AND (college_id IS NOT NULL))` |

---

### Table: `iam.transition_log`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `table_schema` | `text` | **NO** | — |
| `table_name` | `text` | **NO** | — |
| `record_id` | `uuid` | **NO** | — |
| `status_column` | `text` | **NO** | — |
| `from_status` | `text` | YES | — |
| `to_status` | `text` | YES | — |
| `actor_id` | `uuid` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_transition_log_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `transition_log_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_transition_log_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_immutable` | BEFORE | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_immutable` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `transition_log_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('audit.read'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_transition_log_tenant_id_table_schema_table_nam_53c32d47` | `CREATE INDEX ix_transition_log_tenant_id_table_schema_table_nam_53c32d47 ON iam.transition_log USING btree (tenant_id, table_schema, table_name, record_id)` |
| `transition_log_pkey` | `CREATE UNIQUE INDEX transition_log_pkey ON iam.transition_log USING btree (id)` |
| `uq_transition_log_tenant_id` | `CREATE UNIQUE INDEX uq_transition_log_tenant_id ON iam.transition_log USING btree (tenant_id, id)` |

---

### Table: `iam.transition_rules`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `table_schema` | `text` | **NO** | — |
| `table_name` | `text` | **NO** | — |
| `status_column` | `text` | **NO** | `'status'::text` |
| `from_status` | `text` | YES | — |
| `to_status` | `text` | **NO** | — |
| `any_permission` | `ARRAY` | YES | `'{}'::text[]` |
| `sod_distinct_from` | `ARRAY` | YES | `'{}'::text[]` |
| `block_self` | `text` | YES | — |
| `note` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `transition_rules_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `transition_rules_platform` | `ALL` | `{authenticated}` | `( SELECT iam.is_platform_admin() AS is_platform_admin)` |
| `transition_rules_read` | `SELECT` | `{authenticated}` | `true` |

#### Indexes
| Index Name | Definition |
|---|---|
| `transition_rules_pkey` | `CREATE UNIQUE INDEX transition_rules_pkey ON iam.transition_rules USING btree (id)` |
| `uq_transition_rules_table_schema_table_name_status_24c52dd0` | `CREATE UNIQUE INDEX uq_transition_rules_table_schema_table_name_status_24c52dd0 ON iam.transition_rules USING btree (table_schema, table_name, status_column, to_status) WHERE ((deleted_at IS NULL) AND (from_status IS NULL))` |
| `uq_transition_rules_table_schema_table_name_status_6ee18bc5` | `CREATE UNIQUE INDEX uq_transition_rules_table_schema_table_name_status_6ee18bc5 ON iam.transition_rules USING btree (table_schema, table_name, status_column, to_status, from_status) WHERE ((deleted_at IS NULL) AND (from_status IS NOT NULL))` |

---

### Table: `iam.user_group_members`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `group_id` | `uuid` | **NO** | — |
| `user_id` | `uuid` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_user_group_members_group_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, group_id) REFERENCES iam.user_groups(tenant_id, id) ON DELETE CASCADE` |
| `fk_user_group_members_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_user_group_members_user_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE CASCADE` |
| `user_group_members_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_user_group_members_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_guard_group_member` | BEFORE | INSERT | Standard |
| `trg_guard_group_member` | BEFORE | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_guard_group_member` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `user_group_members_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm08.manage'::text) AS perm_all) OR (user_id IN ( SELECT iam.sc_iam_user_profiles('adm08.manage'::text, true) AS sc_iam_user_profiles))))` |
| `user_group_members_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm08.write'::text) AS perm_all) OR (user_id IN ( SELECT iam.sc_iam_user_profiles('adm08.write'::text, true) AS sc_iam_user_profiles))))` |
| `user_group_members_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm08.view'::text) AS perm_all) OR (user_id IN ( SELECT iam.sc_iam_user_profiles('adm08.view'::text, false) AS sc_iam_user_profiles))))` |
| `user_group_members_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm08.write'::text) AS perm_all) OR (user_id IN ( SELECT iam.sc_iam_user_profiles('adm08.write'::text, true) AS sc_iam_user_profiles))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_user_group_members_user_id` | `CREATE INDEX ix_user_group_members_user_id ON iam.user_group_members USING btree (tenant_id, user_id)` |
| `uq_user_group_members_tenant_id` | `CREATE UNIQUE INDEX uq_user_group_members_tenant_id ON iam.user_group_members USING btree (tenant_id, id)` |
| `uq_user_group_members_tenant_id_group_id_user_id` | `CREATE UNIQUE INDEX uq_user_group_members_tenant_id_group_id_user_id ON iam.user_group_members USING btree (tenant_id, group_id, user_id)` |
| `user_group_members_pkey` | `CREATE UNIQUE INDEX user_group_members_pkey ON iam.user_group_members USING btree (id)` |

---

### Table: `iam.user_groups`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `description` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_user_groups_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `user_groups_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_user_groups_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `user_groups_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('adm08.manage'::text) AS has_permission))` |
| `user_groups_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('adm08.write'::text) AS has_permission))` |
| `user_groups_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('adm08.view'::text) AS has_permission))` |
| `user_groups_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('adm08.write'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `uq_user_groups_tenant_id` | `CREATE UNIQUE INDEX uq_user_groups_tenant_id ON iam.user_groups USING btree (tenant_id, id)` |
| `uq_user_groups_tenant_id_code` | `CREATE UNIQUE INDEX uq_user_groups_tenant_id_code ON iam.user_groups USING btree (tenant_id, code) WHERE (deleted_at IS NULL)` |
| `user_groups_pkey` | `CREATE UNIQUE INDEX user_groups_pkey ON iam.user_groups USING btree (id)` |

---

### Table: `iam.user_profiles`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `person_id` | `uuid` | YES | — |
| `college_id` | `uuid` | YES | — |
| `department_id` | `uuid` | YES | — |
| `username` | `text` | YES | — |
| `email` | `text` | **NO** | — |
| `full_name` | `text` | **NO** | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `preferences` | `jsonb` | YES | `'{}'::jsonb` |
| `last_seen_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_user_profiles_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'LOCKED'::text, 'INACTIVE'::text])))` |
| `fk_user_profiles_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_user_profiles_department_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT` |
| `fk_user_profiles_person_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT` |
| `fk_user_profiles_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `user_profiles_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_user_profiles_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `user_profiles_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm08.manage'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm08.manage'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm08.manage'::text) AS has_permission))) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('adm08.manage'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('adm08.manage'::text) AS has_permission)))))` |
| `user_profiles_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm08.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm08.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm08.write'::text) AS has_permission))) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('adm08.write'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('adm08.write'::text) AS has_permission)))))` |
| `user_profiles_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm08.view.all'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm08.view.all'::text)) AS unnest)) OR ((NOT false) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm08.view.all'::text) AS has_permission))) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('adm08.view.all'::text)) AS unnest)) OR ((NOT false) AND (department_id IS NULL) AND ( SELECT iam.has_permission('adm08.view.all'::text) AS has_permission))) OR (id = ( SELECT auth.uid() AS uid))))` |
| `user_profiles_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('adm08.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('adm08.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('adm08.write'::text) AS has_permission))) OR ((department_id IN ( SELECT unnest(iam.perm_department_ids('adm08.write'::text)) AS unnest)) OR ((NOT true) AND (department_id IS NULL) AND ( SELECT iam.has_permission('adm08.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_user_profiles_department_id` | `CREATE INDEX ix_user_profiles_department_id ON iam.user_profiles USING btree (tenant_id, department_id)` |
| `ix_user_profiles_person_id` | `CREATE INDEX ix_user_profiles_person_id ON iam.user_profiles USING btree (tenant_id, person_id)` |
| `ix_user_profiles_tenant_id_college_id_status` | `CREATE INDEX ix_user_profiles_tenant_id_college_id_status ON iam.user_profiles USING btree (tenant_id, college_id, status)` |
| `uq_user_profiles_tenant_id` | `CREATE UNIQUE INDEX uq_user_profiles_tenant_id ON iam.user_profiles USING btree (tenant_id, id)` |
| `uq_user_profiles_tenant_id_email` | `CREATE UNIQUE INDEX uq_user_profiles_tenant_id_email ON iam.user_profiles USING btree (tenant_id, email) WHERE (deleted_at IS NULL)` |
| `uq_user_profiles_tenant_id_person_id` | `CREATE UNIQUE INDEX uq_user_profiles_tenant_id_person_id ON iam.user_profiles USING btree (tenant_id, person_id) WHERE ((deleted_at IS NULL) AND (person_id IS NOT NULL))` |
| `uq_user_profiles_tenant_id_username` | `CREATE UNIQUE INDEX uq_user_profiles_tenant_id_username ON iam.user_profiles USING btree (tenant_id, username) WHERE (deleted_at IS NULL)` |
| `user_profiles_pkey` | `CREATE UNIQUE INDEX user_profiles_pkey ON iam.user_profiles USING btree (id)` |

---

## Schema: `plat`

### Table: `plat.alerts`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_ref_id` | `uuid` | YES | — |
| `severity` | `text` | **NO** | — |
| `title` | `text` | **NO** | — |
| `detail` | `text` | YES | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `acknowledged_by` | `uuid` | YES | — |
| `resolved_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_alerts_severity` | CHECK | `CHECK ((severity = ANY (ARRAY['INFO'::text, 'WARNING'::text, 'ERROR'::text, 'CRITICAL'::text])))` |
| `ck_alerts_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'ACKNOWLEDGED'::text, 'RESOLVED'::text])))` |
| `fk_alerts_tenant_ref_id` | FOREIGN KEY | `FOREIGN KEY (tenant_ref_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `alerts_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `alerts_platform` | `ALL` | `{authenticated}` | `( SELECT iam.is_platform_admin() AS is_platform_admin)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `alerts_pkey` | `CREATE UNIQUE INDEX alerts_pkey ON plat.alerts USING btree (id)` |
| `ix_alerts_status_severity` | `CREATE INDEX ix_alerts_status_severity ON plat.alerts USING btree (status, severity)` |
| `ix_alerts_tenant_ref_id` | `CREATE INDEX ix_alerts_tenant_ref_id ON plat.alerts USING btree (tenant_ref_id)` |

---

### Table: `plat.gateway_transactions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `invoice_id` | `uuid` | YES | — |
| `gateway` | `text` | **NO** | — |
| `gateway_ref` | `text` | YES | — |
| `amount` | `numeric` | **NO** | — |
| `currency_code` | `character(3)` | **NO** | `'INR'::bpchar` |
| `status` | `text` | **NO** | `'PENDING'::text` |
| `raw_response` | `jsonb` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_gateway_transactions_amount` | CHECK | `CHECK ((amount >= (0)::numeric))` |
| `ck_gateway_transactions_status` | CHECK | `CHECK ((status = ANY (ARRAY['PENDING'::text, 'SUCCESS'::text, 'FAILED'::text, 'REFUNDED'::text])))` |
| `fk_gateway_transactions_invoice_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, invoice_id) REFERENCES plat.invoices(tenant_id, id) ON DELETE RESTRICT` |
| `fk_gateway_transactions_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `gateway_transactions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_gateway_transactions_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `gateway_transactions_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |
| `gateway_transactions_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |
| `gateway_transactions_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.read'::text) AS has_permission))` |
| `gateway_transactions_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `gateway_transactions_pkey` | `CREATE UNIQUE INDEX gateway_transactions_pkey ON plat.gateway_transactions USING btree (id)` |
| `ix_gateway_transactions_invoice_id` | `CREATE INDEX ix_gateway_transactions_invoice_id ON plat.gateway_transactions USING btree (tenant_id, invoice_id)` |
| `uq_gateway_transactions_tenant_id` | `CREATE UNIQUE INDEX uq_gateway_transactions_tenant_id ON plat.gateway_transactions USING btree (tenant_id, id)` |
| `uq_gateway_transactions_tenant_id_gateway_gateway_ref` | `CREATE UNIQUE INDEX uq_gateway_transactions_tenant_id_gateway_gateway_ref ON plat.gateway_transactions USING btree (tenant_id, gateway, gateway_ref) WHERE ((deleted_at IS NULL) AND (gateway_ref IS NOT NULL))` |

---

### Table: `plat.invoice_lines`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `invoice_id` | `uuid` | **NO** | — |
| `description` | `text` | **NO** | — |
| `quantity` | `numeric` | **NO** | `1` |
| `unit_price` | `numeric` | **NO** | — |
| `amount` | `numeric` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_invoice_lines_amount` | CHECK | `CHECK ((amount >= (0)::numeric))` |
| `ck_invoice_lines_unit_price` | CHECK | `CHECK ((unit_price >= (0)::numeric))` |
| `fk_invoice_lines_invoice_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, invoice_id) REFERENCES plat.invoices(tenant_id, id) ON DELETE CASCADE` |
| `fk_invoice_lines_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `invoice_lines_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_invoice_lines_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `invoice_lines_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |
| `invoice_lines_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |
| `invoice_lines_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.read'::text) AS has_permission))` |
| `invoice_lines_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `invoice_lines_pkey` | `CREATE UNIQUE INDEX invoice_lines_pkey ON plat.invoice_lines USING btree (id)` |
| `ix_invoice_lines_invoice_id` | `CREATE INDEX ix_invoice_lines_invoice_id ON plat.invoice_lines USING btree (tenant_id, invoice_id)` |
| `uq_invoice_lines_tenant_id` | `CREATE UNIQUE INDEX uq_invoice_lines_tenant_id ON plat.invoice_lines USING btree (tenant_id, id)` |

---

### Table: `plat.invoices`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `subscription_id` | `uuid` | **NO** | — |
| `invoice_number` | `text` | **NO** | — |
| `issued_on` | `date` | YES | — |
| `due_date` | `date` | YES | — |
| `amount` | `numeric` | **NO** | — |
| `currency_code` | `character(3)` | **NO** | `'INR'::bpchar` |
| `status` | `text` | **NO** | `'DRAFT'::text` |
| `paid_at` | `timestamp with time zone` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_invoices_amount` | CHECK | `CHECK ((amount >= (0)::numeric))` |
| `ck_invoices_status` | CHECK | `CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ISSUED'::text, 'PAID'::text, 'VOID'::text, 'UNCOLLECTIBLE'::text])))` |
| `fk_invoices_subscription_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, subscription_id) REFERENCES plat.subscriptions(tenant_id, id) ON DELETE RESTRICT` |
| `fk_invoices_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `invoices_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_invoices_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `invoices_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |
| `invoices_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |
| `invoices_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.read'::text) AS has_permission))` |
| `invoices_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `invoices_pkey` | `CREATE UNIQUE INDEX invoices_pkey ON plat.invoices USING btree (id)` |
| `ix_invoices_subscription_id` | `CREATE INDEX ix_invoices_subscription_id ON plat.invoices USING btree (tenant_id, subscription_id)` |
| `uq_invoices_tenant_id` | `CREATE UNIQUE INDEX uq_invoices_tenant_id ON plat.invoices USING btree (tenant_id, id)` |
| `uq_invoices_tenant_id_invoice_number` | `CREATE UNIQUE INDEX uq_invoices_tenant_id_invoice_number ON plat.invoices USING btree (tenant_id, invoice_number) WHERE (deleted_at IS NULL)` |

---

### Table: `plat.plan_entitlements`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `plan_id` | `uuid` | **NO** | — |
| `entitlement_key` | `text` | **NO** | — |
| `limit_value` | `bigint` | YES | — |
| `is_enabled` | `boolean` | **NO** | `true` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `fk_plan_entitlements_plan_id` | FOREIGN KEY | `FOREIGN KEY (plan_id) REFERENCES plat.subscription_plans(id) ON DELETE CASCADE` |
| `plan_entitlements_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `plan_entitlements_platform` | `ALL` | `{authenticated}` | `( SELECT iam.is_platform_admin() AS is_platform_admin)` |
| `plan_entitlements_read` | `SELECT` | `{authenticated}` | `true` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_plan_entitlements_plan_id` | `CREATE INDEX ix_plan_entitlements_plan_id ON plat.plan_entitlements USING btree (plan_id)` |
| `plan_entitlements_pkey` | `CREATE UNIQUE INDEX plan_entitlements_pkey ON plat.plan_entitlements USING btree (id)` |
| `uq_plan_entitlements_plan_id_entitlement_key` | `CREATE UNIQUE INDEX uq_plan_entitlements_plan_id_entitlement_key ON plat.plan_entitlements USING btree (plan_id, entitlement_key) WHERE (deleted_at IS NULL)` |

---

### Table: `plat.platform_admins`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `user_id` | `uuid` | **NO** | — |
| `full_name` | `text` | YES | — |
| `role_code` | `text` | **NO** | — |
| `status` | `text` | **NO** | `'ACTIVE'::text` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_platform_admins_role_code` | CHECK | `CHECK ((role_code = ANY (ARRAY['SUPER_ADMIN'::text, 'SYSTEM_ADMIN'::text, 'SUPPORT'::text, 'BILLING'::text, 'AUDITOR'::text])))` |
| `ck_platform_admins_status` | CHECK | `CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'LOCKED'::text, 'INACTIVE'::text])))` |
| `fk_platform_admins_user_id` | FOREIGN KEY | `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE RESTRICT` |
| `platform_admins_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `platform_admins_platform` | `ALL` | `{authenticated}` | `( SELECT iam.is_platform_admin() AS is_platform_admin)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_platform_admins_user_id` | `CREATE INDEX ix_platform_admins_user_id ON plat.platform_admins USING btree (user_id)` |
| `platform_admins_pkey` | `CREATE UNIQUE INDEX platform_admins_pkey ON plat.platform_admins USING btree (id)` |
| `uq_platform_admins_user_id` | `CREATE UNIQUE INDEX uq_platform_admins_user_id ON plat.platform_admins USING btree (user_id) WHERE (deleted_at IS NULL)` |

---

### Table: `plat.service_health`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `service_name` | `text` | **NO** | — |
| `status` | `text` | **NO** | — |
| `latency_ms` | `integer` | YES | — |
| `last_heartbeat` | `timestamp with time zone` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_service_health_status` | CHECK | `CHECK ((status = ANY (ARRAY['HEALTHY'::text, 'DEGRADED'::text, 'UNHEALTHY'::text])))` |
| `service_health_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `service_health_platform` | `ALL` | `{authenticated}` | `( SELECT iam.is_platform_admin() AS is_platform_admin)` |

#### Indexes
| Index Name | Definition |
|---|---|
| `service_health_pkey` | `CREATE UNIQUE INDEX service_health_pkey ON plat.service_health USING btree (id)` |
| `uq_service_health_service_name` | `CREATE UNIQUE INDEX uq_service_health_service_name ON plat.service_health USING btree (service_name) WHERE (deleted_at IS NULL)` |

---

### Table: `plat.subscription_plans`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `plan_code` | `text` | **NO** | — |
| `name` | `text` | **NO** | — |
| `billing_cycle` | `text` | **NO** | — |
| `price` | `numeric` | **NO** | — |
| `currency_code` | `character(3)` | **NO** | `'INR'::bpchar` |
| `is_published` | `boolean` | **NO** | `false` |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_subscription_plans_billing_cycle` | CHECK | `CHECK ((billing_cycle = ANY (ARRAY['MONTHLY'::text, 'QUARTERLY'::text, 'ANNUALLY'::text])))` |
| `ck_subscription_plans_price` | CHECK | `CHECK ((price >= (0)::numeric))` |
| `subscription_plans_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `subscription_plans_platform` | `ALL` | `{authenticated}` | `( SELECT iam.is_platform_admin() AS is_platform_admin)` |
| `subscription_plans_read` | `SELECT` | `{authenticated}` | `true` |

#### Indexes
| Index Name | Definition |
|---|---|
| `subscription_plans_pkey` | `CREATE UNIQUE INDEX subscription_plans_pkey ON plat.subscription_plans USING btree (id)` |
| `uq_subscription_plans_plan_code` | `CREATE UNIQUE INDEX uq_subscription_plans_plan_code ON plat.subscription_plans USING btree (plan_code) WHERE (deleted_at IS NULL)` |

---

### Table: `plat.subscriptions`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `plan_id` | `uuid` | **NO** | — |
| `start_date` | `date` | **NO** | — |
| `end_date` | `date` | YES | — |
| `auto_renew` | `boolean` | **NO** | `true` |
| `status` | `text` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_subscriptions_status` | CHECK | `CHECK ((status = ANY (ARRAY['TRIAL'::text, 'ACTIVE'::text, 'PAST_DUE'::text, 'SUSPENDED'::text, 'CANCELLED'::text])))` |
| `ck_subscriptions_x1` | CHECK | `CHECK (((end_date IS NULL) OR (end_date >= start_date)))` |
| `fk_subscriptions_plan_id` | FOREIGN KEY | `FOREIGN KEY (plan_id) REFERENCES plat.subscription_plans(id) ON DELETE RESTRICT` |
| `fk_subscriptions_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `subscriptions_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_subscriptions_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `subscriptions_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |
| `subscriptions_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |
| `subscriptions_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.read'::text) AS has_permission))` |
| `subscriptions_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_subscriptions_plan_id` | `CREATE INDEX ix_subscriptions_plan_id ON plat.subscriptions USING btree (plan_id)` |
| `ix_subscriptions_tenant_id_status_end_date` | `CREATE INDEX ix_subscriptions_tenant_id_status_end_date ON plat.subscriptions USING btree (tenant_id, status, end_date)` |
| `subscriptions_pkey` | `CREATE UNIQUE INDEX subscriptions_pkey ON plat.subscriptions USING btree (id)` |
| `uq_subscriptions_tenant_id` | `CREATE UNIQUE INDEX uq_subscriptions_tenant_id ON plat.subscriptions USING btree (tenant_id, id)` |

---

### Table: `plat.tenant_provisioning`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `college_id` | `uuid` | YES | — |
| `workflow_instance_id` | `uuid` | YES | — |
| `current_step` | `text` | **NO** | — |
| `attempt` | `integer` | **NO** | `1` |
| `status` | `text` | **NO** | `'REQUESTED'::text` |
| `idempotency_key` | `text` | YES | — |
| `requested_by` | `uuid` | YES | — |
| `failure_reason` | `text` | YES | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_tenant_provisioning_status` | CHECK | `CHECK ((status = ANY (ARRAY['REQUESTED'::text, 'VALIDATED'::text, 'PROVISIONING'::text, 'COMPLETED'::text, 'FAILED'::text])))` |
| `fk_tenant_provisioning_college_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT` |
| `fk_tenant_provisioning_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `fk_tenant_provisioning_workflow_instance_id` | FOREIGN KEY | `FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT` |
| `tenant_provisioning_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_tenant_provisioning_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `tenant_provisioning_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('plat.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('plat.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission)))))` |
| `tenant_provisioning_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('plat.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('plat.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission)))))` |
| `tenant_provisioning_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('plat.read'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('plat.read'::text)) AS unnest)) OR ((NOT false) AND (college_id IS NULL) AND ( SELECT iam.has_permission('plat.read'::text) AS has_permission)))))` |
| `tenant_provisioning_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND (( SELECT iam.perm_all('plat.write'::text) AS perm_all) OR ((college_id IN ( SELECT unnest(iam.perm_colleges('plat.write'::text)) AS unnest)) OR ((NOT true) AND (college_id IS NULL) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission)))))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_tenant_provisioning_college_id` | `CREATE INDEX ix_tenant_provisioning_college_id ON plat.tenant_provisioning USING btree (tenant_id, college_id)` |
| `ix_tenant_provisioning_workflow_instance_id` | `CREATE INDEX ix_tenant_provisioning_workflow_instance_id ON plat.tenant_provisioning USING btree (tenant_id, workflow_instance_id)` |
| `tenant_provisioning_pkey` | `CREATE UNIQUE INDEX tenant_provisioning_pkey ON plat.tenant_provisioning USING btree (id)` |
| `uq_tenant_provisioning_tenant_id` | `CREATE UNIQUE INDEX uq_tenant_provisioning_tenant_id ON plat.tenant_provisioning USING btree (tenant_id, id)` |
| `uq_tenant_provisioning_tenant_id_idempotency_key` | `CREATE UNIQUE INDEX uq_tenant_provisioning_tenant_id_idempotency_key ON plat.tenant_provisioning USING btree (tenant_id, idempotency_key) WHERE ((deleted_at IS NULL) AND (idempotency_key IS NOT NULL))` |

---

### Table: `plat.usage_metrics`

#### Columns
| Column | Type | Nullable | Default |
|---|---|---|---|
| `id` | `uuid` | **NO** | `gen_random_uuid()` |
| `tenant_id` | `uuid` | **NO** | `core.current_tenant_id()` |
| `metric_name` | `text` | **NO** | — |
| `dimension` | `text` | YES | — |
| `metric_value` | `numeric` | **NO** | — |
| `recorded_on` | `date` | **NO** | — |
| `created_at` | `timestamp with time zone` | **NO** | `now()` |
| `updated_at` | `timestamp with time zone` | **NO** | `now()` |
| `created_by` | `uuid` | YES | `auth.uid()` |
| `updated_by` | `uuid` | YES | — |
| `row_version` | `integer` | **NO** | `1` |
| `deleted_at` | `timestamp with time zone` | YES | — |

#### Constraints
| Name | Type | Definition |
|---|---|---|
| `ck_usage_metrics_metric_value` | CHECK | `CHECK ((metric_value >= (0)::numeric))` |
| `fk_usage_metrics_tenant` | FOREIGN KEY | `FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT` |
| `usage_metrics_pkey` | PRIMARY KEY | `PRIMARY KEY (id)` |
| `uq_usage_metrics_tenant_id` | UNIQUE | `UNIQUE (tenant_id, id)` |

#### Triggers & Append-Only Flags
| Trigger | Timing | Event | Flag / Action |
|---|---|---|---|
| `trg_audit` | AFTER | INSERT | Standard |
| `trg_audit` | AFTER | DELETE | ⚠️ **NO-DELETE / APPEND-ONLY** |
| `trg_audit` | AFTER | UPDATE | Standard |
| `trg_touch` | BEFORE | UPDATE | Standard |

#### Row Level Security (RLS) Policies
| Policy | Command | Roles | Expression |
|---|---|---|---|
| `usage_metrics_delete` | `DELETE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |
| `usage_metrics_insert` | `INSERT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |
| `usage_metrics_select` | `SELECT` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.read'::text) AS has_permission))` |
| `usage_metrics_update` | `UPDATE` | `{authenticated}` | `((tenant_id = ( SELECT core.current_tenant_id() AS current_tenant_id)) AND ( SELECT iam.has_permission('plat.write'::text) AS has_permission))` |

#### Indexes
| Index Name | Definition |
|---|---|
| `ix_usage_metrics_tenant_id_metric_name_recorded_on` | `CREATE INDEX ix_usage_metrics_tenant_id_metric_name_recorded_on ON plat.usage_metrics USING btree (tenant_id, metric_name, recorded_on DESC)` |
| `uq_usage_metrics_tenant_id` | `CREATE UNIQUE INDEX uq_usage_metrics_tenant_id ON plat.usage_metrics USING btree (tenant_id, id)` |
| `usage_metrics_pkey` | `CREATE UNIQUE INDEX usage_metrics_pkey ON plat.usage_metrics USING btree (id)` |

---

