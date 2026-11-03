# STM-02 Admissions Acceptance & Verification Matrix

| Step / Requirement | Criterion | Execution Mode | Evidence / Output | Status |
|---|---|---|---|---|
| **Step 0: Frontend Startup** | Clean env pointing only at dev DB `azulbaxszsqxymqmlwfe` | Process Overrides | Port 3000, zero references to forbidden ref | **PASS** |
| **Step 1: E2E Auth Users** | 4 personas created in Auth & IAM on dev project | Dev Supabase Auth + MCP SQL | `auth.users`, `iam.user_profiles`, `iam.role_assignments` | **PASS** |
| **Step 2.1: Applicant Submission** | Real login as applicant, submit application & attach document | Playwright Chromium (Port 3000) | `step1_apply_api_response.json`, DB read-back | **PASS** |
| **Step 2.2: Document Verification** | Real login as verifier, verify document & assign faculty reviewer | Playwright Chromium (Port 3000) | `step2_verify_api_response.json`, `step2_assign_api_response.json` | **PASS** |
| **Step 2.3: Academic Review** | Real login as reviewer, submit review with criteria | Playwright Chromium (Port 3000) | `step3_review_api_response.json`, Intelligence emit log | **PASS** |
| **Step 2.4: Decision Recording** | Real login as approver, approve offer decision | Playwright Chromium (Port 3000) | `step4_decision_api_response.json`, DB decision row | **PASS** |
| **Step 2.5: Offer Issuance** | Real login as approver, issue formal offer | Playwright Chromium (Port 3000) | `step5_offer_api_response.json`, DB offer row | **PASS** |
| **Step 2.6: Offer Acceptance** | Real login as applicant, accept formal offer | Playwright Chromium (Port 3000) | `step6_accept_api_response.json`, Status ACCEPTED | **PASS** |
| **Step 2.7: Downstream Handover** | Real login as approver, trigger handover to STM-01 | Playwright Chromium (Port 3000) | `student_linkage_evidence.json`, Status STUDENT_CREATED | **PASS** |
| **Step 3.1: Idempotency Replay** | Replay handover with same idempotency key | Playwright API / DB Query | Same student ID returned, 0 duplicate students created | **PASS** |
| **Step 3.2: Failure & Retry** | Dead port failure -> capture error -> live retry -> STUDENT_CREATED | Playwright API / DB Query | `step3_failure_evidence.json`, `step3_retry_success_evidence.json` | **PASS** |
| **Step 3.3: Intelligence Events** | Governance events emitted across all 5 stages | Java Service Logs | `INTELLIGENCE_LAYER_EMIT` logged for all domain events | **PASS** |
| **Step 3.4: Automated Test Suites** | Java 53/53 tests pass, Next.js build clean | Maven / Next.js CLI | `mvn test`: 53 PASS, `tsc`: 0 errors, `npm run build`: 24 routes PASS | **PASS** |
| **Step 4: Cleanup & Teardown** | Clean up 4 E2E auth users and role assignments | Supabase MCP execute_sql | All 4 E2E users deleted; seed baseline preserved | **PASS** |

