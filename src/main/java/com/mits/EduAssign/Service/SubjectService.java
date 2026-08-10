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

        String newSubjectCode = updatedSubject.getId();
        if (newSubjectCode != null && newSubjectCode.contains("_")) {
            newSubjectCode = newSubjectCode.split("_")[0];
        }
        String newAcadYear = updatedSubject.getAcademicYear() != null ? updatedSubject.getAcademicYear().trim() : subject.getAcademicYear();
        String newId = (newSubjectCode != null && !newSubjectCode.trim().isEmpty()) ? (newSubjectCode.trim() + "_" + newAcadYear) : id;

        if (!newId.equalsIgnoreCase(id)) {
            newId = newId.trim();
            // Check if another subject already exists with the new ID
            if (subjectRepository.existsById(newId)) {
                throw new IllegalArgumentException("Already there is a subject with that subjectcode in the " + newAcadYear + " academic year");
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
                String subjectCode = getCellValueAsString(row.getCell(0)).trim();
                
                String acadYear = "2026-27";
                if (row.getLastCellNum() > 6 && row.getCell(6) != null) {
                    acadYear = getCellValueAsString(row.getCell(6)).trim();
                }
                subject.setAcademicYear(acadYear);
                subject.setId(subjectCode + "_" + acadYear);

                subject.setName(getCellValueAsString(row.getCell(1)));
                subject.setYear(getCellValueAsInt(row.getCell(2)));
                subject.setSem(getCellValueAsInt(row.getCell(3)));
                subject.setDep(getCellValueAsString(row.getCell(4)));
                subject.setRegulation(getCellValueAsString(row.getCell(5)));

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

    private void clearPreferencesInActiveWindow(String facultyId) {
        List<SubjectSelectionWindow> activeWindows = windowRepository.findAll().stream()
                .filter(w -> w.isActive() && LocalDateTime.now().isBefore(w.getDeadline()))
                .collect(Collectors.toList());
        if (!activeWindows.isEmpty()) {
            List<Subject> allSubjects = subjectRepository.findAll();
            java.util.Set<String> subIdsInWindow = new java.util.HashSet<>();
            for (SubjectSelectionWindow window : activeWindows) {
                String currentAcademicYear = window.getAcademicYear() != null ? window.getAcademicYear().trim() : "";
                String filterDepartment = window.getDepartment();
                Integer filterSem = window.getSem();
                Integer filterYear = window.getYear();
                List<String> ids = allSubjects.stream()
                    .filter(s -> (filterSem == null || NumberHelperIsEqual(s.getSem(), filterSem)) &&
                                 (filterDepartment == null || filterDepartment.equalsIgnoreCase(getSubjectDepartmentCode(s))) &&
                                 (currentAcademicYear.isEmpty() || currentAcademicYear.equalsIgnoreCase(s.getAcademicYear())) &&
                                 (filterYear == null || filterYear == 0 || NumberHelperIsEqual(s.getYear(), filterYear)))
                    .map(s -> s.getId().toLowerCase())
                    .collect(Collectors.toList());
                subIdsInWindow.addAll(ids);
            }
            List<FacultySubjectPreference> existing = preferenceRepository.findByFacultyId(facultyId);
            for (FacultySubjectPreference p : existing) {
                if (p.getSubjectId() != null && subIdsInWindow.contains(p.getSubjectId().toLowerCase())) {
                    preferenceRepository.delete(p);
                }
            }
        } else {
            preferenceRepository.deleteByFacultyId(facultyId);
        }
    }

    @Transactional
    public void savePreferences(String facultyId, List<String> subjectIds) {
        // Clear existing preferences first
        clearPreferencesInActiveWindow(facultyId);

        // Save new preferences
        for (String subjectId : subjectIds) {
            FacultySubjectPreference pref = new FacultySubjectPreference(facultyId, subjectId);
            preferenceRepository.save(pref);
        }
    }

    @Transactional
    public void savePreferencesEntity(String facultyId, List<FacultySubjectPreference> preferences) {
        // Clear existing preferences first
        clearPreferencesInActiveWindow(facultyId);

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
    public void setDeadline(
            String message, int days, Integer sem, String academicYear, String department,
            List<Integer> years, Integer hoursPerWeek, Integer maxSubjectsAllocated,
            Integer subjectHoursPerWeek, Integer maxRegularPreferences, Integer maxMockPreferences) {
        
        java.util.Set<Integer> selectedYears = new java.util.HashSet<>();
        if (years != null) {
            selectedYears.addAll(years);
        }

        for (int y = 1; y <= 4; y++) {
            if (selectedYears.contains(y)) {
                SubjectSelectionWindow window = windowRepository.findById(y).orElse(new SubjectSelectionWindow());
                window.setId(y);
                window.setYear(y);
                window.setMessage(message);
                window.setDeadline(LocalDateTime.now().plusDays(days));
                window.setActive(true);
                window.setSem(sem);
                window.setAcademicYear(academicYear);
                window.setDepartment(department);
                window.setHoursPerWeek(hoursPerWeek);
                window.setMaxSubjectsAllocated(maxSubjectsAllocated);
                window.setSubjectHoursPerWeek(subjectHoursPerWeek);
                window.setMaxRegularPreferences(maxRegularPreferences);
                window.setMaxMockPreferences(maxMockPreferences);
                windowRepository.save(window);
            } else {
                SubjectSelectionWindow window = windowRepository.findById(y).orElse(null);
                if (window != null) {
                    window.setActive(false);
                    window.setAcademicYear(null);
                    window.setDepartment(null);
                    window.setSem(null);
                    windowRepository.save(window);
                }
            }
        }
    }

    public SubjectSelectionWindow getActiveDeadline() {
        return windowRepository.findAll().stream()
                .filter(w -> w.isActive() && LocalDateTime.now().isBefore(w.getDeadline()))
                .findFirst()
                .orElse(null);
    }

    public boolean isBeforeDeadline() {
        List<SubjectSelectionWindow> windows = windowRepository.findAll();
        for (SubjectSelectionWindow w : windows) {
            if (w.isActive() && LocalDateTime.now().isBefore(w.getDeadline())) {
                return true;
            }
        }
        return false;
    }

    public boolean isBeforeDeadlineForYear(Integer year) {
        if (year == null) return isBeforeDeadline();
        return windowRepository.findById(year)
                .map(w -> w.isActive() && LocalDateTime.now().isBefore(w.getDeadline()))
                .orElse(false);
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

        Subject subject = subjectRepository.findById(allocation.getSubjectId()).orElse(null);
        if (subject == null) {
            throw new IllegalArgumentException("Subject not found.");
        }

        if (isBeforeDeadlineForYear(subject.getYear())) {
            throw new IllegalStateException("The subject selection window is currently running for Year " + subject.getYear() + ". Please stop it on the Selection Window tab before performing allocations.");
        }

        SubjectAllocation existingAlloc = allocationRepository.findBySubjectIdAndFacultyId(
                allocation.getSubjectId(), allocation.getFacultyId());
        if (existingAlloc != null) {
            throw new IllegalArgumentException("This faculty member is already allocated to this subject.");
        }

        // Validate selection window limits (max subjects and hours)
        SubjectSelectionWindow window = windowRepository.findAll().stream()
                .filter(w -> w.isActive() && Integer.valueOf(subject.getYear()).equals(w.getYear()))
                .findFirst().orElse(null);
        if (window == null) {
            window = windowRepository.findTopByOrderByIdDesc();
        }
        if (window != null) {
            String currentAcademicYear = window.getAcademicYear() != null ? window.getAcademicYear().trim() : "";
            List<SubjectAllocation> facAllocs = getAllocationsByFacultyId(allocation.getFacultyId()).stream()
                    .filter(a -> {
                        Subject s = subjectRepository.findById(a.getSubjectId()).orElse(null);
                        return s != null && s.getAcademicYear() != null && s.getAcademicYear().equalsIgnoreCase(currentAcademicYear);
                    })
                    .collect(Collectors.toList());
            
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
                             s.getDepartmentCode() != null && s.getDepartmentCode().equalsIgnoreCase(subDeptCode) &&
                             (subject.getAcademicYear() == null || s.getAcademicYear() == null || s.getAcademicYear().equalsIgnoreCase(subject.getAcademicYear())))
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

        // Delete corresponding history entries
        List<AllocationHistory> histAllocs = allocationHistoryRepository.findBySubjectId(subjectId).stream()
                .filter(x -> x.getFacultyId() != null && x.getFacultyId().equalsIgnoreCase(facultyId))
                .collect(Collectors.toList());
        allocationHistoryRepository.deleteAll(histAllocs);
        
        return true;
    }

    public List<SubjectAllocation> getAllocationsByFacultyId(String facultyId) {
        if (facultyId == null) return new ArrayList<>();
        String facultyIdUpper = facultyId.trim().toUpperCase();
        
        List<SubjectAllocation> currentAllocs = allocationRepository.findByFacultyId(facultyId);
        List<SubjectAllocation> result = new ArrayList<>(currentAllocs);
        
        // Find existing subject IDs to avoid duplicates
        java.util.Set<String> existingSubjectIds = currentAllocs.stream()
                .map(sa -> sa.getSubjectId().toUpperCase())
                .collect(Collectors.toSet());
                
        // Add historical subject allocations
        List<AllocationHistory> historicalAllocs = allocationHistoryRepository.findByFacultyId(facultyIdUpper);
        if (historicalAllocs != null) {
            for (AllocationHistory hist : historicalAllocs) {
                if (hist.getSubjectId() == null) continue;
                String subIdUpper = hist.getSubjectId().toUpperCase();
                if (!existingSubjectIds.contains(subIdUpper)) {
                    SubjectAllocation sa = new SubjectAllocation(hist.getSubjectId(), facultyId);
                    sa.setFinalized(true);
                    result.add(sa);
                    existingSubjectIds.add(subIdUpper);
                }
            }
        }
        return result;
    }

    @Transactional
    public SectionAllocation allocateSection(SectionAllocation allocation) {
        return allocateSection(allocation, false);
    }

    @Transactional
    public SectionAllocation allocateSection(SectionAllocation allocation, boolean ignoreConstraints) {
        if (allocation.getFacultyId() == null || allocation.getSubjectId() == null || allocation.getSectionName() == null) {
            throw new IllegalArgumentException("Faculty ID, Subject ID, and Section Name are all required.");
        }

        Subject subject = subjectRepository.findById(allocation.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException("Subject not found."));

        if (!ignoreConstraints && isBeforeDeadlineForYear(subject.getYear())) {
            throw new IllegalStateException("The subject selection window is currently running for Year " + subject.getYear() + ". Please stop it on the Selection Window tab before performing allocations.");
        }

        SubjectSelectionWindow window = windowRepository.findAll().stream()
                .filter(w -> w.isActive() && Integer.valueOf(subject.getYear()).equals(w.getYear()))
                .findFirst().orElse(null);
        if (window == null) {
            window = windowRepository.findTopByOrderByIdDesc();
        }
        Integer hoursLimit = (window != null) ? window.getHoursPerWeek() : 14;
        if (hoursLimit == null) hoursLimit = 14;
        Integer subjectHours = (window != null) ? window.getSubjectHoursPerWeek() : 4;
        if (subjectHours == null) subjectHours = 4;

        if (ignoreConstraints) {
            // Find and delete any existing SectionAllocation for this subject + section to avoid duplicates
            SectionAllocation existingSecAlloc = sectionAllocationRepository.findBySubjectIdAndSectionName(
                    allocation.getSubjectId(), allocation.getSectionName());
            if (existingSecAlloc != null) {
                String prevFac = existingSecAlloc.getFacultyId();
                sectionAllocationRepository.delete(existingSecAlloc);
                // Check if the previous owner has other sections for this subject. If not, delete their SubjectAllocation
                List<SectionAllocation> remaining = sectionAllocationRepository.findBySubjectId(allocation.getSubjectId()).stream()
                        .filter(x -> x.getFacultyId().equalsIgnoreCase(prevFac) && !x.getId().equals(existingSecAlloc.getId()))
                        .collect(Collectors.toList());
                if (remaining.isEmpty()) {
                    SubjectAllocation subAlloc = allocationRepository.findBySubjectIdAndFacultyId(allocation.getSubjectId(), prevFac);
                    if (subAlloc != null) {
                        allocationRepository.delete(subAlloc);
                    }
                }
            }

            // Ensure SubjectAllocation exists for target faculty
            SubjectAllocation existingSubAlloc = allocationRepository.findBySubjectIdAndFacultyId(allocation.getSubjectId(), allocation.getFacultyId());
            if (existingSubAlloc == null) {
                SubjectAllocation newSubAlloc = new SubjectAllocation(allocation.getSubjectId(), allocation.getFacultyId());
                newSubAlloc.setFinalized(false);
                allocationRepository.save(newSubAlloc);
            }

            allocation.setFinalized(false);
            return sectionAllocationRepository.save(allocation);
        }

        // Validate section not already allocated to a DIFFERENT faculty member
        SectionAllocation existingSecAlloc = sectionAllocationRepository.findBySubjectIdAndSectionName(
                allocation.getSubjectId(), allocation.getSectionName());
        if (existingSecAlloc != null) {
            if (!existingSecAlloc.getFacultyId().equalsIgnoreCase(allocation.getFacultyId())) {
                throw new IllegalArgumentException("Section " + allocation.getSectionName() + " of this subject is already allocated to " + existingSecAlloc.getFacultyId() + ".");
            }
            return existingSecAlloc;
        }

        // Validate workload limit
        String currentAcademicYear = (window != null && window.getAcademicYear() != null) ? window.getAcademicYear().trim() : "";
        List<SectionAllocation> facSecAllocs = getSectionAllocationsByFacultyId(allocation.getFacultyId()).stream()
                .filter(sa -> {
                    Subject s = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                    return s != null && s.getAcademicYear() != null && s.getAcademicYear().equalsIgnoreCase(currentAcademicYear);
                })
                .collect(Collectors.toList());
        int currentHours = facSecAllocs.size() * subjectHours;
        if (currentHours + subjectHours > hoursLimit) {
            throw new IllegalArgumentException("Maximum weekly workload hours reached (" + hoursLimit + " hours) for this faculty member. Current: " + currentHours + " hrs, Requested: " + subjectHours + " hrs.");
        }

        // Validate subject count and mock/regular rules
        List<SubjectAllocation> facAllocs = getAllocationsByFacultyId(allocation.getFacultyId()).stream()
                .filter(a -> {
                    Subject s = subjectRepository.findById(a.getSubjectId()).orElse(null);
                    return s != null && s.getAcademicYear() != null && s.getAcademicYear().equalsIgnoreCase(currentAcademicYear);
                })
                .collect(Collectors.toList());
        boolean alreadyHasSubject = facAllocs.stream().anyMatch(a -> a.getSubjectId().equalsIgnoreCase(allocation.getSubjectId()));

        if (!alreadyHasSubject) {
            List<Integer> allocatedYears = new ArrayList<>();
            for (SubjectAllocation sa : facAllocs) {
                Subject s = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                if (s != null && s.getAcademicYear() != null && s.getAcademicYear().equalsIgnoreCase(currentAcademicYear)) {
                    allocatedYears.add(s.getYear());
                }
            }
            allocatedYears.add(subject.getYear());

            if (allocatedYears.size() >= 3) {
                long distinctYearsCount = allocatedYears.stream().distinct().count();
                if (distinctYearsCount == 1) {
                    throw new IllegalArgumentException("Faculty member cannot be allocated all subjects from the same study year.");
                }
            }

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
                if (hasPreviousMockAllocation(allocation.getFacultyId(), subject.getAcademicYear())) {
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
        }

        // Ensure SubjectAllocation exists
        SubjectAllocation existingSubAlloc = allocationRepository.findBySubjectIdAndFacultyId(allocation.getSubjectId(), allocation.getFacultyId());
        if (existingSubAlloc == null) {
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
            AllocationHistory ah = allocationHistoryRepository.findById(id).orElse(null);
            if (ah != null) {
                String fId = ah.getFacultyId();
                String sId = ah.getSubjectId();
                String sec = ah.getSectionName();
                allocationHistoryRepository.delete(ah);

                // Also delete any matching SectionAllocation
                List<SectionAllocation> matchingSec = sectionAllocationRepository.findAll().stream()
                        .filter(x -> x.getSubjectId().equalsIgnoreCase(sId) &&
                                     x.getSectionName().equalsIgnoreCase(sec) &&
                                     x.getFacultyId().equalsIgnoreCase(fId))
                        .collect(Collectors.toList());
                sectionAllocationRepository.deleteAll(matchingSec);

                // Check remaining
                List<SectionAllocation> remaining = sectionAllocationRepository.findBySubjectId(sId).stream()
                        .filter(x -> x.getFacultyId().equalsIgnoreCase(fId))
                        .collect(Collectors.toList());
                if (remaining.isEmpty()) {
                    SubjectAllocation subAlloc = allocationRepository.findBySubjectIdAndFacultyId(sId, fId);
                    if (subAlloc != null) {
                        allocationRepository.delete(subAlloc);
                    }
                }
                return true;
            }
            return false;
        }
        String facultyId = sa.getFacultyId();
        String subjectId = sa.getSubjectId();
        String sectionName = sa.getSectionName();

        sectionAllocationRepository.delete(sa);

        // Delete corresponding history entry
        List<AllocationHistory> histAllocs = allocationHistoryRepository.findBySubjectId(subjectId).stream()
                .filter(x -> x.getFacultyId() != null && x.getFacultyId().equalsIgnoreCase(facultyId) &&
                             x.getSectionName() != null && x.getSectionName().equalsIgnoreCase(sectionName))
                .collect(Collectors.toList());
        allocationHistoryRepository.deleteAll(histAllocs);

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
    public void reassignSectionAllocation(String subjectId, String sectionName, String fromFacultyId, String toFacultyId) {
        reassignSectionAllocation(subjectId, sectionName, fromFacultyId, toFacultyId, false);
    }

    @Transactional
    public void reassignSectionAllocation(String subjectId, String sectionName, String fromFacultyId, String toFacultyId, boolean ignoreConstraints) {
        // Find the existing SectionAllocation or AllocationHistory matching subjectId, sectionName, fromFacultyId
        SectionAllocation sa = sectionAllocationRepository.findAll().stream()
                .filter(x -> x.getSubjectId().equalsIgnoreCase(subjectId) &&
                             x.getSectionName().equalsIgnoreCase(sectionName) &&
                             x.getFacultyId().equalsIgnoreCase(fromFacultyId))
                .findFirst().orElse(null);
        
        boolean wasFinalized = false;
        if (sa != null) {
            wasFinalized = sa.isFinalized();
            deleteSectionAllocation(sa.getId());
        } else {
            List<AllocationHistory> histList = allocationHistoryRepository.findAll().stream()
                    .filter(x -> x.getSubjectId().equalsIgnoreCase(subjectId) &&
                                 x.getSectionName().equalsIgnoreCase(sectionName) &&
                                 x.getFacultyId().equalsIgnoreCase(fromFacultyId))
                    .collect(Collectors.toList());
            if (!histList.isEmpty()) {
                wasFinalized = true;
                allocationHistoryRepository.deleteAll(histList);
            }
        }
        
        // Allocate to the new faculty
        try {
            SectionAllocation newAlloc = new SectionAllocation(subjectId, sectionName, toFacultyId);
            newAlloc.setFinalized(wasFinalized);
            allocateSection(newAlloc, ignoreConstraints);

            if (wasFinalized) {
                SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();
                Subject sub = subjectRepository.findById(subjectId).orElse(null);
                String acadYear = sub != null && sub.getAcademicYear() != null ? sub.getAcademicYear() : (window != null && window.getAcademicYear() != null ? window.getAcademicYear() : "2026-27");
                String dept = sub != null && sub.getDep() != null ? sub.getDep() : (window != null && window.getDepartment() != null ? window.getDepartment() : "CSE");
                Integer sem = sub != null && sub.getSem() > 0 ? sub.getSem() : (window != null ? window.getSem() : 1);

                AllocationHistory newHist = new AllocationHistory(acadYear, dept, sem, toFacultyId, subjectId, sectionName);
                allocationHistoryRepository.save(newHist);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    @Transactional
    public void assignUnknownToFaculty(String unknownFacultyId, String newFacultyId) {
        String unknownIdUpper = unknownFacultyId.trim().toUpperCase();
        String newIdUpper = newFacultyId.trim().toUpperCase();

        List<SectionAllocation> secAllocs = sectionAllocationRepository.findByFacultyId(unknownFacultyId);
        List<SubjectAllocation> subAllocs = allocationRepository.findByFacultyId(unknownFacultyId);

        for (SectionAllocation saItem : secAllocs) {
            saItem.setFacultyId(newIdUpper);
            sectionAllocationRepository.save(saItem);
        }

        for (SubjectAllocation saItem : subAllocs) {
            SubjectAllocation existing = allocationRepository.findBySubjectIdAndFacultyId(saItem.getSubjectId(), newIdUpper);
            if (existing == null) {
                saItem.setFacultyId(newIdUpper);
                allocationRepository.save(saItem);
            } else {
                allocationRepository.delete(saItem);
            }
        }

        // Also update AllocationHistory for this unknown faculty
        List<AllocationHistory> histAllocs = allocationHistoryRepository.findAll().stream()
                .filter(h -> h.getFacultyId() != null && h.getFacultyId().equalsIgnoreCase(unknownFacultyId))
                .collect(Collectors.toList());
        for (AllocationHistory h : histAllocs) {
            h.setFacultyId(newIdUpper);
            allocationHistoryRepository.save(h);
        }
    }

    @Transactional
    public void swapAllocationsForSubjects(String facultyId1, String subjectId1, String sectionName1, String subjectId2, String sectionName2) {
        SectionAllocation sa1 = sectionAllocationRepository.findBySubjectIdAndSectionName(subjectId1, sectionName1);
        if (sa1 == null) {
            throw new IllegalArgumentException("Source allocation not found.");
        }
        if (!sa1.getFacultyId().equalsIgnoreCase(facultyId1)) {
            throw new IllegalArgumentException("Faculty member does not hold the source allocation.");
        }

        SectionAllocation sa2 = sectionAllocationRepository.findBySubjectIdAndSectionName(subjectId2, sectionName2);
        String facultyId2 = sa2 != null ? sa2.getFacultyId() : null;

        if (facultyId2 == null) {
            reassignSectionAllocation(subjectId1, sectionName1, facultyId1, "UNKNOWN_1");
            try {
                SectionAllocation targetAlloc = sectionAllocationRepository.findBySubjectIdAndSectionName(subjectId2, sectionName2);
                if (targetAlloc == null) {
                    targetAlloc = new SectionAllocation(subjectId2, sectionName2, facultyId1);
                } else {
                    targetAlloc.setFacultyId(facultyId1);
                }
                targetAlloc.setFinalized(sa1.isFinalized());
                allocateSection(targetAlloc);
            } catch (Exception e) {
                throw new IllegalArgumentException(e.getMessage());
            }
            return;
        }

        if (facultyId1.equalsIgnoreCase(facultyId2)) {
            return;
        }

        boolean wasFinalized = sa1.isFinalized() || sa2.isFinalized();
        deleteSectionAllocation(sa1.getId());
        deleteSectionAllocation(sa2.getId());

        try {
            SectionAllocation newAlloc1 = new SectionAllocation(subjectId1, sectionName1, facultyId2);
            newAlloc1.setFinalized(wasFinalized);
            allocateSection(newAlloc1, true);

            SectionAllocation newAlloc2 = new SectionAllocation(subjectId2, sectionName2, facultyId1);
            newAlloc2.setFinalized(wasFinalized);
            allocateSection(newAlloc2, true);

            if (wasFinalized) {
                SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();
                Subject sub1 = subjectRepository.findById(subjectId1).orElse(null);
                Subject sub2 = subjectRepository.findById(subjectId2).orElse(null);
                String acadYear1 = sub1 != null && sub1.getAcademicYear() != null ? sub1.getAcademicYear() : (window != null && window.getAcademicYear() != null ? window.getAcademicYear() : "2026-27");
                String dept1 = sub1 != null && sub1.getDep() != null ? sub1.getDep() : (window != null && window.getDepartment() != null ? window.getDepartment() : "CSE");
                Integer sem1 = sub1 != null && sub1.getSem() > 0 ? sub1.getSem() : (window != null ? window.getSem() : 1);

                String acadYear2 = sub2 != null && sub2.getAcademicYear() != null ? sub2.getAcademicYear() : (window != null && window.getAcademicYear() != null ? window.getAcademicYear() : "2026-27");
                String dept2 = sub2 != null && sub2.getDep() != null ? sub2.getDep() : (window != null && window.getDepartment() != null ? window.getDepartment() : "CSE");
                Integer sem2 = sub2 != null && sub2.getSem() > 0 ? sub2.getSem() : (window != null ? window.getSem() : 1);

                allocationHistoryRepository.save(new AllocationHistory(acadYear1, dept1, sem1, facultyId2, subjectId1, sectionName1));
                allocationHistoryRepository.save(new AllocationHistory(acadYear2, dept2, sem2, facultyId1, subjectId2, sectionName2));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public List<AdminFaculty> getEligibleFacultyForReassignment(String subjectId, String sectionName) {
        List<AdminFaculty> allFaculty = adminRepository.findAll().stream()
                .filter(u -> "faculty".equalsIgnoreCase(u.getRole()) || 
                               ("ADMIN".equalsIgnoreCase(u.getRole()) && !"ADMIN01".equalsIgnoreCase(u.getId())))
                .collect(Collectors.toList());
        
        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        if (subject == null) {
            return new ArrayList<>();
        }
        
        SubjectSelectionWindow window = windowRepository.findAll().stream()
                .filter(w -> w.isActive() && Integer.valueOf(subject.getYear()).equals(w.getYear()))
                .findFirst().orElse(null);
        if (window == null) {
            window = windowRepository.findTopByOrderByIdDesc();
        }
        Integer hoursLimit = (window != null) ? window.getHoursPerWeek() : 14;
        if (hoursLimit == null) hoursLimit = 14;
        Integer subjectHours = (window != null) ? window.getSubjectHoursPerWeek() : 4;
        if (subjectHours == null) subjectHours = 4;
        
        String currentAcademicYear = (window != null && window.getAcademicYear() != null) ? window.getAcademicYear().trim() : "";
        
        List<AdminFaculty> eligible = new ArrayList<>();
        for (AdminFaculty f : allFaculty) {
            String fId = f.getId();
            
            // Check if they are already teaching this subject/section
            SectionAllocation existing = sectionAllocationRepository.findBySubjectIdAndSectionName(subjectId, sectionName);
            if (existing != null && existing.getFacultyId().equalsIgnoreCase(fId)) {
                // Already teaching this section
                continue;
            }
            
            // Validate workload limit
            List<SectionAllocation> facSecAllocs = getSectionAllocationsByFacultyId(fId).stream()
                    .filter(sa -> {
                        Subject s = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                        return s != null && s.getAcademicYear() != null && s.getAcademicYear().equalsIgnoreCase(currentAcademicYear);
                    })
                    .collect(Collectors.toList());
            int currentHours = facSecAllocs.size() * subjectHours;
            if (currentHours + subjectHours > hoursLimit) {
                continue;
            }
            
            // Validate subject count and mock/regular rules
            List<SubjectAllocation> facAllocs = getAllocationsByFacultyId(fId).stream()
                    .filter(a -> {
                        Subject s = subjectRepository.findById(a.getSubjectId()).orElse(null);
                        return s != null && s.getAcademicYear() != null && s.getAcademicYear().equalsIgnoreCase(currentAcademicYear);
                    })
                    .collect(Collectors.toList());
            boolean alreadyHasSubject = facAllocs.stream().anyMatch(a -> a.getSubjectId().equalsIgnoreCase(subjectId));
            
            if (!alreadyHasSubject) {
                List<Integer> allocatedYears = new ArrayList<>();
                for (SubjectAllocation sa : facAllocs) {
                    Subject s = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                    if (s != null && s.getAcademicYear() != null && s.getAcademicYear().equalsIgnoreCase(currentAcademicYear)) {
                        allocatedYears.add(s.getYear());
                    }
                }
                allocatedYears.add(subject.getYear());
                
                if (allocatedYears.size() >= 3) {
                    long distinctYearsCount = allocatedYears.stream().distinct().count();
                    if (distinctYearsCount == 1) {
                        continue; // Same year rule violation
                    }
                }
                
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
                    if (hasPreviousMockAllocation(fId, subject.getAcademicYear())) {
                        continue; // Mock rules violation
                    }
                    if (regularCount != 2) {
                        continue;
                    }
                    if (mockCount >= 1) {
                        continue;
                    }
                } else {
                    if (regularCount >= 2) {
                        continue;
                    }
                }
            }
            
            eligible.add(f);
        }
        return eligible;
    }

    @Transactional
    public List<SubjectAllocation> autoAllocateForYear(Integer targetYear, Integer hoursLimit, Integer subjectHours, Integer maxSubjects, Integer maxRegular, Integer maxMock, String academicYear, String department, Integer sem) {
        return autoAllocateSubjects(hoursLimit, subjectHours, maxSubjects, maxRegular, maxMock, academicYear, department, sem, java.util.Collections.singletonList(targetYear));
    }

    // Allocation Explanation Data Structure
    public static class AllocationExplanation {
        private String subjectId;
        private String subjectName;
        private int subjectYear;
        private String sectionName;
        private String facultyId;
        private String facultyName;
        private String preference;
        private int hoursBefore;
        private int hoursAfter;
        private String reasonSelected;
        private boolean isUnknown;
        private List<FacultyRejectionDetail> rejectionDetails = new ArrayList<>();

        public AllocationExplanation() {}

        public String getSubjectId() { return subjectId; }
        public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
        public String getSubjectName() { return subjectName; }
        public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
        public int getSubjectYear() { return subjectYear; }
        public void setSubjectYear(int subjectYear) { this.subjectYear = subjectYear; }
        public String getSectionName() { return sectionName; }
        public void setSectionName(String sectionName) { this.sectionName = sectionName; }
        public String getFacultyId() { return facultyId; }
        public void setFacultyId(String facultyId) { this.facultyId = facultyId; }
        public String getFacultyName() { return facultyName; }
        public void setFacultyName(String facultyName) { this.facultyName = facultyName; }
        public String getPreference() { return preference; }
        public void setPreference(String preference) { this.preference = preference; }
        public int getHoursBefore() { return hoursBefore; }
        public void setHoursBefore(int hoursBefore) { this.hoursBefore = hoursBefore; }
        public int getHoursAfter() { return hoursAfter; }
        public void setHoursAfter(int hoursAfter) { this.hoursAfter = hoursAfter; }
        public String getReasonSelected() { return reasonSelected; }
        public void setReasonSelected(String reasonSelected) { this.reasonSelected = reasonSelected; }
        public boolean isUnknown() { return isUnknown; }
        public void setUnknown(boolean unknown) { isUnknown = unknown; }
        public List<FacultyRejectionDetail> getRejectionDetails() { return rejectionDetails; }
        public void setRejectionDetails(List<FacultyRejectionDetail> rejectionDetails) { this.rejectionDetails = rejectionDetails; }
    }

    public static class FacultyRejectionDetail {
        private String facultyId;
        private String facultyName;
        private String preference;
        private int currentWorkload;
        private String reasonRejected;

        public FacultyRejectionDetail(String facultyId, String facultyName, String preference, int currentWorkload, String reasonRejected) {
            this.facultyId = facultyId;
            this.facultyName = facultyName;
            this.preference = preference;
            this.currentWorkload = currentWorkload;
            this.reasonRejected = reasonRejected;
        }

        public String getFacultyId() { return facultyId; }
        public String getFacultyName() { return facultyName; }
        public String getPreference() { return preference; }
        public int getCurrentWorkload() { return currentWorkload; }
        public String getReasonRejected() { return reasonRejected; }
    }

    private List<AllocationExplanation> latestAllocationExplanations = new ArrayList<>();

    public List<AllocationExplanation> getLatestAllocationExplanations() {
        return new ArrayList<>(latestAllocationExplanations);
    }

    @Transactional
    public List<SubjectAllocation> autoAllocateSubjects(Integer hoursLimit, Integer subjectHours, Integer maxSubjects, Integer maxRegular, Integer maxMock, String academicYear, String department, Integer sem, List<Integer> years) {
        SubjectSelectionWindow refWindow = windowRepository.findAll().stream()
                .filter(w -> w.getAcademicYear() != null && !w.getAcademicYear().trim().isEmpty())
                .max((w1, w2) -> {
                    if (w1.getDeadline() == null && w2.getDeadline() == null) return 0;
                    if (w1.getDeadline() == null) return -1;
                    if (w2.getDeadline() == null) return 1;
                    return w1.getDeadline().compareTo(w2.getDeadline());
                })
                .orElse(windowRepository.findTopByOrderByIdDesc());

        String currentAcademicYear = (academicYear != null && !academicYear.trim().isEmpty()) ? academicYear.trim() : ((refWindow != null && refWindow.getAcademicYear() != null) ? refWindow.getAcademicYear().trim() : "2026-27");
        String filterDepartment = (department != null && !department.trim().isEmpty()) ? department.trim() : (refWindow != null && refWindow.getDepartment() != null ? refWindow.getDepartment() : "CSE");
        Integer filterSem = (sem != null) ? sem : (refWindow != null && refWindow.getSem() != null ? refWindow.getSem() : 1);

        // Update active selection windows with parameters if provided
        for (int y = 1; y <= 4; y++) {
            windowRepository.findById(y).ifPresent(w -> {
                if (w.isActive()) {
                    if (hoursLimit != null) w.setHoursPerWeek(hoursLimit);
                    if (subjectHours != null) w.setSubjectHoursPerWeek(subjectHours);
                    if (maxSubjects != null) w.setMaxSubjectsAllocated(maxSubjects);
                    windowRepository.save(w);
                }
            });
        }

        final List<Integer> activeYears;
        if (years != null && !years.isEmpty()) {
            activeYears = new ArrayList<>(years);
        } else {
            List<Integer> computedYears = windowRepository.findAll().stream()
                    .filter(w -> w.getAcademicYear() != null && w.getAcademicYear().equalsIgnoreCase(currentAcademicYear) &&
                                 (filterDepartment == null || w.getDepartment() == null || w.getDepartment().equalsIgnoreCase(filterDepartment)) &&
                                 (filterSem == null || w.getSem() == null || w.getSem().equals(filterSem)))
                    .map(SubjectSelectionWindow::getYear)
                    .filter(java.util.Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());

            if (computedYears.isEmpty()) {
                activeYears = java.util.Arrays.asList(1, 2, 3, 4);
            } else {
                activeYears = computedYears;
            }
        }

        // Auto-stop any running selection windows for target years so allocation proceeds cleanly
        for (Integer targetYear : activeYears) {
            stopDeadline(targetYear);
        }

        List<Subject> allSubjects = subjectRepository.findAll();
        allSubjects.sort(java.util.Comparator.comparing(Subject::getId));
        if (allSubjects.isEmpty()) {
            throw new IllegalStateException("No subjects found in the Subject Directory. Please add or import subjects first.");
        }

        // Filter all target subjects across all active years (GLOBAL scope)
        List<Subject> candidateSubjects = allSubjects.stream()
            .filter(s -> (filterSem == null || filterSem <= 0 || NumberHelperIsEqual(s.getSem(), filterSem)) &&
                         (filterDepartment == null || filterDepartment.trim().isEmpty() || filterDepartment.equalsIgnoreCase("ALL") || filterDepartment.equalsIgnoreCase(getSubjectDepartmentCode(s)) || (s.getDep() != null && s.getDep().equalsIgnoreCase(filterDepartment))) &&
                         (currentAcademicYear.isEmpty() || currentAcademicYear.equalsIgnoreCase(s.getAcademicYear())) &&
                         (activeYears.isEmpty() || activeYears.contains(s.getYear())))
            .sorted(java.util.Comparator.comparing(Subject::getYear).thenComparing(Subject::getId))
            .collect(Collectors.toList());

        // Progressive fallback 1: Match by academic year and active years
        if (candidateSubjects.isEmpty()) {
            candidateSubjects = allSubjects.stream()
                .filter(s -> (currentAcademicYear.isEmpty() || currentAcademicYear.equalsIgnoreCase(s.getAcademicYear())) &&
                             (activeYears.isEmpty() || activeYears.contains(s.getYear())))
                .sorted(java.util.Comparator.comparing(Subject::getYear).thenComparing(Subject::getId))
                .collect(Collectors.toList());
        }

        // Progressive fallback 2: Use all subjects
        if (candidateSubjects.isEmpty()) {
            candidateSubjects = new ArrayList<>(allSubjects);
        }

        final List<Subject> subjects = candidateSubjects;

        // Clean out existing allocations and history for all target subjects
        for (Subject sub : subjects) {
            List<SubjectAllocation> existingSubAllocs = allocationRepository.findBySubjectId(sub.getId());
            allocationRepository.deleteAll(existingSubAllocs);

            List<SectionAllocation> existingSecAllocs = sectionAllocationRepository.findBySubjectId(sub.getId());
            sectionAllocationRepository.deleteAll(existingSecAllocs);

            List<AllocationHistory> existingHistory = allocationHistoryRepository.findBySubjectId(sub.getId());
            allocationHistoryRepository.deleteAll(existingHistory);
        }

        allocationRepository.flush();
        sectionAllocationRepository.flush();
        allocationHistoryRepository.flush();

        int subHours = (subjectHours != null) ? subjectHours : ((refWindow != null && refWindow.getSubjectHoursPerWeek() != null) ? refWindow.getSubjectHoursPerWeek() : 3);
        int hoursLimitVal = (hoursLimit != null) ? hoursLimit : ((refWindow != null && refWindow.getHoursPerWeek() != null) ? refWindow.getHoursPerWeek() : 18);
        int maxSubsVal = (maxSubjects != null) ? maxSubjects : ((refWindow != null && refWindow.getMaxSubjectsAllocated() != null) ? refWindow.getMaxSubjectsAllocated() : 3);
        int maxRegVal = (maxRegular != null) ? maxRegular : ((refWindow != null && refWindow.getMaxRegularPreferences() != null) ? refWindow.getMaxRegularPreferences() : 2);
        int maxMockVal = (maxMock != null) ? maxMock : ((refWindow != null && refWindow.getMaxMockPreferences() != null) ? refWindow.getMaxMockPreferences() : 1);

        // Global faculty workload and subject tracking
        java.util.Map<String, java.util.Set<String>> facultyUniqueSubjects = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.Set<String>> facultyUniqueSections = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.Set<String>> facultyRegularSubjects = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.Set<String>> facultyMockSubjects = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> facultyWorkloadHours = new java.util.LinkedHashMap<>();

        java.util.Set<String> targetSubjectIdsSet = subjects.stream()
                .map(s -> s.getId().toUpperCase())
                .collect(Collectors.toSet());

        List<AdminFaculty> allFaculty = adminRepository.findAll().stream()
                .filter(u -> "faculty".equalsIgnoreCase(u.getRole()) || 
                               ("ADMIN".equalsIgnoreCase(u.getRole()) && !"ADMIN01".equalsIgnoreCase(u.getId())))
                .collect(Collectors.toList());
        allFaculty.sort(java.util.Comparator.comparing(AdminFaculty::getId));

        if (allFaculty.isEmpty()) {
            throw new IllegalStateException("No faculty members found to allocate subjects to.");
        }

        java.util.Map<String, AdminFaculty> facultyMap = new java.util.HashMap<>();
        for (AdminFaculty f : allFaculty) {
            String facIdUpper = f.getId().toUpperCase();
            facultyMap.put(facIdUpper, f);
            facultyUniqueSubjects.put(facIdUpper, new java.util.LinkedHashSet<>());
            facultyUniqueSections.put(facIdUpper, new java.util.LinkedHashSet<>());
            facultyRegularSubjects.put(facIdUpper, new java.util.LinkedHashSet<>());
            facultyMockSubjects.put(facIdUpper, new java.util.LinkedHashSet<>());
            facultyWorkloadHours.put(facIdUpper, 0);
        }

        // Account for pre-existing allocations in other semesters for the same academic year
        for (SubjectAllocation alloc : allocationRepository.findAll()) {
            if (alloc.getFacultyId() == null || alloc.getSubjectId() == null) continue;
            String facIdUpper = alloc.getFacultyId().toUpperCase();
            String subIdUpper = alloc.getSubjectId().toUpperCase();
            if (!targetSubjectIdsSet.contains(subIdUpper)) {
                Subject s = subjectRepository.findById(alloc.getSubjectId()).orElse(null);
                if (s != null && s.getAcademicYear() != null && s.getAcademicYear().equalsIgnoreCase(currentAcademicYear)) {
                    facultyUniqueSubjects.computeIfAbsent(facIdUpper, k -> new java.util.LinkedHashSet<>()).add(subIdUpper);
                    if (s.isMock()) {
                        facultyMockSubjects.computeIfAbsent(facIdUpper, k -> new java.util.LinkedHashSet<>()).add(subIdUpper);
                    } else {
                        facultyRegularSubjects.computeIfAbsent(facIdUpper, k -> new java.util.LinkedHashSet<>()).add(subIdUpper);
                    }
                }
            }
        }

        for (SectionAllocation sa : sectionAllocationRepository.findAll()) {
            if (sa.getFacultyId() == null || sa.getSubjectId() == null || sa.getSectionName() == null) continue;
            String facIdUpper = sa.getFacultyId().toUpperCase();
            String subIdUpper = sa.getSubjectId().toUpperCase();
            String secKey = subIdUpper + "_" + sa.getSectionName().toUpperCase();
            if (!targetSubjectIdsSet.contains(subIdUpper)) {
                facultyUniqueSections.computeIfAbsent(facIdUpper, k -> new java.util.LinkedHashSet<>()).add(secKey);
                facultyWorkloadHours.put(facIdUpper, facultyWorkloadHours.getOrDefault(facIdUpper, 0) + subHours);
            }
        }

        // Build list of all section allocation slots across target subjects
        class SectionSlot {
            Subject subject;
            Section section;
            String sectionName;
            SectionSlot(Subject subject, Section section, String sectionName) {
                this.subject = subject;
                this.section = section;
                this.sectionName = sectionName;
            }
        }

        List<SectionSlot> pendingSlots = new ArrayList<>();
        for (Subject sub : subjects) {
            final int subYear = sub.getYear();
            final String subDep = getSubjectDepartmentCode(sub);

            List<Section> sections = sectionRepository.findAll().stream()
                    .filter(sec -> NumberHelperIsEqual(sec.getYearNumber(), subYear) &&
                                   (filterDepartment == null || filterDepartment.trim().isEmpty() || filterDepartment.equalsIgnoreCase("ALL") || filterDepartment.equalsIgnoreCase(sec.getDepartmentCode())) &&
                                   (currentAcademicYear.isEmpty() || currentAcademicYear.equalsIgnoreCase(sec.getAcademicYear())))
                    .sorted(java.util.Comparator.comparing(Section::getId))
                    .collect(Collectors.toList());

            if (sections.isEmpty()) {
                SectionAllocation finalizedAlloc = sectionAllocationRepository.findBySubjectIdAndSectionName(sub.getId(), "A");
                if (finalizedAlloc == null || !finalizedAlloc.isFinalized()) {
                    pendingSlots.add(new SectionSlot(sub, null, "A"));
                }
            } else {
                for (Section sec : sections) {
                    SectionAllocation finalizedAlloc = sectionAllocationRepository.findBySubjectIdAndSectionName(sub.getId(), sec.getSectionName());
                    if (finalizedAlloc == null || !finalizedAlloc.isFinalized()) {
                        pendingSlots.add(new SectionSlot(sub, sec, sec.getSectionName()));
                    }
                }
            }
        }

        // Count initial total sections configured per subject across all years
        java.util.Map<String, Integer> totalSectionsPerSubject = new java.util.HashMap<>();
        for (SectionSlot slot : pendingSlots) {
            totalSectionsPerSubject.merge(slot.subject.getId().toUpperCase(), 1, Integer::sum);
        }

        // Pre-calculate past relevant experience per faculty per subject from AllocationHistory
        java.util.Map<String, java.util.Map<String, Integer>> facultyExperienceMap = new java.util.HashMap<>();
        for (AllocationHistory h : allocationHistoryRepository.findAll()) {
            if (h.getFacultyId() != null && h.getSubjectId() != null) {
                facultyExperienceMap
                    .computeIfAbsent(h.getFacultyId().toUpperCase(), k -> new java.util.HashMap<>())
                    .merge(h.getSubjectId().toUpperCase(), 1, Integer::sum);
            }
        }

        // Deterministic sorting of section slots: Year -> Subject ID -> Section Name
        pendingSlots.sort((s1, s2) -> {
            int yComp = Integer.compare(s1.subject.getYear(), s2.subject.getYear());
            if (yComp != 0) return yComp;
            int subComp = s1.subject.getId().compareTo(s2.subject.getId());
            if (subComp != 0) return subComp;
            return s1.sectionName.compareTo(s2.sectionName);
        });

        // Load and index faculty preferences: Preference belongs to exact subject (irrespective of year)
        class FacultyPrefEntry {
            String facultyId;
            String subjectId;
            int preferenceNumber;
            boolean isMock;
            Long preferenceDbId;
            FacultyPrefEntry(String facultyId, String subjectId, int preferenceNumber, boolean isMock, Long preferenceDbId) {
                this.facultyId = facultyId;
                this.subjectId = subjectId;
                this.preferenceNumber = preferenceNumber;
                this.isMock = isMock;
                this.preferenceDbId = preferenceDbId;
            }
        }

        // Map of facultyId -> (subjectId -> preference rank)
        java.util.Map<String, java.util.Map<String, Integer>> facultyPrefRankMap = new java.util.HashMap<>();
        java.util.Map<String, Long> facultySubmissionOrderMap = new java.util.HashMap<>();
        java.util.Map<Integer, List<FacultyPrefEntry>> regularPreferenceRoundsMap = new java.util.TreeMap<>();
        java.util.Map<Integer, List<FacultyPrefEntry>> mockPreferenceRoundsMap = new java.util.TreeMap<>();
        int maxRegPrefRound = 0;
        int maxMockPrefRound = 0;

        for (AdminFaculty f : allFaculty) {
            String facId = f.getId();
            String facIdUpper = facId.toUpperCase();
            List<FacultySubjectPreference> facPrefs = preferenceRepository.findByFacultyIdOrderByIdAsc(facId);

            Long minPrefId = Long.MAX_VALUE;
            for (FacultySubjectPreference p : facPrefs) {
                if (p.getId() != null && p.getId() < minPrefId) {
                    minPrefId = p.getId();
                }
            }
            facultySubmissionOrderMap.put(facIdUpper, minPrefId);

            List<FacultySubjectPreference> regList = new ArrayList<>();
            List<FacultySubjectPreference> mockList = new ArrayList<>();
            for (FacultySubjectPreference p : facPrefs) {
                if (p.isMock()) {
                    mockList.add(p);
                } else {
                    regList.add(p);
                }
            }

            int maxRegLimit = (refWindow != null && refWindow.getMaxRegularPreferences() != null) ? refWindow.getMaxRegularPreferences() : 5;
            int maxMockLimit = (refWindow != null && refWindow.getMaxMockPreferences() != null) ? refWindow.getMaxMockPreferences() : 2;

            if (regList.size() > maxRegLimit) regList = regList.subList(0, maxRegLimit);
            if (mockList.size() > maxMockLimit) mockList = mockList.subList(0, maxMockLimit);

            java.util.Map<String, Integer> prefRankSubMap = new java.util.HashMap<>();

            // 1-based preference rounds for regular subjects
            for (int i = 0; i < regList.size(); i++) {
                int pNum = i + 1;
                FacultySubjectPreference pObj = regList.get(i);
                String subId = pObj.getSubjectId();
                if (subId != null) {
                    prefRankSubMap.put(subId.toUpperCase(), pNum);
                    FacultyPrefEntry entry = new FacultyPrefEntry(facId, subId, pNum, false, pObj.getId());
                    regularPreferenceRoundsMap.computeIfAbsent(pNum, k -> new ArrayList<>()).add(entry);
                    if (pNum > maxRegPrefRound) maxRegPrefRound = pNum;
                }
            }

            // Preference rounds for mock subjects
            for (int i = 0; i < mockList.size(); i++) {
                int pNum = i + 1;
                FacultySubjectPreference pObj = mockList.get(i);
                String subId = pObj.getSubjectId();
                if (subId != null) {
                    prefRankSubMap.put(subId.toUpperCase(), pNum);
                    FacultyPrefEntry entry = new FacultyPrefEntry(facId, subId, pNum, true, pObj.getId());
                    mockPreferenceRoundsMap.computeIfAbsent(pNum, k -> new ArrayList<>()).add(entry);
                    if (pNum > maxMockPrefRound) maxMockPrefRound = pNum;
                }
            }

            facultyPrefRankMap.put(facIdUpper, prefRankSubMap);
        }

        // Hard constraints validator function with detailed rejection reason reporting
        java.util.function.BiFunction<String, Subject, String> getRejectionReason = (fId, sub) -> {
            String facIdUpper = fId.toUpperCase();
            String subIdUpper = sub.getId().toUpperCase();

            // 1. Workload hours check
            int currentHours = facultyWorkloadHours.getOrDefault(facIdUpper, 0);
            if (currentHours + subHours > hoursLimitVal) {
                return "Maximum weekly workload hours reached (" + currentHours + "/" + hoursLimitVal + " hrs)";
            }

            boolean isAlreadyTeachingSub = facultyUniqueSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).contains(subIdUpper);

            if (!isAlreadyTeachingSub) {
                int currentUniqueSubs = facultyUniqueSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();
                if (currentUniqueSubs + 1 > maxSubsVal) {
                    return "Maximum distinct subjects limit reached (" + currentUniqueSubs + "/" + maxSubsVal + " subjects)";
                }

                int currentRegCount = facultyRegularSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();
                int currentMockCount = facultyMockSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();

                if (sub.isMock()) {
                    if (currentMockCount + 1 > maxMockVal) {
                        return "Maximum mock subjects limit reached (" + currentMockCount + "/" + maxMockVal + " mock subjects)";
                    }
                    if (hasPreviousMockAllocation(fId, currentAcademicYear)) {
                        return "Faculty already allocated a mock subject in a previous year";
                    }
                } else {
                    if (currentRegCount + 1 > maxRegVal) {
                        return "Maximum regular subjects limit reached (" + currentRegCount + "/" + maxRegVal + " regular subjects)";
                    }
                }

                // Same-year rule: faculty cannot take 3 or more subjects all from the exact same year
                List<Integer> yearsAllocated = new ArrayList<>();
                for (String sId : facultyUniqueSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet())) {
                    Subject s = subjectRepository.findById(sId).orElse(null);
                    if (s != null) yearsAllocated.add(s.getYear());
                }
                yearsAllocated.add(sub.getYear());
                if (yearsAllocated.size() >= 3 && yearsAllocated.stream().distinct().count() == 1) {
                    return "All allocated subjects cannot be from the same study year (" + sub.getYear() + ")";
                }
            }

            return null; // Null indicates legally eligible
        };

        java.util.function.BiPredicate<String, Subject> canTakeSectionStrict = (fId, sub) -> {
            return getRejectionReason.apply(fId, sub) == null;
        };

        List<SubjectAllocation> createdSubjectAllocations = new ArrayList<>();
        List<AllocationExplanation> explanations = new ArrayList<>();

        // Helper to perform section allocation and update tracking state
        java.util.function.BiConsumer<SectionSlot, String> assignSectionToFaculty = (slot, chosenFacId) -> {
            Subject sub = slot.subject;
            String subIdUpper = sub.getId().toUpperCase();
            String chosenFacIdUpper = chosenFacId.toUpperCase();
            int hoursBefore = facultyWorkloadHours.getOrDefault(chosenFacIdUpper, 0);

            SectionAllocation sa = new SectionAllocation(sub.getId(), slot.sectionName, chosenFacId);
            sa.setFinalized(false);
            sectionAllocationRepository.save(sa);

            boolean isNewSub = !facultyUniqueSubjects.get(chosenFacIdUpper).contains(subIdUpper);
            if (isNewSub) {
                SubjectAllocation subAlloc = new SubjectAllocation(sub.getId(), chosenFacId);
                subAlloc.setFinalized(false);
                allocationRepository.save(subAlloc);
                createdSubjectAllocations.add(subAlloc);

                facultyUniqueSubjects.get(chosenFacIdUpper).add(subIdUpper);
                if (sub.isMock()) {
                    facultyMockSubjects.get(chosenFacIdUpper).add(subIdUpper);
                } else {
                    facultyRegularSubjects.get(chosenFacIdUpper).add(subIdUpper);
                }
            }

            facultyUniqueSections.get(chosenFacIdUpper).add(subIdUpper + "_" + slot.sectionName.toUpperCase());
            facultyWorkloadHours.put(chosenFacIdUpper, hoursBefore + subHours);
        };

        // Candidate comparator for fair and deterministic selection
        java.util.Comparator<AdminFaculty> candidateComparator = (f1, f2) -> {
            String f1Upper = f1.getId().toUpperCase();
            String f2Upper = f2.getId().toUpperCase();
            int h1 = facultyWorkloadHours.getOrDefault(f1Upper, 0);
            int h2 = facultyWorkloadHours.getOrDefault(f2Upper, 0);
            if (h1 != h2) return Integer.compare(h1, h2);

            int s1 = facultyUniqueSubjects.getOrDefault(f1Upper, java.util.Collections.emptySet()).size();
            int s2 = facultyUniqueSubjects.getOrDefault(f2Upper, java.util.Collections.emptySet()).size();
            if (s1 != s2) return Integer.compare(s1, s2);

            Long sub1 = facultySubmissionOrderMap.getOrDefault(f1Upper, Long.MAX_VALUE);
            Long sub2 = facultySubmissionOrderMap.getOrDefault(f2Upper, Long.MAX_VALUE);
            if (!sub1.equals(sub2)) return Long.compare(sub1, sub2);

            return f1Upper.compareTo(f2Upper);
        };

        // Helper lambda to process preference rounds for a given preference map (Regular or Mock)
        java.util.function.BiConsumer<java.util.Map<Integer, List<FacultyPrefEntry>>, Integer> processPreferenceRounds = (prefMap, maxRounds) -> {
            for (int roundP = 1; roundP <= maxRounds; roundP++) {
                List<FacultyPrefEntry> roundPrefs = prefMap.getOrDefault(roundP, java.util.Collections.emptyList());
                if (roundPrefs.isEmpty()) continue;

                // Group preferences for this round by subjectId
                java.util.Map<String, List<FacultyPrefEntry>> subjectPrefsInRound = new java.util.LinkedHashMap<>();
                for (FacultyPrefEntry pe : roundPrefs) {
                    subjectPrefsInRound.computeIfAbsent(pe.subjectId.toUpperCase(), k -> new ArrayList<>()).add(pe);
                }

                // Sort subjects in this round: scarce subjects first -> Year ascending -> Subject ID ascending
                List<Subject> subjectsInRound = subjects.stream()
                        .filter(s -> subjectPrefsInRound.containsKey(s.getId().toUpperCase()))
                        .sorted((sub1, sub2) -> {
                            int c1 = subjectPrefsInRound.getOrDefault(sub1.getId().toUpperCase(), java.util.Collections.emptyList()).size();
                            int c2 = subjectPrefsInRound.getOrDefault(sub2.getId().toUpperCase(), java.util.Collections.emptyList()).size();
                            if (c1 != c2) return Integer.compare(c1, c2);

                            int yComp = Integer.compare(sub1.getYear(), sub2.getYear());
                            if (yComp != 0) return yComp;

                            return sub1.getId().compareTo(sub2.getId());
                        })
                        .collect(Collectors.toList());

                for (Subject sub : subjectsInRound) {
                    String subIdUpper = sub.getId().toUpperCase();
                    List<FacultyPrefEntry> candidatesForSub = subjectPrefsInRound.get(subIdUpper);
                    if (candidatesForSub == null || candidatesForSub.isEmpty()) continue;

                    int totalConfiguredSections = totalSectionsPerSubject.getOrDefault(subIdUpper, 0);
                    List<FacultyPrefEntry> remainingCandidates = new ArrayList<>(candidatesForSub);

                    while (!remainingCandidates.isEmpty()) {
                        // Filter candidates in this preference round who legally satisfy hard constraints
                        List<FacultyPrefEntry> eligibleCandidates = remainingCandidates.stream()
                                .filter(pe -> canTakeSectionStrict.test(pe.facultyId, sub))
                                .collect(Collectors.toList());

                        if (eligibleCandidates.isEmpty()) {
                            break;
                        }

                        // Sort eligible candidates fairly: Subject count -> Workload -> Experience -> Earlier Submission Order -> Faculty ID
                        final int currentRound = roundP;
                        eligibleCandidates.sort((c1, c2) -> {
                            String f1Upper = c1.facultyId.toUpperCase();
                            String f2Upper = c2.facultyId.toUpperCase();

                            int s1 = facultyUniqueSubjects.getOrDefault(f1Upper, java.util.Collections.emptySet()).size();
                            int s2 = facultyUniqueSubjects.getOrDefault(f2Upper, java.util.Collections.emptySet()).size();
                            if (s1 != s2) return Integer.compare(s1, s2);

                            int h1 = facultyWorkloadHours.getOrDefault(f1Upper, 0);
                            int h2 = facultyWorkloadHours.getOrDefault(f2Upper, 0);
                            if (h1 != h2) return Integer.compare(h1, h2);

                            int exp1 = facultyExperienceMap.getOrDefault(f1Upper, java.util.Collections.emptyMap()).getOrDefault(subIdUpper, 0);
                            int exp2 = facultyExperienceMap.getOrDefault(f2Upper, java.util.Collections.emptyMap()).getOrDefault(subIdUpper, 0);
                            if (exp1 != exp2) return Integer.compare(exp2, exp1);

                            Long prefId1 = c1.preferenceDbId != null ? c1.preferenceDbId : facultySubmissionOrderMap.getOrDefault(f1Upper, Long.MAX_VALUE);
                            Long prefId2 = c2.preferenceDbId != null ? c2.preferenceDbId : facultySubmissionOrderMap.getOrDefault(f2Upper, Long.MAX_VALUE);
                            if (!prefId1.equals(prefId2)) return Long.compare(prefId1, prefId2);

                            return f1Upper.compareTo(f2Upper);
                        });

                        FacultyPrefEntry chosenCandidate = eligibleCandidates.get(0);
                        String chosenFacId = chosenCandidate.facultyId;
                        String chosenFacIdUpper = chosenFacId.toUpperCase();
                        AdminFaculty chosenFacultyObj = facultyMap.get(chosenFacIdUpper);

                        // P1 Minimum-2 Rule: If faculty has subject as P1 and subject has >= 2 sections, try giving 2 sections
                        int targetSectionsToAllocate = (roundP == 1 && totalConfiguredSections >= 2) ? 2 : 1;
                        int sectionsAllocatedToChosen = 0;

                        while (sectionsAllocatedToChosen < targetSectionsToAllocate) {
                            SectionSlot nextSlot = null;
                            for (SectionSlot slot : pendingSlots) {
                                if (slot.subject.getId().equalsIgnoreCase(sub.getId())) {
                                    nextSlot = slot;
                                    break;
                                }
                            }
                            if (nextSlot == null) break;

                            if (!canTakeSectionStrict.test(chosenFacId, sub)) {
                                break;
                            }

                            int hoursBefore = facultyWorkloadHours.getOrDefault(chosenFacIdUpper, 0);
                            assignSectionToFaculty.accept(nextSlot, chosenFacId);
                            int hoursAfter = facultyWorkloadHours.getOrDefault(chosenFacIdUpper, 0);

                            AllocationExplanation expl = new AllocationExplanation();
                            expl.setSubjectId(sub.getId());
                            expl.setSubjectName(sub.getName());
                            expl.setSubjectYear(sub.getYear());
                            expl.setSectionName(nextSlot.sectionName);
                            expl.setFacultyId(chosenFacId);
                            expl.setFacultyName(chosenFacultyObj != null ? chosenFacultyObj.getName() : chosenFacId);
                            expl.setPreference("P" + roundP);
                            expl.setHoursBefore(hoursBefore);
                            expl.setHoursAfter(hoursAfter);
                            expl.setReasonSelected(roundP == 1 && totalConfiguredSections >= 2 ? "Selected as Preference P1 under Minimum-2 Rule" : "Selected as Preference P" + roundP);
                            expl.setUnknown(false);
                            explanations.add(expl);

                            pendingSlots.remove(nextSlot);
                            sectionsAllocatedToChosen++;
                        }

                        remainingCandidates.remove(chosenCandidate);
                    }
                }
            }
        };

        // ----------------------------------------------------
        // PHASE 1: GLOBAL REGULAR PREFERENCE ROUNDS (P = 1, 2, ... maxRegPrefRound)
        // Allocates core Regular P1, then Regular P2, etc. across all years
        // ----------------------------------------------------
        processPreferenceRounds.accept(regularPreferenceRoundsMap, maxRegPrefRound);

        // ----------------------------------------------------
        // PHASE 1.5: GLOBAL MOCK PREFERENCE ROUNDS (P = 1, 2, ... maxMockPrefRound)
        // Allocates Mock P1, then Mock P2 across all years for faculty mock slots
        // ----------------------------------------------------
        processPreferenceRounds.accept(mockPreferenceRoundsMap, maxMockPrefRound);

        // ----------------------------------------------------
        // PHASE 2: WORKLOAD CAPACITY ALLOCATION FOR REAL FACULTY
        // Assign any remaining unallocated sections to real faculty with remaining capacity (fair workload balancing)
        // ----------------------------------------------------
        for (SectionSlot slot : new ArrayList<>(pendingSlots)) {
            Subject sub = slot.subject;
            String subIdUpper = sub.getId().toUpperCase();

            // 1. Try assigning to faculty who can legally take the subject satisfying strict constraints
            List<AdminFaculty> eligible = allFaculty.stream()
                    .filter(f -> canTakeSectionStrict.test(f.getId(), sub))
                    .sorted((f1, f2) -> {
                        String f1Upper = f1.getId().toUpperCase();
                        String f2Upper = f2.getId().toUpperCase();
                        // Prefer faculty who already teach this subject if workload is similar
                        boolean teaches1 = facultyUniqueSubjects.getOrDefault(f1Upper, java.util.Collections.emptySet()).contains(subIdUpper);
                        boolean teaches2 = facultyUniqueSubjects.getOrDefault(f2Upper, java.util.Collections.emptySet()).contains(subIdUpper);

                        int h1 = facultyWorkloadHours.getOrDefault(f1Upper, 0);
                        int h2 = facultyWorkloadHours.getOrDefault(f2Upper, 0);
                        if (h1 != h2) return Integer.compare(h1, h2);

                        if (teaches1 != teaches2) return teaches1 ? -1 : 1;

                        int s1 = facultyUniqueSubjects.getOrDefault(f1Upper, java.util.Collections.emptySet()).size();
                        int s2 = facultyUniqueSubjects.getOrDefault(f2Upper, java.util.Collections.emptySet()).size();
                        if (s1 != s2) return Integer.compare(s1, s2);

                        int exp1 = facultyExperienceMap.getOrDefault(f1Upper, java.util.Collections.emptyMap()).getOrDefault(subIdUpper, 0);
                        int exp2 = facultyExperienceMap.getOrDefault(f2Upper, java.util.Collections.emptyMap()).getOrDefault(subIdUpper, 0);
                        if (exp1 != exp2) return Integer.compare(exp2, exp1);

                        // If one faculty chose this subject in preferences and the other didn't (or higher preference rank)
                        int p1 = facultyPrefRankMap.getOrDefault(f1Upper, java.util.Collections.emptyMap()).getOrDefault(subIdUpper, 0);
                        int p2 = facultyPrefRankMap.getOrDefault(f2Upper, java.util.Collections.emptyMap()).getOrDefault(subIdUpper, 0);
                        if (p1 > 0 && p2 == 0) return -1;
                        if (p2 > 0 && p1 == 0) return 1;
                        if (p1 > 0 && p2 > 0 && p1 != p2) return Integer.compare(p1, p2);

                        // Submission order preference (earlier preference submitter gets allocated first)
                        Long sub1 = facultySubmissionOrderMap.getOrDefault(f1Upper, Long.MAX_VALUE);
                        Long sub2 = facultySubmissionOrderMap.getOrDefault(f2Upper, Long.MAX_VALUE);
                        if (!sub1.equals(sub2)) return Long.compare(sub1, sub2);

                        return f1Upper.compareTo(f2Upper);
                    })
                    .collect(Collectors.toList());

            // 2. If none, try relaxing same-year rule while strictly maintaining hours, max subjects, max regular, and max mock
            if (eligible.isEmpty()) {
                eligible = allFaculty.stream()
                        .filter(f -> {
                            String facIdUpper = f.getId().toUpperCase();
                            int currentHours = facultyWorkloadHours.getOrDefault(facIdUpper, 0);
                            if (currentHours + subHours > hoursLimitVal) return false;

                            boolean isAlreadyTeachingSub = facultyUniqueSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).contains(subIdUpper);
                            if (!isAlreadyTeachingSub) {
                                int currentUniqueSubs = facultyUniqueSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();
                                if (currentUniqueSubs + 1 > maxSubsVal) return false;

                                int currentRegCount = facultyRegularSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();
                                int currentMockCount = facultyMockSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();

                                if (sub.isMock()) {
                                    if (currentMockCount + 1 > maxMockVal) return false;
                                    if (hasPreviousMockAllocation(f.getId(), currentAcademicYear)) return false;
                                } else {
                                    if (currentRegCount + 1 > maxRegVal) return false;
                                }
                            }
                            return true;
                        })
                        .sorted(candidateComparator)
                        .collect(Collectors.toList());
            }

            if (!eligible.isEmpty()) {
                AdminFaculty chosenFaculty = eligible.get(0);
                String chosenFacId = chosenFaculty.getId();
                String chosenFacIdUpper = chosenFacId.toUpperCase();
                int hoursBefore = facultyWorkloadHours.getOrDefault(chosenFacIdUpper, 0);

                assignSectionToFaculty.accept(slot, chosenFacId);
                int hoursAfter = facultyWorkloadHours.getOrDefault(chosenFacIdUpper, 0);

                int pRank = facultyPrefRankMap.getOrDefault(chosenFacIdUpper, java.util.Collections.emptyMap()).getOrDefault(subIdUpper, 0);
                String pStr = pRank > 0 ? "P" + pRank : "None";

                AllocationExplanation expl = new AllocationExplanation();
                expl.setSubjectId(sub.getId());
                expl.setSubjectName(sub.getName());
                expl.setSubjectYear(sub.getYear());
                expl.setSectionName(slot.sectionName);
                expl.setFacultyId(chosenFacId);
                expl.setFacultyName(chosenFaculty.getName());
                expl.setPreference(pStr);
                expl.setHoursBefore(hoursBefore);
                expl.setHoursAfter(hoursAfter);
                expl.setReasonSelected(pRank > 0 ? "Selected in Workload Balancing Phase (Preference " + pStr + ")" : "Selected in Workload Balancing Phase to satisfy capacity");
                expl.setUnknown(false);
                explanations.add(expl);

                pendingSlots.remove(slot);
            }
        }

        // ----------------------------------------------------
        // PHASE 3: REPAIR & OPTIMIZATION SWAPS (Eliminate Unknown Faculty)
        // Search for valid 1-hop augmenting reassignments and swaps
        // ----------------------------------------------------
        boolean progressMade = true;
        while (!pendingSlots.isEmpty() && progressMade) {
            progressMade = false;

            for (SectionSlot unallocatedSlot : new ArrayList<>(pendingSlots)) {
                Subject unallocSub = unallocatedSlot.subject;
                String unallocSubIdUpper = unallocSub.getId().toUpperCase();

                // Check all real faculty with remaining capacity
                List<AdminFaculty> facultyWithCapacity = allFaculty.stream()
                        .filter(f -> facultyWorkloadHours.getOrDefault(f.getId().toUpperCase(), 0) + subHours <= hoursLimitVal)
                        .sorted(candidateComparator)
                        .collect(Collectors.toList());

                if (facultyWithCapacity.isEmpty()) break;

                // Try finding an augmenting path:
                // Faculty A holds Section K (Subject K). Faculty A can take Unallocated Subject if Section K is moved to Faculty B.
                boolean swapped = false;
                List<SectionAllocation> currentAllocations = sectionAllocationRepository.findAll();

                for (SectionAllocation saItem : currentAllocations) {
                    String holderIdUpper = saItem.getFacultyId().toUpperCase();
                    AdminFaculty holderFac = facultyMap.get(holderIdUpper);
                    if (holderFac == null) continue; // Skip unknown faculty holders

                    Subject holderSub = subjectRepository.findById(saItem.getSubjectId()).orElse(null);
                    if (holderSub == null) continue;

                    // Can holderFac take unallocSub if saItem was removed?
                    // Check if holderFac already has unallocSub or if holderFac would satisfy constraints
                    boolean holderCanTakeUnalloc = false;
                    boolean holderAlreadyHasUnalloc = facultyUniqueSubjects.getOrDefault(holderIdUpper, java.util.Collections.emptySet()).contains(unallocSubIdUpper);
                    if (holderAlreadyHasUnalloc) {
                        holderCanTakeUnalloc = true; // No subject count increase
                    } else {
                        // Check if removing saItem would free a subject slot (if saItem was the only section of holderSub for holderFac)
                        long sectionsOfHolderSub = sectionAllocationRepository.findBySubjectId(holderSub.getId()).stream()
                                .filter(x -> x.getFacultyId().equalsIgnoreCase(holderFac.getId()))
                                .count();
                        int netUniqueSubs = facultyUniqueSubjects.get(holderIdUpper).size() - (sectionsOfHolderSub <= 1 ? 1 : 0);
                        if (netUniqueSubs + 1 <= maxSubsVal) {
                            int netReg = facultyRegularSubjects.get(holderIdUpper).size() - (!holderSub.isMock() && sectionsOfHolderSub <= 1 ? 1 : 0);
                            int netMock = facultyMockSubjects.get(holderIdUpper).size() - (holderSub.isMock() && sectionsOfHolderSub <= 1 ? 1 : 0);
                            if (unallocSub.isMock()) {
                                if (netMock + 1 <= maxMockVal && !hasPreviousMockAllocation(holderFac.getId(), currentAcademicYear)) {
                                    holderCanTakeUnalloc = true;
                                }
                            } else {
                                if (netReg + 1 <= maxRegVal) {
                                    holderCanTakeUnalloc = true;
                                }
                            }
                        }
                    }

                    if (!holderCanTakeUnalloc) continue;

                    // Find a candidate target faculty B with capacity who can legally take saItem (holderSub)
                    for (AdminFaculty targetFac : facultyWithCapacity) {
                        if (targetFac.getId().equalsIgnoreCase(holderFac.getId())) continue;

                        if (canTakeSectionStrict.test(targetFac.getId(), holderSub)) {
                            // Valid augmenting swap found!
                            String targetFacIdUpper = targetFac.getId().toUpperCase();

                            // 1. Move saItem to targetFac
                            saItem.setFacultyId(targetFac.getId());
                            sectionAllocationRepository.save(saItem);

                            boolean targetNewSub = !facultyUniqueSubjects.get(targetFacIdUpper).contains(holderSub.getId().toUpperCase());
                            if (targetNewSub) {
                                SubjectAllocation newSubAlloc = new SubjectAllocation(holderSub.getId(), targetFac.getId());
                                newSubAlloc.setFinalized(false);
                                allocationRepository.save(newSubAlloc);
                                createdSubjectAllocations.add(newSubAlloc);

                                facultyUniqueSubjects.get(targetFacIdUpper).add(holderSub.getId().toUpperCase());
                                if (holderSub.isMock()) {
                                    facultyMockSubjects.get(targetFacIdUpper).add(holderSub.getId().toUpperCase());
                                } else {
                                    facultyRegularSubjects.get(targetFacIdUpper).add(holderSub.getId().toUpperCase());
                                }
                            }
                            facultyUniqueSections.get(targetFacIdUpper).add(holderSub.getId().toUpperCase() + "_" + saItem.getSectionName().toUpperCase());
                            facultyWorkloadHours.put(targetFacIdUpper, facultyWorkloadHours.get(targetFacIdUpper) + subHours);

                            // Clean holderFac's subject allocation for holderSub if no sections remain
                            facultyUniqueSections.get(holderIdUpper).remove(holderSub.getId().toUpperCase() + "_" + saItem.getSectionName().toUpperCase());
                            long holderRemainingSections = sectionAllocationRepository.findBySubjectId(holderSub.getId()).stream()
                                    .filter(x -> x.getFacultyId().equalsIgnoreCase(holderFac.getId()) && !x.getId().equals(saItem.getId()))
                                    .count();
                            if (holderRemainingSections == 0) {
                                facultyUniqueSubjects.get(holderIdUpper).remove(holderSub.getId().toUpperCase());
                                facultyRegularSubjects.get(holderIdUpper).remove(holderSub.getId().toUpperCase());
                                facultyMockSubjects.get(holderIdUpper).remove(holderSub.getId().toUpperCase());
                                SubjectAllocation oldSubAlloc = allocationRepository.findBySubjectIdAndFacultyId(holderSub.getId(), holderFac.getId());
                                if (oldSubAlloc != null) {
                                    allocationRepository.delete(oldSubAlloc);
                                    createdSubjectAllocations.remove(oldSubAlloc);
                                }
                            }

                            // 2. Assign unallocatedSlot to holderFac
                            int holderHoursBefore = facultyWorkloadHours.get(holderIdUpper);
                            assignSectionToFaculty.accept(unallocatedSlot, holderFac.getId());
                            int holderHoursAfter = facultyWorkloadHours.get(holderIdUpper);

                            int pRank = facultyPrefRankMap.getOrDefault(holderIdUpper, java.util.Collections.emptyMap()).getOrDefault(unallocSubIdUpper, 0);
                            String pStr = pRank > 0 ? "P" + pRank : "None";

                            AllocationExplanation expl = new AllocationExplanation();
                            expl.setSubjectId(unallocSub.getId());
                            expl.setSubjectName(unallocSub.getName());
                            expl.setSubjectYear(unallocSub.getYear());
                            expl.setSectionName(unallocatedSlot.sectionName);
                            expl.setFacultyId(holderFac.getId());
                            expl.setFacultyName(holderFac.getName());
                            expl.setPreference(pStr);
                            expl.setHoursBefore(holderHoursBefore);
                            expl.setHoursAfter(holderHoursAfter);
                            expl.setReasonSelected("Selected via Optimization/Repair Swap (Reassigned " + holderSub.getId() + " section " + saItem.getSectionName() + " to " + targetFac.getName() + ")");
                            expl.setUnknown(false);
                            explanations.add(expl);

                            pendingSlots.remove(unallocatedSlot);
                            swapped = true;
                            progressMade = true;
                            break;
                        }
                    }

                    if (swapped) break;
                }
            }
        }

        // ----------------------------------------------------
        // PHASE 4: UNKNOWN FACULTY (ABSOLUTE LAST RESORT ONLY)
        // Only assigned if mathematically and legally impossible to assign to real faculty
        // ----------------------------------------------------
        int unknownIndex = 1;
        for (SectionSlot slot : new ArrayList<>(pendingSlots)) {
            Subject sub = slot.subject;
            String subIdUpper = sub.getId().toUpperCase();

            String targetUnknownId = null;
            while (true) {
                String uId = "UNKNOWN_" + unknownIndex;
                int currentHours = facultyWorkloadHours.getOrDefault(uId, 0);
                if (currentHours + subHours <= hoursLimitVal) {
                    boolean isAlreadyTeachingSub = facultyUniqueSubjects.getOrDefault(uId, java.util.Collections.emptySet()).contains(subIdUpper);
                    boolean canTake = true;
                    if (!isAlreadyTeachingSub) {
                        int curSubs = facultyUniqueSubjects.getOrDefault(uId, java.util.Collections.emptySet()).size();
                        if (curSubs + 1 > maxSubsVal) canTake = false;
                        int curReg = facultyRegularSubjects.getOrDefault(uId, java.util.Collections.emptySet()).size();
                        int curMock = facultyMockSubjects.getOrDefault(uId, java.util.Collections.emptySet()).size();
                        if (sub.isMock() && curMock + 1 > maxMockVal) canTake = false;
                        if (!sub.isMock() && curReg + 1 > maxRegVal) canTake = false;
                    }
                    if (canTake) {
                        targetUnknownId = uId;
                        break;
                    }
                }
                unknownIndex++;
            }

            facultyUniqueSubjects.computeIfAbsent(targetUnknownId, k -> new java.util.LinkedHashSet<>());
            facultyUniqueSections.computeIfAbsent(targetUnknownId, k -> new java.util.LinkedHashSet<>());
            facultyRegularSubjects.computeIfAbsent(targetUnknownId, k -> new java.util.LinkedHashSet<>());
            facultyMockSubjects.computeIfAbsent(targetUnknownId, k -> new java.util.LinkedHashSet<>());

            int hoursBefore = facultyWorkloadHours.getOrDefault(targetUnknownId, 0);
            SectionAllocation sa = new SectionAllocation(sub.getId(), slot.sectionName, targetUnknownId);
            sa.setFinalized(false);
            sectionAllocationRepository.save(sa);

            boolean isNewSub = !facultyUniqueSubjects.get(targetUnknownId).contains(subIdUpper);
            if (isNewSub) {
                SubjectAllocation subAlloc = new SubjectAllocation(sub.getId(), targetUnknownId);
                subAlloc.setFinalized(false);
                allocationRepository.save(subAlloc);
                createdSubjectAllocations.add(subAlloc);

                facultyUniqueSubjects.get(targetUnknownId).add(subIdUpper);
                if (sub.isMock()) {
                    facultyMockSubjects.get(targetUnknownId).add(subIdUpper);
                } else {
                    facultyRegularSubjects.get(targetUnknownId).add(subIdUpper);
                }
            }

            facultyUniqueSections.get(targetUnknownId).add(subIdUpper + "_" + slot.sectionName.toUpperCase());
            facultyWorkloadHours.put(targetUnknownId, hoursBefore + subHours);

            // Build detailed rejection breakdown for all real faculty for this Unknown allocation
            AllocationExplanation expl = new AllocationExplanation();
            expl.setSubjectId(sub.getId());
            expl.setSubjectName(sub.getName());
            expl.setSubjectYear(sub.getYear());
            expl.setSectionName(slot.sectionName);
            expl.setFacultyId(targetUnknownId);
            expl.setFacultyName("Unknown Faculty (" + targetUnknownId + ")");
            expl.setPreference("None");
            expl.setHoursBefore(hoursBefore);
            expl.setHoursAfter(hoursBefore + subHours);
            expl.setReasonSelected("Allocated to Unknown Faculty as absolute last resort (all real faculty exhausted constraints)");
            expl.setUnknown(true);

            for (AdminFaculty f : allFaculty) {
                String fId = f.getId();
                String fIdUpper = fId.toUpperCase();
                int pRank = facultyPrefRankMap.getOrDefault(fIdUpper, java.util.Collections.emptyMap()).getOrDefault(subIdUpper, 0);
                String pStr = pRank > 0 ? "P" + pRank : "None";
                int curWorkload = facultyWorkloadHours.getOrDefault(fIdUpper, 0);
                String rejReason = getRejectionReason.apply(fId, sub);
                if (rejReason == null) rejReason = "Capacity exhausted in previous allocation phases";

                expl.getRejectionDetails().add(new FacultyRejectionDetail(fId, f.getName(), pStr, curWorkload, rejReason));
            }

            explanations.add(expl);
            pendingSlots.remove(slot);
        }

        allocationRepository.flush();
        sectionAllocationRepository.flush();

        // ----------------------------------------------------
        // PHASE 5: PRINT AND LOG ALLOCATION EXPLANATION REPORT
        // Mandatory formatted debug explanation for all sections and unknown allocations
        // ----------------------------------------------------
        this.latestAllocationExplanations = explanations;

        System.out.println("==========================================================================================");
        System.out.println("                         EDUASSIGN AUTO-ALLOCATION EXPLANATION REPORT                      ");
        System.out.println("==========================================================================================");
        System.out.println("Target Academic Year: " + currentAcademicYear + " | Department: " + filterDepartment + " | Sem: " + filterSem + " | Years: " + activeYears);
        System.out.println("Workload Limit: " + hoursLimitVal + " hrs/week | Section Workload: " + subHours + " hrs | Max Subjects: " + maxSubsVal + " (Reg: " + maxRegVal + ", Mock: " + maxMockVal + ")");
        System.out.println("Total Allocated Sections: " + explanations.size());
        System.out.println("------------------------------------------------------------------------------------------");

        for (AllocationExplanation exp : explanations) {
            if (!exp.isUnknown()) {
                System.out.println(exp.getSubjectId() + " [" + exp.getSectionName() + "] (" + exp.getSubjectName() + ", Year " + exp.getSubjectYear() + ")");
                System.out.println("  → selected faculty: " + exp.getFacultyId() + " (" + exp.getFacultyName() + ")");
                System.out.println("  → preference number: " + exp.getPreference());
                System.out.println("  → faculty hours before allocation: " + exp.getHoursBefore() + " hrs");
                System.out.println("  → faculty hours after allocation: " + exp.getHoursAfter() + " hrs");
                System.out.println("  → reason selected: " + exp.getReasonSelected());
                System.out.println();
            } else {
                System.out.println(">>> UNKNOWN ALLOCATION: " + exp.getSubjectId() + " [" + exp.getSectionName() + "] (" + exp.getSubjectName() + ", Year " + exp.getSubjectYear() + ")");
                System.out.println("  → selected faculty: " + exp.getFacultyId());
                System.out.println("  → eligible faculty rejection breakdown:");
                for (FacultyRejectionDetail rej : exp.getRejectionDetails()) {
                    System.out.println("     * Faculty " + rej.getFacultyId() + " (" + rej.getFacultyName() + "): Preference=" + rej.getPreference() + ", Workload=" + rej.getCurrentWorkload() + "/" + hoursLimitVal + " hrs | Rejection Reason: " + rej.getReasonRejected());
                }
                System.out.println();
            }
        }
        System.out.println("==========================================================================================");

        return createdSubjectAllocations;
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

        // Clear out existing history for this academic year, department, and semester first to avoid duplicates
        List<AllocationHistory> existingHistory = allocationHistoryRepository.findByAcademicYearAndDepartmentAndSemester(currentYear, currentDept, currentSem);
        allocationHistoryRepository.deleteAll(existingHistory);

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

    public List<SectionAllocation> getSectionAllocationsByFacultyId(String facultyId) {
        if (facultyId == null) return new ArrayList<>();
        String facultyIdUpper = facultyId.trim().toUpperCase();
        
        List<SectionAllocation> currentAllocs = sectionAllocationRepository.findByFacultyId(facultyId);
        List<SectionAllocation> result = new ArrayList<>(currentAllocs);
        
        // Find existing subjectId + sectionName keys to avoid duplicates
        java.util.Set<String> existingKeys = currentAllocs.stream()
                .map(sa -> sa.getSubjectId().toUpperCase() + "_" + sa.getSectionName().toUpperCase())
                .collect(Collectors.toSet());
                
        // Add historical section allocations
        List<AllocationHistory> historicalAllocs = allocationHistoryRepository.findByFacultyId(facultyIdUpper);
        if (historicalAllocs != null) {
            for (AllocationHistory hist : historicalAllocs) {
                if (hist.getSubjectId() == null || hist.getSectionName() == null) continue;
                String key = hist.getSubjectId().toUpperCase() + "_" + hist.getSectionName().toUpperCase();
                if (!existingKeys.contains(key)) {
                    SectionAllocation sa = new SectionAllocation(hist.getSubjectId(), hist.getSectionName(), facultyId);
                    sa.setFinalized(true);
                    result.add(sa);
                    existingKeys.add(key);
                }
            }
        }
        return result;
    }

    public List<SubjectAllocation> getFinalizedSubjectAllocationsByFacultyId(String facultyId) {
        return allocationRepository.findByFacultyIdAndFinalized(facultyId, true);
    }

    public void stopDeadline() {
        stopDeadline(null);
    }

    public void stopDeadline(Integer year) {
        List<SubjectSelectionWindow> windows = windowRepository.findAll();
        if (year != null) {
            for (SubjectSelectionWindow w : windows) {
                if (w.isActive() && year.equals(w.getYear())) {
                    w.setActive(false);
                    windowRepository.save(w);
                }
            }
        } else {
            for (SubjectSelectionWindow w : windows) {
                if (w.isActive()) {
                    w.setActive(false);
                    windowRepository.save(w);
                }
            }
        }
    }

    public List<SubjectSelectionWindow> getAllDeadlines() {
        return windowRepository.findAll();
    }

    @Transactional
    public void clearAllAllocations() {
        allocationRepository.deleteAll();
        sectionAllocationRepository.deleteAll();
        allocationHistoryRepository.deleteAll();
    }

    @Transactional
    public void clearAllocationsForYears(List<Integer> years) {
        if (years == null || years.isEmpty()) {
            return;
        }
        List<Subject> subjects = subjectRepository.findAll().stream()
                .filter(s -> years.contains(s.getYear()))
                .collect(Collectors.toList());
        for (Subject sub : subjects) {
            List<SubjectAllocation> existingSubAllocs = allocationRepository.findBySubjectId(sub.getId());
            allocationRepository.deleteAll(existingSubAllocs);
            List<SectionAllocation> existingSecAllocs = sectionAllocationRepository.findBySubjectId(sub.getId());
            sectionAllocationRepository.deleteAll(existingSecAllocs);
            List<AllocationHistory> existingHistory = allocationHistoryRepository.findBySubjectId(sub.getId());
            allocationHistoryRepository.deleteAll(existingHistory);
        }
    }

    public List<Integer> getAllocatedYears() {
        java.util.Set<Integer> years = new java.util.TreeSet<>();
        List<SubjectAllocation> subAllocs = allocationRepository.findAll();
        for (SubjectAllocation sa : subAllocs) {
            if (sa.getSubjectId() != null) {
                Subject s = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                if (s != null) {
                    years.add(s.getYear());
                }
            }
        }
        List<SectionAllocation> secAllocs = sectionAllocationRepository.findAll();
        for (SectionAllocation sa : secAllocs) {
            if (sa.getSubjectId() != null) {
                Subject s = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                if (s != null) {
                    years.add(s.getYear());
                }
            }
        }
        return new ArrayList<>(years);
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
        
        String trimmedYear = (academicYear != null) ? academicYear.trim() : "";
        List<FacultySubjectPreference> filtered = all.stream()
            .filter(p -> {
                if (p.getSubjectId() == null) return false;
                Subject sub = subjectRepository.findById(p.getSubjectId()).orElse(null);
                if (sub == null) return false;
                if (trimmedYear.isEmpty()) return true;
                return sub.getAcademicYear() == null || sub.getAcademicYear().trim().equalsIgnoreCase(trimmedYear);
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

    public boolean hasPreviousMockAllocation(String facultyId, String currentAcademicYear) {
        if (facultyId == null) {
            return false;
        }
        String facultyIdUpper = facultyId.trim().toUpperCase();
        String academicYearVal = (currentAcademicYear != null) ? currentAcademicYear.trim() : "";

        // Check current finalized subject allocations from a different academic year
        List<SubjectAllocation> currentAllocs = allocationRepository.findByFacultyId(facultyIdUpper);
        if (currentAllocs != null) {
            for (SubjectAllocation alloc : currentAllocs) {
                if (alloc.getSubjectId() == null) continue;
                Subject s = subjectRepository.findById(alloc.getSubjectId()).orElse(null);
                if (s != null && s.isMock() && alloc.isFinalized()) {
                    String allocYear = s.getAcademicYear() != null ? s.getAcademicYear().trim() : "";
                    if (academicYearVal.isEmpty() || !allocYear.equalsIgnoreCase(academicYearVal)) {
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
                if (academicYearVal.isEmpty() || !histYear.equalsIgnoreCase(academicYearVal)) {
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
