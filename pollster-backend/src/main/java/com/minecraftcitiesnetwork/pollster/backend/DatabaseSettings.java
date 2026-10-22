package com.minecraftcitiesnetwork.pollster.backend;

/**
 * Connection details for the survey database.
 *
 * @param url      URL without the {@code jdbc:} prefix, e.g. {@code mariadb://host:3306/pollster}
 * @param username database user
 * @param password database password
 */
public record DatabaseSettings(String url, String username, String password) {
}
