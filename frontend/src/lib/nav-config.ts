import {
  LayoutDashboard,
  Building2,
  Calendar,
  CalendarDays,
  Users,
  GraduationCap,
  ClipboardCheck,
  History,
  Clock,
  Layers,
  BookOpen,
  Library,
  ListChecks,
  Presentation,
  Wallet,
  FileCheck,
  Briefcase,
  Megaphone,
  MapPin,
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
    { title: 'Campuses', href: '/college/campuses', icon: MapPin },
    { title: 'Faculty Roster', href: '/college/faculty', icon: Users },
    { title: 'Student Roster', href: '/college/students', icon: GraduationCap },
    { title: 'Academic Calendar', href: '/college/calendar', icon: CalendarDays },
    { title: 'Timetable', href: '/college/timetable', icon: Calendar },
    { title: 'Batches & Subjects', href: '/college/batches', icon: Layers },
    { title: 'Curriculum', href: '/college/curriculum', icon: BookOpen },
    { title: 'Readings', href: '/college/resources', icon: Library },
    { title: 'Assessment', href: '/college/assessment', icon: ListChecks },
    { title: 'Learning', href: '/college/learning', icon: Presentation },
    { title: 'Finance', href: '/college/finance', icon: Wallet },
    { title: 'Examinations', href: '/college/examinations', icon: FileCheck },
    { title: 'Staff', href: '/college/staff', icon: Briefcase },
    { title: 'Communications', href: '/college/communications', icon: Megaphone },
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
