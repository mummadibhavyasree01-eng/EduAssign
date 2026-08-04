package com.mits.EduAssign;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EduAssignApplicationTests {

	@org.springframework.beans.factory.annotation.Autowired
	private com.mits.EduAssign.Service.SubjectService subjectService;
	@org.springframework.beans.factory.annotation.Autowired
	private com.mits.EduAssign.Repository.SubjectRepository subRepo;
	@org.springframework.beans.factory.annotation.Autowired
	private com.mits.EduAssign.Repository.SectionRepository secRepo;

	@Test
	void testAllocationOutput() {
		try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("C:\\Users\\Bhavy\\.gemini\\antigravity\\scratch\\diag_output.txt"))) {
			pw.println("====== DIAGNOSTIC ALLOCATION TEST ======");
			java.util.List<com.mits.EduAssign.Entity.Subject> allSubs = subRepo.findAll();
			pw.println("TOTAL SUBJECTS IN DB: " + allSubs.size());
			for (com.mits.EduAssign.Entity.Subject s : allSubs) {
				pw.println("  Sub in DB: " + s.getId() + " | Year: " + s.getYear() + " | AcadYear: " + s.getAcademicYear() + " | Dept: " + s.getDep() + " | Sem: " + s.getSem() + " | Mock: " + s.isMock());
			}
			java.util.List<com.mits.EduAssign.Entity.Subject> subjects = allSubs.stream()
				.filter(s -> s.getYear() == 3 && "2025-26".equals(s.getAcademicYear()) && "CSE".equalsIgnoreCase(s.getDep()) && s.getSem() == 1)
				.collect(java.util.stream.Collectors.toList());
			pw.println("DEBUG QUERY: Year=3, AcadYear=2025-26, Dept=CSE, Sem=1 -> Subjects count: " + subjects.size());
			for (com.mits.EduAssign.Entity.Subject s : subjects) {
				pw.println("  Subject: " + s.getId() + " | Mock: " + s.isMock());
			}

			java.util.List<com.mits.EduAssign.Entity.Section> sections = secRepo.findAll().stream()
				.filter(s -> s.getYearNumber() == 3 && "2025-26".equals(s.getAcademicYear()) && "CSE".equalsIgnoreCase(s.getDepartmentCode()))
				.collect(java.util.stream.Collectors.toList());
			pw.println("DEBUG QUERY: Year=3, AcadYear=2025-26, DeptCode=CSE -> Sections (with AcadYear) count: " + sections.size());

			java.util.List<com.mits.EduAssign.Entity.Section> sectionsNoYear = secRepo.findAll().stream()
				.filter(s -> s.getYearNumber() == 3 && "CSE".equalsIgnoreCase(s.getDepartmentCode()))
				.collect(java.util.stream.Collectors.toList());
			pw.println("DEBUG QUERY: Year=3, DeptCode=CSE -> Sections (ignoring AcadYear) count: " + sectionsNoYear.size());
			for (com.mits.EduAssign.Entity.Section s : sectionsNoYear) {
				pw.println("  Section: " + s.getSectionName() + " | AcadYear: " + s.getAcademicYear());
			}

			subjectService.clearAllAllocations();
			java.util.List<com.mits.EduAssign.Entity.SubjectAllocation> allocs = 
				subjectService.autoAllocateSubjects(14, 4, 3, 2, 1, "2025-26", "CSE", 1, java.util.List.of(3));
			
			pw.println("Allocations for Faculty 7 (Mrs. M. Sri Lakshmi Preethi):");
			for (com.mits.EduAssign.Entity.SubjectAllocation sa : allocs) {
				if (sa.getFacultyId().equals("7")) {
					pw.println("  Subject allocated to 7: " + sa.getSubjectId());
				}
			}

			pw.println("Allocations for BDC (Subject Code 3):");
			for (com.mits.EduAssign.Entity.SubjectAllocation sa : allocs) {
				if (sa.getSubjectId().equals("3")) {
					pw.println("  Faculty allocated to BDC (3): " + sa.getFacultyId());
				}
			}
			pw.println("=========================================");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
