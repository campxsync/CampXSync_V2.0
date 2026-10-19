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
  ],
  student: [
    { title: 'My Attendance', href: '/student', icon: ClipboardCheck },
  ],
};
