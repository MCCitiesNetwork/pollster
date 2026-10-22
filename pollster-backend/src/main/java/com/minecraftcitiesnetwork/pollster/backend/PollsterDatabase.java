package com.minecraftcitiesnetwork.pollster.backend;

import com.minecraftcitiesnetwork.pollster.backend.mapper.AnswerMapper;
import com.minecraftcitiesnetwork.pollster.backend.mapper.PlayerStateMapper;
import com.minecraftcitiesnetwork.pollster.backend.mapper.ResponseMapper;
import com.minecraftcitiesnetwork.pollster.backend.mapper.SurveyStateMapper;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

/** A pooled MariaDB connection source with Pollster's schema applied and MyBatis configured. */
public final class PollsterDatabase implements AutoCloseable {

    private final PooledDataSource dataSource;
    private final SqlSessionFactory sessionFactory;

    private PollsterDatabase(@NotNull PooledDataSource dataSource, @NotNull SqlSessionFactory sessionFactory) {
        this.dataSource = dataSource;
        this.sessionFactory = sessionFactory;
    }

    /**
     * Builds the connection pool and brings the schema up to date.
     *
     * @throws SQLException if the database is unreachable, a migration fails, or the schema is newer
     *                      than this plugin understands. The pool is closed before the exception leaves.
     */
    public static @NotNull PollsterDatabase open(@NotNull DatabaseSettings settings, @NotNull Logger logger)
            throws SQLException {
        PooledDataSource dataSource = new PooledDataSource(
                "org.mariadb.jdbc.Driver", jdbcUrl(settings), settings.username(), settings.password());
        dataSource.setPoolPingEnabled(true);
        dataSource.setPoolPingQuery("SELECT 1");
        dataSource.setPoolPingConnectionsNotUsedFor(600000);
        try {
            new PollsterMigrator().migrate(dataSource, PollsterMigrator.BASE_DIR, PollsterMigrator.STEPS,
                    PollsterDatabase.class.getClassLoader(), logger);
        } catch (SQLException | RuntimeException ex) {
            dataSource.forceCloseAll();
            throw ex;
        }
        return new PollsterDatabase(dataSource, buildSessionFactory(dataSource));
    }

    /**
     * Builds the JDBC URL, pinning the connection's time zone to the JVM's default.
     *
     * <p>Timestamps written by the JVM are compared in SQL against {@code NOW()}. If the database
     * server's time zone differs from the JVM's, those two clocks disagree.
     * {@code connectionTimeZone=LOCAL} plus {@code forceConnectionTimeZoneToSession=true} makes the
     * driver set the session {@code time_zone} to the JVM default so both agree.
     *
     * <p>An explicit {@code connectionTimeZone} in the configured URL is left untouched.
     */
    private static @NotNull String jdbcUrl(@NotNull DatabaseSettings settings) {
        String url = "jdbc:" + settings.url();
        if (url.contains("connectionTimeZone=")) {
            return url;
        }
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "connectionTimeZone=LOCAL&forceConnectionTimeZoneToSession=true";
    }

    private static @NotNull SqlSessionFactory buildSessionFactory(@NotNull DataSource dataSource) {
        Environment environment = new Environment("production", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidBinaryTypeHandler.class);
        configuration.addMapper(SurveyStateMapper.class);
        configuration.addMapper(ResponseMapper.class);
        configuration.addMapper(AnswerMapper.class);
        configuration.addMapper(PlayerStateMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    /** Opens a session with auto-commit off; the caller commits and closes it. */
    public @NotNull SqlSession openSession() {
        return this.sessionFactory.openSession();
    }

    public @NotNull DataSource dataSource() {
        return this.dataSource;
    }

    @Override
    public void close() {
        this.dataSource.forceCloseAll();
    }
}
