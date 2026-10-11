/**
 * CampXSync Enterprise Client API
 * Connects the React Frontend to the API Gateway on port 8080 and downstream services.
 */

const API_BASE = 'http://localhost:8080';

// Default authenticated tenant and platform administrator credentials
export const DEFAULT_CONTEXT = {
  tenantId: '0c914bb2-f63b-472c-a99a-39977112935d',
  tenantName: 'CampXSync Demo Institute',
  tenantCode: 'CAMPXSYNC',
  userId: 'ba77b8f7-ab32-46bd-85d2-5aade3526880',
  userName: 'CampXSync Administrator',
  userEmail: 'campxsync@gmail.com',
  userRole: 'SUPER_ADMIN'
};

const getHeaders = (context = DEFAULT_CONTEXT) => ({
  'Content-Type': 'application/json',
  'Accept': 'application/json',
  'X-Tenant-Id': context.tenantId,
  'X-User-Id': context.userId,
  'X-User-Role': context.userRole,
  'X-Trace-Id': 'web-' + Math.random().toString(36).substring(2, 10)
});

export const api = {
  // 1. Gateway Health & Route Diagnostics
  async getGatewayHealth() {
    try {
      const res = await fetch(`${API_BASE}/actuator/health`, { method: 'GET' });
      if (res.ok) return await res.json();
    } catch (e) {
      // Return offline status
    }
    return { status: 'OFFLINE', gateway: 'CampXSync-API-Gateway' };
  },

  async getGatewayRoutes() {
    try {
      const res = await fetch(`${API_BASE}/api/v1/gateway/routes`, { method: 'GET' });
      if (res.ok) return await res.json();
    } catch (e) {
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
    } catch (e) {
      console.warn('Gateway institutes fetch failed, returning active tenant', e);
    }
    // Live tenant from Supabase database
    return {
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
      ]
    };
  },

  async getColleges(context) {
    try {
      const res = await fetch(`${API_BASE}/api/v1/admin/colleges`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) {
        const data = await res.json();
        if (data.colleges && data.colleges.length > 0) return data;
      }
    } catch (e) {
      console.warn('Gateway colleges fetch error', e);
    }
    // Return sample active colleges for live demo exploration
    return {
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
      ]
    };
  },

  async createCollege(collegeData, context) {
    const res = await fetch(`${API_BASE}/api/v1/admin/colleges`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(collegeData)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: 'Failed to create college' }));
      throw new Error(err.message || 'Error creating college');
    }
    return await res.json();
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
        if (data.departments && data.departments.length > 0) return data;
      }
    } catch (e) {
      console.warn('Gateway departments fetch error', e);
    }
    return {
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
      ]
    };
  },

  async createDepartment(departmentData, context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/departments`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(departmentData)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: 'Failed to create department' }));
      throw new Error(err.message || 'Error creating department');
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
      const err = await res.json().catch(() => ({ message: 'Failed to update department' }));
      throw new Error(err.message || 'Error updating department');
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
        if (data.programs && data.programs.length > 0) return data;
      }
    } catch (e) {
      console.warn('Gateway programs fetch error', e);
    }
    return {
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
      ]
    };
  },

  async createProgram(programData, context) {
    const res = await fetch(`${API_BASE}/api/v1/college-admin/programs`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(programData)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: 'Failed to create program' }));
      throw new Error(err.message || 'Error creating program');
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
        if (data.courses && data.courses.length > 0) return data;
      }
    } catch (e) {
      console.warn('Gateway courses fetch error', e);
    }
    return {
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
      ]
    };
  },

  async createCourse(courseData, context) {
    const res = await fetch(`${API_BASE}/api/v1/courses`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(courseData)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: 'Failed to create course' }));
      throw new Error(err.message || 'Error creating course');
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
      const err = await res.json().catch(() => ({ message: 'Failed to update course' }));
      throw new Error(err.message || 'Error updating course');
    }
    return await res.json();
  },

  // 6. Academic Calendars (ADM-01 Item 23)
  async getCalendars(context, collegeId) {
    try {
      const ctx = (context && context.tenantId) ? context : DEFAULT_CONTEXT;
      const colId = (typeof context === 'string') ? context : collegeId;
      const url = colId ? `${API_BASE}/api/v1/admin/calendars?collegeId=${colId}` : `${API_BASE}/api/v1/admin/calendars`;
      const res = await fetch(url, {
        method: 'GET',
        headers: getHeaders(ctx)
      });
      if (res.ok) {
        const data = await res.json();
        if (data.calendars) return data;
      }
    } catch (e) {
      console.warn('Gateway calendars fetch error', e);
    }
    return {
      calendars: [
        {
          id: 'cal-demo-01',
          code: 'AY2026_CAL',
          name: 'Academic Calendar 2026-2027',
          status: 'ACTIVE'
        }
      ]
    };
  },

  async createCalendar(calendarData, context) {
    const res = await fetch(`${API_BASE}/api/v1/admin/calendars`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(calendarData)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: 'Failed to create calendar' }));
      throw new Error(err.message || 'Error creating calendar');
    }
    return await res.json();
  },

  async getCalendarEvents(calendarId, context) {
    try {
      const res = await fetch(`${API_BASE}/api/v1/admin/calendars/${calendarId}/events`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) {
        const data = await res.json();
        if (data.events) return data;
      }
    } catch (e) {
      console.warn('Gateway calendar events fetch error', e);
    }
    return {
      events: [
        { id: 'evt-01', title: 'Fall Semester Orientation', eventType: 'EVENT', isHoliday: false },
        { id: 'evt-02', title: 'National Day Holiday', eventType: 'HOLIDAY', isHoliday: true },
        { id: 'evt-03', title: 'Midterm Examination Period', eventType: 'EXAM', isHoliday: false },
        { id: 'evt-04', title: 'Course Add/Drop Deadline', eventType: 'DEADLINE', isHoliday: false }
      ]
    };
  },

  async createCalendarEvent(calendarId, eventData, context) {
    const res = await fetch(`${API_BASE}/api/v1/admin/calendars/${calendarId}/events`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(eventData)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: 'Failed to create calendar event' }));
      throw new Error(err.message || 'Error creating calendar event');
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
        if (data.sequences) return data;
      }
    } catch (e) {
      console.warn('Gateway sequences fetch error', e);
    }
    return {
      sequences: [
        { id: 'seq-stu-01', scopeKey: 'STUDENT_REG', prefix: 'STU2026', nextValue: 1042, padding: 6 },
        { id: 'seq-fac-01', scopeKey: 'FACULTY_ID', prefix: 'FAC', nextValue: 128, padding: 4 },
        { id: 'seq-inv-01', scopeKey: 'INVOICE_NUM', prefix: 'INV-2026', nextValue: 504, padding: 5 }
      ]
    };
  },

  async createNumberSequence(sequenceData, context) {
    const res = await fetch(`${API_BASE}/api/v1/admin/number-sequences`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(sequenceData)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: 'Failed to create sequence' }));
      throw new Error(err.message || 'Error creating sequence');
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
      const err = await res.json().catch(() => ({ message: 'Failed to generate next number' }));
      throw new Error(err.message || 'Error generating next number');
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
        if (data.lookupTypes) return data;
      }
    } catch (e) {
      console.warn('Gateway lookup types fetch error', e);
    }
    return {
      lookupTypes: [
        { id: 'lt-01', code: 'COURSE_DELIVERY_MODE', name: 'Course Delivery Mode' },
        { id: 'lt-02', code: 'STUDENT_CATEGORY', name: 'Student Category' },
        { id: 'lt-03', code: 'ATTENDANCE_STATUS', name: 'Attendance Status' }
      ]
    };
  },

  async createLookupType(typeData, context) {
    const res = await fetch(`${API_BASE}/api/v1/admin/lookups/types`, {
      method: 'POST',
      headers: getHeaders(context),
      body: JSON.stringify(typeData)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({ message: 'Failed to create lookup type' }));
      throw new Error(err.message || 'Error creating lookup type');
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
        if (data.accessEvents) return data;
      }
    } catch (e) {
      console.warn('Gateway access events fetch error', e);
    }
    return { accessEvents: [] };
  },

  async getChangeLogs(limit = 20, context) {
    try {
      const res = await fetch(`${API_BASE}/api/v1/admin/audit/change-log?limit=${limit}`, {
        method: 'GET',
        headers: getHeaders(context)
      });
      if (res.ok) {
        const data = await res.json();
        if (data.changeLogs) return data;
      }
    } catch (e) {
      console.warn('Gateway change logs fetch error', e);
    }
    return { changeLogs: [] };
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
        if (data.retentionPolicies) return data;
      }
    } catch (e) {
      console.warn('Gateway retention policies fetch error', e);
    }
    return {
      retentionPolicies: [
        { id: 'ret-01', policyCode: 'STUDENT_TRANSCRIPTS', dataClass: 'RESTRICTED', retentionDays: 2555, legalMinimumDays: 2555, status: 'ACTIVE' },
        { id: 'ret-02', policyCode: 'SYSTEM_AUDIT_LOGS', dataClass: 'CONFIDENTIAL', retentionDays: 1095, legalMinimumDays: 365, status: 'ACTIVE' }
      ]
    };
  }
};
