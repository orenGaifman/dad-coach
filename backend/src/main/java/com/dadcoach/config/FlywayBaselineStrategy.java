package com.dadcoach.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * How Flyway builds a database (DECISIONS D-011). Production has a history (V1-V22 applied, the files are the exact
 * ones it ran) and simply migrates. A fresh, EMPTY schema cannot replay V1-V22 (they need pgvector and rebuild
 * long-deleted features), so it is created from {@value #BASELINE} - production's schema after V23 - and
 * baselined at version 23; V24+ then run on both paths alike.
 */
@Configuration
public class FlywayBaselineStrategy {

    private static final Logger log = LoggerFactory.getLogger(FlywayBaselineStrategy.class);
    static final String BASELINE = "db/baseline/V23__baseline_schema.sql";

    @Bean
    FlywayMigrationStrategy baselineEmptyDatabases() {
        return flyway -> {
            if (isEmpty(flyway)) {
                DataSource ds = flyway.getConfiguration().getDataSource();
                try (Connection c = ds.getConnection()) {
                    ScriptUtils.executeSqlScript(c, new ClassPathResource(BASELINE));
                } catch (SQLException e) {
                    throw new IllegalStateException("could not create the baseline schema", e);
                }
                flyway.baseline();
                log.atInfo().setMessage("flyway.baseline.created").addKeyValue("version",
                        flyway.getConfiguration().getBaselineVersion().getVersion()).log();
            }
            flyway.migrate();
        };
    }

    /** No Flyway history and no table at all in the target schema. */
    static boolean isEmpty(Flyway flyway) {
        try (Connection c = flyway.getConfiguration().getDataSource().getConnection();
             ResultSet rs = c.createStatement().executeQuery(
                     "SELECT count(*) FROM information_schema.tables WHERE table_schema = current_schema()")) {
            rs.next();
            return rs.getInt(1) == 0;
        } catch (SQLException e) {
            throw new IllegalStateException("could not inspect the database schema", e);
        }
    }
}
