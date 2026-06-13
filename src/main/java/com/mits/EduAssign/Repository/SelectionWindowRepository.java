package com.mits.EduAssign.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.mits.EduAssign.Entity.SubjectSelectionWindow;

@Repository
public interface SelectionWindowRepository extends JpaRepository<SubjectSelectionWindow, Integer> {
    SubjectSelectionWindow findTopByOrderByIdDesc();
}
