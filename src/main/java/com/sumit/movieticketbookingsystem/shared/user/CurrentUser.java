package com.sumit.movieticketbookingsystem.shared.user;

import java.util.UUID;

/**
 * The caller, as identified by the API gateway. Declare it as a controller parameter to get it injected.
 * Name, email and phone are optional; the gateway only sends what the user has on file.
 */
public record CurrentUser(UUID id, Role role, String name, String email, String phone) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
