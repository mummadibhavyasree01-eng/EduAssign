package com.mits.EduAssign.Controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mits.EduAssign.Entity.Department;
import com.mits.EduAssign.Entity.AcademicYear;
import com.mits.EduAssign.Entity.Section;
import com.mits.EduAssign.Repository.DepartmentRepository;
import com.mits.EduAssign.Repository.AcademicYearRepository;
import com.mits.EduAssign.Repository.SectionRepository;

@RestController
@RequestMapping("/sections")
public class SectionController {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private AcademicYearRepository academicYearRepository;

    @Autowired
    private SectionRepository sectionRepository;

    // ----------------------------------------------------
    // DEPARTMENT CRUD
    // ----------------------------------------------------

    @GetMapping("/departments")
    public ResponseEntity<List<Department>> getAllDepartments() {
        return ResponseEntity.ok(departmentRepository.findAll());
    }

    @PostMapping("/departments")
    public ResponseEntity<?> addDepartment(@RequestBody Department department) {
        if (department.getCode() == null || department.getCode().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Department code cannot be empty");
        }
        if (departmentRepository.existsById(department.getCode())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Department already exists");
        }
        return ResponseEntity.ok(departmentRepository.save(department));
    }

    @DeleteMapping("/departments/{code}")
    public ResponseEntity<?> deleteDepartment(@PathVariable String code) {
        if (!departmentRepository.existsById(code)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Department not found");
        }
        departmentRepository.deleteById(code);
        return ResponseEntity.ok("Department deleted successfully");
    }

    // ----------------------------------------------------
    // ACADEMIC YEAR CRUD
    // ----------------------------------------------------

    @GetMapping("/years")
    public ResponseEntity<List<AcademicYear>> getAllYears() {
        return ResponseEntity.ok(academicYearRepository.findAll());
    }

    @PostMapping("/years")
    public ResponseEntity<?> addYear(@RequestBody AcademicYear year) {
        if (year.getYearNumber() == null) {
            return ResponseEntity.badRequest().body("Year number is required");
        }
        if (academicYearRepository.existsById(year.getYearNumber())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Year number already exists");
        }
        return ResponseEntity.ok(academicYearRepository.save(year));
    }

    @DeleteMapping("/years/{yrNo}")
    public ResponseEntity<?> deleteYear(@PathVariable Integer yrNo) {
        if (!academicYearRepository.existsById(yrNo)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Year not found");
        }
        academicYearRepository.deleteById(yrNo);
        return ResponseEntity.ok("Year deleted successfully");
    }

    // ----------------------------------------------------
    // SECTION CRUD
    // ----------------------------------------------------

    @GetMapping("/all")
    public ResponseEntity<List<Section>> getAllSections() {
        return ResponseEntity.ok(sectionRepository.findAll());
    }

    @PostMapping("/add")
    public ResponseEntity<?> addSection(@RequestBody Section section) {
        if (section.getDepartmentCode() == null || section.getYearNumber() == null || 
            section.getSectionName() == null || section.getSectionName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Department, Year, and Section Name are all required");
        }
        
        // Check if section already exists for department + year + sectionName
        Section existing = sectionRepository.findByDepartmentCodeAndYearNumberAndSectionName(
                section.getDepartmentCode(), section.getYearNumber(), section.getSectionName().trim());
        
        if (existing != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Section already exists");
        }

        section.setSectionName(section.getSectionName().trim());
        return ResponseEntity.ok(sectionRepository.save(section));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteSection(@PathVariable Long id) {
        if (!sectionRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Section not found");
        }
        sectionRepository.deleteById(id);
        return ResponseEntity.ok("Section deleted successfully");
    }

    @GetMapping("/by-dept-year")
    public ResponseEntity<List<Section>> getSectionsByDeptAndYear(
            @RequestParam String deptCode, @RequestParam Integer year) {
        return ResponseEntity.ok(sectionRepository.findByDepartmentCodeAndYearNumber(deptCode, year));
    }
}
