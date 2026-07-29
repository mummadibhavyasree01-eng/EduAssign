package com.mits.EduAssign.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.mits.EduAssign.Entity.AllocationHistory;

@Repository
public interface AllocationHistoryRepository extends JpaRepository<AllocationHistory, Long> {
    List<AllocationHistory> findByFacultyId(String facultyId);
    List<AllocationHistory> findBySubjectId(String subjectId);
    List<AllocationHistory> findByAcademicYear(String academicYear);
    List<AllocationHistory> findByFacultyIdAndSubjectId(String facultyId, String subjectId);
    List<AllocationHistory> findByDepartmentAndAcademicYear(String department, String academicYear);
}
