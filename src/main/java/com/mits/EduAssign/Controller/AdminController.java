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

	        List<AdminFaculty> facultyList =
	                adminService.viewFaculty();

	        if (facultyList.isEmpty()) {
	            return ResponseEntity.ok("No Faculty Found");
	        }

	        return ResponseEntity.ok(facultyList);
	    }
	    @PutMapping("/updateFaculty/{id}")
	    public ResponseEntity<?> updateFaculty(
	            @PathVariable String id,
	            @RequestBody AdminFaculty faculty) {

	        AdminFaculty updated =
	                adminService.updateFaculty(id, faculty);

	        if (updated == null) {
	            return ResponseEntity
	                    .status(HttpStatus.NOT_FOUND)
	                    .body("Faculty Not Found");
	        }

	        return ResponseEntity.ok(updated);
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
	            @RequestParam int days) {
	        return ResponseEntity.ok(subjectService.setDeadline(message, days));
	    }

	    @GetMapping("/deadline")
	    public ResponseEntity<?> getDeadline() {
	        return ResponseEntity.ok(subjectService.getActiveDeadline());
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

	    @DeleteMapping("/allocation/{id}")
	    public ResponseEntity<?> deleteAllocation(@PathVariable Long id) {
	        boolean deleted = subjectService.deleteAllocation(id);
	        if (!deleted) {
	            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Allocation Not Found");
	        }
	        return ResponseEntity.ok("Allocation Deleted Successfully");
	    }
}
































