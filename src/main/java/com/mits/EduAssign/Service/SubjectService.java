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

    public List<FacultySubjectPreference> getPreferencesByFacultyId(String facultyId) {
        return preferenceRepository.findByFacultyIdOrderByIdAsc(facultyId);
    }

    // ----------------------------------------------------
    // SUBJECT SELECTION DEADLINE WINDOW
    // ----------------------------------------------------

    public SubjectSelectionWindow setDeadline(String message, int days, Integer sem, String academicYear, String department) {
        SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();
        if (window == null) {
            window = new SubjectSelectionWindow();
        }
        window.setMessage(message);
        window.setDeadline(LocalDateTime.now().plusDays(days));
        window.setActive(true);
        window.setSem(sem);
        window.setAcademicYear(academicYear);
        window.setDepartment(department);
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
        if (!allocationRepository.existsById(id)) {
            return false;
        }
        allocationRepository.deleteById(id);
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

        SubjectAllocation subAlloc = allocationRepository.findBySubjectIdAndFacultyId(
                allocation.getSubjectId(), allocation.getFacultyId());
        if (subAlloc == null) {
            throw new IllegalArgumentException("This faculty member must be allocated to the subject first in '1. Subject Allocation' before assigning a section.");
        }

        SectionAllocation existingSecAlloc = sectionAllocationRepository.findBySubjectIdAndSectionName(
                allocation.getSubjectId(), allocation.getSectionName());
        if (existingSecAlloc != null) {
            throw new IllegalArgumentException("Section " + allocation.getSectionName() + " of this subject is already allocated to another faculty member.");
        }

        SectionAllocation existingFacultySecAlloc = sectionAllocationRepository.findBySubjectIdAndFacultyId(
                allocation.getSubjectId(), allocation.getFacultyId());
        if (existingFacultySecAlloc != null) {
            throw new IllegalArgumentException("This faculty member is already allocated to another section of this subject.");
        }

        allocation.setFinalized(false);
        return sectionAllocationRepository.save(allocation);
    }

    public List<SectionAllocation> getAllSectionAllocations() {
        return sectionAllocationRepository.findAll();
    }

    @Transactional
    public boolean deleteSectionAllocation(Long id) {
        if (!sectionAllocationRepository.existsById(id)) {
            return false;
        }
        sectionAllocationRepository.deleteById(id);
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
                             (window.getDepartment() == null || window.getDepartment().equalsIgnoreCase(getSubjectDepartmentCode(s))))
                .collect(Collectors.toList());
            if (!filtered.isEmpty()) {
                subjects = filtered;
            }
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

        List<FacultySubjectPreference> allPreferences = preferenceRepository.findAll();

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

        List<SubjectAllocation> autoAllocations = new ArrayList<>();

        for (Subject sub : subjects) {
            // Find all sections for this subject's department and year case-insensitively
            String subDeptCode = getSubjectDepartmentCode(sub);
            List<Section> sections = sectionRepository.findAll().stream()
                    .filter(s -> s.getYearNumber() != null && s.getYearNumber().equals(sub.getYear()) &&
                                 s.getDepartmentCode() != null && s.getDepartmentCode().equalsIgnoreCase(subDeptCode))
                    .collect(Collectors.toList());
            int maxAllocations = Math.max(1, sections.size());

            // Get existing subject allocations for this subject
            List<SubjectAllocation> existingAllocations = allocationRepository.findBySubjectId(sub.getId());

            int allocationsNeeded = maxAllocations - existingAllocations.size();

            // Add all existing allocations to autoAllocations list so they are returned
            autoAllocations.addAll(existingAllocations);

            if (allocationsNeeded <= 0) {
                continue;
            }

            // Create a set of already allocated faculty IDs for this subject
            java.util.Set<String> allocatedFacultyForSub = new java.util.HashSet<>();
            existingAllocations.forEach(a -> allocatedFacultyForSub.add(a.getFacultyId().toUpperCase()));

            // Find all preferences for this subject
            List<FacultySubjectPreference> prefsForSub = allPreferences.stream()
                    .filter(p -> p.getSubjectId().equalsIgnoreCase(sub.getId()))
                    .collect(Collectors.toList());

            if (!prefsForSub.isEmpty()) {
                // Filter out faculty members who are already allocated to this subject, and ensure the faculty member exists
                List<FacultySubjectPreference> eligiblePrefs = prefsForSub.stream()
                        .filter(p -> p.getFacultyId() != null && 
                                     validFacultyIds.contains(p.getFacultyId().toUpperCase()) &&
                                     !allocatedFacultyForSub.contains(p.getFacultyId().toUpperCase()))
                        .collect(Collectors.toList());

                // Sort remaining preferences by submission order (earliest submission first, case-insensitively)
                eligiblePrefs.sort((p1, p2) -> {
                    Long o1 = facultySubmissionOrder.getOrDefault(p1.getFacultyId().toUpperCase(), Long.MAX_VALUE);
                    Long o2 = facultySubmissionOrder.getOrDefault(p2.getFacultyId().toUpperCase(), Long.MAX_VALUE);
                    return o1.compareTo(o2);
                });

                // Allocate from preferences
                for (FacultySubjectPreference pref : eligiblePrefs) {
                    if (allocationsNeeded <= 0) {
                        break;
                    }
                    String facultyIdUpper = pref.getFacultyId().toUpperCase();
                    if (allocatedFacultyForSub.contains(facultyIdUpper)) {
                        continue;
                    }

                    SubjectAllocation newAlloc = new SubjectAllocation(sub.getId(), pref.getFacultyId());
                    newAlloc.setFinalized(false);
                    SubjectAllocation saved = allocationRepository.save(newAlloc);
                    autoAllocations.add(saved);
                    allocatedFacultyForSub.add(facultyIdUpper);
                    allocationsNeeded--;
                }
            }
        }

        return autoAllocations;
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
        return preferenceRepository.findAll();
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
}
