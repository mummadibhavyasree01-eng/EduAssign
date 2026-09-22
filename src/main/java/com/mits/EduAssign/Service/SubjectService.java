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

    public static final java.util.Map<String, Long> EARLIEST_YEAR_SUBMISSION_MAP = new java.util.concurrent.ConcurrentHashMap<>();

    public static synchronized void recordPreferenceSubmission(String academicYear, String facultyId, Long firstId) {
        if (facultyId == null || facultyId.trim().isEmpty()) return;
        String year = (academicYear != null && !academicYear.trim().isEmpty()) ? academicYear.trim().replaceAll("\\s+", "").toLowerCase() : "2026-27";
        String key = year + "_" + facultyId.toLowerCase().trim();
        if (firstId != null) {
            EARLIEST_YEAR_SUBMISSION_MAP.putIfAbsent(key, firstId);
            Long current = EARLIEST_YEAR_SUBMISSION_MAP.get(key);
            if (firstId < current) {
                EARLIEST_YEAR_SUBMISSION_MAP.put(key, firstId);
            }
        } else {
            EARLIEST_YEAR_SUBMISSION_MAP.putIfAbsent(key, System.currentTimeMillis());
        }
    }

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
                if (p.getSubjectId() != null && (subIdsInWindow.contains(p.getSubjectId().toLowerCase()) || "AUTO_RANDOM".equalsIgnoreCase(p.getSubjectId()))) {
                    preferenceRepository.delete(p);
                }
            }
        } else {
            preferenceRepository.deleteByFacultyId(facultyId);
        }
    }

    @Transactional
    public void savePreferences(String facultyId, List<String> subjectIds) {
        SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();
        String acadYear = (window != null) ? window.getAcademicYear() : "2026-27";
        recordPreferenceSubmission(acadYear, facultyId, null);

        // Clear existing preferences first
        clearPreferencesInActiveWindow(facultyId);

        // Save new preferences
        java.util.Set<String> savedKeys = new java.util.HashSet<>();
        for (String subjectId : subjectIds) {
            if (subjectId == null || subjectId.trim().isEmpty()) continue;
            String key = subjectId.toUpperCase() + "_false";
            if (!savedKeys.contains(key)) {
                savedKeys.add(key);
                FacultySubjectPreference pref = new FacultySubjectPreference(facultyId, subjectId);
                preferenceRepository.save(pref);
            }
        }
    }

    @Transactional
    public void savePreferencesEntity(String facultyId, List<FacultySubjectPreference> preferences) {
        // Fetch current active window info for limits
        SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();
        String acadYear = (window != null) ? window.getAcademicYear() : "2026-27";
        String dept = (window != null) ? window.getDepartment() : null;
        recordPreferenceSubmission(acadYear, facultyId, null);

        java.util.Map<String, Object> limitsMap = getSubjectPreferenceLimits(acadYear, dept);
        List<FacultySubjectPreference> currentFacultyPrefs = preferenceRepository.findByFacultyId(facultyId);
        java.util.Set<String> alreadySelectedSubIds = currentFacultyPrefs.stream()
                .filter(p -> p.getSubjectId() != null)
                .map(p -> p.getSubjectId().toLowerCase() + "_" + p.isMock())
                .collect(Collectors.toSet());

        for (FacultySubjectPreference pref : preferences) {
            if (pref.getSubjectId() != null && !"AUTO_RANDOM".equalsIgnoreCase(pref.getSubjectId())) {
                String subKey = pref.getSubjectId().toLowerCase();
                String matchKey = subKey + "_" + pref.isMock();
                if (!alreadySelectedSubIds.contains(matchKey)) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> limitInfo = (java.util.Map<String, Object>) limitsMap.get(subKey);
                    if (limitInfo != null) {
                        long count = ((Number) limitInfo.get("count")).longValue();
                        int limit = ((Number) limitInfo.get("limit")).intValue();
                        if (count >= limit) {
                            Subject s = subjectRepository.findById(pref.getSubjectId()).orElse(null);
                            String sName = (s != null) ? s.getName() : pref.getSubjectId();
                            String type = pref.isMock() ? "mock" : "regular";
                            throw new IllegalArgumentException("Subject '" + sName + "' has reached its maximum faculty " + type + " selection limit (" + limit + " faculty). Please choose other available subjects.");
                        }
                    }
                }
            }
        }

        // Clear existing preferences first
        clearPreferencesInActiveWindow(facultyId);

        // Save new preferences (deduplicated)
        java.util.Set<String> savedKeys = new java.util.HashSet<>();
        for (FacultySubjectPreference pref : preferences) {
            if (pref.getSubjectId() == null || pref.getSubjectId().trim().isEmpty()) continue;
            pref.setFacultyId(facultyId);
            pref.setId(null);
            String key = pref.getSubjectId().toUpperCase() + "_" + pref.isMock();
            if (!savedKeys.contains(key)) {
                savedKeys.add(key);
                preferenceRepository.save(pref);
            }
        }
    }

    public java.util.Map<String, Object> getSubjectPreferenceLimits(String academicYear, String department) {
        List<Subject> allSubjects = subjectRepository.findAll();
        if (academicYear != null && !academicYear.trim().isEmpty()) {
            allSubjects = allSubjects.stream()
                    .filter(s -> s.getAcademicYear() != null && s.getAcademicYear().equalsIgnoreCase(academicYear.trim()))
                    .collect(Collectors.toList());
        }
        if (department != null && !department.trim().isEmpty()) {
            allSubjects = allSubjects.stream()
                    .filter(s -> s.getDep() != null && s.getDep().equalsIgnoreCase(department.trim()))
                    .collect(Collectors.toList());
        }

        List<AdminFaculty> allFaculties = adminRepository.findAll().stream()
                .filter(f -> !"SUPERADMIN".equalsIgnoreCase(f.getRole()))
                .collect(Collectors.toList());

        List<FacultySubjectPreference> allPreferences = preferenceRepository.findAll();

        java.util.Map<String, Object> limitsMap = new java.util.HashMap<>();

        int totalFaculty = allFaculties.isEmpty() ? 10 : allFaculties.size();

        for (Subject sub : allSubjects) {
            String deptCode = sub.getDep();

            List<Section> secList = (deptCode != null && sub.getYear() > 0)
                    ? sectionRepository.findByDepartmentCodeAndYearNumber(deptCode, sub.getYear())
                    : new ArrayList<>();
            int sectionCount = secList.isEmpty() ? 1 : secList.size();

            // Section limit rules: 4 sections -> 6 members; 10 sections -> 12 members
            int limit;
            if (sectionCount == 4) {
                limit = 6;
            } else if (sectionCount == 10) {
                limit = 12;
            } else {
                limit = (int) Math.round(sectionCount * 1.25);
            }
            if (limit < sectionCount) limit = sectionCount;
            if (limit < 1) limit = 1;

            long count = allPreferences.stream()
                    .filter(p -> p.isMock() == sub.isMock() && p.getSubjectId() != null && p.getSubjectId().equalsIgnoreCase(sub.getId()))
                    .map(FacultySubjectPreference::getFacultyId)
                    .distinct()
                    .count();

            java.util.Map<String, Object> info = new java.util.HashMap<>();
            info.put("count", count);
            info.put("limit", limit);
            info.put("isLimitReached", count >= limit);
            info.put("sectionCount", sectionCount);
            info.put("totalFaculty", totalFaculty);

            limitsMap.put(sub.getId().toLowerCase(), info);
        }

        return limitsMap;
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

        // Automatically clear existing allocations for selected years so faculty preference selection is unlocked
        if (!selectedYears.isEmpty()) {
            clearAllocationsForYears(new ArrayList<>(selectedYears));
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
        if (windows.isEmpty()) return true;
        for (SubjectSelectionWindow w : windows) {
            if (w.isActive() && (w.getDeadline() == null || LocalDateTime.now().isBefore(w.getDeadline()))) {
                return true;
            }
        }
        return false;
    }

    public boolean isBeforeDeadlineForYear(Integer year) {
        if (year == null) return isBeforeDeadline();
        return windowRepository.findById(year)
                .map(w -> w.isActive() && (w.getDeadline() == null || LocalDateTime.now().isBefore(w.getDeadline())))
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
                // Enforce 2 regular subjects allocated before mock subject allocation
                long regularCount = facAllocs.stream()
                        .map(a -> subjectRepository.findById(a.getSubjectId()).orElse(null))
                        .filter(s -> s != null && !s.isMock())
                        .count();
                if (regularCount < 2) {
                    throw new IllegalArgumentException("Mock subject can only be allocated after 2 regular subjects have been allocated to this faculty member (currently allocated: " + regularCount + "/2).");
                }
                // Enforce max 1 mock subject ever
                if (hasAnyMockAllocation(allocation.getFacultyId())) {
                    throw new IllegalArgumentException("Faculty member has already been allocated a mock subject in this or a previous year. Only one mock subject is allowed per faculty.");
                }
            }

            // Enforce: If total allocated regular subjects >= 3, they cannot all be from the same study year
            if (!subject.isMock()) {
                List<Integer> years = new java.util.ArrayList<>();
                for (SubjectAllocation a : facAllocs) {
                    Subject s = subjectRepository.findById(a.getSubjectId()).orElse(null);
                    if (s != null && !s.isMock()) {
                        years.add(s.getYear());
                    }
                }
                years.add(subject.getYear());
                if (years.size() >= 3) {
                    long distinctYearsCount = years.stream().distinct().count();
                    if (distinctYearsCount == 1) {
                        throw new IllegalArgumentException("All allocated regular subjects cannot be from the same study year (" + subject.getYear() + "). Allocation must span at least two different years.");
                    }
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

        boolean isFinalized = allocation.isFinalized();

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
                newSubAlloc.setFinalized(isFinalized);
                allocationRepository.save(newSubAlloc);
            }

            allocation.setFinalized(isFinalized);
            SectionAllocation saved = sectionAllocationRepository.save(allocation);

            if (isFinalized) {
                saveOrUpdateAllocationHistory(saved, subject, window);
            }

            return saved;
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
            if (!subject.isMock()) {
                List<Integer> allocatedYears = new ArrayList<>();
                for (SubjectAllocation sa : facAllocs) {
                    Subject s = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                    if (s != null && !s.isMock() && s.getAcademicYear() != null && s.getAcademicYear().equalsIgnoreCase(currentAcademicYear)) {
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
            newSubAlloc.setFinalized(isFinalized);
            allocationRepository.save(newSubAlloc);
        }

        allocation.setFinalized(isFinalized);
        SectionAllocation saved = sectionAllocationRepository.save(allocation);

        if (isFinalized) {
            saveOrUpdateAllocationHistory(saved, subject, window);
        }

        return saved;
    }

    private void saveOrUpdateAllocationHistory(SectionAllocation sa, Subject sub, SubjectSelectionWindow window) {
        String acadYear = (sub != null && sub.getAcademicYear() != null && !sub.getAcademicYear().trim().isEmpty())
                ? sub.getAcademicYear().trim()
                : ((window != null && window.getAcademicYear() != null) ? window.getAcademicYear().trim() : "2026-27");
        String dept = (sub != null && sub.getDep() != null && !sub.getDep().trim().isEmpty())
                ? sub.getDep().trim()
                : ((window != null && window.getDepartment() != null) ? window.getDepartment().trim() : "CSE");
        Integer sem = (sub != null && sub.getSem() > 0)
                ? sub.getSem()
                : ((window != null && window.getSem() != null) ? window.getSem() : 1);

        // Delete any existing history for this subject and section to avoid duplicates
        List<AllocationHistory> existing = allocationHistoryRepository.findAll().stream()
                .filter(h -> h.getSubjectId() != null && h.getSubjectId().equalsIgnoreCase(sa.getSubjectId()) &&
                             h.getSectionName() != null && h.getSectionName().equalsIgnoreCase(sa.getSectionName()))
                .collect(Collectors.toList());
        allocationHistoryRepository.deleteAll(existing);

        AllocationHistory newHist = new AllocationHistory(acadYear, dept, sem, sa.getFacultyId(), sa.getSubjectId(), sa.getSectionName());
        allocationHistoryRepository.save(newHist);
    }

    public List<SectionAllocation> getAllSectionAllocations() {
        return sectionAllocationRepository.findAll();
    }

    @Transactional
    public boolean deleteSectionAllocation(Long id) {
        if (id == null) return true;
        SectionAllocation sa = sectionAllocationRepository.findById(id).orElse(null);
        if (sa != null) {
            deleteSectionAllocationByDetails(sa.getSubjectId(), sa.getSectionName(), sa.getFacultyId());
            return true;
        }
        AllocationHistory ah = allocationHistoryRepository.findById(id).orElse(null);
        if (ah != null) {
            deleteSectionAllocationByDetails(ah.getSubjectId(), ah.getSectionName(), ah.getFacultyId());
            return true;
        }
        SubjectAllocation subAlloc = allocationRepository.findById(id).orElse(null);
        if (subAlloc != null) {
            deleteSectionAllocationByDetails(subAlloc.getSubjectId(), "N/A", subAlloc.getFacultyId());
            return true;
        }
        return true;
    }

    @Transactional
    public boolean deleteSectionAllocationByDetails(String subjectId, String sectionName, String facultyId) {
        if (subjectId == null) return true;

        boolean isWildcardSec = (sectionName == null || sectionName.trim().isEmpty() || sectionName.equalsIgnoreCase("N/A"));

        List<SectionAllocation> matchingSec = sectionAllocationRepository.findAll().stream()
                .filter(x -> x.getSubjectId() != null && x.getSubjectId().equalsIgnoreCase(subjectId) &&
                             (facultyId == null || (x.getFacultyId() != null && x.getFacultyId().equalsIgnoreCase(facultyId))) &&
                             (isWildcardSec || (x.getSectionName() != null && (x.getSectionName().equalsIgnoreCase(sectionName) || x.getSectionName().equalsIgnoreCase("N/A")))))
                .collect(Collectors.toList());
        sectionAllocationRepository.deleteAll(matchingSec);

        List<AllocationHistory> matchingHist = allocationHistoryRepository.findAll().stream()
                .filter(x -> x.getSubjectId() != null && x.getSubjectId().equalsIgnoreCase(subjectId) &&
                             (facultyId == null || (x.getFacultyId() != null && x.getFacultyId().equalsIgnoreCase(facultyId))) &&
                             (isWildcardSec || (x.getSectionName() != null && (x.getSectionName().equalsIgnoreCase(sectionName) || x.getSectionName().equalsIgnoreCase("N/A")))))
                .collect(Collectors.toList());
        allocationHistoryRepository.deleteAll(matchingHist);

        if (facultyId != null) {
            List<SectionAllocation> remaining = sectionAllocationRepository.findBySubjectId(subjectId).stream()
                    .filter(x -> x.getFacultyId() != null && x.getFacultyId().equalsIgnoreCase(facultyId))
                    .collect(Collectors.toList());
            if (remaining.isEmpty() || isWildcardSec) {
                SubjectAllocation subAlloc = allocationRepository.findBySubjectIdAndFacultyId(subjectId, facultyId);
                if (subAlloc != null) {
                    allocationRepository.delete(subAlloc);
                }
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
        // Delete old allocations for fromFacultyId
        deleteSectionAllocationByDetails(subjectId, sectionName, fromFacultyId);

        // Allocate to new faculty with finalized = true
        SectionAllocation newAlloc = new SectionAllocation(subjectId, sectionName, toFacultyId);
        newAlloc.setFinalized(true);
        allocateSection(newAlloc, ignoreConstraints);
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
        // Find faculty for target allocation if not specified
        SectionAllocation sa2 = sectionAllocationRepository.findAll().stream()
                .filter(x -> x.getSubjectId().equalsIgnoreCase(subjectId2) && x.getSectionName().equalsIgnoreCase(sectionName2))
                .findFirst().orElse(null);
        String facultyId2 = (sa2 != null) ? sa2.getFacultyId() : null;

        if (facultyId2 == null) {
            AllocationHistory ah2 = allocationHistoryRepository.findAll().stream()
                    .filter(x -> x.getSubjectId().equalsIgnoreCase(subjectId2) && x.getSectionName().equalsIgnoreCase(sectionName2))
                    .findFirst().orElse(null);
            if (ah2 != null) {
                facultyId2 = ah2.getFacultyId();
            }
        }

        if (facultyId2 == null) {
            throw new IllegalArgumentException("Target allocation for swap not found.");
        }

        if (facultyId1.equalsIgnoreCase(facultyId2)) {
            return;
        }

        // Clean up both allocations from both tables
        deleteSectionAllocationByDetails(subjectId1, sectionName1, facultyId1);
        deleteSectionAllocationByDetails(subjectId2, sectionName2, facultyId2);

        // Re-allocate swapped with finalized = true
        SectionAllocation newAlloc1 = new SectionAllocation(subjectId1, sectionName1, facultyId2);
        newAlloc1.setFinalized(true);
        allocateSection(newAlloc1, true);

        SectionAllocation newAlloc2 = new SectionAllocation(subjectId2, sectionName2, facultyId1);
        newAlloc2.setFinalized(true);
        allocateSection(newAlloc2, true);
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
                if (!subject.isMock()) {
                    List<Integer> allocatedYears = new ArrayList<>();
                    for (SubjectAllocation sa : facAllocs) {
                        Subject s = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                        if (s != null && !s.isMock() && s.getAcademicYear() != null && s.getAcademicYear().equalsIgnoreCase(currentAcademicYear)) {
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

    public static class FacultyReportRow {
        private String facultyId;
        private String facultyName;
        private List<String> preferences = new ArrayList<>();
        private List<String> allocations = new ArrayList<>();
        private List<String> allocatedSections = new ArrayList<>();
        private int totalCount;
        private int distinctSubjectsCount;
        private int totalHours;
        private int workloadHours;
        private int pref1Matches;
        private int pref2Matches;
        private int pref3Matches;
        private int satisfactionPercentage;
        private String experienceLevel; // High, Medium, Low, None
        private String status; // GOOD, FAIR, NO ALLOCATION
        private String zeroAllocationReason;
        private List<String> zeroAllocationExplanations = new ArrayList<>();

        public FacultyReportRow() {}

        public String getFacultyId() { return facultyId; }
        public void setFacultyId(String facultyId) { this.facultyId = facultyId; }
        public String getFacultyName() { return facultyName; }
        public void setFacultyName(String facultyName) { this.facultyName = facultyName; }
        public List<String> getPreferences() { return preferences; }
        public void setPreferences(List<String> preferences) { this.preferences = preferences; }
        public List<String> getAllocations() { return allocations; }
        public void setAllocations(List<String> allocations) { this.allocations = allocations; this.allocatedSections = allocations; }
        public List<String> getAllocatedSections() { return allocatedSections; }
        public void setAllocatedSections(List<String> allocatedSections) { this.allocatedSections = allocatedSections; this.allocations = allocatedSections; }
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; this.distinctSubjectsCount = totalCount; }
        public int getDistinctSubjectsCount() { return distinctSubjectsCount; }
        public void setDistinctSubjectsCount(int distinctSubjectsCount) { this.distinctSubjectsCount = distinctSubjectsCount; this.totalCount = distinctSubjectsCount; }
        public int getTotalHours() { return totalHours; }
        public void setTotalHours(int totalHours) { this.totalHours = totalHours; this.workloadHours = totalHours; }
        public int getWorkloadHours() { return workloadHours; }
        public void setWorkloadHours(int workloadHours) { this.workloadHours = workloadHours; this.totalHours = workloadHours; }
        public int getPref1Matches() { return pref1Matches; }
        public void setPref1Matches(int pref1Matches) { this.pref1Matches = pref1Matches; }
        public int getPref2Matches() { return pref2Matches; }
        public void setPref2Matches(int pref2Matches) { this.pref2Matches = pref2Matches; }
        public int getPref3Matches() { return pref3Matches; }
        public void setPref3Matches(int pref3Matches) { this.pref3Matches = pref3Matches; }
        public int getSatisfactionPercentage() { return satisfactionPercentage; }
        public void setSatisfactionPercentage(int satisfactionPercentage) { this.satisfactionPercentage = satisfactionPercentage; }
        public String getExperienceLevel() { return experienceLevel; }
        public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getZeroAllocationReason() { return zeroAllocationReason; }
        public void setZeroAllocationReason(String zeroAllocationReason) { this.zeroAllocationReason = zeroAllocationReason; }
        public List<String> getZeroAllocationExplanations() { return zeroAllocationExplanations; }
        public void setZeroAllocationExplanations(List<String> zeroAllocationExplanations) { this.zeroAllocationExplanations = zeroAllocationExplanations; }
    }

    public static class AllocationSummaryReport {
        private String academicYear;
        private String department;
        private Integer semester;
        private int workloadLimit;
        private int subjectHours;
        private int maxSubjects;
        private int maxRegular;
        private int maxMock;

        private int totalFaculty;
        private int totalFacultyCount;
        private int facultyAllocated;
        private int facultyWithoutAllocation;
        private int zeroAllocationFacultyCount;
        private int totalSections;
        private int allocatedSections;
        private int unallocatedSections;
        private double pref1SatisfactionPct;
        private double pref2SatisfactionPct;
        private double pref3SatisfactionPct;
        private double avgSectionsPerFaculty;
        private double averageSatisfactionPercentage;
        private double averageWorkloadHours;
        private double allocationCoveragePercentage;
        private int maxFacultyLoadHours;
        private int workloadViolations;
        private int qualificationViolations;

        private List<FacultyReportRow> facultyRows = new ArrayList<>();
        private List<FacultyReportRow> facultyReports = new ArrayList<>();
        private List<AllocationExplanation> explanations = new ArrayList<>();

        public AllocationSummaryReport() {}

        public String getAcademicYear() { return academicYear; }
        public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        public Integer getSemester() { return semester; }
        public void setSemester(Integer semester) { this.semester = semester; }
        public int getWorkloadLimit() { return workloadLimit; }
        public void setWorkloadLimit(int workloadLimit) { this.workloadLimit = workloadLimit; }
        public int getSubjectHours() { return subjectHours; }
        public void setSubjectHours(int subjectHours) { this.subjectHours = subjectHours; }
        public int getMaxSubjects() { return maxSubjects; }
        public void setMaxSubjects(int maxSubjects) { this.maxSubjects = maxSubjects; }
        public int getMaxRegular() { return maxRegular; }
        public void setMaxRegular(int maxRegular) { this.maxRegular = maxRegular; }
        public int getMaxMock() { return maxMock; }
        public void setMaxMock(int maxMock) { this.maxMock = maxMock; }

        public int getTotalFaculty() { return totalFaculty; }
        public void setTotalFaculty(int totalFaculty) { this.totalFaculty = totalFaculty; this.totalFacultyCount = totalFaculty; }
        public int getTotalFacultyCount() { return totalFacultyCount; }
        public void setTotalFacultyCount(int totalFacultyCount) { this.totalFacultyCount = totalFacultyCount; this.totalFaculty = totalFacultyCount; }

        public int getFacultyAllocated() { return facultyAllocated; }
        public void setFacultyAllocated(int facultyAllocated) { this.facultyAllocated = facultyAllocated; }

        public int getFacultyWithoutAllocation() { return facultyWithoutAllocation; }
        public void setFacultyWithoutAllocation(int facultyWithoutAllocation) { this.facultyWithoutAllocation = facultyWithoutAllocation; this.zeroAllocationFacultyCount = facultyWithoutAllocation; }
        public int getZeroAllocationFacultyCount() { return zeroAllocationFacultyCount; }
        public void setZeroAllocationFacultyCount(int zeroAllocationFacultyCount) { this.zeroAllocationFacultyCount = zeroAllocationFacultyCount; this.facultyWithoutAllocation = zeroAllocationFacultyCount; }

        public int getTotalSections() { return totalSections; }
        public void setTotalSections(int totalSections) { this.totalSections = totalSections; }
        public int getAllocatedSections() { return allocatedSections; }
        public void setAllocatedSections(int allocatedSections) { this.allocatedSections = allocatedSections; }
        public int getUnallocatedSections() { return unallocatedSections; }
        public void setUnallocatedSections(int unallocatedSections) { this.unallocatedSections = unallocatedSections; }

        public double getPref1SatisfactionPct() { return pref1SatisfactionPct; }
        public void setPref1SatisfactionPct(double pref1SatisfactionPct) { this.pref1SatisfactionPct = pref1SatisfactionPct; }
        public double getPref2SatisfactionPct() { return pref2SatisfactionPct; }
        public void setPref2SatisfactionPct(double pref2SatisfactionPct) { this.pref2SatisfactionPct = pref2SatisfactionPct; }
        public double getPref3SatisfactionPct() { return pref3SatisfactionPct; }
        public void setPref3SatisfactionPct(double pref3SatisfactionPct) { this.pref3SatisfactionPct = pref3SatisfactionPct; }

        public double getAvgSectionsPerFaculty() { return avgSectionsPerFaculty; }
        public void setAvgSectionsPerFaculty(double avgSectionsPerFaculty) { this.avgSectionsPerFaculty = avgSectionsPerFaculty; }
        public double getAverageSatisfactionPercentage() { return averageSatisfactionPercentage; }
        public void setAverageSatisfactionPercentage(double averageSatisfactionPercentage) { this.averageSatisfactionPercentage = averageSatisfactionPercentage; }
        public double getAverageWorkloadHours() { return averageWorkloadHours; }
        public void setAverageWorkloadHours(double averageWorkloadHours) { this.averageWorkloadHours = averageWorkloadHours; }
        public double getAllocationCoveragePercentage() { return allocationCoveragePercentage; }
        public void setAllocationCoveragePercentage(double allocationCoveragePercentage) { this.allocationCoveragePercentage = allocationCoveragePercentage; }

        public int getMaxFacultyLoadHours() { return maxFacultyLoadHours; }
        public void setMaxFacultyLoadHours(int maxFacultyLoadHours) { this.maxFacultyLoadHours = maxFacultyLoadHours; }
        public int getWorkloadViolations() { return workloadViolations; }
        public void setWorkloadViolations(int workloadViolations) { this.workloadViolations = workloadViolations; }
        public int getQualificationViolations() { return qualificationViolations; }
        public void setQualificationViolations(int qualificationViolations) { this.qualificationViolations = qualificationViolations; }

        public List<FacultyReportRow> getFacultyRows() { return facultyRows; }
        public void setFacultyRows(List<FacultyReportRow> facultyRows) { this.facultyRows = facultyRows; this.facultyReports = facultyRows; }
        public List<FacultyReportRow> getFacultyReports() { return facultyReports; }
        public void setFacultyReports(List<FacultyReportRow> facultyReports) { this.facultyReports = facultyReports; this.facultyRows = facultyReports; }

        public List<AllocationExplanation> getExplanations() { return explanations; }
        public void setExplanations(List<AllocationExplanation> explanations) { this.explanations = explanations; }
    }

    private List<AllocationExplanation> latestAllocationExplanations = new ArrayList<>();
    private AllocationSummaryReport latestAllocationSummaryReport = new AllocationSummaryReport();

    public List<AllocationExplanation> getLatestAllocationExplanations() {
        return new ArrayList<>(latestAllocationExplanations);
    }

    public AllocationSummaryReport getLatestAllocationSummaryReport() {
        return latestAllocationSummaryReport;
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
                    .filter(w -> w.isActive() && w.getAcademicYear() != null && w.getAcademicYear().equalsIgnoreCase(currentAcademicYear) &&
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

        // Progressive fallback 2: Use active years subjects
        if (candidateSubjects.isEmpty()) {
            candidateSubjects = allSubjects.stream()
                .filter(s -> (activeYears.isEmpty() || activeYears.contains(s.getYear())))
                .sorted(java.util.Comparator.comparing(Subject::getYear).thenComparing(Subject::getId))
                .collect(Collectors.toList());
        }

        final List<Subject> subjects = candidateSubjects;

        // Clean out existing allocations and history for all subjects in the target academic year scope
        List<Subject> academicYearSubjects = allSubjects.stream()
            .filter(s -> currentAcademicYear.isEmpty() || currentAcademicYear.equalsIgnoreCase(s.getAcademicYear()))
            .collect(Collectors.toList());

        for (Subject sub : academicYearSubjects) {
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
            List<FacultySubjectPreference> facPrefs = getPreferencesForFaculty(facId);

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
                    Subject flexSub = findSubjectFlexible(subId, currentAcademicYear);
                    if (flexSub != null) prefRankSubMap.put(flexSub.getId().toUpperCase(), pNum);
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
                    Subject flexSub = findSubjectFlexible(subId, currentAcademicYear);
                    if (flexSub != null) prefRankSubMap.put(flexSub.getId().toUpperCase(), pNum);
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
                int allowedTotalSubs = Math.max(maxSubsVal, maxRegVal + maxMockVal);
                if (currentUniqueSubs + 1 > allowedTotalSubs) {
                    return "Maximum distinct subjects limit reached (" + currentUniqueSubs + "/" + allowedTotalSubs + " subjects)";
                }

                int currentRegCount = facultyRegularSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();
                int currentMockCount = facultyMockSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();
                boolean isUnknown = facIdUpper.startsWith("UNKNOWN");

                if (sub.isMock()) {
                    if (!isUnknown && currentRegCount < 2) {
                        return "Mock subject can only be allocated after 2 regular subjects have been allocated (currently allocated: " + currentRegCount + "/2)";
                    }
                    if (currentMockCount + 1 > maxMockVal) {
                        return "Maximum mock subjects limit reached (" + currentMockCount + "/" + maxMockVal + " mock subjects)";
                    }
                    if (!isUnknown && hasPreviousMockAllocation(fId, currentAcademicYear)) {
                        return "Faculty already allocated a mock subject in a previous year";
                    }
                } else {
                    if (currentRegCount + 1 > maxRegVal) {
                        return "Maximum regular subjects limit reached (" + currentRegCount + "/" + maxRegVal + " regular subjects)";
                    }
                }

                // Same-year rule: faculty cannot take 3 or more regular subjects all from the exact same year when multiple active years exist
                if (!sub.isMock() && activeYears.size() > 1) {
                    List<Integer> yearsAllocated = new ArrayList<>();
                    for (String sId : facultyUniqueSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet())) {
                        Subject s = subjectRepository.findById(sId).orElse(null);
                        if (s != null && !s.isMock()) yearsAllocated.add(s.getYear());
                    }
                    yearsAllocated.add(sub.getYear());
                    if (yearsAllocated.size() >= 3 && yearsAllocated.stream().distinct().count() == 1) {
                        return "All allocated regular subjects cannot be from the same study year (" + sub.getYear() + ")";
                    }
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

        // ----------------------------------------------------
        // PHASE 1: FACULTY-WISE PREFERENCE ALLOCATION
        // Process faculty one by one (in submission order / ID order):
        // 1. Allocate regular subjects based on faculty's regular preferences (1 section per preference).
        // 2. ONLY AFTER completion of regular subjects (>= 2 regular subjects), allocate mock subject based on faculty's mock preferences or auto fallback.
        // 3. Move to next faculty member.
        // ----------------------------------------------------

        List<AdminFaculty> sortedFaculties = new ArrayList<>(allFaculty);
        sortedFaculties.sort((f1, f2) -> {
            String f1Upper = f1.getId().toUpperCase();
            String f2Upper = f2.getId().toUpperCase();
            Long sub1 = facultySubmissionOrderMap.getOrDefault(f1Upper, Long.MAX_VALUE);
            Long sub2 = facultySubmissionOrderMap.getOrDefault(f2Upper, Long.MAX_VALUE);
            if (!sub1.equals(sub2)) return Long.compare(sub1, sub2);
            return f1Upper.compareTo(f2Upper);
        });

        // ----------------------------------------------------
        // PHASE 1: ROUND-ROBIN FAIR PREFERENCE ALLOCATION
        // Allocate preferences in rounds across ALL faculty members so EVERY faculty member gets 1st preference before anyone gets 2nd preference, and 2nd preference before anyone gets mock/3rd preference.
        // ----------------------------------------------------

        int maxRegPrefRounds = 0;
        for (AdminFaculty f : sortedFaculties) {
            List<FacultySubjectPreference> facPrefs = getPreferencesForFaculty(f.getId());
            long count = facPrefs.stream().filter(p -> !p.isMock()).count();
            if (count > maxRegPrefRounds) maxRegPrefRounds = (int) count;
        }
        if (maxRegPrefRounds > maxRegVal) maxRegPrefRounds = maxRegVal;
        if (maxRegPrefRounds < 1) maxRegPrefRounds = 1;

        // Round 1 to Max Regular Preferences (Round-Robin across all faculty)
        for (int round = 1; round <= maxRegPrefRounds; round++) {
            final int currentRound = round;
            for (AdminFaculty f : sortedFaculties) {
                String facId = f.getId();
                String facIdUpper = facId.toUpperCase();
                List<FacultySubjectPreference> regPrefs = getPreferencesForFaculty(facId).stream()
                        .filter(p -> !p.isMock())
                        .collect(Collectors.toList());

                int maxRegLimit = (refWindow != null && refWindow.getMaxRegularPreferences() != null) ? refWindow.getMaxRegularPreferences() : 5;
                if (regPrefs.size() > maxRegLimit) regPrefs = regPrefs.subList(0, maxRegLimit);

                if (regPrefs.size() >= currentRound) {
                    FacultySubjectPreference pObj = regPrefs.get(currentRound - 1);
                    String subId = pObj.getSubjectId();
                    if (subId == null || "AUTO_RANDOM".equalsIgnoreCase(subId)) continue;

                    Subject sub = findSubjectFlexible(subId, currentAcademicYear);
                    if (sub == null || sub.isMock()) continue;

                    int currentRegCount = facultyRegularSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();
                    if (currentRegCount >= maxRegVal) continue;

                    if (!canTakeSectionStrict.test(facId, sub)) continue;

                    SectionSlot targetSlot = null;
                    for (SectionSlot slot : pendingSlots) {
                        if (slot.subject.getId().equalsIgnoreCase(sub.getId())) {
                            targetSlot = slot;
                            break;
                        }
                    }
                    if (targetSlot == null) continue;

                    int hoursBefore = facultyWorkloadHours.getOrDefault(facIdUpper, 0);
                    assignSectionToFaculty.accept(targetSlot, facId);
                    int hoursAfter = facultyWorkloadHours.getOrDefault(facIdUpper, 0);

                    AllocationExplanation expl = new AllocationExplanation();
                    expl.setSubjectId(sub.getId());
                    expl.setSubjectName(sub.getName());
                    expl.setSubjectYear(sub.getYear());
                    expl.setSectionName(targetSlot.sectionName);
                    expl.setFacultyId(facId);
                    expl.setFacultyName(f.getName());
                    expl.setPreference("P" + currentRound);
                    expl.setHoursBefore(hoursBefore);
                    expl.setHoursAfter(hoursAfter);
                    expl.setReasonSelected("Selected as Preference P" + currentRound + " in Round " + currentRound);
                    expl.setUnknown(false);
                    explanations.add(expl);

                    pendingSlots.remove(targetSlot);

                    // 1ST PREFERENCE MULTI-SECTION PASS (Up to 2 sections if feasible)
                    if (currentRound == 1) {
                        // Check if a second section of the same 1st preference subject is available
                        SectionSlot secondSlot = null;
                        for (SectionSlot slot : pendingSlots) {
                            if (slot.subject.getId().equalsIgnoreCase(sub.getId())) {
                                secondSlot = slot;
                                break;
                            }
                        }

                        if (secondSlot != null) {
                            int hoursNow = facultyWorkloadHours.getOrDefault(facIdUpper, 0);
                            if (hoursNow + subHours <= hoursLimitVal) {
                                // Feasibility check: check how many other faculty chose this subject as 1st preference and are eligible
                                long otherEligible1stPrefCount = sortedFaculties.stream()
                                        .filter(other -> !other.getId().equalsIgnoreCase(facId))
                                        .filter(other -> {
                                            List<FacultySubjectPreference> otherPrefs = getPreferencesForFaculty(other.getId()).stream()
                                                    .filter(p -> !p.isMock())
                                                    .collect(Collectors.toList());
                                            if (otherPrefs.isEmpty()) return false;
                                            String firstSubId = otherPrefs.get(0).getSubjectId();
                                            if (firstSubId == null) return false;
                                            Subject otherFirstSub = findSubjectFlexible(firstSubId, currentAcademicYear);
                                            return otherFirstSub != null && otherFirstSub.getId().equalsIgnoreCase(sub.getId()) && canTakeSectionStrict.test(other.getId(), sub);
                                        })
                                        .count();

                                long remainingSubSectionsCount = pendingSlots.stream()
                                        .filter(slot -> slot.subject.getId().equalsIgnoreCase(sub.getId()))
                                        .count();

                                // If giving a 2nd section leaves enough sections for all other 1st-preference claimants
                                if (remainingSubSectionsCount - 1 >= otherEligible1stPrefCount) {
                                    int hBefore2 = facultyWorkloadHours.getOrDefault(facIdUpper, 0);
                                    assignSectionToFaculty.accept(secondSlot, facId);
                                    int hAfter2 = facultyWorkloadHours.getOrDefault(facIdUpper, 0);

                                    AllocationExplanation expl2 = new AllocationExplanation();
                                    expl2.setSubjectId(sub.getId());
                                    expl2.setSubjectName(sub.getName());
                                    expl2.setSubjectYear(sub.getYear());
                                    expl2.setSectionName(secondSlot.sectionName);
                                    expl2.setFacultyId(facId);
                                    expl2.setFacultyName(f.getName());
                                    expl2.setPreference("P1");
                                    expl2.setHoursBefore(hBefore2);
                                    expl2.setHoursAfter(hAfter2);
                                    expl2.setReasonSelected("Selected as 2nd Section for Preference P1 in Round 1 (Feasible up to 2 sections rule)");
                                    expl2.setUnknown(false);
                                    explanations.add(expl2);

                                    pendingSlots.remove(secondSlot);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Mock Subject Allocation Phase (Round-Robin for faculty with >= 2 regular subjects)
        for (AdminFaculty f : sortedFaculties) {
            String facId = f.getId();
            String facIdUpper = facId.toUpperCase();
            int currentRegCountAfterReg = facultyRegularSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();
            int currentMockCount = facultyMockSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();

            if (currentRegCountAfterReg >= 2 && currentMockCount < maxMockVal) {
                List<FacultySubjectPreference> mockPrefs = getPreferencesForFaculty(facId).stream()
                        .filter(p -> p.isMock())
                        .collect(Collectors.toList());

                int maxMockLimit = (refWindow != null && refWindow.getMaxMockPreferences() != null) ? refWindow.getMaxMockPreferences() : 2;
                if (mockPrefs.size() > maxMockLimit) mockPrefs = mockPrefs.subList(0, maxMockLimit);

                Subject targetMockSub = null;
                int mockPNum = 0;

                for (int i = 0; i < mockPrefs.size(); i++) {
                    FacultySubjectPreference pObj = mockPrefs.get(i);
                    String subId = pObj.getSubjectId();
                    if (subId == null || "AUTO_RANDOM".equalsIgnoreCase(subId)) continue;

                    Subject sub = findSubjectFlexible(subId, currentAcademicYear);
                    if (sub == null || !sub.isMock()) continue;

                    if (canTakeSectionStrict.test(facId, sub)) {
                        boolean hasSlot = pendingSlots.stream().anyMatch(slot -> slot.subject.getId().equalsIgnoreCase(sub.getId()));
                        if (hasSlot) {
                            targetMockSub = sub;
                            mockPNum = i + 1;
                            break;
                        }
                    }
                }

                if (targetMockSub == null) {
                    for (SectionSlot slot : pendingSlots) {
                        if (slot.subject.isMock() && canTakeSectionStrict.test(facId, slot.subject)) {
                            targetMockSub = slot.subject;
                            mockPNum = 0;
                            break;
                        }
                    }
                }

                if (targetMockSub != null) {
                    SectionSlot targetSlot = null;
                    for (SectionSlot slot : pendingSlots) {
                        if (slot.subject.getId().equalsIgnoreCase(targetMockSub.getId())) {
                            targetSlot = slot;
                            break;
                        }
                    }
                    if (targetSlot != null) {
                        int hoursBefore = facultyWorkloadHours.getOrDefault(facIdUpper, 0);
                        assignSectionToFaculty.accept(targetSlot, facId);
                        int hoursAfter = facultyWorkloadHours.getOrDefault(facIdUpper, 0);

                        AllocationExplanation expl = new AllocationExplanation();
                        expl.setSubjectId(targetMockSub.getId());
                        expl.setSubjectName(targetMockSub.getName());
                        expl.setSubjectYear(targetMockSub.getYear());
                        expl.setSectionName(targetSlot.sectionName);
                        expl.setFacultyId(facId);
                        expl.setFacultyName(f.getName());
                        expl.setPreference(mockPNum > 0 ? "Mock P" + mockPNum : "Auto Mock");
                        expl.setHoursBefore(hoursBefore);
                        expl.setHoursAfter(hoursAfter);
                        expl.setReasonSelected(mockPNum > 0 ? "Selected as Mock Preference Mock P" + mockPNum + " (after completing 2 regular subjects)" : "Allocated available Mock Subject automatically (after completing 2 regular subjects)");
                        expl.setUnknown(false);
                        explanations.add(expl);

                        pendingSlots.remove(targetSlot);
                    }
                }
            }
        }

        // ----------------------------------------------------
        // PHASE 2: WORKLOAD CAPACITY ALLOCATION FOR REAL FACULTY
        // Assign any remaining unallocated sections to real faculty with remaining capacity (fair workload balancing)
        // ----------------------------------------------------
        for (SectionSlot slot : new ArrayList<>(pendingSlots)) {
            Subject sub = slot.subject;
            String subIdUpper = sub.getId().toUpperCase();

            // 1. Try assigning to faculty who chose this subject in preferences and can legally take the subject satisfying strict constraints
            List<AdminFaculty> eligible = allFaculty.stream()
                    .filter(f -> {
                        String facIdUpper = f.getId().toUpperCase();
                        int pRank = facultyPrefRankMap.getOrDefault(facIdUpper, java.util.Collections.emptyMap()).getOrDefault(subIdUpper, 0);
                        return pRank > 0 && canTakeSectionStrict.test(f.getId(), sub);
                    })
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

            // 2. If none, try relaxing same-year rule for preference-choosing faculty while strictly maintaining hours, max subjects, max regular, and max mock
            if (eligible.isEmpty()) {
                eligible = allFaculty.stream()
                        .filter(f -> {
                            String facIdUpper = f.getId().toUpperCase();
                            int pRank = facultyPrefRankMap.getOrDefault(facIdUpper, java.util.Collections.emptyMap()).getOrDefault(subIdUpper, 0);
                            if (pRank <= 0) return false;

                            int currentHours = facultyWorkloadHours.getOrDefault(facIdUpper, 0);
                            if (currentHours + subHours > hoursLimitVal) return false;

                            boolean isAlreadyTeachingSub = facultyUniqueSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).contains(subIdUpper);
                            if (!isAlreadyTeachingSub) {
                                int currentUniqueSubs = facultyUniqueSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();
                                int allowedTotalSubs = Math.max(maxSubsVal, maxRegVal + maxMockVal);
                                if (currentUniqueSubs + 1 > allowedTotalSubs) return false;

                                int currentRegCount = facultyRegularSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();
                                int currentMockCount = facultyMockSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();

                                if (sub.isMock()) {
                                    if (currentRegCount < 2) return false;
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

            // 3. If none, try assigning to real faculty satisfying strict constraints (prioritizing AUTO_RANDOM opt-in faculty)
            if (eligible.isEmpty()) {
                eligible = allFaculty.stream()
                        .filter(f -> canTakeSectionStrict.test(f.getId(), sub))
                        .sorted((f1, f2) -> {
                            String f1Upper = f1.getId().toUpperCase();
                            String f2Upper = f2.getId().toUpperCase();
                            boolean auto1 = facultyPrefRankMap.getOrDefault(f1Upper, java.util.Collections.emptyMap()).containsKey("AUTO_RANDOM");
                            boolean auto2 = facultyPrefRankMap.getOrDefault(f2Upper, java.util.Collections.emptyMap()).containsKey("AUTO_RANDOM");
                            if (auto1 != auto2) return auto1 ? -1 : 1;

                            return candidateComparator.compare(f1, f2);
                        })
                        .collect(Collectors.toList());
            }

            // 4. If none, try assigning to real faculty with relaxed same-year rule (prioritizing AUTO_RANDOM opt-in faculty)
            if (eligible.isEmpty()) {
                eligible = allFaculty.stream()
                        .filter(f -> {
                            String facIdUpper = f.getId().toUpperCase();
                            int currentHours = facultyWorkloadHours.getOrDefault(facIdUpper, 0);
                            if (currentHours + subHours > hoursLimitVal) return false;

                            boolean isAlreadyTeachingSub = facultyUniqueSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).contains(subIdUpper);
                            if (!isAlreadyTeachingSub) {
                                int currentUniqueSubs = facultyUniqueSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();
                                int allowedTotalSubs = Math.max(maxSubsVal, maxRegVal + maxMockVal);
                                if (currentUniqueSubs + 1 > allowedTotalSubs) return false;

                                int currentRegCount = facultyRegularSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();
                                int currentMockCount = facultyMockSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();

                                if (sub.isMock()) {
                                    if (currentRegCount < 2) return false;
                                    if (currentMockCount + 1 > maxMockVal) return false;
                                    if (hasPreviousMockAllocation(f.getId(), currentAcademicYear)) return false;
                                } else {
                                    if (currentRegCount + 1 > maxRegVal) return false;
                                }
                            }
                            return true;
                        })
                        .sorted((f1, f2) -> {
                            String f1Upper = f1.getId().toUpperCase();
                            String f2Upper = f2.getId().toUpperCase();
                            boolean auto1 = facultyPrefRankMap.getOrDefault(f1Upper, java.util.Collections.emptyMap()).containsKey("AUTO_RANDOM");
                            boolean auto2 = facultyPrefRankMap.getOrDefault(f2Upper, java.util.Collections.emptyMap()).containsKey("AUTO_RANDOM");
                            if (auto1 != auto2) return auto1 ? -1 : 1;

                            return candidateComparator.compare(f1, f2);
                        })
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
                boolean isAutoOptIn = facultyPrefRankMap.getOrDefault(chosenFacIdUpper, java.util.Collections.emptyMap()).containsKey("AUTO_RANDOM");
                String pStr = pRank > 0 ? "P" + pRank : (isAutoOptIn ? "Auto" : "None");

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
                expl.setReasonSelected(pRank > 0 ? "Selected in Workload Balancing Phase (Preference " + pStr + ")" : (isAutoOptIn ? "Allocated automatically based on constraints (Completed Subjects Auto Opt-In)" : "Selected in Workload Balancing Phase to satisfy capacity"));
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

                    int holderPrefRank = facultyPrefRankMap.getOrDefault(holderIdUpper, java.util.Collections.emptyMap()).getOrDefault(unallocSubIdUpper, 0);
                    if (holderPrefRank <= 0) continue;

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
                        int allowedTotalSubs = Math.max(maxSubsVal, maxRegVal + maxMockVal);
                        int netUniqueSubs = facultyUniqueSubjects.get(holderIdUpper).size() - (sectionsOfHolderSub <= 1 ? 1 : 0);
                        if (netUniqueSubs + 1 <= allowedTotalSubs) {
                            int netReg = facultyRegularSubjects.get(holderIdUpper).size() - (!holderSub.isMock() && sectionsOfHolderSub <= 1 ? 1 : 0);
                            int netMock = facultyMockSubjects.get(holderIdUpper).size() - (holderSub.isMock() && sectionsOfHolderSub <= 1 ? 1 : 0);
                            if (unallocSub.isMock()) {
                                if (netReg >= 2 && netMock + 1 <= maxMockVal && !hasPreviousMockAllocation(holderFac.getId(), currentAcademicYear)) {
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

                    // Find a candidate target faculty B with capacity who chose holderSub in preferences and can legally take saItem (holderSub)
                    for (AdminFaculty targetFac : facultyWithCapacity) {
                        if (targetFac.getId().equalsIgnoreCase(holderFac.getId())) continue;
                        String targetFacIdUpper = targetFac.getId().toUpperCase();
                        int targetPrefRank = facultyPrefRankMap.getOrDefault(targetFacIdUpper, java.util.Collections.emptyMap()).getOrDefault(holderSub.getId().toUpperCase(), 0);

                        if (targetPrefRank > 0 && canTakeSectionStrict.test(targetFac.getId(), holderSub)) {
                            // Valid augmenting swap found!

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
        // PHASE 3.5: GLOBAL WORKLOAD FAIRNESS & REBALANCING PHASE
        // Check overall workload distribution across all real faculty after initial allocation phases.
        // Shift sections from high-workload faculty to low-workload faculty to equalize workload hours
        // while strictly enforcing all constraints (workload limit, max subjects, max regular, max mock, same-year rule).
        // ----------------------------------------------------
        boolean rebalanceProgress = true;
        int maxRebalancePasses = 50;
        int passCount = 0;

        while (rebalanceProgress && passCount < maxRebalancePasses) {
            rebalanceProgress = false;
            passCount++;

            List<AdminFaculty> highWorkloadFaculties = allFaculty.stream()
                    .sorted((f1, f2) -> Integer.compare(
                            facultyWorkloadHours.getOrDefault(f2.getId().toUpperCase(), 0),
                            facultyWorkloadHours.getOrDefault(f1.getId().toUpperCase(), 0)))
                    .collect(Collectors.toList());

            List<AdminFaculty> lowWorkloadFaculties = allFaculty.stream()
                    .sorted((f1, f2) -> Integer.compare(
                            facultyWorkloadHours.getOrDefault(f1.getId().toUpperCase(), 0),
                            facultyWorkloadHours.getOrDefault(f2.getId().toUpperCase(), 0)))
                    .collect(Collectors.toList());

            if (highWorkloadFaculties.isEmpty() || lowWorkloadFaculties.isEmpty()) break;

            int topHours = facultyWorkloadHours.getOrDefault(highWorkloadFaculties.get(0).getId().toUpperCase(), 0);
            int bottomHours = facultyWorkloadHours.getOrDefault(lowWorkloadFaculties.get(0).getId().toUpperCase(), 0);

            if (topHours - bottomHours <= subHours) {
                break; // Workload is already fairly balanced
            }

            boolean shifted = false;

            for (AdminFaculty donorFac : highWorkloadFaculties) {
                String donorIdUpper = donorFac.getId().toUpperCase();
                int donorHours = facultyWorkloadHours.getOrDefault(donorIdUpper, 0);

                List<SectionAllocation> donorSecAllocs = sectionAllocationRepository.findAll().stream()
                        .filter(sa -> sa.getFacultyId() != null && sa.getFacultyId().equalsIgnoreCase(donorFac.getId()))
                        .collect(Collectors.toList());

                for (SectionAllocation sa : donorSecAllocs) {
                    Subject sub = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                    if (sub == null) continue;

                    String subIdUpper = sub.getId().toUpperCase();

                    for (AdminFaculty recipientFac : lowWorkloadFaculties) {
                        if (recipientFac.getId().equalsIgnoreCase(donorFac.getId())) continue;
                        String recipientIdUpper = recipientFac.getId().toUpperCase();
                        int recipientHours = facultyWorkloadHours.getOrDefault(recipientIdUpper, 0);

                        if (donorHours - recipientHours <= subHours) continue;

                        boolean recipientCanTake = false;
                        boolean recipientAlreadyHasSub = facultyUniqueSubjects.getOrDefault(recipientIdUpper, java.util.Collections.emptySet()).contains(subIdUpper);

                        if (recipientAlreadyHasSub) {
                            if (recipientHours + subHours <= hoursLimitVal) {
                                recipientCanTake = true;
                            }
                        } else {
                            if (canTakeSectionStrict.test(recipientFac.getId(), sub)) {
                                recipientCanTake = true;
                            }
                        }

                        if (recipientCanTake) {
                            // Perform section reassignment from donorFac to recipientFac
                            sa.setFacultyId(recipientFac.getId());
                            sectionAllocationRepository.save(sa);

                            // Update tracking state
                            facultyWorkloadHours.put(donorIdUpper, donorHours - subHours);
                            facultyWorkloadHours.put(recipientIdUpper, recipientHours + subHours);

                            facultyUniqueSections.getOrDefault(donorIdUpper, new java.util.LinkedHashSet<>()).remove(subIdUpper + "_" + sa.getSectionName().toUpperCase());
                            facultyUniqueSections.computeIfAbsent(recipientIdUpper, k -> new java.util.LinkedHashSet<>()).add(subIdUpper + "_" + sa.getSectionName().toUpperCase());

                            long donorRemainingCount = sectionAllocationRepository.findBySubjectId(sub.getId()).stream()
                                    .filter(x -> x.getFacultyId().equalsIgnoreCase(donorFac.getId()))
                                    .count();

                            if (donorRemainingCount == 0) {
                                facultyUniqueSubjects.getOrDefault(donorIdUpper, new java.util.LinkedHashSet<>()).remove(subIdUpper);
                                if (sub.isMock()) {
                                    facultyMockSubjects.getOrDefault(donorIdUpper, new java.util.LinkedHashSet<>()).remove(subIdUpper);
                                } else {
                                    facultyRegularSubjects.getOrDefault(donorIdUpper, new java.util.LinkedHashSet<>()).remove(subIdUpper);
                                }
                                SubjectAllocation oldSubAlloc = allocationRepository.findBySubjectIdAndFacultyId(sub.getId(), donorFac.getId());
                                if (oldSubAlloc != null) {
                                    allocationRepository.delete(oldSubAlloc);
                                    createdSubjectAllocations.remove(oldSubAlloc);
                                }
                            }

                            if (!recipientAlreadyHasSub) {
                                SubjectAllocation newSubAlloc = new SubjectAllocation(sub.getId(), recipientFac.getId());
                                newSubAlloc.setFinalized(false);
                                allocationRepository.save(newSubAlloc);
                                createdSubjectAllocations.add(newSubAlloc);

                                facultyUniqueSubjects.computeIfAbsent(recipientIdUpper, k -> new java.util.LinkedHashSet<>()).add(subIdUpper);
                                if (sub.isMock()) {
                                    facultyMockSubjects.computeIfAbsent(recipientIdUpper, k -> new java.util.LinkedHashSet<>()).add(subIdUpper);
                                } else {
                                    facultyRegularSubjects.computeIfAbsent(recipientIdUpper, k -> new java.util.LinkedHashSet<>()).add(subIdUpper);
                                }
                            }

                            int pRank = facultyPrefRankMap.getOrDefault(recipientIdUpper, java.util.Collections.emptyMap()).getOrDefault(subIdUpper, 0);
                            String pStr = pRank > 0 ? "P" + pRank : "None";

                            AllocationExplanation expl = new AllocationExplanation();
                            expl.setSubjectId(sub.getId());
                            expl.setSubjectName(sub.getName());
                            expl.setSubjectYear(sub.getYear());
                            expl.setSectionName(sa.getSectionName());
                            expl.setFacultyId(recipientFac.getId());
                            expl.setFacultyName(recipientFac.getName());
                            expl.setPreference(pStr);
                            expl.setHoursBefore(recipientHours);
                            expl.setHoursAfter(recipientHours + subHours);
                            expl.setReasonSelected("Reassigned in Fair Workload Balancing Phase from " + donorFac.getName() + " (was " + donorHours + " hrs) to balance overall faculty workload");
                            expl.setUnknown(false);
                            explanations.add(expl);

                            shifted = true;
                            rebalanceProgress = true;
                            break;
                        }
                    }
                    if (shifted) break;
                }
                if (shifted) break;
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

        // ----------------------------------------------------
        // PHASE 5: FINAL AUDIT SAFETY CLEANUP
        // Ensure no real faculty has a mock subject allocation without at least 2 regular subjects allocated
        // ----------------------------------------------------
        for (AdminFaculty f : allFaculty) {
            String facIdUpper = f.getId().toUpperCase();
            int regCount = facultyRegularSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()).size();
            java.util.Set<String> mockSubs = new java.util.HashSet<>(facultyMockSubjects.getOrDefault(facIdUpper, java.util.Collections.emptySet()));
            if (regCount < 2 && !mockSubs.isEmpty()) {
                for (String mockSubId : mockSubs) {
                    List<SubjectAllocation> mockSubAllocs = allocationRepository.findBySubjectId(mockSubId).stream()
                            .filter(a -> a.getFacultyId().equalsIgnoreCase(f.getId()))
                            .collect(Collectors.toList());
                    allocationRepository.deleteAll(mockSubAllocs);

                    List<SectionAllocation> mockSecAllocs = sectionAllocationRepository.findBySubjectId(mockSubId).stream()
                            .filter(a -> a.getFacultyId().equalsIgnoreCase(f.getId()))
                            .collect(Collectors.toList());
                    sectionAllocationRepository.deleteAll(mockSecAllocs);

                    facultyMockSubjects.get(facIdUpper).remove(mockSubId);
                    facultyUniqueSubjects.get(facIdUpper).remove(mockSubId);
                    explanations.removeIf(e -> e.getFacultyId().equalsIgnoreCase(f.getId()) && e.getSubjectId().equalsIgnoreCase(mockSubId));
                }
            }
        }

        // ----------------------------------------------------
        // BUILD ALLOCATION SUMMARY REPORT & ZERO ALLOCATION EXPLANATIONS
        // ----------------------------------------------------
        AllocationSummaryReport summaryReport = new AllocationSummaryReport();
        summaryReport.setAcademicYear(currentAcademicYear);
        summaryReport.setDepartment(filterDepartment);
        summaryReport.setSemester(filterSem);
        summaryReport.setWorkloadLimit(hoursLimitVal);
        summaryReport.setSubjectHours(subHours);
        summaryReport.setMaxSubjects(maxSubsVal);
        summaryReport.setMaxRegular(maxRegVal);
        summaryReport.setMaxMock(maxMockVal);

        List<FacultyReportRow> facultyRows = new ArrayList<>();
        int totalFac = allFaculty.size();
        int zeroCount = 0;
        int sumSatisfaction = 0;
        int totalHoursAll = 0;

        for (AdminFaculty f : allFaculty) {
            String fIdUpper = f.getId().toUpperCase();
            FacultyReportRow row = new FacultyReportRow();
            row.setFacultyId(f.getId());
            row.setFacultyName(f.getName());

            // Preferences list
            List<FacultySubjectPreference> fPrefs = getPreferencesForFaculty(f.getId());
            List<String> regPrefNames = fPrefs.stream()
                .filter(p -> !p.isMock() && p.getSubjectId() != null && !"AUTO_RANDOM".equalsIgnoreCase(p.getSubjectId()))
                .map(p -> {
                    Subject s = findSubjectFlexible(p.getSubjectId(), currentAcademicYear);
                    return s != null ? s.getName() + " (" + s.getId() + ")" : p.getSubjectId();
                }).collect(Collectors.toList());
            row.setPreferences(regPrefNames);

            // Allocated sections list
            List<String> allocSecs = sectionAllocationRepository.findByFacultyId(f.getId()).stream()
                .map(sa -> sa.getSubjectId() + " Section " + sa.getSectionName())
                .collect(Collectors.toList());
            row.setAllocatedSections(allocSecs);

            int hours = facultyWorkloadHours.getOrDefault(fIdUpper, 0);
            row.setWorkloadHours(hours);
            totalHoursAll += hours;

            int distinctSubs = facultyUniqueSubjects.getOrDefault(fIdUpper, java.util.Collections.emptySet()).size();
            row.setDistinctSubjectsCount(distinctSubs);

            // Compute satisfaction percentage
            int bestPrefRank = 999;
            java.util.Set<String> allocatedSubs = facultyUniqueSubjects.getOrDefault(fIdUpper, java.util.Collections.emptySet());
            for (String subIdUpper : allocatedSubs) {
                int rank = facultyPrefRankMap.getOrDefault(fIdUpper, java.util.Collections.emptyMap()).getOrDefault(subIdUpper, 0);
                if (rank > 0 && rank < bestPrefRank) bestPrefRank = rank;
            }

            int satisfaction = 0;
            if (bestPrefRank == 1) satisfaction = 100;
            else if (bestPrefRank == 2) satisfaction = 80;
            else if (bestPrefRank == 3) satisfaction = 60;
            else if (bestPrefRank == 4) satisfaction = 40;
            else if (bestPrefRank == 5) satisfaction = 20;
            else if (!allocatedSubs.isEmpty()) satisfaction = 50;

            row.setSatisfactionPercentage(satisfaction);
            sumSatisfaction += satisfaction;

            // Zero Allocation Explanations
            if (allocSecs.isEmpty()) {
                zeroCount++;
                List<String> zeroReasons = new ArrayList<>();
                for (int i = 0; i < fPrefs.size(); i++) {
                    FacultySubjectPreference p = fPrefs.get(i);
                    if (p.isMock()) continue;
                    String pSubId = p.getSubjectId();
                    if (pSubId == null || "AUTO_RANDOM".equalsIgnoreCase(pSubId)) continue;
                    Subject pSub = findSubjectFlexible(pSubId, currentAcademicYear);
                    if (pSub == null) continue;

                    String rej = getRejectionReason.apply(f.getId(), pSub);
                    if (rej == null) {
                        zeroReasons.add("Preference P" + (i + 1) + " (" + pSub.getName() + "): All sections were allocated to higher-priority faculty during preference rounds.");
                    } else {
                        zeroReasons.add("Preference P" + (i + 1) + " (" + pSub.getName() + "): " + rej);
                    }
                }
                if (zeroReasons.isEmpty()) {
                    zeroReasons.add("No preferences submitted or available sections were exhausted across all preference rounds.");
                }
                row.setZeroAllocationExplanations(zeroReasons);
            }

            facultyRows.add(row);
        }

        summaryReport.setFacultyReports(facultyRows);
        summaryReport.setTotalFacultyCount(totalFac);
        summaryReport.setZeroAllocationFacultyCount(zeroCount);
        summaryReport.setAverageSatisfactionPercentage(totalFac > 0 ? (double) sumSatisfaction / totalFac : 0.0);
        summaryReport.setAverageWorkloadHours(totalFac > 0 ? (double) totalHoursAll / totalFac : 0.0);

        long totalSecCount = explanations.size();
        long unknownSecCount = explanations.stream().filter(AllocationExplanation::isUnknown).count();
        double cov = totalSecCount > 0 ? (((double) (totalSecCount - unknownSecCount)) / totalSecCount) * 100.0 : 100.0;
        summaryReport.setAllocationCoveragePercentage(cov);

        this.latestAllocationSummaryReport = summaryReport;

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

        java.util.Set<Integer> targetAllocatedYears = sectionAllocationRepository.findAll().stream()
                .map(sa -> sa.getSubjectId() != null ? subjectRepository.findById(sa.getSubjectId()).orElse(null) : null)
                .filter(java.util.Objects::nonNull)
                .map(Subject::getYear)
                .collect(Collectors.toSet());

        if (targetAllocatedYears.isEmpty()) {
            targetAllocatedYears = allocationRepository.findAll().stream()
                    .map(sa -> sa.getSubjectId() != null ? subjectRepository.findById(sa.getSubjectId()).orElse(null) : null)
                    .filter(java.util.Objects::nonNull)
                    .map(Subject::getYear)
                    .collect(Collectors.toSet());
        }

        final java.util.Set<Integer> finalTargetYears = targetAllocatedYears;

        // Clear out history entries for non-target study years in this academic year to prevent stray records
        if (!finalTargetYears.isEmpty()) {
            List<AllocationHistory> obsoleteHistory = allocationHistoryRepository.findByAcademicYear(currentYear).stream()
                    .filter(h -> {
                        if (h.getSubjectId() == null) return true;
                        Subject s = subjectRepository.findById(h.getSubjectId()).orElse(null);
                        return s != null && !finalTargetYears.contains(s.getYear());
                    })
                    .collect(Collectors.toList());
            allocationHistoryRepository.deleteAll(obsoleteHistory);
        }

        List<SubjectAllocation> subAllocs = allocationRepository.findAll();
        for (SubjectAllocation sa : subAllocs) {
            if (sa.getSubjectId() != null) {
                Subject sub = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                if (sub != null && !targetAllocatedYears.isEmpty() && !targetAllocatedYears.contains(sub.getYear())) {
                    allocationRepository.delete(sa);
                    continue;
                }
            }
            sa.setFinalized(true);
            allocationRepository.save(sa);
        }

        List<SectionAllocation> secAllocs = sectionAllocationRepository.findAll();
        for (SectionAllocation sa : secAllocs) {
            if (sa.getSubjectId() != null) {
                Subject sub = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                if (sub != null && !targetAllocatedYears.isEmpty() && !targetAllocatedYears.contains(sub.getYear())) {
                    sectionAllocationRepository.delete(sa);
                    continue;
                }
            }
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
        List<Subject> allSubjects = subjectRepository.findAll();
        java.util.Set<String> targetSubjectIds = new java.util.HashSet<>();
        for (Subject s : allSubjects) {
            if (years.contains(s.getYear())) {
                if (s.getId() != null) {
                    String cleanId = s.getId().trim().toLowerCase();
                    targetSubjectIds.add(cleanId);
                    int underscoreIdx = cleanId.indexOf('_');
                    if (underscoreIdx != -1) {
                        targetSubjectIds.add(cleanId.substring(0, underscoreIdx).trim().toLowerCase());
                    }
                }
            }
        }
        
        List<SubjectAllocation> allAllocs = allocationRepository.findAll();
        List<SubjectAllocation> allocsToDelete = allAllocs.stream()
                .filter(sa -> {
                    if (sa.getSubjectId() == null) return false;
                    String subIdLower = sa.getSubjectId().trim().toLowerCase();
                    if (targetSubjectIds.contains(subIdLower)) return true;
                    int underscoreIdx = subIdLower.indexOf('_');
                    if (underscoreIdx != -1 && targetSubjectIds.contains(subIdLower.substring(0, underscoreIdx))) return true;
                    Subject sub = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                    return sub != null && years.contains(sub.getYear());
                })
                .collect(Collectors.toList());
        if (!allocsToDelete.isEmpty()) {
            allocationRepository.deleteAll(allocsToDelete);
        }

        List<SectionAllocation> allSecAllocs = sectionAllocationRepository.findAll();
        List<SectionAllocation> secAllocsToDelete = allSecAllocs.stream()
                .filter(sa -> {
                    if (sa.getSubjectId() == null) return false;
                    String subIdLower = sa.getSubjectId().trim().toLowerCase();
                    if (targetSubjectIds.contains(subIdLower)) return true;
                    int underscoreIdx = subIdLower.indexOf('_');
                    if (underscoreIdx != -1 && targetSubjectIds.contains(subIdLower.substring(0, underscoreIdx))) return true;
                    Subject sub = subjectRepository.findById(sa.getSubjectId()).orElse(null);
                    return sub != null && years.contains(sub.getYear());
                })
                .collect(Collectors.toList());
        if (!secAllocsToDelete.isEmpty()) {
            sectionAllocationRepository.deleteAll(secAllocsToDelete);
        }

        List<AllocationHistory> allHistAllocs = allocationHistoryRepository.findAll();
        List<AllocationHistory> histToDelete = allHistAllocs.stream()
                .filter(ha -> {
                    if (ha.getSubjectId() == null) return false;
                    String subIdLower = ha.getSubjectId().trim().toLowerCase();
                    if (targetSubjectIds.contains(subIdLower)) return true;
                    int underscoreIdx = subIdLower.indexOf('_');
                    if (underscoreIdx != -1 && targetSubjectIds.contains(subIdLower.substring(0, underscoreIdx))) return true;
                    Subject sub = subjectRepository.findById(ha.getSubjectId()).orElse(null);
                    return sub != null && years.contains(sub.getYear());
                })
                .collect(Collectors.toList());
        if (!histToDelete.isEmpty()) {
            allocationHistoryRepository.deleteAll(histToDelete);
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
        
        List<Subject> allSubs = subjectRepository.findAll();
        java.util.Map<String, Subject> subMap = new java.util.HashMap<>();
        for (Subject s : allSubs) {
            if (s.getId() != null) {
                subMap.put(s.getId().toLowerCase().trim(), s);
            }
        }
        
        String trimmedYear = (academicYear != null) ? academicYear.trim() : "";
        String normalizedTargetYear = trimmedYear.replaceAll("\\s+", "").toLowerCase();
        List<FacultySubjectPreference> filtered = all.stream()
            .filter(p -> {
                if (p.getSubjectId() == null) return false;
                String subIdLower = p.getSubjectId().toLowerCase().trim();
                if ("auto_random".equalsIgnoreCase(subIdLower)) {
                    return true;
                }
                Subject sub = subMap.get(subIdLower);
                if (sub == null) {
                    // Try to find a subject that starts with subIdLower + "_" (fallback for prefix mapping)
                    String prefix = subIdLower + "_";
                    for (java.util.Map.Entry<String, Subject> entry : subMap.entrySet()) {
                        if (entry.getKey().startsWith(prefix)) {
                            sub = entry.getValue();
                            break;
                        }
                    }
                }
                
                String preferenceAcadYear = null;
                if (sub != null) {
                    preferenceAcadYear = sub.getAcademicYear();
                } else {
                    // Try to extract academic year from subjectId (e.g., "cs101_2026-27" -> "2026-27")
                    int underscoreIndex = subIdLower.lastIndexOf('_');
                    if (underscoreIndex != -1 && underscoreIndex < subIdLower.length() - 1) {
                        preferenceAcadYear = subIdLower.substring(underscoreIndex + 1);
                    }
                }
                
                if (trimmedYear.isEmpty()) return true;
                if (preferenceAcadYear == null) {
                    // If we cannot determine the academic year, assume it matches the queried year as a fallback
                    return true;
                }
                return preferenceAcadYear.replaceAll("\\s+", "").equalsIgnoreCase(normalizedTargetYear);
            })
            .collect(Collectors.toList());
        filtered.sort(java.util.Comparator.comparing(FacultySubjectPreference::getId));
        return filtered;
    }

    @Transactional
    public void clearPreferencesByAcademicYear(String academicYear) {
        String trimmedYear = (academicYear != null) ? academicYear.trim() : "";
        if (trimmedYear.isEmpty()) return;
        
        String normalizedTargetYear = trimmedYear.replaceAll("\\s+", "").toLowerCase();
        List<Subject> subjects = subjectRepository.findAll();
        java.util.Set<String> subIds = subjects.stream()
            .filter(s -> s.getAcademicYear() != null && s.getAcademicYear().replaceAll("\\s+", "").equalsIgnoreCase(normalizedTargetYear))
            .map(Subject::getId)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
        List<FacultySubjectPreference> all = preferenceRepository.findAll();
        List<FacultySubjectPreference> toDelete = all.stream()
            .filter(p -> {
                if (p.getSubjectId() == null) return false;
                String subIdLower = p.getSubjectId().toLowerCase();
                if (subIds.contains(subIdLower)) return true;
                
                // Check if any matching subject starts with this ID
                String prefix = subIdLower + "_";
                if (subIds.stream().anyMatch(id -> id.startsWith(prefix))) return true;

                // Also check if the subjectId ends with _academicYear (case-insensitive, space-insensitive)
                String normalizedSubId = subIdLower.replaceAll("\\s+", "");
                String suffix = "_" + normalizedTargetYear;
                return normalizedSubId.endsWith(suffix);
            })
            .collect(Collectors.toList());
        preferenceRepository.deleteAll(toDelete);
        EARLIEST_YEAR_SUBMISSION_MAP.clear();
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

    public List<FacultySubjectPreference> getPreferencesForFaculty(String facultyId) {
        if (facultyId == null) return new ArrayList<>();
        String cleanId = facultyId.trim();
        List<FacultySubjectPreference> list = preferenceRepository.findByFacultyIdIgnoreCaseOrderByIdAsc(cleanId);
        if (list.isEmpty()) {
            list = preferenceRepository.findAll().stream()
                    .filter(p -> p.getFacultyId() != null && p.getFacultyId().trim().equalsIgnoreCase(cleanId))
                    .sorted(java.util.Comparator.comparing(FacultySubjectPreference::getId))
                    .collect(Collectors.toList());
        }
        return list;
    }

    public Subject findSubjectFlexible(String subId, String currentAcademicYear) {
        if (subId == null || subId.trim().isEmpty() || "AUTO_RANDOM".equalsIgnoreCase(subId.trim())) return null;
        String cleanId = subId.trim();

        Subject sub = subjectRepository.findById(cleanId).orElse(null);
        if (sub != null) return sub;

        List<Subject> all = subjectRepository.findAll();
        for (Subject s : all) {
            if (s.getId() != null && s.getId().equalsIgnoreCase(cleanId)) return s;
        }

        String targetPrefix = cleanId.toLowerCase() + "_";
        for (Subject s : all) {
            if (s.getId() != null) {
                String sIdLower = s.getId().toLowerCase();
                if (sIdLower.startsWith(targetPrefix)) {
                    if (currentAcademicYear == null || currentAcademicYear.isEmpty() || currentAcademicYear.equalsIgnoreCase(s.getAcademicYear())) {
                        return s;
                    }
                }
            }
        }

        for (Subject s : all) {
            if (s.getId() != null && s.getId().toLowerCase().startsWith(targetPrefix)) {
                return s;
            }
        }

        int underscoreIdx = cleanId.indexOf('_');
        if (underscoreIdx != -1) {
            String baseCode = cleanId.substring(0, underscoreIdx);
            for (Subject s : all) {
                if (s.getId() != null) {
                    String sId = s.getId();
                    int sUnderscore = sId.indexOf('_');
                    String sBase = (sUnderscore != -1) ? sId.substring(0, sUnderscore) : sId;
                    if (sBase.equalsIgnoreCase(baseCode)) return s;
                }
            }
        }

        return null;
    }
}
