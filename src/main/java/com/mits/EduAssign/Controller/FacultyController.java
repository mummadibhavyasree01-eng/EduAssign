package com.mits.EduAssign.Controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mits.EduAssign.Entity.AdminFaculty;
import com.mits.EduAssign.Entity.FacultySubjectPreference;
import com.mits.EduAssign.Entity.SectionAllocation;
import com.mits.EduAssign.Service.AdminService;
import com.mits.EduAssign.Service.SubjectService;

@RestController
@RequestMapping("/faculty")
public class FacultyController {

    @Autowired
    private SubjectService subjectService;

    @Autowired
    private AdminService adminService;

    @PostMapping("/preferences")
    public ResponseEntity<?> savePreferences(
            @RequestParam String facultyId,
            @RequestBody List<java.util.Map<String, Object>> preferenceList) {
        
        // Check if selection period is still active
        if (!subjectService.isBeforeDeadline()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Subject selection window is closed or deadline has passed.");
        }
        
        List<FacultySubjectPreference> prefs = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> m : preferenceList) {
            String subId = (String) m.get("subjectId");
            Boolean isMockVal = (Boolean) m.get("mock");
            boolean isMock = isMockVal != null && isMockVal;
            FacultySubjectPreference p = new FacultySubjectPreference(facultyId, subId, isMock);
            prefs.add(p);
        }
        
        subjectService.savePreferencesEntity(facultyId, prefs);
        return ResponseEntity.ok("Preferences saved successfully");
    }

    @GetMapping("/preferences/{facultyId}")
    public ResponseEntity<List<FacultySubjectPreference>> getPreferences(@PathVariable String facultyId) {
        return ResponseEntity.ok(subjectService.getPreferencesByFacultyId(facultyId));
    }

    @GetMapping("/allocations/{facultyId}")
    public ResponseEntity<?> getAllocations(@PathVariable String facultyId) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("subjectAllocations", subjectService.getAllocationsByFacultyId(facultyId));
        result.put("sectionAllocations", subjectService.getSectionAllocationsByFacultyId(facultyId));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/has-mock-allocation/{facultyId}")
    public ResponseEntity<Boolean> hasMockAllocation(@PathVariable String facultyId) {
        return ResponseEntity.ok(subjectService.hasPreviousMockAllocation(facultyId));
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateProfile(@RequestBody AdminFaculty updatedFaculty) {
        AdminFaculty faculty = adminService.updateProfilef(updatedFaculty.getId(), updatedFaculty);
        if (faculty == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Faculty Not Found");
        }
        return ResponseEntity.ok(faculty);
    }

    @GetMapping("/preferences/all")
    public ResponseEntity<List<FacultySubjectPreference>> getAllPreferences() {
        return ResponseEntity.ok(subjectService.getAllPreferences());
    }

    @GetMapping("/preferences/by-academic-year")
    public ResponseEntity<List<FacultySubjectPreference>> getPreferencesByAcademicYear(@RequestParam String academicYear) {
        return ResponseEntity.ok(subjectService.getPreferencesByAcademicYear(academicYear));
    }
}
