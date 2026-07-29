package com.mits.EduAssign.Entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class SubjectSelectionWindow {
    @Id
    private Integer id = 1;

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
}
