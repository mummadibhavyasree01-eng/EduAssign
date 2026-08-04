package com.mits.EduAssign.Entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class SubjectSelectionWindow {
    @Id
    private Integer id;

    @Column(name="Message", nullable=false)
    private String message;

    @Column(name="Deadline", nullable=false)
    private LocalDateTime deadline;

    @Column(name="Active", nullable=false)
    private boolean active;

    @Column(name="Sem")
    private Integer sem;

    @Column(name="AcademicYear")
    private String academicYear;

    @Column(name="Department")
    private String department;

    @Column(name="year")
    private Integer year;

    @Column(name="hours_per_week")
    private Integer hoursPerWeek;

    @Column(name="max_subjects_allocated")
    private Integer maxSubjectsAllocated;

    @Column(name="subject_hours_per_week")
    private Integer subjectHoursPerWeek;

    @Column(name="max_regular_preferences")
    private Integer maxRegularPreferences;

    @Column(name="max_mock_preferences")
    private Integer maxMockPreferences;

    public SubjectSelectionWindow() {}

    public SubjectSelectionWindow(String message, LocalDateTime deadline, boolean active, Integer sem, String academicYear, String department) {
        this.message = message;
        this.deadline = deadline;
        this.active = active;
        this.sem = sem;
        this.academicYear = academicYear;
        this.department = department;
    }

    public Integer getSem() {
        return sem;
    }

    public void setSem(Integer sem) {
        this.sem = sem;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getHoursPerWeek() {
        return hoursPerWeek;
    }

    public void setHoursPerWeek(Integer hoursPerWeek) {
        this.hoursPerWeek = hoursPerWeek;
    }

    public Integer getMaxSubjectsAllocated() {
        return maxSubjectsAllocated;
    }

    public void setMaxSubjectsAllocated(Integer maxSubjectsAllocated) {
        this.maxSubjectsAllocated = maxSubjectsAllocated;
    }

    public Integer getSubjectHoursPerWeek() {
        return subjectHoursPerWeek;
    }

    public void setSubjectHoursPerWeek(Integer subjectHoursPerWeek) {
        this.subjectHoursPerWeek = subjectHoursPerWeek;
    }

    public Integer getMaxRegularPreferences() {
        return maxRegularPreferences;
    }

    public void setMaxRegularPreferences(Integer maxRegularPreferences) {
        this.maxRegularPreferences = maxRegularPreferences;
    }

    public Integer getMaxMockPreferences() {
        return maxMockPreferences;
    }

    public void setMaxMockPreferences(Integer maxMockPreferences) {
        this.maxMockPreferences = maxMockPreferences;
    }
}
