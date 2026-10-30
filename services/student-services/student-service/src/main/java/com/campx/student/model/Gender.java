package com.campx.student.model;

/**
 * Valid genders aligned with check constraint {@code ck_persons_gender} on {@code core.persons}.
 */
public enum Gender {
    M,
    F,
    O,
    U;

    public static Gender fromString(String val) {
        if (val == null || val.trim().isEmpty()) {
            return U;
        }
        try {
            return Gender.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return U;
        }
    }
}
