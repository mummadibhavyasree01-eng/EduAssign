package com.mits.EduAssign.Controller;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mits.EduAssign.Entity.AdminFaculty;
import com.mits.EduAssign.Repository.AdminRepository;

@RestController
@RequestMapping("/superadmin")
public class SuperAdminController {

    @Autowired
    private AdminRepository adminRepository;

    @GetMapping("/users")
    public ResponseEntity<List<AdminFaculty>> getAllUsers() {
        // Retrieve all users but exclude the SUPERADMIN role
        List<AdminFaculty> users = adminRepository.findAll().stream()
                .filter(user -> !"SUPERADMIN".equalsIgnoreCase(user.getRole()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @PutMapping("/change-role/{id}")
    public ResponseEntity<?> changeRole(@PathVariable String id, @RequestParam String newRole) {
        AdminFaculty user = adminRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        if ("SUPERADMIN".equalsIgnoreCase(user.getRole())) {
            return ResponseEntity.badRequest().body("Cannot change role of a Super Admin");
        }

        if (!"ADMIN".equalsIgnoreCase(newRole) && !"faculty".equalsIgnoreCase(newRole)) {
            return ResponseEntity.badRequest().body("Invalid role. Role must be ADMIN or faculty");
        }

        user.setRole(newRole.toLowerCase());
        if ("admin".equalsIgnoreCase(newRole)) {
            user.setRole("ADMIN"); // Keep Admin capitalized
        }
        
        return ResponseEntity.ok(adminRepository.save(user));
    }
}
