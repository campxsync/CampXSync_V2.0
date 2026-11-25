import {
  LayoutDashboard,
  Building2,
  Calendar,
  Users,
  GraduationCap,
  ClipboardCheck,
  History,
  Clock,
  Layers,
  BookOpen,
  FileText,
  Link2,
  LucideIcon,
} from 'lucide-react';
import { UserRole } from './auth/resolve-role';

export interface NavItem {
  title: string;
  href: string;
  icon: LucideIcon;
  badge?: string;
}

export const navConfigs: Record<UserRole, NavItem[]> = {
  platform_admin: [
    { title: 'Dashboard', href: '/platform', icon: LayoutDashboard },
    { title: 'Institutes & Tenancies', href: '/platform/institutes', icon: Building2 },
    { title: 'Onboard Institute', href: '/platform/institutes/new', icon: BookOpen },
    { title: 'Platform configuration', href: '/platform/configuration', icon: Layers },
    { title: 'Identity & access', href: '/platform/identity', icon: Users },
    { title: 'Billing & subscriptions', href: '/platform/billing', icon: Layers },
    { title: 'Account Provisioning', href: '/college/account-provisioning', icon: Link2 },
    { title: 'Platform Audit', href: '/platform/audit', icon: History },
  ],
  /** System Admin (R02) — operational identity desks only; no commercial config. */
  system_admin: [
    { title: 'Identity & access', href: '/platform/identity', icon: Users },
    { title: 'Technical identities', href: '/platform/identity/technical', icon: Link2 },
    { title: 'Billing (operational)', href: '/platform/billing', icon: Layers },
  ],
  /** Auditor (R28) — IADM-02 configuration history + IADM-03 evidence read-only. */
  auditor: [
    { title: 'Configuration versions', href: '/platform/configuration/versions', icon: History },
    { title: 'Platform catalogue', href: '/platform/configuration', icon: Layers },
    { title: 'Identity & access', href: '/platform/identity', icon: Users },
    { title: 'Access evidence', href: '/platform/identity/evidence', icon: History },
  ],
  /** Institution Admin (R03) — IADM-01 desk; colleges & handover live here (not Platform wizard). */
  institution_admin: [
    { title: 'Home', href: '/institution', icon: LayoutDashboard },
    { title: 'Institute profile', href: '/institution/profile', icon: Building2 },
    { title: 'Colleges', href: '/institution/colleges', icon: GraduationCap },
    { title: 'Institute access', href: '/institution/access', icon: Users },
    { title: 'Subscription', href: '/institution/billing', icon: Layers },
  ],
  college_admin: [
    { title: 'Dashboard', href: '/college', icon: LayoutDashboard },
    { title: 'Admissions & Applications', href: '/college/admissions', icon: FileText },
    { title: 'Course Registration Desk', href: '/college/enrollment', icon: BookOpen },
    { title: 'Lifecycle Desk', href: '/college/lifecycle', icon: GraduationCap },
    { title: 'Account Provisioning', href: '/college/account-provisioning', icon: Link2 },
    { title: 'Documents Catalog', href: '/college/documents', icon: FileText },
    { title: 'Guardians', href: '/college/guardians', icon: Users },
    { title: 'Departments & Programs', href: '/college/departments', icon: Building2 },
    { title: 'Faculty Roster', href: '/college/faculty', icon: Users },
    { title: 'Student Roster', href: '/college/students', icon: GraduationCap },
    { title: 'Timetable', href: '/college/timetable', icon: Calendar },
    { title: 'Batches & Subjects', href: '/college/batches', icon: Layers },
    { title: 'Attendance', href: '/college/attendance', icon: ClipboardCheck },
    { title: 'Audit Log', href: '/college/audit', icon: History },
  ],
  /** R31 Registrar — STM operational desks only; no Academic/College Admin inheritance (D10/D12d). */
  registrar: [
    { title: 'Lifecycle Desk', href: '/college/lifecycle', icon: GraduationCap },
    { title: 'Course Registration Desk', href: '/college/enrollment', icon: BookOpen },
    { title: 'Account Provisioning', href: '/college/account-provisioning', icon: Link2 },
    { title: 'Document Verification', href: '/college/documents', icon: FileText },
    { title: 'Guardians', href: '/college/guardians', icon: Users },
  ],
  faculty: [
    { title: 'Today', href: '/faculty', icon: Clock },
    { title: 'Take Attendance', href: '/faculty/attendance', icon: ClipboardCheck },
    { title: 'My Sessions', href: '/faculty/sessions', icon: History },
    { title: 'My Timetable', href: '/faculty/timetable', icon: Calendar },
    { title: 'My Advisees', href: '/faculty/advisees', icon: GraduationCap },
    { title: 'Section Rosters', href: '/faculty/roster', icon: Users },
  ],
  student: [
    { title: 'My Attendance', href: '/student', icon: ClipboardCheck },
    { title: 'My Timetable', href: '/student/timetable', icon: Calendar },
    { title: 'Admissions & Applications', href: '/student/admissions', icon: FileText },
    { title: 'Course Registration', href: '/student/enrollment', icon: BookOpen },
    { title: 'Lifecycle Status', href: '/student/lifecycle', icon: GraduationCap },
    { title: 'Guardians & Consent', href: '/student/guardians', icon: Users },
    { title: 'Documents', href: '/student/documents', icon: FileText },
    { title: 'Contact & Address', href: '/student/address', icon: Building2 },
    { title: 'Profile Corrections', href: '/student/corrections', icon: History },
  ],
};
