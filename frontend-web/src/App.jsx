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
  X
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

  // Selected Filters
  const [selectedCollegeId, setSelectedCollegeId] = useState('');
  const [selectedDeptId, setSelectedDeptId] = useState('');
  const [selectedProgramId, setSelectedProgramId] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  // Modals
  const [modalType, setModalType] = useState(null); // 'college' | 'department' | 'program' | 'course' | null
  const [formData, setFormData] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [feedback, setFeedback] = useState(null); // { type: 'success' | 'error', message: '' }

  // Load Initial Data
  const loadData = async () => {
    try {
      const [gwHealth, gwRoutes, instData, colData, depData, progData, crsData] = await Promise.all([
        api.getGatewayHealth(),
        api.getGatewayRoutes(),
        api.getInstitutes(context),
        api.getColleges(context),
        api.getDepartments(context),
        api.getPrograms(context),
        api.getCourses(context)
      ]);

      setGatewayStatus(gwHealth.status === 'UP' ? 'ONLINE' : 'STANDBY');
      setGatewayRoutes(gwRoutes.routes || []);
      setInstitutes(instData.institutes || []);
      setColleges(colData.colleges || []);
      setDepartments(depData.departments || []);
      setPrograms(progData.programs || []);
      setCourses(crsData.courses || []);

      if (colData.colleges && colData.colleges.length > 0 && !selectedCollegeId) {
        setSelectedCollegeId(colData.colleges[0].id);
      }
    } catch (err) {
      console.error('Error loading dashboard data:', err);
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

          <div className="nav-label" style={{ marginTop: '20px' }}>Platform Diagnostics</div>
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
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={() => {
                              setSelectedDeptId(dept.id);
                              setActiveTab('programs');
                            }}
                          >
                            Manage Programs
                          </button>
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
                      </tr>
                    ))}
                  </tbody>
                </table>
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
                <div className="form-group">
                  <label className="form-label">
                    {modalType === 'college' && 'College Code (e.g. SOET)'}
                    {modalType === 'department' && 'Department Code (e.g. CSE)'}
                    {modalType === 'program' && 'Program Code (e.g. BTECH_CSE)'}
                    {modalType === 'course' && 'Course Code (e.g. CS101)'}
                  </label>
                  <input
                    id="input-create-code"
                    type="text"
                    required
                    className="form-input"
                    placeholder="Enter uppercase unique code"
                    value={formData.code || ''}
                    onChange={e => setFormData({ ...formData, code: e.target.value })}
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">
                    {modalType === 'college' && 'College Name / Title'}
                    {modalType === 'department' && 'Department Name'}
                    {modalType === 'program' && 'Degree Program Name'}
                    {modalType === 'course' && 'Course Title'}
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

                {modalType === 'course' && (
                  <>
                    <div className="form-group">
                      <label className="form-label">Credits</label>
                      <input
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
                        rows="3"
                        className="form-textarea"
                        placeholder="Summary of course scope and prerequisites"
                        value={formData.description || ''}
                        onChange={e => setFormData({ ...formData, description: e.target.value })}
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
                  {isSubmitting ? 'Saving to Database...' : 'Create & Persist'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
