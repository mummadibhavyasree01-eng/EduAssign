package com.mits.EduAssign.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.mits.EduAssign.Entity.SubjectAllocation;

@Repository
public interface AllocationRepository extends JpaRepository<SubjectAllocation, Long> {
    List<SubjectAllocation> findByFacultyId(String facultyId);
    SubjectAllocation findBySubjectIdAndSectionName(String subjectId, String sectionName);
    SubjectAllocation findBySubjectIdAndFacultyId(String subjectId, String facultyId);
    List<SubjectAllocation> findBySubjectId(String subjectId);
}
