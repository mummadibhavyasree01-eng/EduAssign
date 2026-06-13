package com.mits.EduAssign.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.mits.EduAssign.Entity.Department;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, String> {
}
