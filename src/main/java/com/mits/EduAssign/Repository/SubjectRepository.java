package com.mits.EduAssign.Repository;

import com.mits.EduAssign.Entity.Subject;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectRepository extends JpaRepository<Subject,String> {

}