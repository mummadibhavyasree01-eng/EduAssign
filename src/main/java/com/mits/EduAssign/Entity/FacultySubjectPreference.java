package com.mits.EduAssign.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class FacultySubjectPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="FacultyId", nullable=false)
    private String facultyId;

    @Column(name="SubjectId", nullable=false)
    private String subjectId;

    @Column(name="is_mock", nullable=false)
    private boolean isMock = false;

    public FacultySubjectPreference() {}

    public FacultySubjectPreference(String facultyId, String subjectId) {
        this.facultyId = facultyId;
        this.subjectId = subjectId;
        this.isMock = false;
    }

    public FacultySubjectPreference(String facultyId, String subjectId, boolean isMock) {
        this.facultyId = facultyId;
        this.subjectId = subjectId;
        this.isMock = isMock;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public boolean isMock() {
        return isMock;
    }

    public void setMock(boolean isMock) {
        this.isMock = isMock;
    }
}
