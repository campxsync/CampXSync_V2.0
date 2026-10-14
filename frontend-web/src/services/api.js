/**
 * CampXSync Enterprise Client API
 * Connects the React Frontend to the API Gateway on port 8080 and downstream services.
 * Enforces real Supabase authentication headers and explicit developer mock controls.
 */

const getEnvVar = (key) => {
  if (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env[key]) {
    return import.meta.env[key];
  }
  if (typeof process !== 'undefined' && process.env && process.env[key]) {
    return process.env[key];
  }
  return '';
};

export const API_BASE = getEnvVar('VITE_API_BASE') || 'http://localhost:8080';

/**
 * Explicit Developer Mock Mode Setting
 * MUST be disabled by default. Mock data is only allowed when VITE_ENABLE_MOCK_FALLBACK=true.
 */
export const isMockFallbackEnabled = () => {
  const envVal = getEnvVar('VITE_ENABLE_MOCK_FALLBACK');
  return envVal === 'true';
};

// Default context used only when explicit dev mock mode is enabled and unauthenticated
export const DEFAULT_CONTEXT = {
  tenantId: '0c914bb2-f63b-472c-a99a-39977112935d',
  tenantName: 'CampXSync Demo Institute',
  tenantCode: 'CAMPXSYNC',
  userId: 'ba77b8f7-ab32-46bd-85d2-5aade3526880',
  userName: 'CampXSync Administrator',
  userEmail: 'campxsync@gmail.com',
  userRole: 'SUPER_ADMIN'
};

/**
 * Builds HTTP headers for API requests.
 * Attaches Authorization: Bearer <access_token> when authenticated.
 * Attaches client correlation and debug headers.
 */
export const getHeaders = (context) => {
  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'X-Trace-Id': 'web-' + Math.random().toString(36).substring(2, 10)
  };

  if (context && context.accessToken) {
    headers['Authorization'] = `Bearer ${context.accessToken}`;
  }

  // Provide non-authoritative client hints for dev / logging
  if (context && context.tenantId) {
    headers['X-Tenant-Id'] = context.tenantId;
  }
  if (context && context.userId) {
    headers['X-User-Id'] = context.userId;
  }
  if (context && context.userRole) {
    headers['X-User-Role'] = context.userRole;
  }

  return headers;
};

/**
 * Parses RFC 7807 problem details or standard error payloads from HTTP responses.
 */
export async function parseApiError(res, operationName) {
  let details = null;
  let rawText = '';
  try {
    rawText = await res.text();
    details = JSON.parse(rawText);
  } catch {
    // Response was not JSON
  }

  const message =
    details?.detail ||
    details?.message ||
    details?.title ||
    rawText ||
    res.statusText ||
    `HTTP ${res.status}`;

  const err = new Error(`${operationName} failed (${res.status}): ${message}`);
  err.status = res.status;
  err.title = details?.title || res.statusText;
  err.detail = details?.detail || message;
  err.type = details?.type || null;
  err.code = details?.code || null;
  err.problemDetails = details;
  err.operation = operationName;
  return err;
}

/**
 * Hardcoded Developer Mock Fixtures
 * Only served when isMockFallbackEnabled() returns true.
 */
export const MOCK_FIXTURES = {
  institutes: [
    {
      id: DEFAULT_CONTEXT.tenantId,
      code: DEFAULT_CONTEXT.tenantCode,
      name: DEFAULT_CONTEXT.tenantName,
      status: 'ACTIVE',
      currency: 'INR',
      timezone: 'Asia/Kolkata',
      locale: 'en-IN'
    }
  ],
  colleges: [
    {
      id: '11111111-2222-3333-4444-555555555551',
      code: 'SOET',
      name: 'School of Engineering & Technology',
      status: 'ACTIVE',
      provisioningStatus: 'ACTIVE',
      affiliatingUniversity: 'State Technical University',
      establishedYear: 2018,
      email: 'engineering@campx.edu'
    },
    {
      id: '11111111-2222-3333-4444-555555555552',
      code: 'SOAS',
      name: 'School of Applied Sciences',
      status: 'ACTIVE',
      provisioningStatus: 'ACTIVE',
      affiliatingUniversity: 'State University of Sciences',
      establishedYear: 2020,
      email: 'sciences@campx.edu'
    }
  ],
  departments: [
    {
      id: '22222222-3333-4444-5555-666666666661',
      code: 'CSE',
      name: 'Computer Science & Engineering',
      status: 'ACTIVE',
      headUserId: 'DR_ALAN_TURING',
      collegeId: '11111111-2222-3333-4444-555555555551'
    },
    {
      id: '22222222-3333-4444-5555-666666666662',
      code: 'ECE',
      name: 'Electronics & Communication Engineering',
      status: 'ACTIVE',
      headUserId: 'DR_CLAUDE_SHANNON',
      collegeId: '11111111-2222-3333-4444-555555555551'
    },
    {
      id: '22222222-3333-4444-5555-666666666663',
      code: 'MATH',
      name: 'Department of Applied Mathematics',
      status: 'ACTIVE',
      headUserId: 'DR_KATHERINE_JOHNSON',
      collegeId: '11111111-2222-3333-4444-555555555552'
    }
  ],
  programs: [
    {
      id: '33333333-4444-5555-6666-777777777771',
      code: 'BTECH_CSE',
      name: 'Bachelor of Technology in Computer Science',
      departmentId: '22222222-3333-4444-5555-666666666661',
      durationYears: 4,
      level: 'UNDERGRADUATE',
      status: 'ACTIVE',
      published: true
    },
    {
      id: '33333333-4444-5555-6666-777777777772',
      code: 'MTECH_AI',
      name: 'Master of Technology in Artificial Intelligence',
      departmentId: '22222222-3333-4444-5555-666666666661',
      durationYears: 2,
      level: 'POSTGRADUATE',
      status: 'ACTIVE',
      published: true
    },
    {
      id: '33333333-4444-5555-6666-777777777773',
      code: 'BTECH_ECE',
      name: 'Bachelor of Technology in Electronics',
      departmentId: '22222222-3333-4444-5555-666666666662',
      durationYears: 4,
      level: 'UNDERGRADUATE',
      status: 'ACTIVE',
      published: true
    }
  ],
  courses: [
    {
      id: '44444444-5555-6666-7777-888888888881',
      code: 'CS101',
      name: 'Introduction to Algorithms & Data Structures',
      credits: 4,
      level: '100',
      departmentId: '22222222-3333-4444-5555-666666666661',
      programId: '33333333-4444-5555-6666-777777777771',
      status: 'PUBLISHED',
      description: 'Fundamental data structures, sorting algorithms, and complexity analysis.'
    },
    {
      id: '44444444-5555-6666-7777-888888888882',
      code: 'CS201',
      name: 'Database Management Systems & SQL',
      credits: 4,
      level: '200',
      departmentId: '22222222-3333-4444-5555-666666666661',
      programId: '33333333-4444-5555-6666-777777777771',
      status: 'PUBLISHED',
      description: 'Relational calculus, SQL querying, indexing, and ACID transaction guarantees.'
    },
    {
      id: '44444444-5555-6666-7777-888888888883',
      code: 'AI501',
      name: 'Neural Networks and Deep Learning',
      credits: 3,
      level: '500',
      departmentId: '22222222-3333-4444-5555-666666666661',
      programId: '33333333-4444-5555-6666-777777777772',
      status: 'PUBLISHED',
      description: 'Backpropagation, transformers, convolutional architectures, and optimization.'
    },
    {
      id: '44444444-5555-6666-7777-888888888884',
      code: 'EC101',
      name: 'Circuits, Signals and Systems',
      credits: 4,
      level: '100',
      departmentId: '22222222-3333-4444-5555-666666666662',
      programId: '33333333-4444-5555-6666-777777777773',
      status: 'PUBLISHED',
      description: 'Analog and digital signal theory, Fourier transforms, and circuit filtering.'
    }
  ],
  calendars: [
    {
      id: 'cal-demo-01',
      code: 'AY2026_CAL',
      name: 'Academic Calendar 2026-2027',
      status: 'ACTIVE'
    }
  ],
  calendarEvents: [
    { id: 'evt-01', title: 'Fall Semester Orientation', eventType: 'EVENT', isHoliday: false },
    { id: 'evt-02', title: 'National Day Holiday', eventType: 'HOLIDAY', isHoliday: true },
    { id: 'evt-03', title: 'Midterm Examination Period', eventType: 'EXAM', isHoliday: false },
    { id: 'evt-04', title: 'Course Add/Drop Deadline', eventType: 'DEADLINE', isHoliday: false }
  ],
  sequences: [
    { id: 'seq-stu-01', scopeKey: 'STUDENT_REG', prefix: 'STU2026', nextValue: 1042, padding: 6 },
    { id: 'seq-fac-01', scopeKey: 'FACULTY_ID', prefix: 'FAC', nextValue: 128, padding: 4 },
    { id: 'seq-inv-01', scopeKey: 'INVOICE_NUM', prefix: 'INV-2026', nextValue: 504, padding: 5 }
  ],
  lookupTypes: [
    { id: 'lt-01', code: 'COURSE_DELIVERY_MODE', name: 'Course Delivery Mode' },
    { id: 'lt-02', code: 'STUDENT_CATEGORY', name: 'Student Category' },
    { id: 'lt-03', code: 'ATTENDANCE_STATUS', name: 'Attendance Status' }
  ],
  retentionPolicies: [
    { id: 'ret-01', policyCode: 'STUDENT_TRANSCRIPTS', dataClass: 'RESTRICTED', retentionDays: 2555, legalMinimumDays: 2555, status: 'ACTIVE' },
    { id: 'ret-02', policyCode: 'SYSTEM_AUDIT_LOGS', dataClass: 'CONFIDENTIAL', retentionDays: 1095, legalMinimumDays: 365, status: 'ACTIVE' }
  ]
};

function handleMockFallbackOrThrow(operationName, fixtureKey, error) {
  if (isMockFallbackEnabled()) {
    console.warn(`[DEV MOCK FALLBACK] Returning mock data for ${operationName} because VITE_ENABLE_MOCK_FALLBACK=true:`, error);
    if (fixtureKey === 'institutes') return { institutes: MOCK_FIXTURES.institutes };
    if (fixtureKey === 'colleges') return { colleges: MOCK_FIXTURES.colleges };
    if (fixtureKey === 'departments') return { departments: MOCK_FIXTURES.departments };
    if (fixtureKey === 'programs') return { programs: MOCK_FIXTURES.programs };
    if (fixtureKey === 'courses') return { courses: MOCK_FIXTURES.courses };
    if (fixtureKey === 'calendars') return { calendars: MOCK_FIXTURES.calendars };
    if (fixtureKey === 'calendarEvents') return { events: MOCK_FIXTURES.calendarEvents };
    if (fixtureKey === 'sequences') return { sequences: MOCK_FIXTURES.sequences };
    if (fixtureKey === 'lookupTypes') return { lookupTypes: MOCK_FIXTURES.lookupTypes };
    if (fixtureKey === 'accessEvents') return { accessEvents: [] };
    if (fixtureKey === 'changeLogs') return { changeLogs: [] };
    if (fixtureKey === 'retentionPolicies') return { retentionPolicies: MOCK_FIXTURES.retentionPolicies };
  }
  throw error;
}

export const api = {
  // 1. Gateway Health & Route Diagnostics
  async getGatewayHealth() {
    try {
      const res = await fetch(`${API_BASE}/actuator/health`, { method: 'GET' });
      if (res.ok) return await res.json();
      return { status: 'DEGRADED', gateway: 'CampXSync-API-Gateway', httpStatus: res.status };
    } catch (e) {
      return { status: 'OFFLINE', gateway: 'CampXSync-API-Gateway', error: e.message };
    }
  },

  async getGatewayRoutes() {
    try {
      const res = await fetch(`${API_BASE}/api/v1/gateway/routes`, { method: 'GET' });
      if (res.ok) return await res.json();
      return { routes: [] };
    } catch {
      return { routes: [] };
    }
  },

  // 2. ADM-01 Institute & Colleges
  async getInstitutes(context) {
    try {
      const res = await fetch(`${API_BASE}/v1/institutes`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) return await res.json();
      const err = await parseApiError(res, 'getInstitutes');
      return handleMockFallbackOrThrow('getInstitutes', 'institutes', err);
    } catch (e) {
      return handleMockFallbackOrThrow('getInstitutes', 'institutes', e);
    }
  },

  async getColleges(context) {
    try {
      const res = await fetch(`${API_BASE}/api/v1/admin/colleges`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) {
        const data = await res.json();
        return data && data.colleges ? data : { colleges: [] };
      }
      const err = await parseApiError(res, 'getColleges');
      return handleMockFallbackOrThrow('getColleges', 'colleges', err);
    } catch (e) {
      return handleMockFallbackOrThrow('getColleges', 'colleges', e);
    }
  },

  async createCollege(collegeData, context) {
    const res = await fetch(`${API_BASE}/api/v1/admin/colleges`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(collegeData)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'createCollege');
    }
    return await res.json();
  },

  // 2b. ADM-02 College Operational Profile & Governance
  async getCollegeProfile(context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/profile`, {
      method: 'GET',
      headers: getHeaders(context)
    });
    if (res.ok) return await res.json();
    if (res.status === 404) return null;
    throw await parseApiError(res, 'getCollegeProfile');
  },

  async updateCollegeProfile(profileData, context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/profile`, {
      method: 'PUT',
      headers: getHeaders(context),
      body: JSON.stringify(profileData)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'updateCollegeProfile');
    }
    return await res.json();
  },

  async getCollegeSettings(context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/settings`, {
      method: 'GET',
      headers: getHeaders(context)
    });
    if (res.ok) {
      const data = await res.json();
      return data && data.settings ? data : { settings: [] };
    }
    throw await parseApiError(res, 'getCollegeSettings');
  },

  async setCollegeSetting(settingData, context) {
    const isSec = !!(settingData.secret || settingData.isSecret);
    const payload = {
      ...settingData,
      secret: isSec,
      isSecret: isSec
    };
    const res = await fetch(`${API_BASE}/api/v1/college-admin/settings`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(payload)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'setCollegeSetting');
    }
    return await res.json();
  },

  async getFeatureOverrides(context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/feature-overrides`, {
      method: 'GET',
      headers: getHeaders(context)
    });
    if (res.ok) {
      const data = await res.json();
      return data && data.overrides ? data : { overrides: [] };
    }
    throw await parseApiError(res, 'getFeatureOverrides');
  },

  async createFeatureOverride(overrideData, context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/feature-overrides`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(overrideData)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'createFeatureOverride');
    }
    return await res.json();
  },

  async getLocalPolicies(context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/policies`, {
      method: 'GET',
      headers: getHeaders(context)
    });
    if (res.ok) {
      const data = await res.json();
      return data && data.policies ? data : { policies: [] };
    }
    throw await parseApiError(res, 'getLocalPolicies');
  },

  async createLocalPolicy(policyData, context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/policies`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify({
        policyCode: policyData.policyCode,
        rules: policyData.rules
      })
    });
    if (!res.ok) {
      throw await parseApiError(res, 'createLocalPolicy');
    }
    return await res.json();
  },

  async publishLocalPolicy(policyCode, context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/policies/${encodeURIComponent(policyCode)}/publish`, {
      method: 'POST',
      headers: getHeaders(context)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'publishLocalPolicy');
    }
    return await res.json();
  },

  async getGovernanceDocuments(context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/documents`, {
      method: 'GET',
      headers: getHeaders(context)
    });
    if (res.ok) {
      const data = await res.json();
      return data && data.documents ? data : { documents: [] };
    }
    throw await parseApiError(res, 'getGovernanceDocuments');
  },

  async registerGovernanceDocument(docData, context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/documents`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(docData)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'registerGovernanceDocument');
    }
    const created = await res.json();
    return { ...docData, ...created };
  },

  // 3. ADM-02 Departments
  async getDepartments(context) {
    try {
      const res = await fetch(`${API_BASE}/api/v1/college-admin/departments`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) {
        const data = await res.json();
        return data && data.departments ? data : { departments: [] };
      }
      const err = await parseApiError(res, 'getDepartments');
      return handleMockFallbackOrThrow('getDepartments', 'departments', err);
    } catch (e) {
      return handleMockFallbackOrThrow('getDepartments', 'departments', e);
    }
  },

  async createDepartment(departmentData, context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/departments`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(departmentData)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'createDepartment');
    }
    return await res.json();
  },

  async updateDepartment(depId, departmentData, context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/departments/${depId}`, {
      method: 'PUT',
      headers: getHeaders(context),
      body: JSON.stringify(departmentData)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'updateDepartment');
    }
    return await res.json();
  },

  // 4. ADM-02 Programs
  async getPrograms(context) {
    try {
      const res = await fetch(`${API_BASE}/api/v1/college-admin/programs`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) {
        const data = await res.json();
        return data && data.programs ? data : { programs: [] };
      }
      const err = await parseApiError(res, 'getPrograms');
      return handleMockFallbackOrThrow('getPrograms', 'programs', err);
    } catch (e) {
      return handleMockFallbackOrThrow('getPrograms', 'programs', e);
    }
  },

  async createProgram(programData, context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/programs`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(programData)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'createProgram');
    }
    return await res.json();
  },

  // 5. ACD-01 Courses
  async getCourses(context) {
    try {
      const res = await fetch(`${API_BASE}/api/v1/courses`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) {
        const data = await res.json();
        return data && data.courses ? data : { courses: [] };
      }
      const err = await parseApiError(res, 'getCourses');
      return handleMockFallbackOrThrow('getCourses', 'courses', err);
    } catch (e) {
      return handleMockFallbackOrThrow('getCourses', 'courses', e);
    }
  },

  async createCourse(courseData, context) {
    const res = await fetch(`${API_BASE}/api/v1/courses`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(courseData)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'createCourse');
    }
    return await res.json();
  },

  async updateCourse(courseId, courseData, context) {
    const res = await fetch(`${API_BASE}/api/v1/courses/${courseId}`, {
      method: 'PUT',
      headers: getHeaders(context),
      body: JSON.stringify(courseData)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'updateCourse');
    }
    return await res.json();
  },

  // 6. Academic Calendars (ADM-01 Item 23)
  async getCalendars(context, collegeId) {
    try {
      const colId = (typeof context === 'string') ? context : collegeId;
      const ctx = (context && typeof context === 'object') ? context : null;
      const url = colId ? `${API_BASE}/api/v1/admin/calendars?collegeId=${encodeURIComponent(colId)}` : `${API_BASE}/api/v1/admin/calendars`;
      const res = await fetch(url, {
        method: 'GET',
        headers: getHeaders(ctx)
      });
      if (res.ok) {
        const data = await res.json();
        return data && data.calendars ? data : { calendars: [] };
      }
      const err = await parseApiError(res, 'getCalendars');
      return handleMockFallbackOrThrow('getCalendars', 'calendars', err);
    } catch (e) {
      return handleMockFallbackOrThrow('getCalendars', 'calendars', e);
    }
  },

  async createCalendar(calendarData, context) {
    const res = await fetch(`${API_BASE}/api/v1/admin/calendars`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(calendarData)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'createCalendar');
    }
    return await res.json();
  },

  async getCalendarEvents(calendarId, context) {
    try {
      const res = await fetch(`${API_BASE}/api/v1/admin/calendars/${encodeURIComponent(calendarId)}/events`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) {
        const data = await res.json();
        return data && data.events ? data : { events: [] };
      }
      const err = await parseApiError(res, 'getCalendarEvents');
      return handleMockFallbackOrThrow('getCalendarEvents', 'calendarEvents', err);
    } catch (e) {
      return handleMockFallbackOrThrow('getCalendarEvents', 'calendarEvents', e);
    }
  },

  async createCalendarEvent(calendarId, eventData, context) {
    const res = await fetch(`${API_BASE}/api/v1/admin/calendars/${encodeURIComponent(calendarId)}/events`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(eventData)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'createCalendarEvent');
    }
    return await res.json();
  },

  // 7. Number Sequences (ADM-01 Item 24)
  async getNumberSequences(context) {
    try {
      const res = await fetch(`${API_BASE}/api/v1/admin/number-sequences`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) {
        const data = await res.json();
        return data && data.sequences ? data : { sequences: [] };
      }
      const err = await parseApiError(res, 'getNumberSequences');
      return handleMockFallbackOrThrow('getNumberSequences', 'sequences', err);
    } catch (e) {
      return handleMockFallbackOrThrow('getNumberSequences', 'sequences', e);
    }
  },

  async createNumberSequence(sequenceData, context) {
    const res = await fetch(`${API_BASE}/api/v1/admin/number-sequences`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(sequenceData)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'createNumberSequence');
    }
    return await res.json();
  },

  async generateNextNumber(scopeKey, context, collegeId) {
    let ctx = context;
    let colId = collegeId;
    if (typeof context === 'string') {
      colId = context;
      ctx = collegeId;
    }
    const res = await fetch(`${API_BASE}/api/v1/admin/number-sequences/next`, {
      method: 'POST',
      headers: getHeaders(ctx),
      body: JSON.stringify({ scopeKey, collegeId: colId })
    });
    if (!res.ok) {
      throw await parseApiError(res, 'generateNextNumber');
    }
    return await res.json();
  },

  // 8. Reference Lookups (ADM-01 Item 25)
  async getLookupTypes(context) {
    try {
      const res = await fetch(`${API_BASE}/api/v1/admin/lookups/types`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) {
        const data = await res.json();
        return data && data.lookupTypes ? data : { lookupTypes: [] };
      }
      const err = await parseApiError(res, 'getLookupTypes');
      return handleMockFallbackOrThrow('getLookupTypes', 'lookupTypes', err);
    } catch (e) {
      return handleMockFallbackOrThrow('getLookupTypes', 'lookupTypes', e);
    }
  },

  async createLookupType(typeData, context) {
    const res = await fetch(`${API_BASE}/api/v1/admin/lookups/types`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(typeData)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'createLookupType');
    }
    return await res.json();
  },

  // 9. Access & Change Audit Logs (ADM-01 Items 26 & 27)
  async getAccessEvents(limit = 20, context) {
    try {
      const res = await fetch(`${API_BASE}/api/v1/admin/audit/access-events?limit=${limit}`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) {
        const data = await res.json();
        return data && data.accessEvents ? data : { accessEvents: [] };
      }
      const err = await parseApiError(res, 'getAccessEvents');
      return handleMockFallbackOrThrow('getAccessEvents', 'accessEvents', err);
    } catch (e) {
      return handleMockFallbackOrThrow('getAccessEvents', 'accessEvents', e);
    }
  },

  async getChangeLogs(limit = 20, context) {
    try {
      const res = await fetch(`${API_BASE}/api/v1/admin/audit/change-log?limit=${limit}`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) {
        const data = await res.json();
        return data && data.changeLogs ? data : { changeLogs: [] };
      }
      const err = await parseApiError(res, 'getChangeLogs');
      return handleMockFallbackOrThrow('getChangeLogs', 'changeLogs', err);
    } catch (e) {
      return handleMockFallbackOrThrow('getChangeLogs', 'changeLogs', e);
    }
  },

  // 10. Data Governance (ADM-01 Item 28)
  async getRetentionPolicies(context) {
    try {
      const res = await fetch(`${API_BASE}/api/v1/admin/data-governance/retention`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) {
        const data = await res.json();
        return data && data.retentionPolicies ? data : { retentionPolicies: [] };
      }
      const err = await parseApiError(res, 'getRetentionPolicies');
      return handleMockFallbackOrThrow('getRetentionPolicies', 'retentionPolicies', err);
    } catch (e) {
      return handleMockFallbackOrThrow('getRetentionPolicies', 'retentionPolicies', e);
    }
  },

  // 11. ADM-01 Role Templates (MVP-ADM01-03)
  async getRoleTemplates(context) {
    const res = await fetch(`${API_BASE}/api/v1/admin/role-templates`, {
      method: 'GET',
      headers: getHeaders(context)
    });
    if (res.ok) return await res.json();
    throw await parseApiError(res, 'getRoleTemplates');
  },

  async createRoleTemplate(data, context) {
    const res = await fetch(`${API_BASE}/api/v1/admin/role-templates`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(data)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'createRoleTemplate');
    }
    return await res.json();
  },

  async updateRoleTemplate(idOrCode, data, context, etag) {
    const headers = getHeaders(context);
    if (etag) {
      headers['If-Match'] = etag;
    }
    const res = await fetch(`${API_BASE}/api/v1/admin/role-templates/${encodeURIComponent(idOrCode)}`, {
      method: 'PUT',
      headers,
      body: JSON.stringify(data)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'updateRoleTemplate');
    }
    return await res.json();
  },

  async deleteRoleTemplate(idOrCode, context) {
    const res = await fetch(`${API_BASE}/api/v1/admin/role-templates/${encodeURIComponent(idOrCode)}`, {
      method: 'DELETE',
      headers: getHeaders(context)
    });
    if (!res.ok) {
      throw await parseApiError(res, 'deleteRoleTemplate');
    }
    return await res.json();
  }
};
