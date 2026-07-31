package com.mits.EduAssign.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.mits.EduAssign.Entity.Subject;
import com.mits.EduAssign.Entity.SubjectSelectionWindow;
import com.mits.EduAssign.Entity.FacultySubjectPreference;
import com.mits.EduAssign.Entity.SubjectAllocation;
import com.mits.EduAssign.Entity.SectionAllocation;
import com.mits.EduAssign.Entity.AdminFaculty;
import com.mits.EduAssign.Entity.AllocationHistory;
import com.mits.EduAssign.Repository.SubjectRepository;
import com.mits.EduAssign.Repository.SelectionWindowRepository;
import com.mits.EduAssign.Repository.PreferenceRepository;
import com.mits.EduAssign.Repository.AllocationRepository;
import com.mits.EduAssign.Repository.SectionAllocationRepository;
import com.mits.EduAssign.Repository.AdminRepository;
import com.mits.EduAssign.Repository.AllocationHistoryRepository;
import com.mits.EduAssign.Repository.SectionRepository;
import com.mits.EduAssign.Repository.DepartmentRepository;
import com.mits.EduAssign.Entity.Section;
import com.mits.EduAssign.Entity.Department;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
public class SubjectService {

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private SelectionWindowRepository windowRepository;

    @Autowired
    private PreferenceRepository preferenceRepository;

    @Autowired
    private AllocationRepository allocationRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private SectionAllocationRepository sectionAllocationRepository;

    @Autowired
    private AllocationHistoryRepository allocationHistoryRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    public Subject addSubject(Subject subject) {
        return subjectRepository.save(subject);
    }

    public List<Subject> viewAllSubjects() {
        return subjectRepository.findAll();
    }

    public Subject viewSubjectById(String id) {
        return subjectRepository.findById(id).orElse(null);
    }

    public Subject updateSubject(String id, Subject updatedSubject) {
        Subject subject = subjectRepository.findById(id).orElse(null);
        if (subject == null) {
            return null;
        }

        String newId = updatedSubject.getId();
        if (newId != null && !newId.trim().isEmpty() && !newId.equalsIgnoreCase(id)) {
            newId = newId.trim();
            // Check if another subject already exists with the new ID
            if (subjectRepository.existsById(newId)) {
                throw new IllegalArgumentException("Already there is a subject with that subjectcode");
            }
            
            // Delete the old subject record
            subjectRepository.delete(subject);
            
            // Set the new ID on the entity
            subject.setId(newId);
            
            // Update referencing tables using jdbcTemplate
            jdbcTemplate.update("UPDATE allocation_history SET subject_id = ? WHERE subject_id = ?", newId, id);
            jdbcTemplate.update("UPDATE faculty_subject_preference SET subject_id = ? WHERE subject_id = ?", newId, id);
            jdbcTemplate.update("UPDATE section_allocation SET subject_id = ? WHERE subject_id = ?", newId, id);
            jdbcTemplate.update("UPDATE subject_allocation SET subject_id = ? WHERE subject_id = ?", newId, id);
        }

        if (updatedSubject.getName() != null)
            subject.setName(updatedSubject.getName());

        if (updatedSubject.getRegulation() != null)
            subject.setRegulation(updatedSubject.getRegulation());

        if (updatedSubject.getDep() != null)
            subject.setDep(updatedSubject.getDep());

        if (updatedSubject.getYear() != 0)
            subject.setYear(updatedSubject.getYear());

        if (updatedSubject.getSem() != 0)
            subject.setSem(updatedSubject.getSem());

        if (updatedSubject.getAcademicYear() != null)
            subject.setAcademicYear(updatedSubject.getAcademicYear());

        subject.setMock(updatedSubject.isMock());

        return subjectRepository.save(subject);
    }

    public boolean deleteSubject(String id) {
        if (!subjectRepository.existsById(id)) {
            return false;
        }
        subjectRepository.deleteById(id);
        return true;
    }

    @Transactional
    public void uploadSubject(MultipartFile file) {
        try {
            Workbook workbook = new XSSFWorkbook(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || row.getCell(0) == null) {
                    continue;
                }

                Subject subject = new Subject();
                subject.setId(getCellValueAsString(row.getCell(0)));
                subject.setName(getCellValueAsString(row.getCell(1)));
                subject.setYear(getCellValueAsInt(row.getCell(2)));
                subject.setSem(getCellValueAsInt(row.getCell(3)));
                subject.setDep(getCellValueAsString(row.getCell(4)));
                subject.setRegulation(getCellValueAsString(row.getCell(5)));

                if (row.getLastCellNum() > 6 && row.getCell(6) != null) {
                    subject.setAcademicYear(getCellValueAsString(row.getCell(6)));
                } else {
                    subject.setAcademicYear("2026-27");
                }

                if (row.getLastCellNum() > 7 && row.getCell(7) != null) {
                    String typeVal = getCellValueAsString(row.getCell(7)).trim();
                    boolean isMockVal = "mock".equalsIgnoreCase(typeVal) || 
                                       "yes".equalsIgnoreCase(typeVal) || 
                                       "true".equalsIgnoreCase(typeVal) || 
                                       "1".equals(typeVal);
                    subject.setMock(isMockVal);
                } else {
                    subject.setMock(false);
                }

                subjectRepository.save(subject);
            }
            workbook.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ----------------------------------------------------
    // SUBJECT PREFERENCES (FACULTY CHOICES)
    // ----------------------------------------------------

    @Transactional
    public void savePreferences(String facultyId, List<String> subjectIds) {
        // Clear existing preferences first
        preferenceRepository.deleteByFacultyId(facultyId);

        // Save new preferences
        for (String subjectId : subjectIds) {
            FacultySubjectPreference pref = new FacultySubjectPreference(facultyId, subjectId);
            preferenceRepository.save(pref);
        }
    }

    @Transactional
    public void savePreferencesEntity(String facultyId, List<FacultySubjectPreference> preferences) {
        // Clear existing preferences first
        preferenceRepository.deleteByFacultyId(facultyId);

        // Save new preferences
        for (FacultySubjectPreference pref : preferences) {
            preferenceRepository.save(pref);
        }
    }

    public List<FacultySubjectPreference> getPreferencesByFacultyId(String facultyId) {
        return preferenceRepository.findByFacultyIdOrderByIdAsc(facultyId);
    }

    // ----------------------------------------------------
    // SUBJECT SELECTION DEADLINE WINDOW
    // ----------------------------------------------------

    @Transactional
    public SubjectSelectionWindow setDeadline(
            String message, int days, Integer sem, String academicYear, String department,
            Integer year, Integer hoursPerWeek, Integer maxSubjectsAllocated,
            Integer subjectHoursPerWeek, Integer maxRegularPreferences, Integer maxMockPreferences) {
        SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();

        boolean selectionCriteriaChanged = true;
        if (window != null) {
            boolean yearUnchanged = (window.getYear() == null && year == null) || (window.getYear() != null && window.getYear().equals(year));
            boolean semUnchanged = (window.getSem() == null && sem == null) || (window.getSem() != null && window.getSem().equals(sem));
            boolean deptUnchanged = (window.getDepartment() == null && department == null) || (window.getDepartment() != null && department != null && window.getDepartment().trim().equalsIgnoreCase(department.trim()));
            
            if (yearUnchanged && semUnchanged && deptUnchanged) {
                selectionCriteriaChanged = false;
            }
        }

        // Refresh/wipe preferences table ONLY when selection criteria (year, sem, department) actually changes
        if (selectionCriteriaChanged) {
            preferenceRepository.deleteAll();
        }

        if (window == null) {
            window = new SubjectSelectionWindow();
        }
        window.setMessage(message);
        window.setDeadline(LocalDateTime.now().plusDays(days));
        window.setActive(true);
        window.setSem(sem);
        window.setAcademicYear(academicYear);
        window.setDepartment(department);
        window.setYear(year);
        window.setHoursPerWeek(hoursPerWeek);
        window.setMaxSubjectsAllocated(maxSubjectsAllocated);
        window.setSubjectHoursPerWeek(subjectHoursPerWeek);
        window.setMaxRegularPreferences(maxRegularPreferences);
        window.setMaxMockPreferences(maxMockPreferences);
        return windowRepository.save(window);
    }

    public SubjectSelectionWindow getActiveDeadline() {
        return windowRepository.findTopByOrderByIdDesc();
    }

    public boolean isBeforeDeadline() {
        SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();
        if (window == null || !window.isActive()) {
            return false;
        }
        return LocalDateTime.now().isBefore(window.getDeadline());
    }

    // ----------------------------------------------------
    // SUBJECT ALLOCATIONS
    // ----------------------------------------------------

    private String getSubjectDepartmentCode(Subject sub) {
        if (sub == null || sub.getDep() == null) {
            return null;
        }
        String dep = sub.getDep().trim();
        List<Department> depts = departmentRepository.findAll();
        for (Department d : depts) {
            if (d.getCode().equalsIgnoreCase(dep) || d.getName().equalsIgnoreCase(dep)) {
                return d.getCode();
            }
        }
        return dep;
    }

    @Transactional
    public SubjectAllocation allocateSubject(SubjectAllocation allocation) {
        if (allocation.getFacultyId() == null || allocation.getSubjectId() == null) {
            throw new IllegalArgumentException("Faculty ID and Subject ID are required.");
        }
        if (isBeforeDeadline()) {
            throw new IllegalStateException("The subject selection window is currently running. Please click 'Stop Selection Window' on the Selection Window tab before performing allocations.");
        }

        SubjectAllocation existingAlloc = allocationRepository.findBySubjectIdAndFacultyId(
                allocation.getSubjectId(), allocation.getFacultyId());
        if (existingAlloc != null) {
            throw new IllegalArgumentException("This faculty member is already allocated to this subject.");
        }

        Subject subject = subjectRepository.findById(allocation.getSubjectId()).orElse(null);
        if (subject == null) {
            throw new IllegalArgumentException("Subject not found.");
        }

        // Validate selection window limits (max subjects and hours)
        SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();
        if (window != null) {
            List<SubjectAllocation> facAllocs = allocationRepository.findByFacultyId(allocation.getFacultyId());
            
            Integer maxSubs = window.getMaxSubjectsAllocated();
            if (maxSubs != null && maxSubs > 0 && facAllocs.size() >= maxSubs) {
                throw new IllegalArgumentException("Maximum subjects allocation limit reached (" + maxSubs + " subject(s)) for this faculty member.");
            }

            Integer maxHours = window.getHoursPerWeek();
            Integer subHours = window.getSubjectHoursPerWeek();
            if (maxHours != null && maxHours > 0 && subHours != null && subHours > 0) {
                int currentHours = facAllocs.size() * subHours;
                if (currentHours + subHours > maxHours) {
                    throw new IllegalArgumentException("Maximum weekly workload hours reached (" + maxHours + " hours) for this faculty member. Current: " + currentHours + " hrs, Requested: " + subHours + " hrs.");
                }
            }

            // Enforce max 2 regular subjects rule
            if (!subject.isMock()) {
                long regularCount = facAllocs.stream()
                        .map(a -> subjectRepository.findById(a.getSubjectId()).orElse(null))
                        .filter(s -> s != null && !s.isMock())
                        .count();
                if (regularCount >= 2) {
                    throw new IllegalArgumentException("Faculty member has already been allocated " + regularCount + " regular subjects. Maximum allowed is 2. Additional allocations must be mock.");
                }
            } else {
                // Enforce max 1 mock subject ever
                if (hasAnyMockAllocation(allocation.getFacultyId())) {
                    throw new IllegalArgumentException("Faculty member has already been allocated a mock subject in this or a previous year. Only one mock subject is allowed per faculty.");
                }
            }

            // Enforce: If total allocated subjects >= 3, they cannot all be from the same study year
            List<Integer> years = new java.util.ArrayList<>();
            for (SubjectAllocation a : facAllocs) {
                Subject s = subjectRepository.findById(a.getSubjectId()).orElse(null);
                if (s != null) {
                    years.add(s.getYear());
                }
            }
            years.add(subject.getYear());
            if (years.size() >= 3) {
                long distinctYearsCount = years.stream().distinct().count();
                if (distinctYearsCount == 1) {
                    throw new IllegalArgumentException("All allocated subjects cannot be from the same study year (" + subject.getYear() + "). Allocation must span at least two different years.");
                }
            }
        }

        String subDeptCode = getSubjectDepartmentCode(subject);
        List<Section> sections = sectionRepository.findAll().stream()
                .filter(s -> s.getYearNumber() != null && s.getYearNumber().equals(subject.getYear()) &&
                             s.getDepartmentCode() != null && s.getDepartmentCode().equalsIgnoreCase(subDeptCode))
                .collect(Collectors.toList());
        int maxAllocations = Math.max(1, sections.size());

        List<SubjectAllocation> existingAllocations = allocationRepository.findBySubjectId(allocation.getSubjectId());
        if (existingAllocations.size() >= maxAllocations) {
            throw new IllegalArgumentException("Allocation limit reached. Maximum faculty allowed for this subject is " + maxAllocations + " (based on " + maxAllocations + " section(s) in Year " + subject.getYear() + " " + subDeptCode + ").");
        }

        allocation.setFinalized(false);
        return allocationRepository.save(allocation);
    }

    public List<SubjectAllocation> getAllocations() {
        return allocationRepository.findAll();
    }

    @Transactional
    public boolean deleteAllocation(Long id) {
        SubjectAllocation sa = allocationRepository.findById(id).orElse(null);
        if (sa == null) {
            return false;
        }
        String facultyId = sa.getFacultyId();
        String subjectId = sa.getSubjectId();
        
        allocationRepository.delete(sa);
        
        // Delete corresponding section allocations
        List<SectionAllocation> secAllocs = sectionAllocationRepository.findBySubjectId(subjectId).stream()
                .filter(x -> x.getFacultyId().equalsIgnoreCase(facultyId))
                .collect(Collectors.toList());
        sectionAllocationRepository.deleteAll(secAllocs);
        
        return true;
    }

    public List<SubjectAllocation> getAllocationsByFacultyId(String facultyId) {
        return allocationRepository.findByFacultyId(facultyId);
    }

    @Transactional
    public SectionAllocation allocateSection(SectionAllocation allocation) {
        if (allocation.getFacultyId() == null || allocation.getSubjectId() == null || allocation.getSectionName() == null) {
            throw new IllegalArgumentException("Faculty ID, Subject ID, and Section Name are all required.");
        }
        if (isBeforeDeadline()) {
            throw new IllegalStateException("The subject selection window is currently running. Please click 'Stop Selection Window' on the Selection Window tab before performing allocations.");
        }

        SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();
        Integer hoursLimit = (window != null) ? window.getHoursPerWeek() : 14;
        if (hoursLimit == null) hoursLimit = 14;
        Integer subjectHours = (window != null) ? window.getSubjectHoursPerWeek() : 4;
        if (subjectHours == null) subjectHours = 4;

        Subject subject = subjectRepository.findById(allocation.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException("Subject not found."));

        // Validate section not already allocated
        SectionAllocation existingSecAlloc = sectionAllocationRepository.findBySubjectIdAndSectionName(
                allocation.getSubjectId(), allocation.getSectionName());
        if (existingSecAlloc != null) {
            throw new IllegalArgumentException("Section " + allocation.getSectionName() + " of this subject is already allocated to " + existingSecAlloc.getFacultyId() + ".");
        }

        // Validate workload limit
        List<SectionAllocation> facSecAllocs = sectionAllocationRepository.findByFacultyId(allocation.getFacultyId());
        int currentHours = facSecAllocs.size() * subjectHours;
        if (currentHours + subjectHours > hoursLimit) {
            throw new IllegalArgumentException("Maximum weekly workload hours reached (" + hoursLimit + " hours) for this faculty member. Current: " + currentHours + " hrs, Requested: " + subjectHours + " hrs.");
        }

        // Validate subject count and mock/regular rules
        List<SubjectAllocation> facAllocs = allocationRepository.findByFacultyId(allocation.getFacultyId());
        boolean alreadyHasSubject = facAllocs.stream().anyMatch(a -> a.getSubjectId().equalsIgnoreCase(allocation.getSubjectId()));

        if (!alreadyHasSubject) {
            long regularCount = 0;
            long mockCount = 0;
            for (SubjectAllocation sa : facAllocs) {
                Subject s = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                if (s != null) {
                    if (s.isMock()) {
                        mockCount++;
                    } else {
                        regularCount++;
                    }
                }
            }

            if (subject.isMock()) {
                if (hasPreviousMockAllocation(allocation.getFacultyId())) {
                    throw new IllegalArgumentException("Faculty member has already been allocated a mock subject in a previous year. Only one mock subject is allowed per faculty.");
                }
                if (regularCount != 2) {
                    throw new IllegalArgumentException("A mock subject can only be allocated to a faculty member who has exactly 2 regular subjects allocated. Currently allocated: " + regularCount + " regular subject(s).");
                }
                if (mockCount >= 1) {
                    throw new IllegalArgumentException("Faculty member has already been allocated a mock subject in the current year.");
                }
            } else {
                if (regularCount >= 2) {
                    throw new IllegalArgumentException("Faculty member has already been allocated " + regularCount + " regular subjects. Maximum allowed is 2. Additional allocations must be mock.");
                }
            }

            // Create subject allocation
            SubjectAllocation newSubAlloc = new SubjectAllocation(allocation.getSubjectId(), allocation.getFacultyId());
            newSubAlloc.setFinalized(false);
            allocationRepository.save(newSubAlloc);
        }

        allocation.setFinalized(false);
        return sectionAllocationRepository.save(allocation);
    }

    public List<SectionAllocation> getAllSectionAllocations() {
        return sectionAllocationRepository.findAll();
    }

    @Transactional
    public boolean deleteSectionAllocation(Long id) {
        SectionAllocation sa = sectionAllocationRepository.findById(id).orElse(null);
        if (sa == null) {
            return false;
        }
        String facultyId = sa.getFacultyId();
        String subjectId = sa.getSubjectId();

        sectionAllocationRepository.delete(sa);

        // Check if there are any other section allocations for this faculty and subject
        List<SectionAllocation> remaining = sectionAllocationRepository.findBySubjectId(subjectId).stream()
                .filter(x -> x.getFacultyId().equalsIgnoreCase(facultyId))
                .collect(Collectors.toList());
        if (remaining.isEmpty()) {
            SubjectAllocation subAlloc = allocationRepository.findBySubjectIdAndFacultyId(subjectId, facultyId);
            if (subAlloc != null) {
                allocationRepository.delete(subAlloc);
            }
        }
        return true;
    }

    @Transactional
    public List<SubjectAllocation> autoAllocateSubjects() {
        if (isBeforeDeadline()) {
            throw new IllegalStateException("The subject selection window is currently running. Please click 'Stop Selection Window' on the Selection Window tab before performing allocations.");
        }

        List<Subject> allSubjects = subjectRepository.findAll();
        if (allSubjects.isEmpty()) {
            throw new IllegalStateException("No subjects found in the Subject Directory. Please add or import subjects first.");
        }

        SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();
        List<Subject> subjects = allSubjects;
        if (window != null) {
            List<Subject> filtered = allSubjects.stream()
                .filter(s -> (window.getSem() == null || NumberHelperIsEqual(s.getSem(), window.getSem())) &&
                             (window.getYear() == null || NumberHelperIsEqual(s.getYear(), window.getYear())) &&
                             (window.getDepartment() == null || window.getDepartment().equalsIgnoreCase(getSubjectDepartmentCode(s))) &&
                             (window.getAcademicYear() == null || window.getAcademicYear().equalsIgnoreCase(s.getAcademicYear())))
                .collect(Collectors.toList());
            if (!filtered.isEmpty()) {
                subjects = filtered;
            }
        }

        // Clean out existing non-finalized allocations for the target subjects in this batch
        for (Subject sub : subjects) {
            List<SubjectAllocation> drafts = allocationRepository.findBySubjectId(sub.getId()).stream()
                    .filter(sa -> !sa.isFinalized())
                    .collect(Collectors.toList());
            allocationRepository.deleteAll(drafts);

            List<SectionAllocation> draftSecs = sectionAllocationRepository.findBySubjectId(sub.getId()).stream()
                    .filter(sa -> !sa.isFinalized())
                    .collect(Collectors.toList());
            sectionAllocationRepository.deleteAll(draftSecs);
        }

        List<AdminFaculty> allFaculty = adminRepository.findAll().stream()
                .filter(u -> "faculty".equalsIgnoreCase(u.getRole()) || 
                               ("ADMIN".equalsIgnoreCase(u.getRole()) && !"ADMIN01".equalsIgnoreCase(u.getId())))
                .collect(Collectors.toList());

        if (allFaculty.isEmpty()) {
            throw new IllegalStateException("No faculty members found to allocate subjects to.");
        }

        java.util.Set<String> validFacultyIds = allFaculty.stream()
                .map(f -> f.getId().toUpperCase())
                .collect(Collectors.toSet());

        // Fetch all preferences (both regular and mock)
        List<FacultySubjectPreference> allPreferences = preferenceRepository.findAll();

        // Sort preferences so regular preferences are processed first (mock = false < mock = true), then by ID (first come first served)
        allPreferences.sort((p1, p2) -> {
            if (p1.isMock() != p2.isMock()) {
                return Boolean.compare(p1.isMock(), p2.isMock());
            }
            return Long.compare(p1.getId(), p2.getId());
        });

        // Calculate the minimum preference ID for each faculty to determine first-come-first-serve order (case-insensitive keys)
        java.util.Map<String, Long> facultySubmissionOrder = new java.util.HashMap<>();
        for (FacultySubjectPreference pref : allPreferences) {
            if (pref.getFacultyId() == null) continue;
            String facId = pref.getFacultyId().toUpperCase();
            long prefId = pref.getId();
            if (!facultySubmissionOrder.containsKey(facId) || prefId < facultySubmissionOrder.get(facId)) {
                facultySubmissionOrder.put(facId, prefId);
            }
        }

        // Group preferences by faculty and separate mock vs regular to compute choice ranks
        java.util.Map<String, List<FacultySubjectPreference>> facultyRegularPrefs = new java.util.HashMap<>();
        java.util.Map<String, List<FacultySubjectPreference>> facultyMockPrefs = new java.util.HashMap<>();
        for (FacultySubjectPreference pref : allPreferences) {
            if (pref.getFacultyId() == null) continue;
            String facId = pref.getFacultyId().toUpperCase();
            if (pref.isMock()) {
                facultyMockPrefs.computeIfAbsent(facId, k -> new ArrayList<>()).add(pref);
            } else {
                facultyRegularPrefs.computeIfAbsent(facId, k -> new ArrayList<>()).add(pref);
            }
        }
        
        // Sort each list by ID ascending to guarantee correct ranking order
        for (List<FacultySubjectPreference> list : facultyRegularPrefs.values()) {
            list.sort(java.util.Comparator.comparing(FacultySubjectPreference::getId));
        }
        for (List<FacultySubjectPreference> list : facultyMockPrefs.values()) {
            list.sort(java.util.Comparator.comparing(FacultySubjectPreference::getId));
        }
        
        // Build a map of preference ID to choice rank (0-based)
        java.util.Map<Long, Integer> preferenceRanks = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, List<FacultySubjectPreference>> entry : facultyRegularPrefs.entrySet()) {
            List<FacultySubjectPreference> list = entry.getValue();
            for (int i = 0; i < list.size(); i++) {
                preferenceRanks.put(list.get(i).getId(), i);
            }
        }
        for (java.util.Map.Entry<String, List<FacultySubjectPreference>> entry : facultyMockPrefs.entrySet()) {
            List<FacultySubjectPreference> list = entry.getValue();
            for (int i = 0; i < list.size(); i++) {
                preferenceRanks.put(list.get(i).getId(), i);
            }
        }

        Integer subHours = (window != null) ? window.getSubjectHoursPerWeek() : null;
        if (subHours == null) subHours = 4; // default fallback
        Integer hoursLimit = (window != null) ? window.getHoursPerWeek() : 14;
        if (hoursLimit == null) hoursLimit = 14;

        java.util.Map<String, Integer> facultySubjectCount = new java.util.HashMap<>();
        java.util.Map<String, Integer> facultyRegularCount = new java.util.HashMap<>();
        java.util.Map<String, Integer> facultyMockCount = new java.util.HashMap<>();
        java.util.Map<String, Integer> facultyWorkloadHours = new java.util.HashMap<>();

        // Seed tracker maps with existing finalized allocations
        for (SubjectAllocation alloc : allocationRepository.findAll()) {
            if (alloc.getFacultyId() == null) continue;
            String facId = alloc.getFacultyId().toUpperCase();
            
            // Look up subject mock status
            Subject s = subjectRepository.findById(alloc.getSubjectId()).orElse(null);
            if (s != null) {
                facultySubjectCount.put(facId, facultySubjectCount.getOrDefault(facId, 0) + 1);
                if (s.isMock()) {
                    facultyMockCount.put(facId, facultyMockCount.getOrDefault(facId, 0) + 1);
                } else {
                    facultyRegularCount.put(facId, facultyRegularCount.getOrDefault(facId, 0) + 1);
                }
            }
        }

        // Count finalized sections for workload hours
        for (SectionAllocation sa : sectionAllocationRepository.findAll()) {
            if (sa.getFacultyId() == null) continue;
            String facId = sa.getFacultyId().toUpperCase();
            if (sa.isFinalized()) {
                facultyWorkloadHours.put(facId, facultyWorkloadHours.getOrDefault(facId, 0) + subHours);
            }
        }

        // Compile list of all section allocation slots to be filled
        class SectionSlot {
            Subject subject;
            Section section;
            SectionSlot(Subject subject, Section section) {
                this.subject = subject;
                this.section = section;
            }
        }

        List<SectionSlot> pendingSlots = new ArrayList<>();
        for (Subject sub : subjects) {
            String subDeptCode = getSubjectDepartmentCode(sub);
            List<Section> sections = sectionRepository.findAll().stream()
                    .filter(s -> s.getYearNumber() != null && s.getYearNumber().equals(sub.getYear()) &&
                                 s.getDepartmentCode() != null && s.getDepartmentCode().equalsIgnoreCase(subDeptCode))
                    .collect(Collectors.toList());
            
            // We require that all sections of the subject are allocated
            for (Section sec : sections) {
                // Check if this section allocation is already finalized
                SectionAllocation finalizedAlloc = sectionAllocationRepository.findBySubjectIdAndSectionName(sub.getId(), sec.getSectionName());
                if (finalizedAlloc == null || !finalizedAlloc.isFinalized()) {
                    // It needs allocation
                    pendingSlots.add(new SectionSlot(sub, sec));
                }
            }
        }

        // Phase 1: Allocate section slots based on faculty preferences
        // Compile all preferences into an ordered list across all faculty
        List<FacultySubjectPreference> orderedPrefs = new ArrayList<>(allPreferences);
        orderedPrefs.sort((p1, p2) -> {
            int rank1 = preferenceRanks.getOrDefault(p1.getId(), 999);
            int rank2 = preferenceRanks.getOrDefault(p2.getId(), 999);
            if (rank1 != rank2) {
                return Integer.compare(rank1, rank2);
            }
            Long o1 = facultySubmissionOrder.getOrDefault(p1.getFacultyId().toUpperCase(), Long.MAX_VALUE);
            Long o2 = facultySubmissionOrder.getOrDefault(p2.getFacultyId().toUpperCase(), Long.MAX_VALUE);
            return o1.compareTo(o2);
        });

        for (FacultySubjectPreference pref : orderedPrefs) {
            String facultyIdUpper = pref.getFacultyId().toUpperCase();
            if (!validFacultyIds.contains(facultyIdUpper)) {
                continue;
            }

            // Find if there is a pending slot for this preferred subject
            SectionSlot targetSlot = null;
            for (SectionSlot slot : pendingSlots) {
                if (slot.subject.getId().equalsIgnoreCase(pref.getSubjectId())) {
                    targetSlot = slot;
                    break;
                }
            }

            if (targetSlot == null) {
                continue; // No unallocated sections left for this preferred subject
            }

            Subject sub = targetSlot.subject;

            // Check eligibility for this faculty member
            boolean isAlreadyAllocatedToSubject = allocationRepository.findBySubjectIdAndFacultyId(sub.getId(), pref.getFacultyId()) != null;

            int currentHours = facultyWorkloadHours.getOrDefault(facultyIdUpper, 0);
            if (currentHours + subHours > hoursLimit) {
                continue; // Exceeds workload limit
            }

            if (!isAlreadyAllocatedToSubject) {
                int currentUniqueSubs = facultySubjectCount.getOrDefault(facultyIdUpper, 0);
                if (currentUniqueSubs >= 3) {
                    continue; // Exceeds max 3 subjects limit
                }

                if (sub.isMock()) {
                    int regCount = facultyRegularCount.getOrDefault(facultyIdUpper, 0);
                    int mockCount = facultyMockCount.getOrDefault(facultyIdUpper, 0);
                    if (regCount != 2 || mockCount >= 1 || hasPreviousMockAllocation(pref.getFacultyId())) {
                        continue; // Mock rules violation
                    }
                } else {
                    int regCount = facultyRegularCount.getOrDefault(facultyIdUpper, 0);
                    if (regCount >= 2) {
                        continue; // Max 2 regular subjects limit
                    }
                }
            }

            // Perform allocation
            SectionAllocation sa = new SectionAllocation(sub.getId(), pref.getFacultyId(), targetSlot.section.getSectionName());
            sa.setFinalized(false);
            sectionAllocationRepository.save(sa);

            if (!isAlreadyAllocatedToSubject) {
                SubjectAllocation subAlloc = new SubjectAllocation(sub.getId(), pref.getFacultyId());
                subAlloc.setFinalized(false);
                allocationRepository.save(subAlloc);

                facultySubjectCount.put(facultyIdUpper, facultySubjectCount.getOrDefault(facultyIdUpper, 0) + 1);
                if (sub.isMock()) {
                    facultyMockCount.put(facultyIdUpper, facultyMockCount.getOrDefault(facultyIdUpper, 0) + 1);
                } else {
                    facultyRegularCount.put(facultyIdUpper, facultyRegularCount.getOrDefault(facultyIdUpper, 0) + 1);
                }
            }

            facultyWorkloadHours.put(facultyIdUpper, currentHours + subHours);
            pendingSlots.remove(targetSlot);
        }

        // Phase 2: Force allocate remaining pending slots to ensure NO pending subjects
        for (SectionSlot slot : new ArrayList<>(pendingSlots)) {
            Subject sub = slot.subject;
            
            // Find eligible faculty members
            List<AdminFaculty> eligibleFaculty = new ArrayList<>();
            for (AdminFaculty f : allFaculty) {
                String fIdUpper = f.getId().toUpperCase();

                boolean isAlreadyAllocatedToSubject = allocationRepository.findBySubjectIdAndFacultyId(sub.getId(), f.getId()) != null;
                int currentHours = facultyWorkloadHours.getOrDefault(fIdUpper, 0);

                // Check workload hours
                if (currentHours + subHours > hoursLimit) {
                    continue;
                }

                if (!isAlreadyAllocatedToSubject) {
                    int currentUniqueSubs = facultySubjectCount.getOrDefault(fIdUpper, 0);
                    if (currentUniqueSubs >= 3) {
                        continue;
                    }

                    if (sub.isMock()) {
                        int regCount = facultyRegularCount.getOrDefault(fIdUpper, 0);
                        int mockCount = facultyMockCount.getOrDefault(fIdUpper, 0);
                        if (regCount != 2 || mockCount >= 1 || hasPreviousMockAllocation(f.getId())) {
                            continue;
                        }
                    } else {
                        int regCount = facultyRegularCount.getOrDefault(fIdUpper, 0);
                        if (regCount >= 2) {
                            continue;
                        }
                    }
                }

                eligibleFaculty.add(f);
            }

            AdminFaculty chosenFaculty = null;
            if (!eligibleFaculty.isEmpty()) {
                // Sort by workload ascending, then unique subject count ascending
                eligibleFaculty.sort((f1, f2) -> {
                    int h1 = facultyWorkloadHours.getOrDefault(f1.getId().toUpperCase(), 0);
                    int h2 = facultyWorkloadHours.getOrDefault(f2.getId().toUpperCase(), 0);
                    if (h1 != h2) return Integer.compare(h1, h2);
                    int s1 = facultySubjectCount.getOrDefault(f1.getId().toUpperCase(), 0);
                    int s2 = facultySubjectCount.getOrDefault(f2.getId().toUpperCase(), 0);
                    return Integer.compare(s1, s2);
                });
                chosenFaculty = eligibleFaculty.get(0);
            } else {
                // Relax limits to ensure no pending subjects:
                // Find ANY faculty who doesn't teach this section and has the lowest workload
                List<AdminFaculty> relaxedFaculty = new ArrayList<>(allFaculty);
                relaxedFaculty.sort((f1, f2) -> {
                    int h1 = facultyWorkloadHours.getOrDefault(f1.getId().toUpperCase(), 0);
                    int h2 = facultyWorkloadHours.getOrDefault(f2.getId().toUpperCase(), 0);
                    return Integer.compare(h1, h2);
                });
                if (!relaxedFaculty.isEmpty()) {
                    chosenFaculty = relaxedFaculty.get(0);
                }
            }

            if (chosenFaculty != null) {
                String fIdUpper = chosenFaculty.getId().toUpperCase();
                boolean isAlreadyAllocatedToSubject = allocationRepository.findBySubjectIdAndFacultyId(sub.getId(), chosenFaculty.getId()) != null;

                SectionAllocation sa = new SectionAllocation(sub.getId(), chosenFaculty.getId(), slot.section.getSectionName());
                sa.setFinalized(false);
                sectionAllocationRepository.save(sa);

                if (!isAlreadyAllocatedToSubject) {
                    SubjectAllocation subAlloc = new SubjectAllocation(sub.getId(), chosenFaculty.getId());
                    subAlloc.setFinalized(false);
                    allocationRepository.save(subAlloc);

                    facultySubjectCount.put(fIdUpper, facultySubjectCount.getOrDefault(fIdUpper, 0) + 1);
                    if (sub.isMock()) {
                        facultyMockCount.put(fIdUpper, facultyMockCount.getOrDefault(fIdUpper, 0) + 1);
                    } else {
                        facultyRegularCount.put(fIdUpper, facultyRegularCount.getOrDefault(fIdUpper, 0) + 1);
                    }
                }

                facultyWorkloadHours.put(fIdUpper, facultyWorkloadHours.getOrDefault(fIdUpper, 0) + subHours);
                pendingSlots.remove(slot);
            }
        }

        // Return current draft subject allocations for subjects in selection window
        List<String> targetSubjectIds = subjects.stream().map(Subject::getId).collect(Collectors.toList());
        return allocationRepository.findAll().stream()
                .filter(a -> !a.isFinalized() && targetSubjectIds.contains(a.getSubjectId()))
                .collect(Collectors.toList());
    }

    private boolean NumberHelperIsEqual(int a, Integer b) {
        return b != null && a == b;
    }

    @Transactional
    public void uploadAllocationHistory(MultipartFile file) {
        try {
            Workbook workbook = new XSSFWorkbook(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || row.getCell(0) == null) {
                    continue;
                }

                AllocationHistory history = new AllocationHistory();
                history.setAcademicYear(getCellValueAsString(row.getCell(0)));
                history.setDepartment(getCellValueAsString(row.getCell(1)));
                history.setSemester(getCellValueAsInt(row.getCell(2)));
                history.setFacultyId(getCellValueAsString(row.getCell(3)));
                history.setSubjectId(getCellValueAsString(row.getCell(4)));
                if (row.getLastCellNum() > 5 && row.getCell(5) != null) {
                    history.setSectionName(getCellValueAsString(row.getCell(5)));
                }

                allocationHistoryRepository.save(history);
            }
            workbook.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<AllocationHistory> getAllHistory() {
        return allocationHistoryRepository.findAll();
    }

    @Transactional
    public void finalizeAllocations() {
        SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();
        String currentYear = (window != null && window.getAcademicYear() != null) ? window.getAcademicYear() : "2026-27";
        String currentDept = (window != null && window.getDepartment() != null) ? window.getDepartment() : "CSE";
        Integer currentSem = (window != null) ? window.getSem() : 1;

        List<SubjectAllocation> subAllocs = allocationRepository.findAll();
        for (SubjectAllocation sa : subAllocs) {
            sa.setFinalized(true);
            allocationRepository.save(sa);
        }

        List<SectionAllocation> secAllocs = sectionAllocationRepository.findAll();
        for (SectionAllocation sa : secAllocs) {
            sa.setFinalized(true);
            sectionAllocationRepository.save(sa);

            // Save to AllocationHistory
            AllocationHistory hist = new AllocationHistory(
                currentYear,
                currentDept,
                currentSem,
                sa.getFacultyId(),
                sa.getSubjectId(),
                sa.getSectionName()
            );
            allocationHistoryRepository.save(hist);
        }
    }

    public boolean isAllocationsFinalized() {
        List<SubjectAllocation> subAllocs = allocationRepository.findAll();
        if (subAllocs.isEmpty()) {
            return false;
        }
        for (SubjectAllocation sa : subAllocs) {
            if (!sa.isFinalized()) {
                return false;
            }
        }
        return true;
    }

    public List<SectionAllocation> getFinalizedSectionAllocationsByFacultyId(String facultyId) {
        return sectionAllocationRepository.findByFacultyIdAndFinalized(facultyId, true);
    }

    public List<SubjectAllocation> getFinalizedSubjectAllocationsByFacultyId(String facultyId) {
        return allocationRepository.findByFacultyIdAndFinalized(facultyId, true);
    }

    public void stopDeadline() {
        SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();
        if (window != null) {
            window.setActive(false);
            windowRepository.save(window);
        }
    }

    public List<FacultySubjectPreference> getAllPreferences() {
        return preferenceRepository.findAll().stream()
                .filter(p -> !p.isMock())
                .collect(Collectors.toList());
    }

    public List<AdminFaculty> getFacultyWithNoPreferences() {
        List<AdminFaculty> allFaculty = adminRepository.findAll().stream()
                .filter(user -> "faculty".equalsIgnoreCase(user.getRole()) || 
                               ("ADMIN".equalsIgnoreCase(user.getRole()) && !"ADMIN01".equalsIgnoreCase(user.getId())))
                .collect(Collectors.toList());
        
        List<String> facultyWithPrefs = preferenceRepository.findAll().stream()
                .map(FacultySubjectPreference::getFacultyId)
                .distinct()
                .collect(Collectors.toList());
                
        List<AdminFaculty> result = allFaculty.stream()
                .filter(f -> !facultyWithPrefs.contains(f.getId()))
                .collect(Collectors.toList());
        result.sort(new NaturalOrderComparator());
        return result;
    }

    // ----------------------------------------------------
    // EXCEL VALUE HELPERS
    // ----------------------------------------------------

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double val = cell.getNumericCellValue();
                if (val == (long) val) {
                    return String.valueOf((long) val);
                }
                return String.valueOf(val);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    private int getCellValueAsInt(Cell cell) {
        if (cell == null) {
            return 0;
        }
        switch (cell.getCellType()) {
            case NUMERIC:
                return (int) cell.getNumericCellValue();
            case STRING:
                try {
                    return Integer.parseInt(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            case BOOLEAN:
                return cell.getBooleanCellValue() ? 1 : 0;
            default:
                return 0;
        }
    }

    public List<String> getAcademicYears() {
        java.util.Set<String> years = new java.util.TreeSet<>();
        allocationHistoryRepository.findAll().forEach(h -> {
            if (h.getAcademicYear() != null && !h.getAcademicYear().trim().isEmpty()) {
                years.add(h.getAcademicYear().trim());
            }
        });
        subjectRepository.findAll().forEach(s -> {
            if (s.getAcademicYear() != null && !s.getAcademicYear().trim().isEmpty()) {
                years.add(s.getAcademicYear().trim());
            }
        });
        windowRepository.findAll().forEach(w -> {
            if (w.getAcademicYear() != null && !w.getAcademicYear().trim().isEmpty()) {
                years.add(w.getAcademicYear().trim());
            }
        });
        if (years.isEmpty()) {
            years.add("2026-27");
        }
        return new ArrayList<>(years);
    }

    public List<FacultySubjectPreference> getPreferencesByAcademicYear(String academicYear) {
        List<FacultySubjectPreference> all = preferenceRepository.findAll();
        if (all.isEmpty()) return all;
        
        List<FacultySubjectPreference> filtered = all.stream()
            .filter(p -> {
                Subject sub = subjectRepository.findById(p.getSubjectId()).orElse(null);
                return sub != null && academicYear.equalsIgnoreCase(sub.getAcademicYear());
            })
            .collect(Collectors.toList());
        filtered.sort(java.util.Comparator.comparing(FacultySubjectPreference::getId));
        return filtered;
    }

    public List<AdminFaculty> getFacultyWithNoPreferencesByAcademicYear(String academicYear) {
        List<AdminFaculty> allFaculty = adminRepository.findAll().stream()
                .filter(user -> "faculty".equalsIgnoreCase(user.getRole()) || 
                               ("ADMIN".equalsIgnoreCase(user.getRole()) && !"ADMIN01".equalsIgnoreCase(user.getId())))
                .collect(Collectors.toList());
        
        List<FacultySubjectPreference> prefs = getPreferencesByAcademicYear(academicYear);
        List<String> facultyWithPrefs = prefs.stream()
                .map(FacultySubjectPreference::getFacultyId)
                .distinct()
                .collect(Collectors.toList());
                
        List<AdminFaculty> result = allFaculty.stream()
                .filter(f -> !facultyWithPrefs.contains(f.getId()))
                .collect(Collectors.toList());
        result.sort(new NaturalOrderComparator());
        return result;
    }

    public boolean hasAnyMockAllocation(String facultyId) {
        if (facultyId == null) {
            return false;
        }
        String facultyIdUpper = facultyId.trim().toUpperCase();
        
        // 1. Check current allocations (both draft and finalized)
        List<SubjectAllocation> currentAllocs = allocationRepository.findByFacultyId(facultyIdUpper);
        if (currentAllocs != null) {
            for (SubjectAllocation alloc : currentAllocs) {
                if (alloc.getSubjectId() == null) continue;
                Subject s = subjectRepository.findById(alloc.getSubjectId()).orElse(null);
                if (s != null && s.isMock()) {
                    return true;
                }
            }
        }
        
        // 2. Check historical allocations
        List<AllocationHistory> historicalAllocs = allocationHistoryRepository.findByFacultyId(facultyIdUpper);
        if (historicalAllocs != null) {
            for (AllocationHistory hist : historicalAllocs) {
                if (hist.getSubjectId() == null) continue;
                Subject s = subjectRepository.findById(hist.getSubjectId()).orElse(null);
                if (s != null && s.isMock()) {
                    return true;
                }
                String subIdLower = hist.getSubjectId().toLowerCase();
                if (subIdLower.contains("mock") || subIdLower.contains("mooc")) {
                    return true;
                }
            }
        }
        
        return false;
    }

    public boolean hasPreviousMockAllocation(String facultyId) {
        if (facultyId == null) {
            return false;
        }
        String facultyIdUpper = facultyId.trim().toUpperCase();
        
        // Get current academic year from active selection window
        SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();
        String currentAcademicYear = (window != null && window.getAcademicYear() != null) ? window.getAcademicYear().trim() : "";

        // Check current finalized subject allocations from a different academic year
        List<SubjectAllocation> currentAllocs = allocationRepository.findByFacultyId(facultyIdUpper);
        if (currentAllocs != null) {
            for (SubjectAllocation alloc : currentAllocs) {
                if (alloc.getSubjectId() == null) continue;
                Subject s = subjectRepository.findById(alloc.getSubjectId()).orElse(null);
                if (s != null && s.isMock() && alloc.isFinalized()) {
                    String allocYear = s.getAcademicYear() != null ? s.getAcademicYear().trim() : "";
                    if (currentAcademicYear.isEmpty() || !allocYear.equalsIgnoreCase(currentAcademicYear)) {
                        return true;
                    }
                }
            }
        }
        
        // Check historical allocations from a different academic year
        List<AllocationHistory> historicalAllocs = allocationHistoryRepository.findByFacultyId(facultyIdUpper);
        if (historicalAllocs != null) {
            for (AllocationHistory hist : historicalAllocs) {
                if (hist.getSubjectId() == null) continue;
                String histYear = hist.getAcademicYear() != null ? hist.getAcademicYear().trim() : "";
                if (currentAcademicYear.isEmpty() || !histYear.equalsIgnoreCase(currentAcademicYear)) {
                    Subject s = subjectRepository.findById(hist.getSubjectId()).orElse(null);
                    if (s != null && s.isMock()) {
                        return true;
                    }
                    String subIdLower = hist.getSubjectId().toLowerCase();
                    if (subIdLower.contains("mock") || subIdLower.contains("mooc")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Transactional
    public void swapAllocations(Long id1, Long id2) {
        SubjectAllocation a1 = allocationRepository.findById(id1)
            .orElseThrow(() -> new IllegalArgumentException("Allocation 1 not found"));
        SubjectAllocation a2 = allocationRepository.findById(id2)
            .orElseThrow(() -> new IllegalArgumentException("Allocation 2 not found"));

        String fac1 = a1.getFacultyId();
        String fac2 = a2.getFacultyId();
        String sub1 = a1.getSubjectId();
        String sub2 = a2.getSubjectId();

        // Swap the faculty in the subject allocations
        a1.setFacultyId(fac2);
        a2.setFacultyId(fac1);
        allocationRepository.save(a1);
        allocationRepository.save(a2);

        // Update corresponding section allocations symmetrically if they exist
        List<SectionAllocation> secAllocs = new java.util.ArrayList<>();
        if (sub1 != null) {
            secAllocs.addAll(sectionAllocationRepository.findBySubjectId(sub1));
        }
        if (sub2 != null && !sub2.equalsIgnoreCase(sub1)) {
            secAllocs.addAll(sectionAllocationRepository.findBySubjectId(sub2));
        }

        for (SectionAllocation sa : secAllocs) {
            if (sa.getFacultyId() != null) {
                if (sa.getFacultyId().trim().equalsIgnoreCase(fac1.trim())) {
                    sa.setFacultyId(fac2);
                    sectionAllocationRepository.save(sa);
                } else if (sa.getFacultyId().trim().equalsIgnoreCase(fac2.trim())) {
                    sa.setFacultyId(fac1);
                    sectionAllocationRepository.save(sa);
                }
            }
        }
    }
}
