package com.devsocial.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Small schema fixes that Hibernate's ddl-auto=update can't make on databases created by older
 * versions. Each statement is idempotent and safe to run on every start.
 *
 *  - reports.target_type: older databases have a CHECK constraint listing only POST, COMMENT and
 *    USER, which would reject reports about direct messages (MESSAGE). The application validates
 *    the value itself (Java enum), so the constraint is dropped.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaUpgrades implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaUpgrades.class);
    private final JdbcTemplate jdbc;

    public SchemaUpgrades(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute("alter table reports drop constraint if exists reports_target_type_check");
        } catch (RuntimeException e) {
            log.warn("Schema upgrade skipped: {}", e.getMessage());
        }
    }
}
