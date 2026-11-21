/** IADM story catalog for the platform demo desk (status from IADM_User_Stories.md). */

export type IadmStoryStatus = 'completed' | 'DEFERRED';

export type IadmStory = {
  id: string;
  title: string;
  status: IadmStoryStatus;
};

export type IadmService = {
  code: string;
  name: string;
  port: number;
  gatewayPrefix: string;
  healthPath: string;
  sampleGetPath: string;
  stories: IadmStory[];
};

export const IADM_SERVICES: IadmService[] = [
  {
    code: '01',
    name: 'Institute & Tenant Lifecycle',
    port: 8099,
    gatewayPrefix: '/api/v1/iadm/01',
    healthPath: '/api/v1/iadm/01/health',
    sampleGetPath: '/api/v1/iadm/01/health',
    stories: [
      { id: 'IADM-01-US-001', title: 'Register institute', status: 'completed' },
      { id: 'IADM-01-US-002', title: 'Appoint Institution Admin (via quorum)', status: 'completed' },
      { id: 'IADM-01-US-003', title: 'Onboard college + first College Admin', status: 'completed' },
      { id: 'IADM-01-US-004', title: 'College handover', status: 'completed' },
      { id: 'IADM-01-US-005', title: 'View institute profile', status: 'completed' },
      { id: 'IADM-01-US-006', title: 'Suspend / reactivate institute', status: 'completed' },
      { id: 'IADM-01-US-007', title: 'Lifecycle request + R01 decide', status: 'completed' },
      { id: 'IADM-01-US-008', title: 'Auditor lifecycle history', status: 'completed' },
    ],
  },
  {
    code: '02',
    name: 'Platform Configuration & Policy',
    port: 8100,
    gatewayPrefix: '/api/v1/iadm/02',
    healthPath: '/api/v1/iadm/02/health',
    sampleGetPath: '/api/v1/iadm/02/health',
    stories: [
      { id: 'IADM-02-US-001', title: 'Configuration catalogue', status: 'completed' },
      { id: 'IADM-02-US-002', title: 'Platform defaults & locks', status: 'completed' },
      { id: 'IADM-02-US-003', title: 'Institute allow-listed settings', status: 'completed' },
      { id: 'IADM-02-US-004', title: 'Delegate college settings', status: 'completed' },
      { id: 'IADM-02-US-005', title: 'Effective value resolution', status: 'completed' },
      { id: 'IADM-02-US-006', title: 'College Admin effective view', status: 'completed' },
      { id: 'IADM-02-US-007', title: 'History & rollback', status: 'completed' },
      { id: 'IADM-02-US-008', title: 'Auditor config read-only', status: 'completed' },
    ],
  },
  {
    code: '03',
    name: 'Identity & Access Governance',
    port: 8101,
    gatewayPrefix: '/api/v1/iadm/03',
    healthPath: '/api/v1/iadm/03/health',
    sampleGetPath: '/api/v1/iadm/03/health',
    stories: [
      { id: 'IADM-03-US-001', title: 'Role catalogue', status: 'completed' },
      { id: 'IADM-03-US-002', title: 'Role ceilings', status: 'completed' },
      { id: 'IADM-03-US-003', title: 'Assign/remove roles', status: 'completed' },
      { id: 'IADM-03-US-004', title: 'Access-change request/decide', status: 'completed' },
      { id: 'IADM-03-US-005', title: 'Time-boxed delegations', status: 'completed' },
      { id: 'IADM-03-US-006', title: 'Access review certify/revoke', status: 'completed' },
      { id: 'IADM-03-US-007', title: 'Auditor role/delegation view', status: 'completed' },
      { id: 'IADM-03-US-008', title: 'Break-glass tenant recovery', status: 'completed' },
      { id: 'IADM-03-US-009', title: 'Two-person R03 appoint/remove', status: 'completed' },
    ],
  },
  {
    code: '04',
    name: 'Subscription, Billing & Entitlements',
    port: 8102,
    gatewayPrefix: '/api/v1/iadm/04',
    healthPath: '/api/v1/iadm/04/health',
    sampleGetPath: '/api/v1/iadm/04/health',
    stories: [
      { id: 'IADM-04-US-001', title: 'Plans create/publish/retire', status: 'completed' },
      { id: 'IADM-04-US-002', title: 'Assign subscription + transitions', status: 'completed' },
      { id: 'IADM-04-US-003', title: 'View own subscription', status: 'completed' },
      { id: 'IADM-04-US-004', title: 'Plan change request', status: 'completed' },
      { id: 'IADM-04-US-005', title: 'Invoice issue/reconcile', status: 'completed' },
      { id: 'IADM-04-US-006', title: 'Entitlement check API', status: 'completed' },
      { id: 'IADM-04-US-007', title: 'Auditor billing read-only', status: 'completed' },
    ],
  },
  {
    code: '05',
    name: 'Data Governance & Privacy',
    port: 8103,
    gatewayPrefix: '/api/v1/iadm/05',
    healthPath: '/api/v1/iadm/05/health',
    sampleGetPath: '/api/v1/iadm/05/health',
    stories: [
      { id: 'IADM-05-US-001', title: 'Retention ≥ platform floor', status: 'completed' },
      { id: 'IADM-05-US-002', title: 'Privacy notice / classifications', status: 'completed' },
      { id: 'IADM-05-US-003', title: 'Data-subject requests', status: 'completed' },
      { id: 'IADM-05-US-004', title: 'College Admin execute tasks', status: 'completed' },
      { id: 'IADM-05-US-005', title: 'Erase/export orchestration', status: 'completed' },
      { id: 'IADM-05-US-006', title: 'Legal hold blocks erasure', status: 'completed' },
      { id: 'IADM-05-US-007', title: 'Guardian-consent under-18', status: 'DEFERRED' },
      { id: 'IADM-05-US-008', title: 'Auditor privacy read-only', status: 'completed' },
    ],
  },
  {
    code: '06',
    name: 'Platform Analytics & Usage',
    port: 8104,
    gatewayPrefix: '/api/v1/iadm/06',
    healthPath: '/api/v1/iadm/06/health',
    sampleGetPath: '/api/v1/iadm/06/health',
    stories: [
      { id: 'IADM-06-US-001', title: 'Platform usage aggregates', status: 'completed' },
      { id: 'IADM-06-US-002', title: 'Institute usage by college', status: 'completed' },
      { id: 'IADM-06-US-003', title: 'Export aggregate reports', status: 'completed' },
      { id: 'IADM-06-US-004', title: 'Metering feed to billing', status: 'completed' },
      { id: 'IADM-06-US-005', title: 'Auditor analytics read-only', status: 'completed' },
    ],
  },
  {
    code: '07',
    name: 'Security & Compliance',
    port: 8105,
    gatewayPrefix: '/api/v1/iadm/07',
    healthPath: '/api/v1/iadm/07/health',
    sampleGetPath: '/api/v1/iadm/07/health',
    stories: [
      { id: 'IADM-07-US-001', title: 'Institute policy ≥ minimum', status: 'completed' },
      { id: 'IADM-07-US-002', title: 'Compliance posture', status: 'completed' },
      { id: 'IADM-07-US-003', title: 'Track security findings', status: 'completed' },
      { id: 'IADM-07-US-004', title: 'Platform security minimums', status: 'completed' },
      { id: 'IADM-07-US-005', title: 'College posture view', status: 'completed' },
      { id: 'IADM-07-US-006', title: 'Auditor security read-only', status: 'completed' },
    ],
  },
  {
    code: '08',
    name: 'Audit, Health & Operations',
    port: 8106,
    gatewayPrefix: '/api/v1/iadm/08',
    healthPath: '/api/v1/iadm/08/health',
    sampleGetPath: '/api/v1/iadm/08/health',
    stories: [
      { id: 'IADM-08-US-001', title: 'Search audit trail', status: 'completed' },
      { id: 'IADM-08-US-002', title: 'Auditor export evidence', status: 'completed' },
      { id: 'IADM-08-US-003', title: 'Audit retention policies', status: 'completed' },
      { id: 'IADM-08-US-004', title: 'View service health', status: 'completed' },
      { id: 'IADM-08-US-005', title: 'Institute operational status', status: 'completed' },
      { id: 'IADM-08-US-006', title: 'Audit self-check immutable', status: 'completed' },
    ],
  },
];

export function iadmStoryTotals() {
  let completed = 0;
  let deferred = 0;
  for (const svc of IADM_SERVICES) {
    for (const s of svc.stories) {
      if (s.status === 'completed') completed += 1;
      else deferred += 1;
    }
  }
  return { completed, deferred, total: completed + deferred };
}
