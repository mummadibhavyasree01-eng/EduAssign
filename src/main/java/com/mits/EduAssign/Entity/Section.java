package com.mits.EduAssign.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Section {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="DepartmentCode", nullable=false)
    private String departmentCode;

    @Column(name="YearNumber", nullable=false)
    private Integer yearNumber;

    @Column(name="SectionName", nullable=false)
    private String sectionName;

    @Column(name="AcademicYear", nullable=true)
    private String academicYear;

    public Section() {}

    public Section(String departmentCode, Integer yearNumber, String sectionName) {
        this.departmentCode = departmentCode;
        this.yearNumber = yearNumber;
        this.sectionName = sectionName;
    }

    public Section(String departmentCode, Integer yearNumber, String sectionName, String academicYear) {
        this.departmentCode = departmentCode;
        this.yearNumber = yearNumber;
        this.sectionName = sectionName;
        this.academicYear = academicYear;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String departmentCode) {
        this.departmentCode = departmentCode;
    }

    public Integer getYearNumber() {
        return yearNumber;
    }

    public void setYearNumber(Integer yearNumber) {
        this.yearNumber = yearNumber;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }
}
