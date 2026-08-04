package com.mits.EduAssign.Controller;

import java.util.List;
import com.mits.EduAssign.Service.NaturalOrderComparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mits.EduAssign.Entity.AdminFaculty;
import com.mits.EduAssign.Entity.AllocationHistory;
import com.mits.EduAssign.Entity.FacultySubjectPreference;
import com.mits.EduAssign.Entity.SectionAllocation;
import com.mits.EduAssign.Entity.Subject;
import com.mits.EduAssign.Entity.SubjectAllocation;
import com.mits.EduAssign.Repository.AdminRepository;
import com.mits.EduAssign.Repository.AllocationHistoryRepository;
import com.mits.EduAssign.Repository.AllocationRepository;
import com.mits.EduAssign.Repository.PreferenceRepository;
import com.mits.EduAssign.Repository.SectionAllocationRepository;
import com.mits.EduAssign.Repository.SelectionWindowRepository;
import com.mits.EduAssign.Repository.SubjectRepository;

@RestController
@RequestMapping("/superadmin")
public class SuperAdminController {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private AllocationHistoryRepository allocationHistoryRepository;

    @Autowired
    private SelectionWindowRepository windowRepository;

    @Autowired
    private SectionAllocationRepository sectionAllocationRepository;

    @Autowired
    private AllocationRepository allocationRepository;

    @Autowired
    private PreferenceRepository preferenceRepository;

    @PutMapping("/update")
    public ResponseEntity<?> updateProfile(@RequestBody AdminFaculty updatedAdmin) {
        AdminFaculty admin = adminRepository.findById(updatedAdmin.getId()).orElse(null);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Super Admin Not Found");
        }
        if (updatedAdmin.getName() != null) {
            admin.setName(updatedAdmin.getName());
        }
        if (updatedAdmin.getEmail() != null) {
            admin.setEmail(updatedAdmin.getEmail());
        }
        if (updatedAdmin.getPassword() != null && !updatedAdmin.getPassword().trim().isEmpty()) {
            admin.setPassword(updatedAdmin.getPassword().trim());
        }
        if (updatedAdmin.getProfileImage() != null) {
            if (updatedAdmin.getProfileImage().trim().isEmpty()) {
                admin.setProfileImage(null);
            } else {
                admin.setProfileImage(updatedAdmin.getProfileImage());
            }
        }
        return ResponseEntity.ok(adminRepository.save(admin));
    }

    @GetMapping("/reports/academic-years")
    public ResponseEntity<List<String>> getAcademicYears() {
        Set<String> years = new TreeSet<>();
        
        // Retrieve distinct academic years from history
        allocationHistoryRepository.findAll().forEach(h -> {
            if (h.getAcademicYear() != null && !h.getAcademicYear().trim().isEmpty()) {
                years.add(h.getAcademicYear().trim());
            }
        });
        
        // Retrieve distinct academic years from subjects
        subjectRepository.findAll().forEach(s -> {
            if (s.getAcademicYear() != null && !s.getAcademicYear().trim().isEmpty()) {
                years.add(s.getAcademicYear().trim());
            }
        });
        
        // Retrieve distinct academic years from active window
        windowRepository.findAll().forEach(w -> {
            if (w.getAcademicYear() != null && !w.getAcademicYear().trim().isEmpty()) {
                years.add(w.getAcademicYear().trim());
            }
        });
        
        if (years.isEmpty()) {
            years.add("2026-27");
        }
        
        return ResponseEntity.ok(new ArrayList<>(years));
    }

    @GetMapping("/reports/allocations")
    public ResponseEntity<?> getYearWiseReport(@RequestParam String academicYear) {
        // Fetch all faculty and admin users (exclude SUPERADMIN role)
        List<AdminFaculty> faculties = adminRepository.findAll().stream()
                .filter(u -> !"SUPERADMIN".equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());
        faculties.sort(new NaturalOrderComparator());

        // Fetch all history for this academic year
        List<AllocationHistory> historyList = allocationHistoryRepository.findByAcademicYear(academicYear);

        // Fetch all subjects for mapping names and years
        List<Subject> allSubjects = subjectRepository.findAll();
        Map<String, Subject> subjectMap = allSubjects.stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(
                        s -> s.getId().toLowerCase(),
                        s -> s,
                        (existing, replacing) -> existing
                ));

        // Fetch all preferences and group them by faculty to find the choice number
        List<FacultySubjectPreference> allPrefs = preferenceRepository.findAll().stream()
                .filter(p -> !p.isMock())
                .collect(Collectors.toList());
        allPrefs.sort(java.util.Comparator.comparing(FacultySubjectPreference::getId));
        Map<String, List<String>> facultyPrefsMap = new HashMap<>();
        for (FacultySubjectPreference p : allPrefs) {
            if (p.getFacultyId() != null && p.getSubjectId() != null) {
                facultyPrefsMap.computeIfAbsent(p.getFacultyId().toLowerCase(), k -> new ArrayList<>()).add(p.getSubjectId().toLowerCase());
            }
        }

        // Fetch active allocations
        List<SectionAllocation> activeSecAllocs = sectionAllocationRepository.findAll();
        List<SubjectAllocation> activeSubAllocs = allocationRepository.findAll();

        List<Map<String, Object>> report = new ArrayList<>();
        for (AdminFaculty f : faculties) {
            Map<String, Object> facData = new HashMap<>();
            facData.put("facultyId", f.getId());
            facData.put("name", f.getName());
            facData.put("email", f.getEmail());

            // LinkedHashMap to maintain order and avoid duplicates (Key: subjectId + "_" + sectionName)
            Map<String, Map<String, Object>> allocMap = new LinkedHashMap<>();

            // Helper to get preference choice number (1-based index)
            List<String> prefSubjects = facultyPrefsMap.get(f.getId().toLowerCase());

            // 1. Add historical allocations
            for (AllocationHistory h : historyList) {
                if (h.getFacultyId() != null && h.getFacultyId().equalsIgnoreCase(f.getId())) {
                    Map<String, Object> alloc = new HashMap<>();
                    alloc.put("id", h.getId());
                    alloc.put("subjectId", h.getSubjectId());
                    Subject sub = h.getSubjectId() != null ? subjectMap.get(h.getSubjectId().toLowerCase()) : null;
                    alloc.put("subjectName", sub != null ? sub.getName() : "Unknown Subject");
                    alloc.put("department", h.getDepartment());
                    alloc.put("semester", h.getSemester());
                    alloc.put("sectionName", h.getSectionName() != null ? h.getSectionName() : "N/A");
                    alloc.put("status", "Finalized");
                    alloc.put("year", sub != null ? sub.getYear() : 0);
                    
                    int prefNum = -1;
                    if (prefSubjects != null && h.getSubjectId() != null) {
                        int idx = prefSubjects.indexOf(h.getSubjectId().toLowerCase());
                        if (idx != -1) {
                            prefNum = idx + 1;
                        }
                    }
                    alloc.put("preferenceNumber", prefNum);
                    
                    String key = h.getSubjectId() + "_" + (h.getSectionName() != null ? h.getSectionName() : "N/A");
                    allocMap.put(key, alloc);
                }
            }

            // 2. Add active section allocations that match the requested academic year (only if finalized)
            for (SectionAllocation sa : activeSecAllocs) {
                if (sa.getFacultyId() != null && sa.getFacultyId().equalsIgnoreCase(f.getId()) && sa.isFinalized()) {
                    Subject sub = sa.getSubjectId() != null ? subjectMap.get(sa.getSubjectId().toLowerCase()) : null;
                    if (sub != null && sub.getAcademicYear() != null && sub.getAcademicYear().equalsIgnoreCase(academicYear)) {
                        String key = sa.getSubjectId() + "_" + (sa.getSectionName() != null ? sa.getSectionName() : "N/A");
                        if (!allocMap.containsKey(key)) {
                            Map<String, Object> alloc = new HashMap<>();
                            alloc.put("id", sa.getId());
                            alloc.put("subjectId", sa.getSubjectId());
                            alloc.put("subjectName", sub.getName());
                            alloc.put("department", sub.getDep());
                            alloc.put("semester", sub.getSem());
                            alloc.put("sectionName", sa.getSectionName() != null ? sa.getSectionName() : "N/A");
                            alloc.put("status", "Finalized");
                            alloc.put("year", sub.getYear());
                            
                            int prefNum = -1;
                            if (prefSubjects != null && sa.getSubjectId() != null) {
                                int idx = prefSubjects.indexOf(sa.getSubjectId().toLowerCase());
                                if (idx != -1) {
                                    prefNum = idx + 1;
                                }
                            }
                            alloc.put("preferenceNumber", prefNum);
                            
                            allocMap.put(key, alloc);
                        }
                    }
                }
            }

            // 3. Add active subject allocations (with no section yet) matching requested academic year (only if finalized)
            for (SubjectAllocation sa : activeSubAllocs) {
                if (sa.getFacultyId() != null && sa.getFacultyId().equalsIgnoreCase(f.getId()) && sa.isFinalized()) {
                    Subject sub = sa.getSubjectId() != null ? subjectMap.get(sa.getSubjectId().toLowerCase()) : null;
                    if (sub != null && sub.getAcademicYear() != null && sub.getAcademicYear().equalsIgnoreCase(academicYear)) {
                        boolean alreadyHasSectionOrHistory = false;
                        for (String key : allocMap.keySet()) {
                            if (key.startsWith(sa.getSubjectId() + "_")) {
                                alreadyHasSectionOrHistory = true;
                                break;
                            }
                        }
                        if (!alreadyHasSectionOrHistory) {
                            String key = sa.getSubjectId() + "_N/A";
                            Map<String, Object> alloc = new HashMap<>();
                            alloc.put("id", sa.getId());
                            alloc.put("subjectId", sa.getSubjectId());
                            alloc.put("subjectName", sub.getName());
                            alloc.put("department", sub.getDep());
                            alloc.put("semester", sub.getSem());
                            alloc.put("sectionName", "N/A");
                            alloc.put("status", "Finalized");
                            alloc.put("year", sub.getYear());
                            
                            int prefNum = -1;
                            if (prefSubjects != null && sa.getSubjectId() != null) {
                                int idx = prefSubjects.indexOf(sa.getSubjectId().toLowerCase());
                                if (idx != -1) {
                                    prefNum = idx + 1;
                                }
                            }
                            alloc.put("preferenceNumber", prefNum);
                            
                            allocMap.put(key, alloc);
                        }
                    }
                }
            }

            facData.put("allocations", new ArrayList<>(allocMap.values()));
            report.add(facData);
        }

        // 4. Gather virtual unknown faculty members
        java.util.Set<String> unknownFacultyIds = new java.util.TreeSet<>();
        for (AllocationHistory h : historyList) {
            if (h.getFacultyId() != null && h.getFacultyId().toUpperCase().startsWith("UNKNOWN_")) {
                unknownFacultyIds.add(h.getFacultyId());
            }
        }
        for (SectionAllocation sa : activeSecAllocs) {
            if (sa.getFacultyId() != null && sa.getFacultyId().toUpperCase().startsWith("UNKNOWN_")) {
                Subject sub = sa.getSubjectId() != null ? subjectMap.get(sa.getSubjectId().toLowerCase()) : null;
                if (sub != null && sub.getAcademicYear() != null && sub.getAcademicYear().equalsIgnoreCase(academicYear)) {
                    unknownFacultyIds.add(sa.getFacultyId());
                }
            }
        }
        for (SubjectAllocation sa : activeSubAllocs) {
            if (sa.getFacultyId() != null && sa.getFacultyId().toUpperCase().startsWith("UNKNOWN_")) {
                Subject sub = sa.getSubjectId() != null ? subjectMap.get(sa.getSubjectId().toLowerCase()) : null;
                if (sub != null && sub.getAcademicYear() != null && sub.getAcademicYear().equalsIgnoreCase(academicYear)) {
                    unknownFacultyIds.add(sa.getFacultyId());
                }
            }
        }

        // Process allocations for each virtual unknown faculty
        for (String unknownId : unknownFacultyIds) {
            Map<String, Object> facData = new HashMap<>();
            facData.put("facultyId", unknownId);
            facData.put("name", "Unknown Faculty " + unknownId.substring(8));
            facData.put("email", "N/A");
            facData.put("isUnknown", true);

            Map<String, Map<String, Object>> allocMap = new LinkedHashMap<>();

            // 1. Historical allocations
            for (AllocationHistory h : historyList) {
                if (h.getFacultyId() != null && h.getFacultyId().equalsIgnoreCase(unknownId)) {
                    Map<String, Object> alloc = new HashMap<>();
                    alloc.put("id", h.getId());
                    alloc.put("subjectId", h.getSubjectId());
                    Subject sub = h.getSubjectId() != null ? subjectMap.get(h.getSubjectId().toLowerCase()) : null;
                    alloc.put("subjectName", sub != null ? sub.getName() : "Unknown Subject");
                    alloc.put("department", h.getDepartment());
                    alloc.put("semester", h.getSemester());
                    alloc.put("sectionName", h.getSectionName() != null ? h.getSectionName() : "N/A");
                    alloc.put("status", "Finalized");
                    alloc.put("year", sub != null ? sub.getYear() : 0);
                    alloc.put("preferenceNumber", -1);
                    
                    String key = h.getSubjectId() + "_" + (h.getSectionName() != null ? h.getSectionName() : "N/A");
                    allocMap.put(key, alloc);
                }
            }

            // 2. Active section allocations
            for (SectionAllocation sa : activeSecAllocs) {
                if (sa.getFacultyId() != null && sa.getFacultyId().equalsIgnoreCase(unknownId)) {
                    Subject sub = sa.getSubjectId() != null ? subjectMap.get(sa.getSubjectId().toLowerCase()) : null;
                    if (sub != null && sub.getAcademicYear() != null && sub.getAcademicYear().equalsIgnoreCase(academicYear)) {
                        String key = sa.getSubjectId() + "_" + (sa.getSectionName() != null ? sa.getSectionName() : "N/A");
                        if (!allocMap.containsKey(key)) {
                            Map<String, Object> alloc = new HashMap<>();
                            alloc.put("id", sa.getId());
                            alloc.put("subjectId", sa.getSubjectId());
                            alloc.put("subjectName", sub.getName());
                            alloc.put("department", sub.getDep());
                            alloc.put("semester", sub.getSem());
                            alloc.put("sectionName", sa.getSectionName() != null ? sa.getSectionName() : "N/A");
                            alloc.put("status", sa.isFinalized() ? "Finalized" : "Draft");
                            alloc.put("year", sub.getYear());
                            alloc.put("preferenceNumber", -1);
                            
                            allocMap.put(key, alloc);
                        }
                    }
                }
            }

            // 3. Active subject allocations
            for (SubjectAllocation sa : activeSubAllocs) {
                if (sa.getFacultyId() != null && sa.getFacultyId().equalsIgnoreCase(unknownId)) {
                    Subject sub = sa.getSubjectId() != null ? subjectMap.get(sa.getSubjectId().toLowerCase()) : null;
                    if (sub != null && sub.getAcademicYear() != null && sub.getAcademicYear().equalsIgnoreCase(academicYear)) {
                        boolean alreadyHasSectionOrHistory = false;
                        for (String key : allocMap.keySet()) {
                            if (key.startsWith(sa.getSubjectId() + "_")) {
                                alreadyHasSectionOrHistory = true;
                                break;
                            }
                        }
                        if (!alreadyHasSectionOrHistory) {
                            String key = sa.getSubjectId() + "_N/A";
                            Map<String, Object> alloc = new HashMap<>();
                            alloc.put("id", sa.getId());
                            alloc.put("subjectId", sa.getSubjectId());
                            alloc.put("subjectName", sub.getName());
                            alloc.put("department", sub.getDep());
                            alloc.put("semester", sub.getSem());
                            alloc.put("sectionName", "N/A");
                            alloc.put("status", sa.isFinalized() ? "Finalized" : "Draft");
                            alloc.put("year", sub.getYear());
                            alloc.put("preferenceNumber", -1);
                            
                            allocMap.put(key, alloc);
                        }
                    }
                }
            }

            facData.put("allocations", new ArrayList<>(allocMap.values()));
            report.add(facData);
        }

        return ResponseEntity.ok(report);
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminFaculty>> getAllUsers() {
        // Retrieve all users but exclude the SUPERADMIN role
        List<AdminFaculty> users = adminRepository.findAll().stream()
                .filter(user -> !"SUPERADMIN".equalsIgnoreCase(user.getRole()))
                .collect(Collectors.toList());
        users.sort(new NaturalOrderComparator());
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
