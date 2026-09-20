package com.mits.EduAssign.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mits.EduAssign.Entity.AdminFaculty;
import com.mits.EduAssign.Entity.SubjectAllocation;
import com.mits.EduAssign.Entity.SectionAllocation;
import com.mits.EduAssign.Service.AdminService;
import com.mits.EduAssign.Service.SubjectService;

@RestController
@RequestMapping("/adminfaculty")
public class AdminController {
	    @Autowired
	    private AdminService adminService;

	    @Autowired
	    private SubjectService subjectService;

	    @PostMapping("/login")
	    public ResponseEntity<?> login(
	            @RequestParam String email,
	            @RequestParam String password) {

	        AdminFaculty user = adminService.login(email, password);

	        if (user == null) {
	        return ResponseEntity
	                .status(HttpStatus.UNAUTHORIZED)
	                .body("Invalid Email or Password");
	    }
	        Map<String, Object> response = new HashMap<>();

	        response.put("id", user.getId());
	        response.put("name", user.getName());
	        response.put("email", user.getEmail());
	        response.put("role", user.getRole());
	        response.put("profileImage", user.getProfileImage());

	        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
	            response.put("adminDashboard", true);
	            response.put("facultyDashboard", true);
	            response.put("message", "Admin Login Successful");
	        } else {
	            response.put("adminDashboard", false);
	            response.put("facultyDashboard", true);
	            response.put("message", "Faculty Login Successful");
	        }
	        return ResponseEntity.ok(response);
}
	    @PostMapping("/addFaculty")
	    public ResponseEntity<?> addFaculty(
	            @RequestBody AdminFaculty faculty) {

	        AdminFaculty savedFaculty =
	                adminService.addFaculty(faculty);

	        return ResponseEntity.ok(savedFaculty);
	    }
	    @GetMapping("/viewfaculty")
	    public ResponseEntity<?> viewFaculty() {
	        List<AdminFaculty> facultyList = adminService.viewFaculty();
	        return ResponseEntity.ok(facultyList);
	    }
	    @PutMapping("/updateFaculty/{id}")
	    public ResponseEntity<?> updateFaculty(
	            @PathVariable String id,
	            @RequestBody AdminFaculty faculty) {
	        try {
	            AdminFaculty updated = adminService.updateFaculty(id, faculty);
	            if (updated == null) {
	                return ResponseEntity
	                        .status(HttpStatus.NOT_FOUND)
	                        .body("Faculty Not Found");
	            }
	            return ResponseEntity.ok(updated);
	        } catch (IllegalArgumentException e) {
	            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
	        } catch (Exception e) {
	            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating faculty: " + e.getMessage());
	        }
	    }
	    @DeleteMapping("/deleteFaculty/{id}")
	    public ResponseEntity<?> deleteFaculty(
	            @PathVariable String id) {

	        boolean deleted =
	                adminService.deleteFaculty(id);

	        if (!deleted) {
	            return ResponseEntity
	                    .status(HttpStatus.NOT_FOUND)
	                    .body("Faculty Not Found");
	        }

	        return ResponseEntity.ok("Faculty Deleted Successfully");
	    }
	    @PutMapping("/faculty/update")
	    public ResponseEntity<?> updateProfilef(
	            @RequestBody AdminFaculty updatedFaculty) {

	        AdminFaculty faculty =
	                adminService.updateProfilef(
	                        updatedFaculty.getId(),
	                        updatedFaculty);

	        if (faculty == null) {
	            return ResponseEntity
	                    .status(HttpStatus.NOT_FOUND)
	                    .body("Faculty Not Found");
	        }

	        return ResponseEntity.ok(faculty);
	    }
	    @PutMapping("/update")
	    public ResponseEntity<?> updateProfilea(
	            @RequestBody AdminFaculty updatedAdmin) {

	        AdminFaculty admin =
	                adminService.updateProfilea(
	                        updatedAdmin.getId(),
	                        updatedAdmin);

	        if (admin == null) {
	            return ResponseEntity
	                    .status(HttpStatus.NOT_FOUND)
	                    .body("Admin Not Found");
	        }

	        return ResponseEntity.ok(admin);
	    }
	    @PostMapping("/uploadFaculty")
	    public String uploadFaculty(
	            @RequestParam("file") MultipartFile file)
	            throws Exception {

	        adminService.uploadFaculty(file);

	        return "Faculty Uploaded Successfully";
	    }

	    @PostMapping("/deadline")
	    public ResponseEntity<?> setDeadline(
	            @RequestParam String message,
	            @RequestParam int days,
	            @RequestParam(required = false) Integer sem,
	            @RequestParam String academicYear,
	            @RequestParam String department,
	            @RequestParam(value = "years", required = false) List<Integer> years,
	            @RequestParam(required = false) Integer hoursPerWeek,
	            @RequestParam(required = false) Integer maxSubjectsAllocated,
	            @RequestParam(required = false) Integer subjectHoursPerWeek,
	            @RequestParam(required = false) Integer maxRegularPreferences,
	            @RequestParam(required = false) Integer maxMockPreferences) {
	        subjectService.setDeadline(
	                message, days, sem, academicYear, department,
	                years, hoursPerWeek, maxSubjectsAllocated,
	                subjectHoursPerWeek, maxRegularPreferences, maxMockPreferences);
	        return ResponseEntity.ok(Map.of("message", "Deadlines published successfully"));
	    }

	    @GetMapping("/deadline")
	    public ResponseEntity<?> getDeadline() {
	        return ResponseEntity.ok(subjectService.getAllDeadlines());
	    }

	    @PostMapping("/allocate")
	    public ResponseEntity<?> allocateSubject(
	            @RequestBody SubjectAllocation allocation) {
	        try {
	            SubjectAllocation saved = subjectService.allocateSubject(allocation);
	            return ResponseEntity.ok(saved);
	        } catch (IllegalStateException e) {
	            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
	        } catch (IllegalArgumentException e) {
	            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
	        }
	    }

	    @GetMapping("/allocations")
	    public ResponseEntity<?> getAllocations() {
	        return ResponseEntity.ok(subjectService.getAllocations());
	    }

	    @PostMapping("/swap-allocations")
	    public ResponseEntity<?> swapAllocations(
	            @RequestParam Long id1,
	            @RequestParam Long id2) {
	        try {
	            subjectService.swapAllocations(id1, id2);
	            return ResponseEntity.ok("Allocations swapped successfully");
	        } catch (IllegalArgumentException e) {
	            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
	        }
	    }

	    @DeleteMapping({"/delete-allocation/{id}", "/allocation/{id}"})
	    public ResponseEntity<?> deleteAllocation(@PathVariable Long id) {
	        boolean deleted = subjectService.deleteAllocation(id);
	        if (!deleted) {
	            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Allocation Not Found");
	        }
	        return ResponseEntity.ok("Allocation Deleted Successfully");
	    }

	    @PostMapping("/allocate-section")
	    public ResponseEntity<?> allocateSection(
	            @RequestBody SectionAllocation allocation,
	            @RequestParam(required = false, defaultValue = "false") boolean ignoreConstraints) {
	        try {
	            SectionAllocation saved = subjectService.allocateSection(allocation, ignoreConstraints);
	            return ResponseEntity.ok(saved);
	        } catch (IllegalStateException e) {
	            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
	        } catch (IllegalArgumentException e) {
	            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
	        }
	    }

	    @GetMapping("/section-allocations")
	    public ResponseEntity<?> getAllSectionAllocations() {
	        return ResponseEntity.ok(subjectService.getAllSectionAllocations());
	    }

	    @DeleteMapping({"/delete-section-allocation/{id}", "/section-allocation/{id}"})
	    public ResponseEntity<?> deleteSectionAllocation(@PathVariable Long id) {
	        boolean deleted = subjectService.deleteSectionAllocation(id);
	        if (!deleted) {
	            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Section Allocation Not Found");
	        }
	        return ResponseEntity.ok("Section Allocation Deleted Successfully");
	    }

	    @PostMapping("/delete-allocation-by-details")
	    public ResponseEntity<?> deleteAllocationByDetails(
	            @RequestParam(required = false) String subjectId,
	            @RequestParam(required = false) String sectionName,
	            @RequestParam(required = false) String facultyId,
	            @RequestParam(required = false) Long id) {
	        try {
	            if (subjectId != null) {
	                subjectService.deleteSectionAllocationByDetails(subjectId, sectionName, facultyId);
	            }
	            if (id != null) {
	                subjectService.deleteSectionAllocation(id);
	            }
	            return ResponseEntity.ok(Map.of("message", "Allocation deleted successfully"));
	        } catch (Exception e) {
	            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
	        }
	    }

	    @PostMapping("/finalize-allocations")
	    public ResponseEntity<?> finalizeAllocations() {
	        subjectService.finalizeAllocations();
	        return ResponseEntity.ok("Allocations finalized successfully");
	    }

	    @PostMapping("/clear-allocations")
	    public ResponseEntity<?> clearAllocations(@RequestParam(required = false) java.util.List<Integer> years) {
	        if (years == null || years.isEmpty()) {
	            subjectService.clearAllAllocations();
	            return ResponseEntity.ok(java.util.Map.of("message", "All allocation data cleared successfully"));
	        } else {
	            subjectService.clearAllocationsForYears(years);
	            return ResponseEntity.ok(java.util.Map.of("message", "Allocation data for selected years cleared successfully"));
	        }
	    }

	    @GetMapping("/allocated-years")
	    public ResponseEntity<?> getAllocatedYears() {
	        return ResponseEntity.ok(subjectService.getAllocatedYears());
	    }

	    @GetMapping("/is-finalized")
	    public ResponseEntity<?> isAllocationsFinalized() {
	        return ResponseEntity.ok(subjectService.isAllocationsFinalized());
	    }

	    @PostMapping("/stop-deadline")
	    public ResponseEntity<?> stopDeadline(@RequestParam(required = false) Integer year) {
	        subjectService.stopDeadline(year);
	        return ResponseEntity.ok("Deadline stopped successfully");
	    }

	    @GetMapping("/no-preferences-faculty")
	    public ResponseEntity<?> getFacultyWithNoPreferences(@RequestParam(required = false) String academicYear) {
	        if (academicYear != null && !academicYear.trim().isEmpty()) {
	            return ResponseEntity.ok(subjectService.getFacultyWithNoPreferencesByAcademicYear(academicYear.trim()));
	        }
	        return ResponseEntity.ok(subjectService.getFacultyWithNoPreferences());
	    }

	    @GetMapping("/academic-years")
	    public ResponseEntity<List<String>> getAcademicYears() {
	        return ResponseEntity.ok(subjectService.getAcademicYears());
	    }

	    @PostMapping("/auto-allocate")
	    public ResponseEntity<?> autoAllocateSubjects(
	            @RequestParam(required = false) Integer hoursLimit,
	            @RequestParam(required = false) Integer subjectHours,
	            @RequestParam(required = false) Integer maxSubjects,
	            @RequestParam(required = false) Integer maxRegular,
	            @RequestParam(required = false) Integer maxMock,
	            @RequestParam(required = false) String academicYear,
	            @RequestParam(required = false) String department,
	            @RequestParam(required = false) Integer sem,
	            @RequestParam(value = "years", required = false) List<Integer> years) {
	        try {
	            return ResponseEntity.ok(subjectService.autoAllocateSubjects(hoursLimit, subjectHours, maxSubjects, maxRegular, maxMock, academicYear, department, sem, years));
	        } catch (IllegalStateException e) {
	            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
	        } catch (Exception e) {
	            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error performing auto-allocation: " + e.getMessage());
	        }
	    }

	    @GetMapping("/allocation-explanations")
	    public ResponseEntity<?> getAllocationExplanations() {
	        return ResponseEntity.ok(subjectService.getLatestAllocationExplanations());
	    }

	    @PostMapping("/history/upload")
	    public ResponseEntity<?> uploadAllocationHistory(@RequestParam("file") MultipartFile file) {
	        try {
	            subjectService.uploadAllocationHistory(file);
	            return ResponseEntity.ok("Allocation History uploaded successfully");
	        } catch (Exception e) {
	            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error uploading history: " + e.getMessage());
	        }
	    }

	    @GetMapping("/history/all")
	    public ResponseEntity<?> getAllAllocationHistory() {
	        return ResponseEntity.ok(subjectService.getAllHistory());
	    }

	    @PostMapping("/reassign-allocation")
	    public ResponseEntity<?> reassignAllocation(
	            @RequestParam String subjectId,
	            @RequestParam String sectionName,
	            @RequestParam String fromFacultyId,
	            @RequestParam String toFacultyId,
	            @RequestParam(required = false, defaultValue = "false") boolean ignoreConstraints) {
	        try {
	            subjectService.reassignSectionAllocation(subjectId, sectionName, fromFacultyId, toFacultyId, ignoreConstraints);
	            return ResponseEntity.ok(Map.of("message", "Allocation reassigned successfully"));
	        } catch (Exception e) {
	            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
	        }
	    }

	    @PostMapping("/assign-unknown-to-faculty")
	    public ResponseEntity<?> assignUnknownToFaculty(
	            @RequestParam String unknownFacultyId,
	            @RequestParam String newFacultyId) {
	        try {
	            subjectService.assignUnknownToFaculty(unknownFacultyId, newFacultyId);
	            return ResponseEntity.ok(Map.of("message", "Unknown faculty allocations successfully assigned to new faculty"));
	        } catch (Exception e) {
	            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
	        }
	    }

	    @GetMapping("/reassign-eligible-faculty")
	    public ResponseEntity<?> getEligibleFacultyForReassignment(
	            @RequestParam String subjectId,
	            @RequestParam String sectionName) {
	        try {
	            List<AdminFaculty> eligible = subjectService.getEligibleFacultyForReassignment(subjectId, sectionName);
	            return ResponseEntity.ok(eligible);
	        } catch (Exception e) {
	            return ResponseEntity.badRequest().body(e.getMessage());
	        }
	    }

	    @PostMapping("/swap-subjects")
	    public ResponseEntity<?> swapSubjects(
	            @RequestParam String facultyId,
	            @RequestParam String oldSubjectId,
	            @RequestParam String oldSectionName,
	            @RequestParam String newSubjectId,
	            @RequestParam String newSectionName) {
	        try {
	            subjectService.swapAllocationsForSubjects(facultyId, oldSubjectId, oldSectionName, newSubjectId, newSectionName);
	            return ResponseEntity.ok(Map.of("message", "Allocations swapped successfully"));
	        } catch (IllegalArgumentException e) {
	            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
	        }
	    }
}
































