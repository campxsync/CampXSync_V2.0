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
    { title: 'Platform Audit', href: '/platform/audit', icon: History },
  ],
  college_admin: [
    { title: 'Dashboard', href: '/college', icon: LayoutDashboard },
    { title: 'Admissions & Applications', href: '/college/admissions', icon: FileText },
    { title: 'Course Registration Desk', href: '/college/enrollment', icon: BookOpen },
    { title: 'Lifecycle Desk', href: '/college/lifecycle', icon: GraduationCap },
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
    { title: 'Admissions & Applications', href: '/student/admissions', icon: FileText },
    { title: 'Course Registration', href: '/student/enrollment', icon: BookOpen },
    { title: 'Lifecycle Status', href: '/student/lifecycle', icon: GraduationCap },
    { title: 'Guardians & Consent', href: '/student/guardians', icon: Users },
    { title: 'Documents', href: '/student/documents', icon: FileText },
    { title: 'Contact & Address', href: '/student/address', icon: Building2 },
    { title: 'Profile Corrections', href: '/student/corrections', icon: History },
  ],
};
