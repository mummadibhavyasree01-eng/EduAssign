package com.mits.EduAssign;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import com.mits.EduAssign.Entity.AdminFaculty;
import com.mits.EduAssign.Entity.Semester;
import com.mits.EduAssign.Repository.AdminRepository;
import com.mits.EduAssign.Repository.SemesterRepository;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        // Seed semesters (1 to 2) if empty
        if (semesterRepository.count() == 0) {
            semesterRepository.save(new Semester(1, "Semester 1"));
            semesterRepository.save(new Semester(2, "Semester 2"));
            System.out.println("Seeded Semesters 1 to 2");
        } else {
            // Prune semesters higher than 2
            semesterRepository.findAll().forEach(s -> {
                if (s.getSemNumber() > 2) {
                    semesterRepository.delete(s);
                }
            });
        }

        // Fix database schema for subject_allocation table if old section_name column exists with NOT NULL constraint
        try {
            jdbcTemplate.execute("ALTER TABLE subject_allocation MODIFY section_name VARCHAR(255) NULL");
        } catch (Exception e) {
            // Ignore if column doesn't exist
        }
        try {
            jdbcTemplate.execute("ALTER TABLE subject_allocation DROP COLUMN section_name");
        } catch (Exception e) {
            // Ignore if column already dropped
        }

        // Programmatic one-time cleanup of orphaned database records referencing deleted faculty IDs
        try {
            jdbcTemplate.execute("DELETE FROM faculty_subject_preference WHERE faculty_id NOT IN (SELECT id FROM admin_faculty)");
            jdbcTemplate.execute("DELETE FROM subject_allocation WHERE faculty_id NOT IN (SELECT id FROM admin_faculty) AND faculty_id NOT LIKE 'UNKNOWN_%'");
            jdbcTemplate.execute("DELETE FROM section_allocation WHERE faculty_id NOT IN (SELECT id FROM admin_faculty) AND faculty_id NOT LIKE 'UNKNOWN_%'");
            jdbcTemplate.execute("DELETE FROM allocation_history WHERE faculty_id NOT IN (SELECT id FROM admin_faculty) AND faculty_id NOT LIKE 'UNKNOWN_%'");
            jdbcTemplate.execute("DELETE FROM allocation_history WHERE section_name REGEXP '^[0-9]+$'");
            System.out.println("--- Cleaned up orphaned preferences and allocations referencing deleted/actual faculty IDs and purged corrupt history ---");
        } catch (Exception e) {
            System.err.println("Error cleaning up orphaned allocations: " + e.getMessage());
        }

        // Deduplication logic commented out to prevent startup deletion of faculty data
        /*
        try {
            List<AdminFaculty> allUsers = adminRepository.findAll();
            java.util.Map<String, List<AdminFaculty>> usersByEmail = new java.util.HashMap<>();
            for (AdminFaculty u : allUsers) {
                if (u.getEmail() != null) {
                    String emailKey = u.getEmail().toLowerCase().trim();
                    usersByEmail.computeIfAbsent(emailKey, k -> new java.util.ArrayList<>()).add(u);
                }
            }

            for (java.util.Map.Entry<String, List<AdminFaculty>> entry : usersByEmail.entrySet()) {
                List<AdminFaculty> list = entry.getValue();
                if (list.size() > 1) {
                    System.out.println("Found duplicate email: " + entry.getKey() + " with " + list.size() + " accounts.");
                    AdminFaculty keepUser = null;
                    // 1. Try to find a non-numeric ID first
                    for (AdminFaculty u : list) {
                        if (u.getId() != null && !u.getId().matches("\\d+")) {
                            keepUser = u;
                            break;
                        }
                    }
                    // 2. Fallback to roles
                    if (keepUser == null) {
                        for (AdminFaculty u : list) {
                            if ("SUPERADMIN".equalsIgnoreCase(u.getRole())) {
                                keepUser = u;
                                break;
                            }
                        }
                    }
                    if (keepUser == null) {
                        for (AdminFaculty u : list) {
                            if ("ADMIN".equalsIgnoreCase(u.getRole())) {
                                keepUser = u;
                                break;
                            }
                        }
                    }
                    if (keepUser == null) {
                        keepUser = list.get(0);
                    }

                    String keepId = keepUser.getId();
                    System.out.println("Keeping user ID: " + keepId + " for email: " + keepUser.getEmail());

                    for (AdminFaculty u : list) {
                        if (!u.getId().equals(keepId)) {
                            String dupId = u.getId();
                            System.out.println("Merging and deleting duplicate user ID: " + dupId);
                            
                            // Re-link references in other tables
                            jdbcTemplate.update("UPDATE faculty_subject_preference SET faculty_id = ? WHERE faculty_id = ?", keepId, dupId);
                            jdbcTemplate.update("UPDATE subject_allocation SET faculty_id = ? WHERE faculty_id = ?", keepId, dupId);
                            jdbcTemplate.update("UPDATE section_allocation SET faculty_id = ? WHERE faculty_id = ?", keepId, dupId);
                            jdbcTemplate.update("UPDATE allocation_history SET faculty_id = ? WHERE faculty_id = ?", keepId, dupId);
                            
                            // Delete duplicate
                            adminRepository.delete(u);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error during database deduplication: " + e.getMessage());
            e.printStackTrace();
        }
        */

        // Reset all faculty passwords to faculty@mits on startup
        try {
            jdbcTemplate.update("UPDATE admin_faculty SET Password = ? WHERE Role = ?", "faculty@mits", "faculty");
            System.out.println("--- Reset all faculty passwords to faculty@mits ---");
        } catch (Exception e) {
            System.err.println("Error resetting faculty passwords: " + e.getMessage());
        }



        // Print registered users to console
        System.out.println("--- Cleaned Database Users ---");
        for (AdminFaculty u : adminRepository.findAll()) {
            System.out.println("User -> ID: " + u.getId() + " | Email: " + u.getEmail() + " | Name: " + u.getName() + " | Role: " + u.getRole() + " | Password: " + u.getPassword());
        }
    }
}
