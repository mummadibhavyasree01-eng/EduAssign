package com.mits.EduAssign.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class AdminFaculty {
	@Id
	@Column(name="Id",nullable=false)
	private String id;
@Column(name="Name",nullable=false)
private String name;
@Column(name="EmailId",nullable=false)
private String email;
@Column(name="Password",nullable=false)
private String password;
@Column(name="Role",nullable=false)
private String role;
public void setId(String id) {
	this.id=id;
}
public void setName(String name) {
	this.name=name;
}
public void setEmail(String email) {
	this.email=email;
}
public void setPassword(String password) {
	this.password=password;
}
public void setRole(String role) {
	this.role=role;
}
public String getId() {
	return id;
}
public String getName() {
	return name;
}
public String getEmail() {
	return email;
}
public String getPassword() {
	return password;
}
public String getRole() {
	return role;
}
}
























