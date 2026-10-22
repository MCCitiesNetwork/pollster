package com.minecraftcitiesnetwork.pollster.backend;

import org.jetbrains.annotations.NotNull;

/**
 * Connection settings for the MariaDB database.
 *
 * @param url      the JDBC URL without the leading {@code jdbc:}, for example
 *                 {@code mariadb://localhost:3306/pollster}
 * @param username the database user
 * @param password the database password
 */
public record DatabaseSettings(@NotNull String url, @NotNull String username, @NotNull String password) {

}
