package com.mits.EduAssign.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Semester {
    @Id
    @Column(name="SemNumber", nullable=false)
    private Integer semNumber;

    @Column(name="Name", nullable=false)
    private String name;

    public Semester() {}

    public Semester(Integer semNumber, String name) {
        this.semNumber = semNumber;
        this.name = name;
    }

    public Integer getSemNumber() {
        return semNumber;
    }

    public void setSemNumber(Integer semNumber) {
        this.semNumber = semNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
