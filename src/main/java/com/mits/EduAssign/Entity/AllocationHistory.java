package com.mits.EduAssign.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class AllocationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "AcademicYear", nullable = false)
    private String academicYear;

    @Column(name = "Department", nullable = false)
    private String department;

    @Column(name = "Semester")
    private Integer semester;

    @Column(name = "FacultyId", nullable = false)
    private String facultyId;

    @Column(name = "SubjectId", nullable = false)
    private String subjectId;

    @Column(name = "SectionName")
    private String sectionName;

    public AllocationHistory() {}

    public AllocationHistory(String academicYear, String department, Integer semester, String facultyId, String subjectId, String sectionName) {
        this.academicYear = academicYear;
        this.department = department;
        this.semester = semester;
        this.facultyId = facultyId;
        this.subjectId = subjectId;
        this.sectionName = sectionName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Integer getSemester() {
        return semester;
    }

    public void setSemester(Integer semester) {
        this.semester = semester;
    }

    public String getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(String facultyId) {
        this.facultyId = facultyId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }
}
