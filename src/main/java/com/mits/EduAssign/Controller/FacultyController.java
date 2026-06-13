package com.mits.EduAssign.Controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mits.EduAssign.Entity.AdminFaculty;
import com.mits.EduAssign.Entity.FacultySubjectPreference;
import com.mits.EduAssign.Entity.SubjectAllocation;
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
            @RequestBody List<String> subjectIds) {
        
        // Check if selection period is still active
        if (!subjectService.isBeforeDeadline()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Subject selection window is closed or deadline has passed.");
        }
        
        subjectService.savePreferences(facultyId, subjectIds);
        return ResponseEntity.ok("Preferences saved successfully");
    }

    @GetMapping("/preferences/{facultyId}")
    public ResponseEntity<List<FacultySubjectPreference>> getPreferences(@PathVariable String facultyId) {
        return ResponseEntity.ok(subjectService.getPreferencesByFacultyId(facultyId));
    }

    @GetMapping("/allocations/{facultyId}")
    public ResponseEntity<List<SubjectAllocation>> getAllocations(@PathVariable String facultyId) {
        return ResponseEntity.ok(subjectService.getAllocationsByFacultyId(facultyId));
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateProfile(@RequestBody AdminFaculty updatedFaculty) {
        AdminFaculty faculty = adminService.updateProfilef(updatedFaculty.getId(), updatedFaculty);
        if (faculty == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Faculty Not Found");
        }
        return ResponseEntity.ok(faculty);
    }
}
