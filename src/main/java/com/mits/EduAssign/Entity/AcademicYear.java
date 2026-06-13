package com.mits.EduAssign.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class AcademicYear {
    @Id
    @Column(name="YearNumber", nullable=false)
    private Integer yearNumber;

    @Column(name="Name", nullable=false)
    private String name;

    public AcademicYear() {}

    public AcademicYear(Integer yearNumber, String name) {
        this.yearNumber = yearNumber;
        this.name = name;
    }

    public Integer getYearNumber() {
        return yearNumber;
    }

    public void setYearNumber(Integer yearNumber) {
        this.yearNumber = yearNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
