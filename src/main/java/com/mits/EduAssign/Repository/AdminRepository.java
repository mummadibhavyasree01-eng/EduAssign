package com.mits.EduAssign.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mits.EduAssign.Entity.AdminFaculty;

@Repository
public interface AdminRepository extends JpaRepository<AdminFaculty,String>{
	public AdminFaculty findByEmailAndPassword(String email,String password);

	public AdminFaculty findByEmailIgnoreCaseAndPassword(String email, String password);

	public List<AdminFaculty> findByRole(String role);

	public List<AdminFaculty> findByRoleIgnoreCase(String role);
}
