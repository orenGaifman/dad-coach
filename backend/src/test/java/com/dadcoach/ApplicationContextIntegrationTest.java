package com.dadcoach;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationContextIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void dataSourceBeanExists() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    void flywayMigrationApplied() throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.getMetaData().getTables(null, "public", "father", null)) {
            assertThat(rs.next()).isTrue();
        }
    }
}
