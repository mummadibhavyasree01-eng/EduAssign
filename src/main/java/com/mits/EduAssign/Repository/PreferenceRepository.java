package com.mits.EduAssign.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.mits.EduAssign.Entity.FacultySubjectPreference;

@Repository
public interface PreferenceRepository extends JpaRepository<FacultySubjectPreference, Long> {
    List<FacultySubjectPreference> findByFacultyId(String facultyId);
    List<FacultySubjectPreference> findByFacultyIdOrderByIdAsc(String facultyId);
    List<FacultySubjectPreference> findByFacultyIdIgnoreCaseOrderByIdAsc(String facultyId);
    void deleteByFacultyId(String facultyId);
    void deleteByFacultyIdIgnoreCase(String facultyId);
}
