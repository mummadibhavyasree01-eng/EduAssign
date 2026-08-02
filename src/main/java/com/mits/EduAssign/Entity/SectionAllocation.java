package com.mits.EduAssign.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class SectionAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="SubjectId", nullable=false)
    private String subjectId;

    @Column(name="SectionName", nullable=false)
    private String sectionName;

    @Column(name="FacultyId", nullable=false)
    private String facultyId;

    @Column(name="OriginalUnknownFacultyId", nullable=true)
    private String originalUnknownFacultyId;

    @Column(name="Finalized", nullable=false)
    private boolean finalized = false;

    public SectionAllocation() {}

    public SectionAllocation(String subjectId, String sectionName, String facultyId) {
        this.subjectId = subjectId;
        this.sectionName = sectionName;
        this.facultyId = facultyId;
        this.finalized = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(String facultyId) {
        this.facultyId = facultyId;
    }

    public String getOriginalUnknownFacultyId() {
        return originalUnknownFacultyId;
    }

    public void setOriginalUnknownFacultyId(String originalUnknownFacultyId) {
        this.originalUnknownFacultyId = originalUnknownFacultyId;
    }

    public boolean isFinalized() {
        return finalized;
    }

    public void setFinalized(boolean finalized) {
        this.finalized = finalized;
    }
}
