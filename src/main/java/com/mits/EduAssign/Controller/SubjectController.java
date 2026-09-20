package com.mits.EduAssign.Controller;

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

import com.mits.EduAssign.Entity.Subject;
import com.mits.EduAssign.Service.SubjectService;

@RestController
@RequestMapping("/subject")
public class SubjectController {

    @Autowired
    SubjectService subjectService;

    @PostMapping("/add")
    public ResponseEntity<?> addSubject(
            @RequestBody Subject subject) {
        if (subject == null || subject.getId() == null || subject.getId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Subject Code is required");
        }
        String subjectCode = subject.getId().trim();
        if (subjectCode.contains("_")) {
            subjectCode = subjectCode.split("_")[0].trim();
        }
        String acadYear = (subject.getAcademicYear() != null && !subject.getAcademicYear().trim().isEmpty())
                ? subject.getAcademicYear().trim() : "2026-27";
        String compoundId = subjectCode + "_" + acadYear;

        Subject existingCompound = subjectService.viewSubjectById(compoundId);
        Subject existingClean = subjectService.viewSubjectById(subjectCode);
        boolean existsInYear = false;
        if (existingCompound != null) {
            existsInYear = true;
        } else if (existingClean != null) {
            String existingCleanYear = existingClean.getAcademicYear() != null ? existingClean.getAcademicYear().trim() : "2026-27";
            if (existingCleanYear.equalsIgnoreCase(acadYear)) {
                existsInYear = true;
            }
        }

        if (existsInYear) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Already there is a subject with that subjectcode in the " + acadYear + " academic year");
        }
        subject.setId(compoundId);
        try {
            return ResponseEntity.ok(
                    subjectService.addSubject(subject));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Database error: " + e.getMessage());
        }
    }

    @GetMapping("/viewAll")
    public ResponseEntity<?> viewAllSubjects() {

        return ResponseEntity.ok(
                subjectService.viewAllSubjects());
    }

    @GetMapping("/view/{id}")
    public ResponseEntity<?> viewSubject(
            @PathVariable String id) {

        Subject subject =
                subjectService.viewSubjectById(id);

        if(subject == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Subject Not Found");
        }

        return ResponseEntity.ok(subject);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateSubject(
            @PathVariable String id,
            @RequestBody Subject subject) {

        try {
            Subject updated =
                    subjectService.updateSubject(id, subject);

            if(updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Subject Not Found");
            }

            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteSubject(
            @PathVariable String id) {

        boolean deleted =
                subjectService.deleteSubject(id);

        if(!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Subject Not Found");
        }

        return ResponseEntity.ok(
                "Subject Deleted Successfully");
    }
    @PostMapping("/uploadSubject")
    public String uploadFaculty(
            @RequestParam("file") MultipartFile file)
            throws Exception {

        subjectService.uploadSubject(file);

        return "subject Uploaded Successfully";
    }
}