package com.mits.EduAssign;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;

@SpringBootTest
public class DatabaseDumpTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void dumpWindows() {
        System.out.println("=== START WINDOW DUMP ===");
        try {
            List<Map<String, Object>> windows = jdbcTemplate.queryForList("SELECT * FROM subject_selection_window");
            for (Map<String, Object> w : windows) {
                System.out.println(w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("=== END WINDOW DUMP ===");
    }
}
