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
import com.mits.EduAssign.Entity.SubjectSelectionWindow;
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

        // Fetch all raw preferences from DB to compute absolute earliest preference submission ID per faculty
        List<FacultySubjectPreference> rawPrefs = preferenceRepository.findAll();
        Map<String, Long> facultyEarliestPrefIdMap = new HashMap<>();
        for (FacultySubjectPreference p : rawPrefs) {
            if (p.getFacultyId() != null && p.getId() != null) {
                String fKey = p.getFacultyId().trim().toLowerCase();
                Long currentMin = facultyEarliestPrefIdMap.get(fKey);
                if (currentMin == null || p.getId() < currentMin) {
                    facultyEarliestPrefIdMap.put(fKey, p.getId());
                }
            }
        }

        // Merge in-memory static tracker in SubjectService if available
        for (Map.Entry<String, Long> entry : com.mits.EduAssign.Service.SubjectService.EARLIEST_YEAR_SUBMISSION_MAP.entrySet()) {
            String fKey = entry.getKey();
            if (fKey.contains("_")) {
                fKey = fKey.substring(fKey.indexOf("_") + 1);
            }
            Long currentMin = facultyEarliestPrefIdMap.get(fKey);
            if (currentMin == null || entry.getValue() < currentMin) {
                facultyEarliestPrefIdMap.put(fKey, entry.getValue());
            }
        }

        // Sort faculties by preference submission order (who submitted preferences earliest comes FIRST)
        faculties.sort((f1, f2) -> {
            String k1 = f1.getId().trim().toLowerCase();
            String k2 = f2.getId().trim().toLowerCase();
            Long order1 = facultyEarliestPrefIdMap.get(k1);
            Long order2 = facultyEarliestPrefIdMap.get(k2);

            boolean has1 = (order1 != null);
            boolean has2 = (order2 != null);

            if (has1 != has2) {
                return has1 ? -1 : 1;
            }
            if (has1 && !order1.equals(order2)) {
                return Long.compare(order1, order2);
            }
            return new NaturalOrderComparator().compare(f1, f2);
        });

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

        // Group preferences by faculty to find choice numbers
        List<FacultySubjectPreference> allPrefs = rawPrefs.stream()
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

        // Determine active allocated study years for the requested academic year
        java.util.Set<Integer> activeAllocatedYears = new java.util.HashSet<>();
        for (SectionAllocation sa : activeSecAllocs) {
            if (sa.isFinalized() && sa.getSubjectId() != null) {
                Subject s = subjectMap.get(sa.getSubjectId().toLowerCase());
                if (s != null && s.getAcademicYear() != null && s.getAcademicYear().equalsIgnoreCase(academicYear)) {
                    activeAllocatedYears.add(s.getYear());
                }
            }
        }
        for (SubjectAllocation sa : activeSubAllocs) {
            if (sa.isFinalized() && sa.getSubjectId() != null) {
                Subject s = subjectMap.get(sa.getSubjectId().toLowerCase());
                if (s != null && s.getAcademicYear() != null && s.getAcademicYear().equalsIgnoreCase(academicYear)) {
                    activeAllocatedYears.add(s.getYear());
                }
            }
        }

        if (activeAllocatedYears.isEmpty()) {
            activeAllocatedYears = windowRepository.findAll().stream()
                    .filter(w -> w.isActive() && w.getAcademicYear() != null && w.getAcademicYear().equalsIgnoreCase(academicYear))
                    .map(SubjectSelectionWindow::getYear)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        List<Map<String, Object>> report = new ArrayList<>();
        for (AdminFaculty f : faculties) {
            Map<String, Object> facData = new HashMap<>();
            facData.put("facultyId", f.getId());
            facData.put("name", f.getName());
            facData.put("email", f.getEmail());
            String fKey = f.getId().trim().toLowerCase();
            Long prefOrder = facultyEarliestPrefIdMap.get(fKey);
            boolean hasPrefs = (prefOrder != null);
            facData.put("submissionOrder", hasPrefs ? prefOrder : Long.MAX_VALUE);
            facData.put("hasPreferences", hasPrefs);

            // LinkedHashMap to maintain order and avoid duplicates (Key: subjectId + "_" + sectionName)
            Map<String, Map<String, Object>> allocMap = new LinkedHashMap<>();

            // Helper to get preference choice number (1-based index)
            List<String> prefSubjects = facultyPrefsMap.get(f.getId().toLowerCase());

            // 1. Add historical allocations
            for (AllocationHistory h : historyList) {
                if (h.getFacultyId() != null && h.getFacultyId().equalsIgnoreCase(f.getId())) {
                    Subject sub = h.getSubjectId() != null ? subjectMap.get(h.getSubjectId().toLowerCase()) : null;
                    if (sub != null && !activeAllocatedYears.isEmpty() && !activeAllocatedYears.contains(sub.getYear())) {
                        continue;
                    }
                    Map<String, Object> alloc = new HashMap<>();
                    alloc.put("id", h.getId());
                    alloc.put("subjectId", h.getSubjectId());
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
                        if (!activeAllocatedYears.isEmpty() && !activeAllocatedYears.contains(sub.getYear())) {
                            continue;
                        }
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
                        if (!activeAllocatedYears.isEmpty() && !activeAllocatedYears.contains(sub.getYear())) {
                            continue;
                        }
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
            if (sa.getFacultyId() != null && sa.getFacultyId().toUpperCase().startsWith("UNKNOWN_") && sa.isFinalized()) {
                Subject sub = sa.getSubjectId() != null ? subjectMap.get(sa.getSubjectId().toLowerCase()) : null;
                if (sub != null && sub.getAcademicYear() != null && sub.getAcademicYear().equalsIgnoreCase(academicYear)) {
                    unknownFacultyIds.add(sa.getFacultyId());
                }
            }
        }
        for (SubjectAllocation sa : activeSubAllocs) {
            if (sa.getFacultyId() != null && sa.getFacultyId().toUpperCase().startsWith("UNKNOWN_") && sa.isFinalized()) {
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
                    Subject sub = h.getSubjectId() != null ? subjectMap.get(h.getSubjectId().toLowerCase()) : null;
                    if (sub != null && !activeAllocatedYears.isEmpty() && !activeAllocatedYears.contains(sub.getYear())) {
                        continue;
                    }
                    Map<String, Object> alloc = new HashMap<>();
                    alloc.put("id", h.getId());
                    alloc.put("subjectId", h.getSubjectId());
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

            // 2. Active section allocations (only if finalized)
            for (SectionAllocation sa : activeSecAllocs) {
                if (sa.getFacultyId() != null && sa.getFacultyId().equalsIgnoreCase(unknownId) && sa.isFinalized()) {
                    Subject sub = sa.getSubjectId() != null ? subjectMap.get(sa.getSubjectId().toLowerCase()) : null;
                    if (sub != null && sub.getAcademicYear() != null && sub.getAcademicYear().equalsIgnoreCase(academicYear)) {
                        if (!activeAllocatedYears.isEmpty() && !activeAllocatedYears.contains(sub.getYear())) {
                            continue;
                        }
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
                            alloc.put("preferenceNumber", -1);
                            
                            allocMap.put(key, alloc);
                        }
                    }
                }
            }

            // 3. Active subject allocations (only if finalized)
            for (SubjectAllocation sa : activeSubAllocs) {
                if (sa.getFacultyId() != null && sa.getFacultyId().equalsIgnoreCase(unknownId) && sa.isFinalized()) {
                    Subject sub = sa.getSubjectId() != null ? subjectMap.get(sa.getSubjectId().toLowerCase()) : null;
                    if (sub != null && sub.getAcademicYear() != null && sub.getAcademicYear().equalsIgnoreCase(academicYear)) {
                        if (!activeAllocatedYears.isEmpty() && !activeAllocatedYears.contains(sub.getYear())) {
                            continue;
                        }
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

            List<Map<String, Object>> allocList = new ArrayList<>(allocMap.values());
            if (!allocList.isEmpty()) {
                facData.put("allocations", allocList);
                report.add(facData);
            }
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
