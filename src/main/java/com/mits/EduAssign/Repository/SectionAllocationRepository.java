package com.mits.EduAssign.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.mits.EduAssign.Entity.SectionAllocation;

@Repository
public interface SectionAllocationRepository extends JpaRepository<SectionAllocation, Long> {
    List<SectionAllocation> findByFacultyId(String facultyId);
    List<SectionAllocation> findByFacultyIdAndFinalized(String facultyId, boolean finalized);
    SectionAllocation findBySubjectIdAndSectionName(String subjectId, String sectionName);
    SectionAllocation findBySubjectIdAndFacultyId(String subjectId, String facultyId);
    List<SectionAllocation> findBySubjectId(String subjectId);
}
