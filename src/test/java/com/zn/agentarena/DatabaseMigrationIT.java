package com.zn.agentarena;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("ci")
class DatabaseMigrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesCoreSchemaAndAccountColumns() {
        Integer gameTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = 'aa_game'",
                Integer.class);
        Integer deletedColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'aa_model_config' "
                        + "AND column_name = 'deleted'",
                Integer.class);
        Integer accountColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'aa_user' "
                        + "AND column_name IN ('username', 'password_hash')",
                Integer.class);

        assertEquals(1, gameTableCount);
        assertEquals(1, deletedColumnCount);
        assertEquals(2, accountColumnCount);
    }
}
