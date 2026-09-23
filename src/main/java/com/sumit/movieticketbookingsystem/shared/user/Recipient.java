package com.sumit.movieticketbookingsystem.shared.user;

/**
 * How to reach a user. Any of the fields can be null; the gateway only sends what the user has on file.
 */
public record Recipient(String name, String email, String phone) {
}
