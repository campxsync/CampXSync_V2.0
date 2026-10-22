package com.campx.student.model;

/**
 * Valid student lifecycle states as defined in the database check constraint
 * {@code ck_students_status} on table {@code stu.students}.
 */
public enum StudentStatus {
    ACTIVE,
    SUSPENDED,
    DETAINED,
    WITHDRAWN,
    GRADUATED,
    ALUMNI;

    /**
     * Checks if transitioning from the current state to the target state is permissible.
     *
     * @param target The requested target state.
     * @return true if the transition is allowed; false otherwise.
     */
    public boolean canTransitionTo(StudentStatus target) {
        if (target == null || target == this) {
            return false;
        }

        switch (this) {
            case ACTIVE:
                return target == SUSPENDED || target == DETAINED || target == WITHDRAWN || target == GRADUATED;
            case SUSPENDED:
                return target == ACTIVE || target == WITHDRAWN;
            case DETAINED:
                return target == ACTIVE || target == WITHDRAWN;
            case GRADUATED:
                return target == ALUMNI;
            case WITHDRAWN:
            case ALUMNI:
            default:
                // Terminal states cannot transition to other states without formal re-admission
                return false;
        }
    }
}
