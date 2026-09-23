package com.sumit.movieticketbookingsystem.shared.persistence;

import java.sql.SQLException;

/**
 * For turning a specific database constraint violation into a business error.
 */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    // Hibernate only fills in the constraint name for some SQL states (not for exclusion constraints, 23P01),
    // so this reads the driver's message instead; Postgres always quotes the constraint name in it.
    public static boolean isViolationOf(Throwable error, String constraintName) {
        String quoted = '"' + constraintName + '"';
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getMessage() != null && sql.getMessage().contains(quoted)) {
                return true;
            }
        }
        return false;
    }
}
