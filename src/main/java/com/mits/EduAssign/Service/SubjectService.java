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
import com.mits.EduAssign.Repository.SubjectRepository;
import com.mits.EduAssign.Repository.SelectionWindowRepository;
import com.mits.EduAssign.Repository.PreferenceRepository;
import com.mits.EduAssign.Repository.AllocationRepository;

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
        return preferenceRepository.findByFacultyId(facultyId);
    }

    // ----------------------------------------------------
    // SUBJECT SELECTION DEADLINE WINDOW
    // ----------------------------------------------------

    public SubjectSelectionWindow setDeadline(String message, int days) {
        SubjectSelectionWindow window = windowRepository.findTopByOrderByIdDesc();
        if (window == null) {
            window = new SubjectSelectionWindow();
        }
        window.setMessage(message);
        window.setDeadline(LocalDateTime.now().plusDays(days));
        window.setActive(true);
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

    @Transactional
    public SubjectAllocation allocateSubject(SubjectAllocation allocation) {
        // Rule 1: Cannot allocate before deadline
        if (isBeforeDeadline()) {
            throw new IllegalStateException("Still there is time for faculty to select subjects. You cannot perform allocation before the deadline.");
        }

        // Rule 2: Section already allocated
        SubjectAllocation existingSecAlloc = allocationRepository.findBySubjectIdAndSectionName(
                allocation.getSubjectId(), allocation.getSectionName());
        if (existingSecAlloc != null) {
            throw new IllegalArgumentException("This section of the subject is already allocated to another faculty member.");
        }

        // Rule 3: Faculty member already allocated to another section of this subject
        SubjectAllocation existingFacultyAlloc = allocationRepository.findBySubjectIdAndFacultyId(
                allocation.getSubjectId(), allocation.getFacultyId());
        if (existingFacultyAlloc != null) {
            throw new IllegalArgumentException("This faculty member is already allocated to another section of this subject.");
        }

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
}
