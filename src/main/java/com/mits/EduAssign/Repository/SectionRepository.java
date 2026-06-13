package com.mits.EduAssign.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.mits.EduAssign.Entity.Section;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findByDepartmentCodeAndYearNumber(String departmentCode, Integer yearNumber);
    Section findByDepartmentCodeAndYearNumberAndSectionName(String departmentCode, Integer yearNumber, String sectionName);
}
