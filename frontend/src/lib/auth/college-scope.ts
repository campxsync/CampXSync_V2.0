export function crossesCollege(
  assignedCollegeId: string | null | undefined,
  resourceCollegeId: string | null | undefined
): boolean {
  return Boolean(assignedCollegeId && resourceCollegeId && assignedCollegeId !== resourceCollegeId);
}
