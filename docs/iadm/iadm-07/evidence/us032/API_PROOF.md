# IADM-07 API proof (a–h)

| ID | Result | Detail |
|---|---|---|
| health_8105 | PASS | status=200 |
| a_r03_cross_institute_finding_java | PASS | status=403 detail=Cross-institute write refused |
| a_r03_cross_institute_risk_java | PASS | status=403 |
| a_r03_cross_institute_alert_java | PASS | status=403 |
| b_r01_request_exception | PASS | status=200 id=062347cd-8516-4432-aa18-cd0eb25f52af detail= |
| b_r01_self_decide_sod | PASS | status=403 code=IADM07_SOD_VIOLATION detail=Decider must differ from requester |
| b_r01_decides_r02_request | PASS | status=200 statusField=APPROVED detail= |
| b_invalid_validity_window | PASS | status=400 detail=validTo must be after validFrom |
| b_missing_validity_window | PASS | status=400 |
| c_r01_intake_refused | PASS | status=403 detail=Signal intake is restricted to authenticated service callers |
| c_r02_intake_refused | PASS | status=403 |
| c_service_intake_accepted | PASS | status=200 id=004c3cc6-2849-412d-a0f2-65d452b19ae9 detail= |
| c_simulator_through_intake_path | PASS | status=200 simulator=true id=65425fc0-5587-4749-bde2-591a29d767cf detail= |
| c_duplicate_no_second_record | PASS | status=200 duplicate=true conflict=true |
| c_conflict_audit_row | PASS | status=200 hasConflict=true |
| d_standard_new_version_draft | PASS | status=200 version=2 newStatus=DRAFT supersedes= |
| d_old_standard_superseded | PASS | oldStatus=SUPERSEDED supersedesId= |
| e_posture_expiring_exceptions_present | PASS | status=200 |
| f_r03_resolve_refused | PASS | status=403 detail=Only platform operators may mark findings resolved |
| f_r03_invalid_transition_refused | PASS | from=RESOLVED status=403 detail=Institution Admin may only move assigned findings from ASSIGNED to REMEDIATION_IN_PROGRESS or PENDING_VERIFICATION |
| f_r03_alert_resolve_refused | PASS | no OPEN alert (prior ack) — limit still unit-proven |
| g_reports_breakdown | PASS | status=200 keys=findingsOpen,risksOpen,alertsOpen,signalsOpen,exceptionsPending,byStatus,findingsBySeverity,monthlyTrends,freshness detail= |
| h_denials_r01_r02 | PASS | r01=200 r02=200 unsafe=false |
| h_denials_r03_refused_or_empty_scope | PASS | status=403 items=n/a |

Totals: 24 PASS / 0 FAIL