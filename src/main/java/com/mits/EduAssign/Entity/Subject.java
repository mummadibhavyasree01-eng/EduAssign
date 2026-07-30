package com.mits.EduAssign.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Subject {
	@Id
	@Column(name="SubjectCode",nullable=false)
	private String id;
	@Column(name="SubjectName",nullable=false)
	private String name;
	@Column(name="year",nullable=false)
	private int year;
	@Column(name="Semester",nullable=false)
	private int sem;
	@Column(name="Regulation",nullable=false)
	private String regulation;
	@Column(name="Department",nullable=false)
	private String dep;
	@Column(name="AcademicYear")
	private String academicYear;
	@Column(name="is_mock", nullable=false)
	private boolean isMock = false;

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public int getYear() {
		return year;
	}
	public void setYear(int year) {
		this.year = year;
	}
	public int getSem() {
		return sem;
	}
	public void setSem(int sem) {
		this.sem = sem;
	}
	public String getRegulation() {
		return regulation;
	}
	public void setRegulation(String regulation) {
		this.regulation = regulation;
	}
	public String getDep() {
		return dep;
	}
	public void setDep(String dep) {
		this.dep = dep;
	}
	public String getAcademicYear() {
		return academicYear;
	}
	public void setAcademicYear(String academicYear) {
		this.academicYear = academicYear;
	}
	public boolean isMock() {
		return isMock;
	}
	public void setMock(boolean isMock) {
		this.isMock = isMock;
	}
}
