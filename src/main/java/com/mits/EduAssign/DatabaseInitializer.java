package com.mits.EduAssign;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.mits.EduAssign.Entity.AdminFaculty;
import com.mits.EduAssign.Entity.Department;
import com.mits.EduAssign.Entity.AcademicYear;
import com.mits.EduAssign.Entity.Section;
import com.mits.EduAssign.Repository.AdminRepository;
import com.mits.EduAssign.Repository.DepartmentRepository;
import com.mits.EduAssign.Repository.AcademicYearRepository;
import com.mits.EduAssign.Repository.SectionRepository;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @Override
    public void run(String... args) throws Exception {
        // 1. Seed SUPERADMIN
        if (!adminRepository.existsById("SUPERADMIN01")) {
            AdminFaculty superAdmin = new AdminFaculty();
            superAdmin.setId("SUPERADMIN01");
            superAdmin.setName("Super Admin");
            superAdmin.setEmail("superadmin@gmail.com");
            superAdmin.setPassword("superadmin");
            superAdmin.setRole("SUPERADMIN");
            adminRepository.save(superAdmin);
            System.out.println("Seeded Super Admin: superadmin@gmail.com / superadmin");
        }

        // 2. Seed default ADMIN
        if (!adminRepository.existsById("ADMIN01")) {
            AdminFaculty admin = new AdminFaculty();
            admin.setId("ADMIN01");
            admin.setName("System Admin");
            admin.setEmail("admin@gmail.com");
            admin.setPassword("admin");
            admin.setRole("ADMIN");
            adminRepository.save(admin);
            System.out.println("Seeded Admin: admin@gmail.com / admin");
        }

        // 3. Seed Departments
        if (departmentRepository.count() == 0) {
            departmentRepository.save(new Department("CSE", "Computer Science & Engineering"));
            departmentRepository.save(new Department("ECE", "Electronics & Communication Engineering"));
            departmentRepository.save(new Department("EEE", "Electrical & Electronics Engineering"));
            departmentRepository.save(new Department("ME", "Mechanical Engineering"));
            departmentRepository.save(new Department("CE", "Civil Engineering"));
            System.out.println("Seeded default Departments");
        }

        // 4. Seed Academic Years
        if (academicYearRepository.count() == 0) {
            academicYearRepository.save(new AcademicYear(1, "I Year"));
            academicYearRepository.save(new AcademicYear(2, "II Year"));
            academicYearRepository.save(new AcademicYear(3, "III Year"));
            academicYearRepository.save(new AcademicYear(4, "IV Year"));
            System.out.println("Seeded default Academic Years");
        }

        // 5. Seed Sections
        if (sectionRepository.count() == 0) {
            // Let's seed default sections for CSE department
            sectionRepository.save(new Section("CSE", 1, "A"));
            sectionRepository.save(new Section("CSE", 1, "B"));
            sectionRepository.save(new Section("CSE", 2, "A"));
            sectionRepository.save(new Section("CSE", 2, "B"));
            sectionRepository.save(new Section("CSE", 2, "C"));
            sectionRepository.save(new Section("CSE", 3, "A"));
            sectionRepository.save(new Section("CSE", 3, "B"));
            sectionRepository.save(new Section("CSE", 3, "C"));
            sectionRepository.save(new Section("CSE", 3, "D"));
            sectionRepository.save(new Section("CSE", 4, "A"));
            sectionRepository.save(new Section("CSE", 4, "B"));

            // Seed ECE
            sectionRepository.save(new Section("ECE", 1, "A"));
            sectionRepository.save(new Section("ECE", 2, "A"));
            sectionRepository.save(new Section("ECE", 2, "B"));
            sectionRepository.save(new Section("ECE", 3, "A"));
            sectionRepository.save(new Section("ECE", 4, "A"));
            System.out.println("Seeded default Sections");
        }
    }
}
