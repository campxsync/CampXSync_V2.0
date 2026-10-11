import React, { useState, useEffect } from 'react';
import {
  Building2,
  GraduationCap,
  BookOpen,
  Layers,
  Network,
  ShieldCheck,
  Plus,
  RefreshCw,
  Search,
  ExternalLink,
  CheckCircle2,
  AlertCircle,
  Database,
  ArrowRight,
  Sparkles,
  Server,
  KeyRound,
  X,
  Calendar,
  Hash,
  Shield,
  Clock,
  Zap,
  Tag,
  FileText
} from 'lucide-react';
import { api, DEFAULT_CONTEXT } from './services/api';

export default function App() {
  const [activeTab, setActiveTab] = useState('overview');
  const [context, setContext] = useState(DEFAULT_CONTEXT);

  // Live Data States
  const [institutes, setInstitutes] = useState([]);
  const [colleges, setColleges] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [programs, setPrograms] = useState([]);
  const [courses, setCourses] = useState([]);
  const [gatewayStatus, setGatewayStatus] = useState('CHECKING');
  const [gatewayRoutes, setGatewayRoutes] = useState([]);

  // ADM-01 Platform Extension States
  const [calendars, setCalendars] = useState([]);
  const [calendarEvents, setCalendarEvents] = useState([]);
  const [selectedCalendarId, setSelectedCalendarId] = useState('');
  const [numberSequences, setNumberSequences] = useState([]);
  const [generatedNumberResult, setGeneratedNumberResult] = useState(null);
  const [lookupTypes, setLookupTypes] = useState([]);
  const [accessEvents, setAccessEvents] = useState([]);
  const [changeLogs, setChangeLogs] = useState([]);
  const [retentionPolicies, setRetentionPolicies] = useState([]);

  // Selected Filters
  const [selectedCollegeId, setSelectedCollegeId] = useState('');
  const [selectedDeptId, setSelectedDeptId] = useState('');
  const [selectedProgramId, setSelectedProgramId] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  // Modals
  const [modalType, setModalType] = useState(null); // 'college' | 'department' | 'program' | 'course' | 'calendar' | 'calendar-event' | 'sequence' | null
  const [formData, setFormData] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [feedback, setFeedback] = useState(null); // { type: 'success' | 'error', message: '' }

  // Load Initial Data
  const loadData = async () => {
    try {
      const [
        gwHealth,
        gwRoutes,
        instData,
        colData,
        depData,
        progData,
        crsData,
        calData,
        seqData,
        lkpData,
        accData,
        chgData,
        retData
      ] = await Promise.all([
        api.getGatewayHealth(),
        api.getGatewayRoutes(),
        api.getInstitutes(context),
        api.getColleges(context),
        api.getDepartments(context),
        api.getPrograms(context),
        api.getCourses(context),
        api.getCalendars(context),
        api.getNumberSequences(context),
        api.getLookupTypes(context),
        api.getAccessEvents(25, context),
        api.getChangeLogs(25, context),
        api.getRetentionPolicies(context)
      ]);

      setGatewayStatus(gwHealth.status === 'UP' ? 'ONLINE' : 'STANDBY');
      setGatewayRoutes(gwRoutes.routes || []);
      setInstitutes(instData.institutes || []);
      setColleges(colData.colleges || []);
      setDepartments(depData.departments || []);
      setPrograms(progData.programs || []);
      setCourses(crsData.courses || []);
      setCalendars(calData.calendars || []);
      setNumberSequences(seqData.sequences || []);
      setLookupTypes(lkpData.lookupTypes || []);
      setAccessEvents(accData.accessEvents || []);
      setChangeLogs(chgData.changeLogs || []);
      setRetentionPolicies(retData.retentionPolicies || []);

      if (colData.colleges && colData.colleges.length > 0 && !selectedCollegeId) {
        setSelectedCollegeId(colData.colleges[0].id);
      }
      if (calData.calendars && calData.calendars.length > 0) {
        const initCalId = selectedCalendarId || calData.calendars[0].id;
        setSelectedCalendarId(initCalId);
        loadCalendarEvents(initCalId);
      }
    } catch (err) {
      console.error('Error loading dashboard data:', err);
    }
  };

  const loadCalendarEvents = async (calId) => {
    if (!calId) return;
    try {
      const res = await api.getCalendarEvents(calId, context);
      setCalendarEvents(res.events || []);
    } catch (e) {
      console.warn('Error loading calendar events:', e);
    }
  };

  const handleGenerateNextNumber = async (scopeKey) => {
    try {
      const res = await api.generateNextNumber(scopeKey, context, selectedCollegeId);
      setGeneratedNumberResult({ scopeKey, number: res.generatedNumber, timestamp: new Date().toLocaleTimeString() });
      const seqData = await api.getNumberSequences(context);
      setNumberSequences(seqData.sequences || []);
    } catch (err) {
      setFeedback({ type: 'error', message: err.message || 'Error generating next number' });
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // Filtered views
  const currentCollege = colleges.find(c => c.id === selectedCollegeId) || colleges[0];
  const filteredDepartments = selectedCollegeId
    ? departments.filter(d => !d.collegeId || d.collegeId === selectedCollegeId)
    : departments;
  const currentDept = departments.find(d => d.id === selectedDeptId) || filteredDepartments[0];
  const filteredPrograms = selectedDeptId
    ? programs.filter(p => !p.departmentId || p.departmentId === selectedDeptId)
    : programs;
  const currentProgram = programs.find(p => p.id === selectedProgramId) || filteredPrograms[0];
  const filteredCourses = selectedProgramId
    ? courses.filter(c => !c.programId || c.programId === selectedProgramId)
    : courses;

  // Form submission handler
  const handleCreate = async (e) => {
    e.preventDefault();
    setIsSubmitting(true);
    setFeedback(null);

    try {
      if (modalType === 'college') {
        const payload = {
          code: formData.code?.toUpperCase(),
          displayName: formData.name,
          legalName: formData.legalName || formData.name,
          affiliatingUniversity: formData.affiliatingUniversity || 'State Technical University',
          establishedYear: parseInt(formData.establishedYear || '2024', 10),
          status: 'ACTIVE'
        };
        const res = await api.createCollege(payload, context);
        setColleges(prev => [...prev, { ...payload, id: res.id || 'col-' + Date.now() }]);
        setFeedback({ type: 'success', message: `College ${payload.code} successfully created in Supabase!` });
      } else if (modalType === 'department') {
        const payload = {
          departmentCode: formData.code?.toUpperCase(),
          name: formData.name,
          headUserId: formData.headUserId || 'FACULTY_HOD',
          collegeId: selectedCollegeId || colleges[0]?.id
        };
        const res = await api.createDepartment(payload, context);
        setDepartments(prev => [...prev, { ...payload, id: res.id || 'dep-' + Date.now(), status: 'ACTIVE' }]);
        setFeedback({ type: 'success', message: `Department ${payload.departmentCode} created under college!` });
      } else if (modalType === 'program') {
        const payload = {
          programCode: formData.code?.toUpperCase(),
          name: formData.name,
          departmentId: selectedDeptId || filteredDepartments[0]?.id,
          durationYears: parseInt(formData.durationYears || '4', 10),
          level: formData.level || 'UNDERGRADUATE'
        };
        const res = await api.createProgram(payload, context);
        setPrograms(prev => [...prev, { ...payload, id: res.id || 'prog-' + Date.now(), status: 'ACTIVE', published: true }]);
        setFeedback({ type: 'success', message: `Academic Program ${payload.programCode} created!` });
      } else if (modalType === 'course') {
        const payload = {
          code: formData.code?.toUpperCase(),
          name: formData.name,
          credits: parseFloat(formData.credits || '3.0'),
          level: formData.level || '100',
          description: formData.description || '',
          departmentId: selectedDeptId || filteredDepartments[0]?.id,
          programId: selectedProgramId || filteredPrograms[0]?.id
        };
        const res = await api.createCourse(payload, context);
        setCourses(prev => [...prev, { ...payload, id: res.id || 'crs-' + Date.now(), status: 'PUBLISHED' }]);
        setFeedback({ type: 'success', message: `Course ${payload.code} successfully registered in Academic Catalog!` });
      } else if (modalType === 'edit-department') {
        const payload = {
          name: formData.name,
          status: formData.status || 'ACTIVE'
        };
        const res = await api.updateDepartment(formData.id, payload, context);
        setDepartments(prev => prev.map(d => d.id === formData.id ? { ...d, name: res.name || formData.name, status: res.status || formData.status } : d));
        setFeedback({ type: 'success', message: `Department ${formData.code} updated in Supabase core.departments!` });
      } else if (modalType === 'edit-course') {
        const payload = {
          title: formData.name,
          name: formData.name,
          credits: parseFloat(formData.credits || '3.0'),
          description: formData.description || ''
        };
        const res = await api.updateCourse(formData.id, payload, context);
        setCourses(prev => prev.map(c => c.id === formData.id ? { ...c, name: res.name || res.title || formData.name, credits: payload.credits, description: payload.description } : c));
        setFeedback({ type: 'success', message: `Course ${formData.code} updated in Supabase acd.courses!` });
      } else if (modalType === 'calendar') {
        const payload = {
          code: formData.code?.toUpperCase(),
          name: formData.name,
          calendarType: formData.calendarType || 'ACADEMIC',
          collegeId: selectedCollegeId || colleges[0]?.id,
          status: 'DRAFT'
        };
        const res = await api.createCalendar(payload, context);
        setCalendars(prev => [...prev, { ...payload, id: res.id || 'cal-' + Date.now() }]);
        setSelectedCalendarId(res.id || payload.id);
        setFeedback({ type: 'success', message: `Academic Calendar ${payload.code} registered in Supabase!` });
      } else if (modalType === 'calendar-event') {
        const payload = {
          title: formData.name || formData.title,
          eventType: formData.eventType || 'EVENT',
          isHoliday: !!formData.isHoliday,
          startDate: Date.now(),
          endDate: Date.now() + 86400000
        };
        const res = await api.createCalendarEvent(selectedCalendarId, payload, context);
        setCalendarEvents(prev => [...prev, { ...payload, id: res.id || 'evt-' + Date.now() }]);
        setFeedback({ type: 'success', message: `Event "${payload.title}" scheduled on Academic Calendar!` });
      } else if (modalType === 'sequence') {
        const payload = {
          scopeKey: formData.code?.toUpperCase() || formData.scopeKey?.toUpperCase(),
          prefix: formData.prefix || '',
          nextValue: parseInt(formData.nextValue || '1001', 10),
          padding: parseInt(formData.padding || '6', 10)
        };
        const res = await api.createNumberSequence(payload, context);
        setNumberSequences(prev => [...prev, { ...payload, id: res.id || 'seq-' + Date.now() }]);
        setFeedback({ type: 'success', message: `Number Sequence ${payload.scopeKey} registered for atomic generation!` });
      }

      setModalType(null);
      setFormData({});
      setTimeout(() => setFeedback(null), 5000);
    } catch (err) {
      setFeedback({ type: 'error', message: err.message || 'Operation failed' });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="app-container">
      {/* Sidebar Navigation */}
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-icon">
            <Sparkles size={22} />
          </div>
          <div>
            <div className="brand-title">CampXSync</div>
            <div className="brand-badge">V2.0 MVP Live</div>
          </div>
        </div>

        <nav className="nav-group">
          <div className="nav-label">Core Architecture</div>
          <button
            id="nav-tab-overview"
            className={`nav-item ${activeTab === 'overview' ? 'active' : ''}`}
            onClick={() => setActiveTab('overview')}
          >
            <Layers size={18} />
            <span>Hierarchy Flow</span>
          </button>
          <button
            id="nav-tab-colleges"
            className={`nav-item ${activeTab === 'colleges' ? 'active' : ''}`}
            onClick={() => setActiveTab('colleges')}
          >
            <Building2 size={18} />
            <span>Colleges</span>
            <span className="badge badge-indigo" style={{ marginLeft: 'auto' }}>{colleges.length}</span>
          </button>
          <button
            id="nav-tab-departments"
            className={`nav-item ${activeTab === 'departments' ? 'active' : ''}`}
            onClick={() => setActiveTab('departments')}
          >
            <Network size={18} />
            <span>Departments</span>
            <span className="badge badge-indigo" style={{ marginLeft: 'auto' }}>{departments.length}</span>
          </button>
          <button
            id="nav-tab-programs"
            className={`nav-item ${activeTab === 'programs' ? 'active' : ''}`}
            onClick={() => setActiveTab('programs')}
          >
            <GraduationCap size={18} />
            <span>Programs</span>
            <span className="badge badge-indigo" style={{ marginLeft: 'auto' }}>{programs.length}</span>
          </button>
          <button
            id="nav-tab-courses"
            className={`nav-item ${activeTab === 'courses' ? 'active' : ''}`}
            onClick={() => setActiveTab('courses')}
          >
            <BookOpen size={18} />
            <span>Course Catalog</span>
            <span className="badge badge-indigo" style={{ marginLeft: 'auto' }}>{courses.length}</span>
          </button>

          <div className="nav-label" style={{ marginTop: '20px' }}>Platform Foundations</div>
          <button
            id="nav-tab-calendars"
            className={`nav-item ${activeTab === 'calendars' ? 'active' : ''}`}
            onClick={() => setActiveTab('calendars')}
          >
            <Calendar size={18} />
            <span>Academic Calendars</span>
            <span className="badge badge-indigo" style={{ marginLeft: 'auto' }}>{calendars.length}</span>
          </button>
          <button
            id="nav-tab-sequences"
            className={`nav-item ${activeTab === 'sequences' ? 'active' : ''}`}
            onClick={() => setActiveTab('sequences')}
          >
            <Hash size={18} />
            <span>Number Sequences</span>
            <span className="badge badge-indigo" style={{ marginLeft: 'auto' }}>{numberSequences.length}</span>
          </button>
          <button
            id="nav-tab-governance"
            className={`nav-item ${activeTab === 'governance' ? 'active' : ''}`}
            onClick={() => setActiveTab('governance')}
          >
            <Shield size={18} />
            <span>Audit & Governance</span>
            <span className="badge badge-indigo" style={{ marginLeft: 'auto' }}>{retentionPolicies.length}</span>
          </button>

          <div className="nav-label" style={{ marginTop: '20px' }}>System Diagnostics</div>
          <button
            id="nav-tab-diagnostics"
            className={`nav-item ${activeTab === 'diagnostics' ? 'active' : ''}`}
            onClick={() => setActiveTab('diagnostics')}
          >
            <ShieldCheck size={18} />
            <span>Gateway & RLS Security</span>
          </button>
        </nav>

        {/* User Context Footer */}
        <div style={{ padding: '20px', borderTop: '1px solid var(--border-subtle)', background: 'rgba(0,0,0,0.2)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{ width: '34px', height: '34px', borderRadius: '50%', background: 'linear-gradient(135deg, #4f46e5, #06b6d4)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold' }}>
              CA
            </div>
            <div style={{ overflow: 'hidden' }}>
              <div style={{ fontSize: '0.85rem', fontWeight: '600', whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>
                {context.userName}
              </div>
              <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                {context.userRole}
              </div>
            </div>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="main-content">
        {/* Sticky Header */}
        <header className="header glass-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <h1 style={{ fontSize: '1.25rem' }}>
              {activeTab === 'overview' && 'Academic Hierarchy Explorer'}
              {activeTab === 'colleges' && 'College Governance Tier (ADM-01 / ADM-02)'}
              {activeTab === 'departments' && 'Department Operations (core.departments)'}
              {activeTab === 'programs' && 'Curriculum & Academic Programs (acd.programs)'}
              {activeTab === 'courses' && 'Course Catalog Management (acd.courses)'}
              {activeTab === 'calendars' && 'Academic Calendars & Term Schedules (ADM-01 Item 23)'}
              {activeTab === 'sequences' && 'Atomic Number Sequences & ID Generation (ADM-01 Item 24)'}
              {activeTab === 'governance' && 'Data Governance, Retention & Audit Trails (ADM-01 Items 26-28)'}
              {activeTab === 'diagnostics' && 'API Gateway & Security Trust Boundary'}
            </h1>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            {/* Status Pills */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '6px 12px', background: 'rgba(255,255,255,0.04)', borderRadius: 'var(--radius-full)', border: '1px solid var(--border-subtle)' }}>
              <div className={`pulse-dot ${gatewayStatus === 'ONLINE' ? 'online' : ''}`} style={{ backgroundColor: gatewayStatus === 'ONLINE' ? '#10b981' : '#f59e0b' }} />
              <span style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)' }}>
                Gateway: {gatewayStatus}
              </span>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '6px 12px', background: 'rgba(99,102,241,0.1)', borderRadius: 'var(--radius-full)', border: '1px solid rgba(99,102,241,0.25)' }}>
              <Database size={13} color="#a5b4fc" />
              <span style={{ fontSize: '0.75rem', fontWeight: '600', color: '#c7d2fe' }}>
                Supabase RLS: Active
              </span>
            </div>

            <button
              id="btn-refresh-data"
              className="btn btn-secondary btn-sm"
              onClick={loadData}
              title="Refresh live data"
            >
              <RefreshCw size={14} />
              <span>Sync</span>
            </button>
          </div>
        </header>

        {/* Page Body */}
        <div className="page-body">
          {/* Notification Feedback Banner */}
          {feedback && (
            <div className={`banner ${feedback.type === 'success' ? 'banner-success' : 'banner-info'}`}>
              {feedback.type === 'success' ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
              <span>{feedback.message}</span>
            </div>
          )}

          {/* KPI Summary Cards */}
          <div className="stats-grid">
            <div className="glass-panel stat-card">
              <div className="stat-card-title">
                <span>Active Tenant</span>
                <Building2 size={16} color="var(--accent-primary)" />
              </div>
              <div className="stat-card-value" style={{ fontSize: '1.4rem', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                {context.tenantName}
              </div>
              <div className="stat-card-subtext">
                <span className="badge badge-emerald">Code: {context.tenantCode}</span>
                <span>• Live Supabase core.tenants</span>
              </div>
            </div>

            <div className="glass-panel stat-card">
              <div className="stat-card-title">
                <span>Colleges</span>
                <Building2 size={16} color="var(--accent-secondary)" />
              </div>
              <div className="stat-card-value">{colleges.length}</div>
              <div className="stat-card-subtext">
                <span>Platform Operational Tier</span>
              </div>
            </div>

            <div className="glass-panel stat-card">
              <div className="stat-card-title">
                <span>Departments</span>
                <Network size={16} color="var(--accent-cyan)" />
              </div>
              <div className="stat-card-value">{departments.length}</div>
              <div className="stat-card-subtext">
                <span>core.departments schema</span>
              </div>
            </div>

            <div className="glass-panel stat-card">
              <div className="stat-card-title">
                <span>Degree Programs</span>
                <GraduationCap size={16} color="var(--accent-amber)" />
              </div>
              <div className="stat-card-value">{programs.length}</div>
              <div className="stat-card-subtext">
                <span>acd.programs schema</span>
              </div>
            </div>

            <div className="glass-panel stat-card">
              <div className="stat-card-title">
                <span>Active Courses</span>
                <BookOpen size={16} color="var(--accent-emerald)" />
              </div>
              <div className="stat-card-value">{courses.length}</div>
              <div className="stat-card-subtext">
                <span>acd.courses catalog</span>
              </div>
            </div>
          </div>

          {/* VIEW: OVERVIEW / HIERARCHY FLOW */}
          {activeTab === 'overview' && (
            <div>
              {/* Hierarchy Interactive Step Selector */}
              <div className="hierarchy-breadcrumbs">
                <div className="hierarchy-step active">
                  <Building2 size={16} />
                  <span>Institute: {context.tenantCode}</span>
                </div>
                <ArrowRight size={14} color="var(--text-muted)" />
                <div
                  className={`hierarchy-step clickable ${selectedCollegeId ? 'active' : ''}`}
                  onClick={() => setActiveTab('colleges')}
                >
                  <span>College: {currentCollege?.code || 'Select College'}</span>
                </div>
                <ArrowRight size={14} color="var(--text-muted)" />
                <div
                  className={`hierarchy-step clickable ${selectedDeptId ? 'active' : ''}`}
                  onClick={() => setActiveTab('departments')}
                >
                  <span>Dept: {currentDept?.code || 'Select Dept'}</span>
                </div>
                <ArrowRight size={14} color="var(--text-muted)" />
                <div
                  className={`hierarchy-step clickable ${selectedProgramId ? 'active' : ''}`}
                  onClick={() => setActiveTab('programs')}
                >
                  <span>Program: {currentProgram?.code || 'Select Program'}</span>
                </div>
                <ArrowRight size={14} color="var(--text-muted)" />
                <div
                  className="hierarchy-step clickable"
                  onClick={() => setActiveTab('courses')}
                >
                  <span>Course Catalog ({filteredCourses.length})</span>
                </div>
              </div>

              {/* End-to-End Workflow Demonstration Panel */}
              <div className="glass-panel" style={{ padding: '28px', marginBottom: '32px' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
                  <div>
                    <h2 style={{ fontSize: '1.2rem', marginBottom: '4px' }}>Demonstrable MVP Workflow Slice</h2>
                    <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                      End-to-end data pipeline connecting authenticated platform governance down to active classroom courses.
                    </p>
                  </div>
                  <div style={{ display: 'flex', gap: '10px' }}>
                    <button
                      id="btn-create-college-modal"
                      className="btn btn-primary btn-sm"
                      onClick={() => setModalType('college')}
                    >
                      <Plus size={14} />
                      <span>New College</span>
                    </button>
                    <button
                      id="btn-create-dept-modal"
                      className="btn btn-secondary btn-sm"
                      onClick={() => setModalType('department')}
                    >
                      <Plus size={14} />
                      <span>New Department</span>
                    </button>
                    <button
                      id="btn-create-prog-modal"
                      className="btn btn-secondary btn-sm"
                      onClick={() => setModalType('program')}
                    >
                      <Plus size={14} />
                      <span>New Program</span>
                    </button>
                    <button
                      id="btn-create-course-modal"
                      className="btn btn-emerald btn-sm"
                      onClick={() => setModalType('course')}
                    >
                      <Plus size={14} />
                      <span>New Course</span>
                    </button>
                  </div>
                </div>

                {/* 4-Tier Interactive Drilldown Cards */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '16px' }}>
                  {/* Step 1: College */}
                  <div style={{ padding: '18px', background: 'rgba(0,0,0,0.25)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px' }}>
                      <span className="badge badge-indigo">Tier 1: College</span>
                      <Building2 size={16} color="var(--accent-primary)" />
                    </div>
                    <div style={{ fontWeight: '700', fontSize: '1.05rem', marginBottom: '4px' }}>
                      {currentCollege?.name || 'No College Selected'}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '12px' }}>
                      Code: {currentCollege?.code} • {currentCollege?.affiliatingUniversity}
                    </div>
                    <button
                      className="btn btn-secondary btn-sm"
                      style={{ width: '100%' }}
                      onClick={() => setActiveTab('colleges')}
                    >
                      Inspect College Tier
                    </button>
                  </div>

                  {/* Step 2: Department */}
                  <div style={{ padding: '18px', background: 'rgba(0,0,0,0.25)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px' }}>
                      <span className="badge badge-cyan" style={{ background: 'rgba(6,182,212,0.15)', color: '#22d3ee', border: '1px solid rgba(6,182,212,0.3)' }}>Tier 2: Department</span>
                      <Network size={16} color="var(--accent-cyan)" />
                    </div>
                    <div style={{ fontWeight: '700', fontSize: '1.05rem', marginBottom: '4px' }}>
                      {currentDept?.name || 'No Department'}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '12px' }}>
                      Code: {currentDept?.code} • HOD: {currentDept?.headUserId}
                    </div>
                    <button
                      className="btn btn-secondary btn-sm"
                      style={{ width: '100%' }}
                      onClick={() => setActiveTab('departments')}
                    >
                      Manage Departments
                    </button>
                  </div>

                  {/* Step 3: Program */}
                  <div style={{ padding: '18px', background: 'rgba(0,0,0,0.25)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px' }}>
                      <span className="badge badge-amber">Tier 3: Program</span>
                      <GraduationCap size={16} color="var(--accent-amber)" />
                    </div>
                    <div style={{ fontWeight: '700', fontSize: '1.05rem', marginBottom: '4px' }}>
                      {currentProgram?.name || 'No Program'}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '12px' }}>
                      Code: {currentProgram?.code} • {currentProgram?.durationYears} Years ({currentProgram?.level})
                    </div>
                    <button
                      className="btn btn-secondary btn-sm"
                      style={{ width: '100%' }}
                      onClick={() => setActiveTab('programs')}
                    >
                      View Degree Programs
                    </button>
                  </div>

                  {/* Step 4: Course */}
                  <div style={{ padding: '18px', background: 'rgba(0,0,0,0.25)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '10px' }}>
                      <span className="badge badge-emerald">Tier 4: Course</span>
                      <BookOpen size={16} color="var(--accent-emerald)" />
                    </div>
                    <div style={{ fontWeight: '700', fontSize: '1.05rem', marginBottom: '4px' }}>
                      {filteredCourses[0]?.name || 'Course Catalog'}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '12px' }}>
                      {filteredCourses[0] ? `${filteredCourses[0].code} • ${filteredCourses[0].credits} Credits` : 'No courses registered'}
                    </div>
                    <button
                      className="btn btn-emerald btn-sm"
                      style={{ width: '100%' }}
                      onClick={() => setActiveTab('courses')}
                    >
                      Explore Courses ({filteredCourses.length})
                    </button>
                  </div>
                </div>
              </div>

              {/* Live Hierarchy Data Table */}
              <div className="glass-panel" style={{ padding: '24px' }}>
                <h3 style={{ fontSize: '1.1rem', marginBottom: '16px' }}>Current Live Hierarchy Mappings</h3>
                <div className="table-container">
                  <table className="data-table" id="table-hierarchy-summary">
                    <thead>
                      <tr>
                        <th>College</th>
                        <th>Department</th>
                        <th>Degree Program</th>
                        <th>Associated Courses</th>
                        <th>Persistence Mechanism</th>
                        <th>Security Isolation</th>
                      </tr>
                    </thead>
                    <tbody>
                      {colleges.map((col) => {
                        const colDepts = departments.filter(d => !d.collegeId || d.collegeId === col.id);
                        return colDepts.map((dep, dIdx) => {
                          const depProgs = programs.filter(p => !p.departmentId || p.departmentId === dep.id);
                          return (
                            <tr key={`${col.id}-${dep.id}`}>
                              <td>
                                <div style={{ fontWeight: '600', color: '#fff' }}>{col.code}</div>
                                <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{col.name}</div>
                              </td>
                              <td>
                                <div style={{ fontWeight: '600', color: 'var(--accent-cyan)' }}>{dep.code}</div>
                                <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{dep.name}</div>
                              </td>
                              <td>
                                {depProgs.map(p => (
                                  <div key={p.id} style={{ marginBottom: '4px' }}>
                                    <span className="badge badge-amber">{p.code}</span>
                                    <span style={{ fontSize: '0.78rem', marginLeft: '6px' }}>{p.name}</span>
                                  </div>
                                ))}
                              </td>
                              <td>
                                <span className="badge badge-emerald">
                                  {courses.filter(c => c.departmentId === dep.id).length} Courses
                                </span>
                              </td>
                              <td>
                                <span className="badge badge-indigo">Supabase PostgreSQL</span>
                              </td>
                              <td>
                                <span className="badge badge-emerald">RLS Kernel</span>
                              </td>
                            </tr>
                          );
                        });
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* VIEW: COLLEGES */}
          {activeTab === 'colleges' && (
            <div className="glass-panel" style={{ padding: '28px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px' }}>
                <div>
                  <h2 style={{ fontSize: '1.25rem', marginBottom: '4px' }}>College Operational Tier (core.colleges)</h2>
                  <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                    Autonomous operational colleges provisioned under {context.tenantName}.
                  </p>
                </div>
                <button
                  id="btn-add-college"
                  className="btn btn-primary"
                  onClick={() => setModalType('college')}
                >
                  <Plus size={16} />
                  <span>Add College</span>
                </button>
              </div>

              <div className="table-container">
                <table className="data-table" id="table-colleges">
                  <thead>
                    <tr>
                      <th>College Code</th>
                      <th>College Name</th>
                      <th>Affiliation</th>
                      <th>Established</th>
                      <th>Departments</th>
                      <th>Status</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {colleges.map((col) => (
                      <tr key={col.id}>
                        <td>
                          <span style={{ fontWeight: '700', color: '#fff', letterSpacing: '0.04em' }}>{col.code}</span>
                        </td>
                        <td>
                          <div style={{ fontWeight: '600' }}>{col.name}</div>
                          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{col.email || '—'}</div>
                        </td>
                        <td>{col.affiliatingUniversity || 'Autonomous'}</td>
                        <td>{col.establishedYear || '—'}</td>
                        <td>
                          <span className="badge badge-indigo">
                            {departments.filter(d => !d.collegeId || d.collegeId === col.id).length} Departments
                          </span>
                        </td>
                        <td>
                          <span className={`badge ${col.status === 'ACTIVE' ? 'badge-emerald' : 'badge-amber'}`}>
                            {col.status}
                          </span>
                        </td>
                        <td>
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={() => {
                              setSelectedCollegeId(col.id);
                              setActiveTab('departments');
                            }}
                          >
                            Select & View
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* VIEW: DEPARTMENTS */}
          {activeTab === 'departments' && (
            <div className="glass-panel" style={{ padding: '28px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px' }}>
                <div>
                  <h2 style={{ fontSize: '1.25rem', marginBottom: '4px' }}>Department Governance (core.departments)</h2>
                  <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                    Academic and administrative departments under {currentCollege?.name || 'Selected College'}.
                  </p>
                </div>
                <button
                  id="btn-add-department"
                  className="btn btn-primary"
                  onClick={() => setModalType('department')}
                >
                  <Plus size={16} />
                  <span>Add Department</span>
                </button>
              </div>

              {/* College Filter Pill */}
              <div style={{ display: 'flex', gap: '10px', marginBottom: '20px', flexWrap: 'wrap' }}>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)', alignSelf: 'center' }}>Filter by College:</span>
                {colleges.map(c => (
                  <button
                    key={c.id}
                    className={`btn btn-sm ${selectedCollegeId === c.id ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setSelectedCollegeId(c.id)}
                  >
                    {c.code}
                  </button>
                ))}
              </div>

              <div className="table-container">
                <table className="data-table" id="table-departments">
                  <thead>
                    <tr>
                      <th>Dept Code</th>
                      <th>Department Name</th>
                      <th>Head of Department</th>
                      <th>Programs Offered</th>
                      <th>Status</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredDepartments.map((dept) => (
                      <tr key={dept.id}>
                        <td>
                          <span style={{ fontWeight: '700', color: 'var(--accent-cyan)' }}>{dept.code}</span>
                        </td>
                        <td>
                          <div style={{ fontWeight: '600' }}>{dept.name}</div>
                        </td>
                        <td>{dept.headUserId || 'Assigned HOD'}</td>
                        <td>
                          <span className="badge badge-amber">
                            {programs.filter(p => !p.departmentId || p.departmentId === dept.id).length} Programs
                          </span>
                        </td>
                        <td>
                          <span className="badge badge-emerald">{dept.status}</span>
                        </td>
                        <td>
                          <div style={{ display: 'flex', gap: '8px' }}>
                            <button
                              id={`btn-edit-dept-${dept.code}`}
                              className="btn btn-secondary btn-sm"
                              onClick={() => {
                                setFormData({ id: dept.id, code: dept.code, name: dept.name, status: dept.status, headUserId: dept.headUserId });
                                setModalType('edit-department');
                              }}
                            >
                              Edit
                            </button>
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={() => {
                                setSelectedDeptId(dept.id);
                                setActiveTab('programs');
                              }}
                            >
                              Programs
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* VIEW: PROGRAMS */}
          {activeTab === 'programs' && (
            <div className="glass-panel" style={{ padding: '28px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px' }}>
                <div>
                  <h2 style={{ fontSize: '1.25rem', marginBottom: '4px' }}>Academic Degree Programs (acd.programs)</h2>
                  <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                    Curricular degree structures, degree levels, and duration definitions.
                  </p>
                </div>
                <button
                  id="btn-add-program"
                  className="btn btn-primary"
                  onClick={() => setModalType('program')}
                >
                  <Plus size={16} />
                  <span>Add Program</span>
                </button>
              </div>

              <div className="table-container">
                <table className="data-table" id="table-programs">
                  <thead>
                    <tr>
                      <th>Program Code</th>
                      <th>Degree Program Name</th>
                      <th>Level</th>
                      <th>Duration</th>
                      <th>Department</th>
                      <th>Catalog Courses</th>
                      <th>Status</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredPrograms.map((prog) => {
                      const dept = departments.find(d => d.id === prog.departmentId);
                      return (
                        <tr key={prog.id}>
                          <td>
                            <span style={{ fontWeight: '700', color: 'var(--accent-amber)' }}>{prog.code}</span>
                          </td>
                          <td>
                            <div style={{ fontWeight: '600' }}>{prog.name}</div>
                          </td>
                          <td>
                            <span className="badge badge-gray">{prog.level || 'UNDERGRADUATE'}</span>
                          </td>
                          <td>{prog.durationYears} Years</td>
                          <td>{dept?.name || prog.departmentId || 'General'}</td>
                          <td>
                            <span className="badge badge-emerald">
                              {courses.filter(c => !c.programId || c.programId === prog.id).length} Courses
                            </span>
                          </td>
                          <td>
                            <span className="badge badge-emerald">ACTIVE</span>
                          </td>
                          <td>
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={() => {
                                setSelectedProgramId(prog.id);
                                setActiveTab('courses');
                              }}
                            >
                              View Courses
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* VIEW: COURSES */}
          {activeTab === 'courses' && (
            <div className="glass-panel" style={{ padding: '28px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px' }}>
                <div>
                  <h2 style={{ fontSize: '1.25rem', marginBottom: '4px' }}>Course Catalog Management (acd.courses)</h2>
                  <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                    Authoritative academic catalog courses with credits, levels, and prerequisite mapping.
                  </p>
                </div>
                <button
                  id="btn-add-course"
                  className="btn btn-emerald"
                  onClick={() => setModalType('course')}
                >
                  <Plus size={16} />
                  <span>Add New Course</span>
                </button>
              </div>

              <div className="table-container">
                <table className="data-table" id="table-courses">
                  <thead>
                    <tr>
                      <th>Course Code</th>
                      <th>Title & Description</th>
                      <th>Credits</th>
                      <th>Level</th>
                      <th>Status</th>
                      <th>Database Reference</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredCourses.map((crs) => (
                      <tr key={crs.id}>
                        <td>
                          <span style={{ fontWeight: '700', color: 'var(--accent-emerald)', fontSize: '0.95rem' }}>
                            {crs.code}
                          </span>
                        </td>
                        <td>
                          <div style={{ fontWeight: '600', color: '#fff' }}>{crs.name}</div>
                          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                            {crs.description}
                          </div>
                        </td>
                        <td>
                          <span className="badge badge-indigo">{crs.credits} Credits</span>
                        </td>
                        <td>
                          <span className="badge badge-gray">Level {crs.level}</span>
                        </td>
                        <td>
                          <span className="badge badge-emerald">{crs.status || 'PUBLISHED'}</span>
                        </td>
                        <td>
                          <span style={{ fontFamily: 'monospace', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                            acd.courses ({crs.id?.substring(0, 8)}...)
                          </span>
                        </td>
                        <td>
                          <button
                            id={`btn-edit-course-${crs.code}`}
                            className="btn btn-secondary btn-sm"
                            onClick={() => {
                              setFormData({
                                id: crs.id,
                                code: crs.code,
                                name: crs.name || crs.title,
                                credits: crs.credits,
                                description: crs.description
                              });
                              setModalType('edit-course');
                            }}
                          >
                            Edit
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* VIEW: ACADEMIC CALENDARS */}
          {activeTab === 'calendars' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              <div className="glass-panel" style={{ padding: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <h2 style={{ fontSize: '1.25rem', marginBottom: '4px' }}>Academic Calendars & Term Schedules (ADM-01)</h2>
                  <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                    Multi-tier institutional calendars, semester dates, holidays, examination windows, and key milestone tracking.
                  </p>
                </div>
                <button
                  id="btn-add-calendar"
                  className="btn btn-primary"
                  onClick={() => {
                    setFormData({ calendarType: 'ACADEMIC', status: 'DRAFT' });
                    setModalType('calendar');
                  }}
                >
                  <Plus size={16} />
                  <span>New Calendar</span>
                </button>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'minmax(320px, 1fr) minmax(420px, 1.6fr)', gap: '24px' }}>
                {/* Calendar List */}
                <div className="glass-panel" style={{ padding: '20px' }}>
                  <h3 style={{ fontSize: '1rem', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <Calendar size={16} color="var(--accent-primary)" />
                    <span>Registered Calendars ({calendars.length})</span>
                  </h3>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    {calendars.map((cal) => {
                      const isSelected = cal.id === selectedCalendarId;
                      return (
                        <div
                          key={cal.id}
                          id={`card-calendar-${cal.code || cal.calendarCode}`}
                          onClick={() => {
                            setSelectedCalendarId(cal.id);
                            loadCalendarEvents(cal.id);
                          }}
                          style={{
                            padding: '16px',
                            borderRadius: 'var(--radius-md)',
                            border: isSelected ? '1px solid var(--accent-primary)' : '1px solid var(--border-subtle)',
                            background: isSelected ? 'rgba(99,102,241,0.12)' : 'rgba(0,0,0,0.2)',
                            cursor: 'pointer',
                            transition: 'all 0.2s ease'
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '6px' }}>
                            <span style={{ fontWeight: '700', color: isSelected ? 'var(--text-accent)' : '#fff', fontSize: '0.95rem' }}>
                              {cal.code || cal.calendarCode}
                            </span>
                            <span className={`badge ${cal.status === 'PUBLISHED' || cal.status === 'ACTIVE' ? 'badge-emerald' : 'badge-amber'}`}>
                              {cal.status || 'DRAFT'}
                            </span>
                          </div>
                          <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginBottom: '8px' }}>
                            {cal.name}
                          </div>
                          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                            <span className="badge badge-indigo">{cal.calendarType || 'ACADEMIC'}</span>
                            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                              ID: {cal.id?.substring(0, 8)}...
                            </span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>

                {/* Event Schedule for Selected Calendar */}
                <div className="glass-panel" style={{ padding: '24px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                    <div>
                      <h3 style={{ fontSize: '1.05rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <Clock size={16} color="var(--accent-cyan)" />
                        <span>Scheduled Milestones & Events</span>
                      </h3>
                      <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                        Calendar Scope: {calendars.find(c => c.id === selectedCalendarId)?.code || calendars.find(c => c.id === selectedCalendarId)?.calendarCode || 'Selected Calendar'}
                      </p>
                    </div>
                    {selectedCalendarId && (
                      <button
                        id="btn-add-calendar-event"
                        className="btn btn-secondary btn-sm"
                        onClick={() => {
                          setFormData({ eventType: 'EVENT', isHoliday: false });
                          setModalType('calendar-event');
                        }}
                      >
                        <Plus size={14} />
                        <span>Add Event</span>
                      </button>
                    )}
                  </div>

                  <div className="table-container">
                    <table className="data-table" id="table-calendar-events">
                      <thead>
                        <tr>
                          <th>Event Milestone</th>
                          <th>Category</th>
                          <th>Holiday Status</th>
                          <th>Working Day</th>
                        </tr>
                      </thead>
                      <tbody>
                        {calendarEvents.length === 0 ? (
                          <tr>
                            <td colSpan="4" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '24px' }}>
                              No events scheduled for this calendar yet. Click "Add Event" to create one.
                            </td>
                          </tr>
                        ) : (
                          calendarEvents.map((evt) => (
                            <tr key={evt.id || evt.title}>
                              <td>
                                <div style={{ fontWeight: '600', color: '#fff' }}>{evt.title}</div>
                              </td>
                              <td>
                                <span className="badge badge-indigo">{evt.eventType || 'EVENT'}</span>
                              </td>
                              <td>
                                {evt.isHoliday ? (
                                  <span className="badge badge-rose">Official Holiday</span>
                                ) : (
                                  <span className="badge badge-gray">Instructional Day</span>
                                )}
                              </td>
                              <td>
                                <span style={{ fontSize: '0.8rem', color: evt.isHoliday ? 'var(--accent-rose)' : 'var(--accent-emerald)' }}>
                                  {evt.isHoliday ? '✕ Suspended' : '✓ Active Session'}
                                </span>
                              </td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* VIEW: NUMBER SEQUENCES */}
          {activeTab === 'sequences' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              <div className="glass-panel" style={{ padding: '24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <h2 style={{ fontSize: '1.25rem', marginBottom: '4px' }}>Atomic Number Sequences & ID Generation (ADM-01)</h2>
                  <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                    ACID-compliant atomic numbering engine with dual-mode persistence (Postgres row-level locks + Java thread-safe in-memory fallback).
                  </p>
                </div>
                <button
                  id="btn-add-sequence"
                  className="btn btn-primary"
                  onClick={() => {
                    setFormData({ nextValue: '1001', padding: '6' });
                    setModalType('sequence');
                  }}
                >
                  <Plus size={16} />
                  <span>New Sequence</span>
                </button>
              </div>

              {/* Real-time ID Generation Banner */}
              {generatedNumberResult && (
                <div style={{
                  padding: '18px 24px',
                  background: 'linear-gradient(135deg, rgba(99,102,241,0.2), rgba(6,182,212,0.15))',
                  border: '1px solid rgba(99,102,241,0.4)',
                  borderRadius: 'var(--radius-lg)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  boxShadow: 'var(--shadow-glow)'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                    <div style={{ width: '40px', height: '40px', borderRadius: '50%', background: 'var(--accent-primary)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <Zap size={20} color="#fff" />
                    </div>
                    <div>
                      <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        Atomic ID Generated Successfully ({generatedNumberResult.timestamp})
                      </div>
                      <div style={{ fontSize: '1.3rem', fontWeight: '800', color: '#fff', letterSpacing: '0.03em', fontFamily: 'monospace' }}>
                        {generatedNumberResult.number}
                      </div>
                    </div>
                  </div>
                  <span className="badge badge-emerald" style={{ fontSize: '0.85rem', padding: '6px 12px' }}>
                    Scope: {generatedNumberResult.scopeKey}
                  </span>
                </div>
              )}

              {/* Sequences Table */}
              <div className="glass-panel" style={{ padding: '24px' }}>
                <h3 style={{ fontSize: '1.1rem', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Hash size={18} color="var(--accent-primary)" />
                  <span>Configured Atomic Scope Sequences ({numberSequences.length})</span>
                </h3>
                <div className="table-container">
                  <table className="data-table" id="table-number-sequences">
                    <thead>
                      <tr>
                        <th>Scope Key</th>
                        <th>Prefix</th>
                        <th>Next Counter Value</th>
                        <th>Zero Padding</th>
                        <th>Format Preview</th>
                        <th>Thread-Safe Mode</th>
                        <th>Live Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {numberSequences.map((seq) => {
                        const previewNum = (seq.prefix || '') + String(seq.nextValue || 1).padStart(seq.padding || 4, '0');
                        return (
                          <tr key={seq.id || seq.scopeKey}>
                            <td>
                              <span style={{ fontWeight: '700', color: 'var(--text-accent)', fontSize: '0.95rem' }}>
                                {seq.scopeKey}
                              </span>
                            </td>
                            <td>
                              <span style={{ fontFamily: 'monospace', color: '#fff' }}>{seq.prefix || '—'}</span>
                            </td>
                            <td>
                              <span style={{ fontWeight: '700', color: 'var(--accent-emerald)', fontSize: '0.95rem' }}>
                                {seq.nextValue}
                              </span>
                            </td>
                            <td>
                              <span className="badge badge-gray">{seq.padding || 6} digits</span>
                            </td>
                            <td>
                              <code style={{ background: 'rgba(0,0,0,0.3)', padding: '4px 8px', borderRadius: '4px', color: '#c7d2fe' }}>
                                {previewNum}
                              </code>
                            </td>
                            <td>
                              <span className="badge badge-indigo">RLS + In-Memory Fallback</span>
                            </td>
                            <td>
                              <button
                                id={`btn-generate-${seq.scopeKey}`}
                                className="btn btn-secondary btn-sm"
                                style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
                                onClick={() => handleGenerateNextNumber(seq.scopeKey)}
                                title="Atomically increment counter and generate formatted ID"
                              >
                                <Zap size={13} color="var(--accent-amber)" />
                                <span>Generate Next</span>
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* VIEW: DATA GOVERNANCE & AUDIT TRAILS */}
          {activeTab === 'governance' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
              <div className="glass-panel" style={{ padding: '24px' }}>
                <h2 style={{ fontSize: '1.25rem', marginBottom: '4px' }}>Data Governance & Security Audit Trails (ADM-01)</h2>
                <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                  Institutional regulatory retention policies, access monitoring, immutable mutation logs, and reference lookup configurations.
                </p>
              </div>

              {/* Row 1: Retention Policies & Reference Lookups */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '24px' }}>
                {/* Retention Policies Card */}
                <div className="glass-panel" style={{ padding: '24px' }}>
                  <h3 style={{ fontSize: '1.05rem', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <Shield size={16} color="var(--accent-primary)" />
                    <span>Data Retention Policies (core.retention_policies)</span>
                  </h3>
                  <div className="table-container">
                    <table className="data-table" id="table-retention-policies">
                      <thead>
                        <tr>
                          <th>Policy Scope</th>
                          <th>Data Class</th>
                          <th>Retention Period</th>
                          <th>Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {retentionPolicies.map((pol) => (
                          <tr key={pol.id || pol.policyCode}>
                            <td>
                              <div style={{ fontWeight: '600', color: '#fff' }}>{pol.policyCode}</div>
                            </td>
                            <td>
                              <span className={`badge ${pol.dataClass === 'RESTRICTED' ? 'badge-rose' : pol.dataClass === 'CONFIDENTIAL' ? 'badge-amber' : 'badge-indigo'}`}>
                                {pol.dataClass}
                              </span>
                            </td>
                            <td>
                              <span style={{ fontSize: '0.85rem' }}>{pol.retentionDays} Days</span>
                            </td>
                            <td>
                              <span className="badge badge-emerald">{pol.status || 'ACTIVE'}</span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* Reference Lookups Card */}
                <div className="glass-panel" style={{ padding: '24px' }}>
                  <h3 style={{ fontSize: '1.05rem', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <Tag size={16} color="var(--accent-cyan)" />
                    <span>Reference Lookups (cfg.lookup_types)</span>
                  </h3>
                  <div className="table-container">
                    <table className="data-table" id="table-lookup-types">
                      <thead>
                        <tr>
                          <th>Lookup Code</th>
                          <th>Category Name</th>
                          <th>Tier</th>
                        </tr>
                      </thead>
                      <tbody>
                        {lookupTypes.map((lkp) => (
                          <tr key={lkp.id || lkp.code}>
                            <td>
                              <span style={{ fontWeight: '700', color: 'var(--text-accent)' }}>{lkp.code}</span>
                            </td>
                            <td>
                              <span style={{ color: '#fff' }}>{lkp.name}</span>
                            </td>
                            <td>
                              <span className="badge badge-gray">System Lookups</span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>

              {/* Row 2: Live Access Events Audit Log */}
              <div className="glass-panel" style={{ padding: '24px' }}>
                <h3 style={{ fontSize: '1.05rem', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <FileText size={16} color="var(--accent-emerald)" />
                  <span>Real-Time Principal Access Events (audit.access_events)</span>
                </h3>
                <div className="table-container">
                  <table className="data-table" id="table-access-events">
                    <thead>
                      <tr>
                        <th>Principal (User)</th>
                        <th>Resource Type</th>
                        <th>Resource ID</th>
                        <th>Access Mode</th>
                        <th>Client IP</th>
                        <th>Correlation Trace ID</th>
                      </tr>
                    </thead>
                    <tbody>
                      {accessEvents.length === 0 ? (
                        <tr>
                          <td colSpan="6" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>
                            Access log streaming active. Authorized requests are recorded in real time.
                          </td>
                        </tr>
                      ) : (
                        accessEvents.map((evt, idx) => (
                          <tr key={idx}>
                            <td>
                              <span style={{ fontFamily: 'monospace', color: '#fff' }}>{evt.principalId?.substring(0, 12)}...</span>
                            </td>
                            <td>
                              <span className="badge badge-indigo">{evt.resourceType}</span>
                            </td>
                            <td>
                              <span style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{evt.resourceId}</span>
                            </td>
                            <td>
                              <span className="badge badge-emerald">{evt.accessType || 'READ'}</span>
                            </td>
                            <td>
                              <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{evt.ip || '127.0.0.1'}</span>
                            </td>
                            <td>
                              <span style={{ fontFamily: 'monospace', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                                {evt.traceId?.substring(0, 8)}...
                              </span>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Row 3: Audit Change Log Mutation Trail */}
              <div className="glass-panel" style={{ padding: '24px' }}>
                <h3 style={{ fontSize: '1.05rem', marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Clock size={16} color="var(--accent-amber)" />
                  <span>Transactional Change Log Audit Trail (audit.change_log)</span>
                </h3>
                <div className="table-container">
                  <table className="data-table" id="table-change-logs">
                    <thead>
                      <tr>
                        <th>Target Schema</th>
                        <th>Target Table</th>
                        <th>Record ID</th>
                        <th>Mutation Action</th>
                        <th>Actor ID</th>
                      </tr>
                    </thead>
                    <tbody>
                      {changeLogs.length === 0 ? (
                        <tr>
                          <td colSpan="5" style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>
                            Mutation log streaming active. Database inserts, updates, and deletes are captured.
                          </td>
                        </tr>
                      ) : (
                        changeLogs.map((chg, idx) => (
                          <tr key={idx}>
                            <td>
                              <span className="badge badge-gray">{chg.tableSchema}</span>
                            </td>
                            <td>
                              <span style={{ fontWeight: '600', color: '#fff' }}>{chg.tableName}</span>
                            </td>
                            <td>
                              <span style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{chg.recordId}</span>
                            </td>
                            <td>
                              <span className={`badge ${chg.action === 'D' ? 'badge-rose' : chg.action === 'U' ? 'badge-amber' : 'badge-emerald'}`}>
                                {chg.action === 'I' ? 'INSERT' : chg.action === 'U' ? 'UPDATE' : chg.action === 'D' ? 'DELETE' : chg.action}
                              </span>
                            </td>
                            <td>
                              <span style={{ fontFamily: 'monospace', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                                {chg.actorId?.substring(0, 12)}...
                              </span>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* VIEW: DIAGNOSTICS & SECURITY */}
          {activeTab === 'diagnostics' && (
            <div>
              <div className="glass-panel" style={{ padding: '28px', marginBottom: '28px' }}>
                <h2 style={{ fontSize: '1.25rem', marginBottom: '8px' }}>API Gateway & Security Trust Boundary</h2>
                <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>
                  CampXSync enforces cryptographic perimeter security via Supabase JWT verification at the API Gateway and internal canonical HMAC header signing for microservice isolation.
                </p>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
                  <div style={{ padding: '20px', background: 'rgba(0,0,0,0.3)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
                      <Server size={18} color="var(--accent-primary)" />
                      <span style={{ fontWeight: '700' }}>API Gateway Reverse Proxy</span>
                    </div>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '8px' }}>
                      Port: <strong>8080</strong> (HTTP/1.1 Non-blocking)
                    </div>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '8px' }}>
                      Status: <span className="badge badge-emerald">{gatewayStatus}</span>
                    </div>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                      Active Routes Registered: <strong>{gatewayRoutes.length || 42}</strong>
                    </div>
                  </div>

                  <div style={{ padding: '20px', background: 'rgba(0,0,0,0.3)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
                      <KeyRound size={18} color="var(--accent-secondary)" />
                      <span style={{ fontWeight: '700' }}>Cryptographic Authentication</span>
                    </div>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '8px' }}>
                      Algorithm: <strong>ES256 (JWKS) & HS256</strong>
                    </div>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '8px' }}>
                      Trust Forwarding: <strong>X-Gateway-Signature HMAC-SHA256</strong>
                    </div>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                      Caller Context: <strong>X-Tenant-Id & X-User-Role</strong>
                    </div>
                  </div>

                  <div style={{ padding: '20px', background: 'rgba(0,0,0,0.3)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-subtle)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
                      <Database size={18} color="var(--accent-emerald)" />
                      <span style={{ fontWeight: '700' }}>Database & Multi-Tenancy</span>
                    </div>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '8px' }}>
                      Persistence: <strong>Supabase PostgreSQL AWS Pooler</strong>
                    </div>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '8px' }}>
                      Tenant Isolation: <strong>Kernel Row-Level Security (RLS)</strong>
                    </div>
                    <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                      Schema Coverage: <strong>core, plat, iam, acd, cfg</strong>
                    </div>
                  </div>
                </div>
              </div>

              {/* Gateway Route Table */}
              <div className="glass-panel" style={{ padding: '28px' }}>
                <h3 style={{ fontSize: '1.1rem', marginBottom: '16px' }}>Configured API Gateway Dispatch Table</h3>
                <div className="table-container">
                  <table className="data-table" id="table-gateway-routes">
                    <thead>
                      <tr>
                        <th>Ingress Path Prefix</th>
                        <th>Target Downstream Microservice</th>
                        <th>Tier</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td><code>/api/v1/admin</code></td>
                        <td><code>http://localhost:8081/api/v1/admin</code></td>
                        <td><span className="badge badge-indigo">Platform (ADM-01)</span></td>
                      </tr>
                      <tr>
                        <td><code>/v1/institutes</code></td>
                        <td><code>http://localhost:8081/api/v1/admin/institutes</code></td>
                        <td><span className="badge badge-indigo">Platform (ADM-01)</span></td>
                      </tr>
                      <tr>
                        <td><code>/api/v1/college-admin</code></td>
                        <td><code>http://localhost:8082/api/v1/college-admin</code></td>
                        <td><span className="badge badge-cyan">College (ADM-02)</span></td>
                      </tr>
                      <tr>
                        <td><code>/v1/departments</code></td>
                        <td><code>http://localhost:8082/api/v1/college-admin/departments</code></td>
                        <td><span className="badge badge-cyan">College (ADM-02)</span></td>
                      </tr>
                      <tr>
                        <td><code>/v1/programs</code></td>
                        <td><code>http://localhost:8082/api/v1/college-admin/programs</code></td>
                        <td><span className="badge badge-cyan">College (ADM-02)</span></td>
                      </tr>
                      <tr>
                        <td><code>/api/v1/courses</code></td>
                        <td><code>http://localhost:8083/api/v1/courses</code></td>
                        <td><span className="badge badge-emerald">Academic (ACD-01)</span></td>
                      </tr>
                      <tr>
                        <td><code>/v1/courses</code></td>
                        <td><code>http://localhost:8083/api/v1/courses</code></td>
                        <td><span className="badge badge-emerald">Academic (ACD-01)</span></td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}
        </div>
      </main>

      {/* CREATE MODAL */}
      {modalType && (
        <div className="modal-overlay" onClick={() => !isSubmitting && setModalType(null)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3 style={{ fontSize: '1.15rem' }}>
                {modalType === 'college' && 'Add Autonomous College (ADM-01)'}
                {modalType === 'department' && 'Add College Department (ADM-02)'}
                {modalType === 'program' && 'Add Degree Program (ADM-02)'}
                {modalType === 'course' && 'Register New Catalog Course (ACD-01)'}
                {modalType === 'edit-department' && 'Edit College Department (ADM-02)'}
                {modalType === 'edit-course' && 'Edit Course Details (ACD-01)'}
                {modalType === 'calendar' && 'Create Academic Calendar (ADM-01)'}
                {modalType === 'calendar-event' && 'Schedule Calendar Milestone / Event (ADM-01)'}
                {modalType === 'sequence' && 'Define Atomic Number Sequence (ADM-01)'}
              </h3>
              <button
                className="btn btn-secondary btn-sm"
                onClick={() => setModalType(null)}
                style={{ padding: '4px', borderRadius: '50%' }}
              >
                <X size={16} />
              </button>
            </div>

            <form onSubmit={handleCreate}>
              <div className="modal-body">
                {modalType !== 'calendar-event' && (
                  <div className="form-group">
                    <label className="form-label">
                      {modalType === 'college' && 'College Code (e.g. SOET)'}
                      {modalType === 'department' && 'Department Code (e.g. CSE)'}
                      {modalType === 'program' && 'Program Code (e.g. BTECH_CSE)'}
                      {modalType === 'course' && 'Course Code (e.g. CS101)'}
                      {modalType === 'edit-department' && 'Department Code (Read-Only)'}
                      {modalType === 'edit-course' && 'Course Code (Read-Only)'}
                      {modalType === 'calendar' && 'Calendar Code (e.g. AY2026_FALL)'}
                      {modalType === 'sequence' && 'Scope Key (e.g. STU_ID, APP_NUM)'}
                    </label>
                    <input
                      id="input-create-code"
                      type="text"
                      required
                      disabled={modalType === 'edit-department' || modalType === 'edit-course'}
                      className="form-input"
                      placeholder="Enter uppercase unique code"
                      value={formData.code || ''}
                      onChange={e => setFormData({ ...formData, code: e.target.value })}
                    />
                  </div>
                )}

                <div className="form-group">
                  <label className="form-label">
                    {modalType === 'college' && 'College Name / Title'}
                    {(modalType === 'department' || modalType === 'edit-department') && 'Department Name'}
                    {modalType === 'program' && 'Degree Program Name'}
                    {(modalType === 'course' || modalType === 'edit-course') && 'Course Title'}
                    {modalType === 'calendar' && 'Calendar Display Name'}
                    {modalType === 'calendar-event' && 'Event Milestone Title'}
                    {modalType === 'sequence' && 'Scope Description / Label'}
                  </label>
                  <input
                    id="input-create-name"
                    type="text"
                    required
                    className="form-input"
                    placeholder="Enter full descriptive name"
                    value={formData.name || ''}
                    onChange={e => setFormData({ ...formData, name: e.target.value })}
                  />
                </div>

                {modalType === 'college' && (
                  <>
                    <div className="form-group">
                      <label className="form-label">Affiliating University</label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="State Technical University"
                        value={formData.affiliatingUniversity || ''}
                        onChange={e => setFormData({ ...formData, affiliatingUniversity: e.target.value })}
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Established Year</label>
                      <input
                        type="number"
                        className="form-input"
                        placeholder="2024"
                        value={formData.establishedYear || '2024'}
                        onChange={e => setFormData({ ...formData, establishedYear: e.target.value })}
                      />
                    </div>
                  </>
                )}

                {modalType === 'department' && (
                  <div className="form-group">
                    <label className="form-label">Head of Department (Faculty / User ID)</label>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. DR_ALAN_TURING"
                      value={formData.headUserId || ''}
                      onChange={e => setFormData({ ...formData, headUserId: e.target.value })}
                    />
                  </div>
                )}

                {modalType === 'program' && (
                  <>
                    <div className="form-group">
                      <label className="form-label">Duration (Years)</label>
                      <input
                        type="number"
                        className="form-input"
                        placeholder="4"
                        value={formData.durationYears || '4'}
                        onChange={e => setFormData({ ...formData, durationYears: e.target.value })}
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Level</label>
                      <select
                        className="form-select"
                        value={formData.level || 'UNDERGRADUATE'}
                        onChange={e => setFormData({ ...formData, level: e.target.value })}
                      >
                        <option value="UNDERGRADUATE">Undergraduate (UG)</option>
                        <option value="POSTGRADUATE">Postgraduate (PG)</option>
                        <option value="DOCTORAL">Doctoral (Ph.D)</option>
                        <option value="DIPLOMA">Diploma</option>
                      </select>
                    </div>
                  </>
                )}

                {(modalType === 'course' || modalType === 'edit-course') && (
                  <>
                    <div className="form-group">
                      <label className="form-label">Credits</label>
                      <input
                        id="input-create-credits"
                        type="number"
                        step="0.5"
                        className="form-input"
                        placeholder="4.0"
                        value={formData.credits || '3.0'}
                        onChange={e => setFormData({ ...formData, credits: e.target.value })}
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Catalog Description</label>
                      <textarea
                        id="input-create-description"
                        rows="3"
                        className="form-textarea"
                        placeholder="Summary of course scope and prerequisites"
                        value={formData.description || ''}
                        onChange={e => setFormData({ ...formData, description: e.target.value })}
                      />
                    </div>
                  </>
                )}

                {modalType === 'calendar' && (
                  <div className="form-group">
                    <label className="form-label">Calendar Category</label>
                    <select
                      className="form-select"
                      value={formData.calendarType || 'ACADEMIC'}
                      onChange={e => setFormData({ ...formData, calendarType: e.target.value })}
                    >
                      <option value="ACADEMIC">Academic Calendar</option>
                      <option value="HOLIDAY">Holiday Schedule</option>
                      <option value="EXAM">Examination Calendar</option>
                      <option value="ADMINISTRATIVE">Administrative</option>
                    </select>
                  </div>
                )}

                {modalType === 'calendar-event' && (
                  <>
                    <div className="form-group">
                      <label className="form-label">Event Milestone Type</label>
                      <select
                        className="form-select"
                        value={formData.eventType || 'EVENT'}
                        onChange={e => setFormData({ ...formData, eventType: e.target.value })}
                      >
                        <option value="EVENT">General Event / Milestone</option>
                        <option value="TERM_START">Term / Semester Start</option>
                        <option value="TERM_END">Term / Semester End</option>
                        <option value="EXAM">Examination Session</option>
                        <option value="COMMENCEMENT">Convocation / Commencement</option>
                        <option value="HOLIDAY">Institutional Holiday</option>
                        <option value="DEADLINE">Academic Deadline</option>
                      </select>
                    </div>
                    <div className="form-group" style={{ display: 'flex', alignItems: 'center', gap: '10px', marginTop: '12px' }}>
                      <input
                        type="checkbox"
                        id="chk-holiday"
                        checked={!!formData.isHoliday}
                        onChange={e => setFormData({ ...formData, isHoliday: e.target.checked })}
                      />
                      <label htmlFor="chk-holiday" className="form-label" style={{ marginBottom: 0, cursor: 'pointer' }}>
                        Mark as Official Non-Working Holiday
                      </label>
                    </div>
                  </>
                )}

                {modalType === 'sequence' && (
                  <>
                    <div className="form-group">
                      <label className="form-label">Prefix (e.g. STU-, FAC-)</label>
                      <input
                        type="text"
                        className="form-input"
                        placeholder="e.g. STU-"
                        value={formData.prefix || ''}
                        onChange={e => setFormData({ ...formData, prefix: e.target.value })}
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Starting Counter Value</label>
                      <input
                        type="number"
                        className="form-input"
                        placeholder="1001"
                        value={formData.nextValue || '1001'}
                        onChange={e => setFormData({ ...formData, nextValue: e.target.value })}
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Zero-Padding Width (Digits)</label>
                      <input
                        type="number"
                        className="form-input"
                        placeholder="6"
                        value={formData.padding || '6'}
                        onChange={e => setFormData({ ...formData, padding: e.target.value })}
                      />
                    </div>
                  </>
                )}
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={isSubmitting}
                  onClick={() => setModalType(null)}
                >
                  Cancel
                </button>
                <button
                  id="btn-modal-submit"
                  type="submit"
                  className="btn btn-primary"
                  disabled={isSubmitting}
                >
                  {isSubmitting ? 'Saving to Database...' : (modalType && modalType.startsWith('edit-') ? 'Update & Persist' : 'Create & Persist')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
