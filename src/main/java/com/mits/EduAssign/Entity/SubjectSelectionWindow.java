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

    public SubjectSelectionWindow() {}

    public SubjectSelectionWindow(String message, LocalDateTime deadline, boolean active) {
        this.message = message;
        this.deadline = deadline;
        this.active = active;
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
}
