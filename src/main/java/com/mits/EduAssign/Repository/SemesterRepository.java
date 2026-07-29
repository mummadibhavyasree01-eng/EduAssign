package com.mits.EduAssign.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.mits.EduAssign.Entity.Semester;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, Integer> {
}
