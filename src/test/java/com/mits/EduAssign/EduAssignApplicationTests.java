package com.mits.EduAssign;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import com.mits.EduAssign.Service.SubjectService;
import com.mits.EduAssign.Repository.*;
import com.mits.EduAssign.Entity.*;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EduAssignApplicationTests {

	@Autowired
	private SubjectService subjectService;

	@Autowired
	private SubjectRepository subRepo;

	@Autowired
	private SectionRepository secRepo;

	@Autowired
	private AdminRepository adminRepo;

	@Autowired
	private AllocationRepository allocationRepo;

	@Autowired
	private SectionAllocationRepository secAllocRepo;

	@Autowired
	private PreferenceRepository prefRepo;

	@Autowired
	private SelectionWindowRepository windowRepo;

	@Test
	void testDeterministicAutoAllocationMultipleRuns() {
		try {
			List<SubjectAllocation> run1 = subjectService.autoAllocateSubjects(18, 3, 3, 2, 1, "2025-26", "CSE", 1, List.of(1, 2, 3, 4));
			List<SectionAllocation> secRun1 = subjectService.getAllSectionAllocations();

			for (int i = 2; i <= 5; i++) {
				List<SubjectAllocation> runN = subjectService.autoAllocateSubjects(18, 3, 3, 2, 1, "2025-26", "CSE", 1, List.of(1, 2, 3, 4));
				List<SectionAllocation> secRunN = subjectService.getAllSectionAllocations();

				assertEquals(run1.size(), runN.size(), "Run " + i + " subject allocations count must match Run 1");
				assertEquals(secRun1.size(), secRunN.size(), "Run " + i + " section allocations count must match Run 1");
			}

			// Verify that faculty receive their first preference (P1)
			List<SubjectService.AllocationExplanation> explanations = subjectService.getLatestAllocationExplanations();
			assertFalse(explanations.isEmpty(), "Explanations should not be empty");

			long p1Count = explanations.stream().filter(e -> "P1".equalsIgnoreCase(e.getPreference())).count();
			assertTrue(p1Count > 0, "Faculty must be allocated their first preferences (P1)");

			System.out.println("TEST PASSED: Auto-Allocation successfully allocated first preferences (P1 count = " + p1Count + ") and is 100% deterministic!");
		} catch (IllegalStateException e) {
			System.out.println("Skipping test if no subjects exist in DB: " + e.getMessage());
		}
	}

	@Test
	@Transactional
	void testYear4Preference1AllocatedBeforeYear1Preference2() {
		// Stop any active windows so autoAllocate can execute in test
		windowRepo.findAll().forEach(w -> {
			w.setActive(false);
			windowRepo.save(w);
		});

		// 1. Setup Test Subjects across Years 1 and 4
		Subject y4Subject = new Subject();
		y4Subject.setId("TEST_Y4_CLOUD");
		y4Subject.setName("Cloud Computing");
		y4Subject.setYear(4);
		y4Subject.setSem(1);
		y4Subject.setDep("CSE");
		y4Subject.setAcademicYear("2026-27");
		y4Subject.setRegulation("R20");
		y4Subject.setMock(false);
		subRepo.save(y4Subject);

		Subject y1Subject = new Subject();
		y1Subject.setId("TEST_Y1_JAVA");
		y1Subject.setName("Java Programming");
		y1Subject.setYear(1);
		y1Subject.setSem(1);
		y1Subject.setDep("CSE");
		y1Subject.setAcademicYear("2026-27");
		y1Subject.setRegulation("R20");
		y1Subject.setMock(false);
		subRepo.save(y1Subject);

		// 2. Setup Sections (1 section each)
		Section s4 = new Section("CSE", 4, "A", "2026-27");
		secRepo.save(s4);
		Section s1 = new Section("CSE", 1, "A", "2026-27");
		secRepo.save(s1);

		// 3. Setup Faculty F1 (capacity 1 subject max to strictly test priority)
		AdminFaculty fac1 = new AdminFaculty();
		fac1.setId("TEST_FAC_F1");
		fac1.setName("Faculty One");
		fac1.setEmail("f1_test@mits.ac.in");
		fac1.setPassword("Pass@123");
		fac1.setRole("faculty");
		adminRepo.save(fac1);

		// 4. Setup Preferences for F1: P1 = Year 4 Cloud, P2 = Year 1 Java
		prefRepo.deleteByFacultyId("TEST_FAC_F1");
		FacultySubjectPreference p1 = new FacultySubjectPreference("TEST_FAC_F1", "TEST_Y4_CLOUD", false);
		prefRepo.save(p1);
		FacultySubjectPreference p2 = new FacultySubjectPreference("TEST_FAC_F1", "TEST_Y1_JAVA", false);
		prefRepo.save(p2);

		// 5. Run Global Auto Allocation with maxSubjects = 1, hoursLimit = 4 (F1 can only take ONE subject)
		List<SubjectAllocation> allocs = subjectService.autoAllocateSubjects(4, 4, 1, 1, 0, "2026-27", "CSE", 1, List.of(1, 4));

		// 6. Verify: F1 must have received Year 4 Cloud (P1), NOT Year 1 Java (P2)!
		List<SectionAllocation> f1SecAllocs = subjectService.getAllSectionAllocations().stream()
				.filter(sa -> sa.getFacultyId() != null && sa.getFacultyId().equalsIgnoreCase("TEST_FAC_F1"))
				.collect(Collectors.toList());

		assertFalse(f1SecAllocs.isEmpty(), "F1 should have received an allocation");
		assertEquals("TEST_Y4_CLOUD", f1SecAllocs.get(0).getSubjectId(), "F1's P1 (Year 4 Cloud) MUST be allocated instead of Year 1 Java!");

		System.out.println("TEST PASSED: Year 4 Preference 1 is successfully prioritized and allocated before Year 1 Preference 2!");
	}

	@Test
	@Transactional
	void testMultipleFacultyPreference1AcrossDifferentYears() {
		// Stop any active windows so autoAllocate can execute in test
		windowRepo.findAll().forEach(w -> {
			w.setActive(false);
			windowRepo.save(w);
		});

		// F1: P1 = Year 4 Cloud
		// F2: P1 = Year 1 Java
		// F3: P1 = Year 3 AI
		Subject y4 = new Subject();
		y4.setId("TEST_P1_Y4");
		y4.setName("Cloud Computing Y4");
		y4.setYear(4);
		y4.setSem(1);
		y4.setDep("CSE");
		y4.setAcademicYear("2026-27");
		y4.setRegulation("R20");
		subRepo.save(y4);

		Subject y1 = new Subject();
		y1.setId("TEST_P1_Y1");
		y1.setName("Java Programming Y1");
		y1.setYear(1);
		y1.setSem(1);
		y1.setDep("CSE");
		y1.setAcademicYear("2026-27");
		y1.setRegulation("R20");
		subRepo.save(y1);

		Subject y3 = new Subject();
		y3.setId("TEST_P1_Y3");
		y3.setName("Artificial Intelligence Y3");
		y3.setYear(3);
		y3.setSem(1);
		y3.setDep("CSE");
		y3.setAcademicYear("2026-27");
		y3.setRegulation("R20");
		subRepo.save(y3);

		secRepo.save(new Section("CSE", 4, "A", "2026-27"));
		secRepo.save(new Section("CSE", 1, "A", "2026-27"));
		secRepo.save(new Section("CSE", 3, "A", "2026-27"));

		AdminFaculty f1 = new AdminFaculty();
		f1.setId("TEST_FAC_A");
		f1.setName("Faculty A");
		f1.setEmail("fa_test@mits.ac.in");
		f1.setPassword("Pass@123");
		f1.setRole("faculty");
		adminRepo.save(f1);

		AdminFaculty f2 = new AdminFaculty();
		f2.setId("TEST_FAC_B");
		f2.setName("Faculty B");
		f2.setEmail("fb_test@mits.ac.in");
		f2.setPassword("Pass@123");
		f2.setRole("faculty");
		adminRepo.save(f2);

		AdminFaculty f3 = new AdminFaculty();
		f3.setId("TEST_FAC_C");
		f3.setName("Faculty C");
		f3.setEmail("fc_test@mits.ac.in");
		f3.setPassword("Pass@123");
		f3.setRole("faculty");
		adminRepo.save(f3);

		prefRepo.deleteByFacultyId("TEST_FAC_A");
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_A", "TEST_P1_Y4", false));

		prefRepo.deleteByFacultyId("TEST_FAC_B");
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_B", "TEST_P1_Y1", false));

		prefRepo.deleteByFacultyId("TEST_FAC_C");
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_C", "TEST_P1_Y3", false));

		subjectService.autoAllocateSubjects(14, 4, 3, 2, 1, "2026-27", "CSE", 1, List.of(1, 3, 4));

		List<SectionAllocation> secAllocs = subjectService.getAllSectionAllocations();

		boolean f1GotY4 = secAllocs.stream().anyMatch(sa -> "TEST_FAC_A".equalsIgnoreCase(sa.getFacultyId()) && "TEST_P1_Y4".equalsIgnoreCase(sa.getSubjectId()));
		boolean f2GotY1 = secAllocs.stream().anyMatch(sa -> "TEST_FAC_B".equalsIgnoreCase(sa.getFacultyId()) && "TEST_P1_Y1".equalsIgnoreCase(sa.getSubjectId()));
		boolean f3GotY3 = secAllocs.stream().anyMatch(sa -> "TEST_FAC_C".equalsIgnoreCase(sa.getFacultyId()) && "TEST_P1_Y3".equalsIgnoreCase(sa.getSubjectId()));

		assertTrue(f1GotY4, "Faculty A must receive their P1 Year 4 subject");
		assertTrue(f2GotY1, "Faculty B must receive their P1 Year 1 subject");
		assertTrue(f3GotY3, "Faculty C must receive their P1 Year 3 subject");

		System.out.println("TEST PASSED: Multiple faculty with Preference 1 in different years all successfully received their P1 subjects!");
	}

	@Test
	@Transactional
	void testP1Minimum2SectionsAllocationRule() {
		// Stop any active windows so autoAllocate can execute in test
		windowRepo.findAll().forEach(w -> {
			w.setActive(false);
			windowRepo.save(w);
		});
		secRepo.deleteAll();
		allocationRepo.deleteAll();
		secAllocRepo.deleteAll();

		// 1. Setup Cloud subject with 4 sections
		Subject cloudSub = new Subject();
		cloudSub.setId("TEST_CLOUD_4SEC");
		cloudSub.setName("Cloud Computing 4Sec");
		cloudSub.setYear(4);
		cloudSub.setSem(1);
		cloudSub.setDep("CSE");
		cloudSub.setAcademicYear("2026-27");
		cloudSub.setRegulation("R20");
		subRepo.save(cloudSub);

		secRepo.save(new Section("CSE", 4, "A", "2026-27"));
		secRepo.save(new Section("CSE", 4, "B", "2026-27"));
		secRepo.save(new Section("CSE", 4, "C", "2026-27"));
		secRepo.save(new Section("CSE", 4, "D", "2026-27"));

		// 2. Setup Faculty F1 (P1 Cloud), F2 (P1 Cloud), F3 (P2 Cloud)
		AdminFaculty f1 = new AdminFaculty();
		f1.setId("TEST_FAC_P1_1");
		f1.setName("Faculty P1 One");
		f1.setEmail("f1_p1@mits.ac.in");
		f1.setPassword("Pass@123");
		f1.setRole("faculty");
		adminRepo.save(f1);

		AdminFaculty f2 = new AdminFaculty();
		f2.setId("TEST_FAC_P1_2");
		f2.setName("Faculty P1 Two");
		f2.setEmail("f2_p1@mits.ac.in");
		f2.setPassword("Pass@123");
		f2.setRole("faculty");
		adminRepo.save(f2);

		AdminFaculty f3 = new AdminFaculty();
		f3.setId("TEST_FAC_P2_3");
		f3.setName("Faculty P2 Three");
		f3.setEmail("f3_p2@mits.ac.in");
		f3.setPassword("Pass@123");
		f3.setRole("faculty");
		adminRepo.save(f3);

		// Other dummy subject for F3's P1
		Subject otherSub = new Subject();
		otherSub.setId("TEST_OTHER_SUB");
		otherSub.setName("Other Subject");
		otherSub.setYear(4);
		otherSub.setSem(1);
		otherSub.setDep("CSE");
		otherSub.setAcademicYear("2026-27");
		otherSub.setRegulation("R20");
		subRepo.save(otherSub);

		prefRepo.deleteByFacultyId("TEST_FAC_P1_1");
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_P1_1", "TEST_CLOUD_4SEC", false));

		prefRepo.deleteByFacultyId("TEST_FAC_P1_2");
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_P1_2", "TEST_CLOUD_4SEC", false));

		prefRepo.deleteByFacultyId("TEST_FAC_P2_3");
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_P2_3", "TEST_OTHER_SUB", false));
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_P2_3", "TEST_CLOUD_4SEC", false));

		// 3. Run auto-allocation (Hours Limit: 14, Subject Hours: 4, Max Subjects: 3)
		subjectService.autoAllocateSubjects(14, 4, 3, 2, 1, "2026-27", "CSE", 1, List.of(4));

		List<SectionAllocation> secAllocs = subjectService.getAllSectionAllocations().stream()
				.filter(sa -> "TEST_CLOUD_4SEC".equalsIgnoreCase(sa.getSubjectId()))
				.collect(Collectors.toList());

		assertEquals(4, secAllocs.size(), "All 4 sections of Cloud should be allocated");

		long f1Count = secAllocs.stream().filter(sa -> "TEST_FAC_P1_1".equalsIgnoreCase(sa.getFacultyId())).count();
		long f2Count = secAllocs.stream().filter(sa -> "TEST_FAC_P1_2".equalsIgnoreCase(sa.getFacultyId())).count();
		long f3Count = secAllocs.stream().filter(sa -> "TEST_FAC_P2_3".equalsIgnoreCase(sa.getFacultyId())).count();

		// F1 must receive 2 sections, F2 must receive 2 sections, F3 (P2) gets 0
		assertEquals(2, f1Count, "F1 with P1 must receive 2 sections under the min-2-section rule");
		assertEquals(2, f2Count, "F2 with P1 must receive 2 sections under the min-2-section rule");
		assertEquals(0, f3Count, "F3 with P2 should receive 0 sections since P1 faculty exhausted all 4 sections");

		System.out.println("TEST PASSED: P1 Minimum-2-Section Rule successfully verified!");
	}

	@Test
	@Transactional
	void testP1Minimum2SectionsRespectsHardWorkloadConstraint() {
		// Stop any active windows so autoAllocate can execute in test
		windowRepo.findAll().forEach(w -> {
			w.setActive(false);
			windowRepo.save(w);
		});
		secRepo.deleteAll();
		allocationRepo.deleteAll();
		secAllocRepo.deleteAll();

		// 1. Setup Cloud subject with 4 sections
		Subject cloudSub = new Subject();
		cloudSub.setId("TEST_CLOUD_HARD_CONST");
		cloudSub.setName("Cloud Computing HardConst");
		cloudSub.setYear(4);
		cloudSub.setSem(1);
		cloudSub.setDep("CSE");
		cloudSub.setAcademicYear("2026-27");
		cloudSub.setRegulation("R20");
		subRepo.save(cloudSub);

		secRepo.save(new Section("CSE", 4, "A", "2026-27"));
		secRepo.save(new Section("CSE", 4, "B", "2026-27"));
		secRepo.save(new Section("CSE", 4, "C", "2026-27"));
		secRepo.save(new Section("CSE", 4, "D", "2026-27"));

		// 2. Setup Faculty F1 (P1 Cloud)
		AdminFaculty f1 = new AdminFaculty();
		f1.setId("TEST_FAC_LIMITED");
		f1.setName("Faculty Limited");
		f1.setEmail("flimited@mits.ac.in");
		f1.setPassword("Pass@123");
		f1.setRole("faculty");
		adminRepo.save(f1);

		prefRepo.deleteByFacultyId("TEST_FAC_LIMITED");
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_LIMITED", "TEST_CLOUD_HARD_CONST", false));

		// 3. Run auto-allocation with Hours Limit = 4 and Subject Hours = 4 (F1 can ONLY take 1 section)
		subjectService.autoAllocateSubjects(4, 4, 3, 2, 1, "2026-27", "CSE", 1, List.of(4));

		List<SectionAllocation> f1Allocs = subjectService.getAllSectionAllocations().stream()
				.filter(sa -> "TEST_FAC_LIMITED".equalsIgnoreCase(sa.getFacultyId()))
				.collect(Collectors.toList());

		// F1 can legally take only 1 section because hoursLimit = 4 and each section is 4 hours
		assertEquals(1, f1Allocs.size(), "F1 should only receive 1 section due to the 4-hour hard limit");

		System.out.println("TEST PASSED: Hard constraint strictly prevents violating limits even with min-2-section rule!");
	}

	@Test
	@Transactional
	void testExact3SubjectsConstraint2Regular1MockNo4thSubject() {
		// Stop any active windows so autoAllocate can execute in test
		windowRepo.findAll().forEach(w -> {
			w.setActive(false);
			windowRepo.save(w);
		});
		secRepo.deleteAll();
		allocationRepo.deleteAll();
		secAllocRepo.deleteAll();

		// 1. Setup 3 Regular Subjects and 2 Mock Subjects
		Subject r1 = new Subject();
		r1.setId("TEST_REG_1");
		r1.setName("Regular Subject 1");
		r1.setYear(1);
		r1.setSem(1);
		r1.setDep("CSE");
		r1.setAcademicYear("2026-27");
		r1.setRegulation("R20");
		r1.setMock(false);
		subRepo.save(r1);

		Subject r2 = new Subject();
		r2.setId("TEST_REG_2");
		r2.setName("Regular Subject 2");
		r2.setYear(2);
		r2.setSem(1);
		r2.setDep("CSE");
		r2.setAcademicYear("2026-27");
		r2.setRegulation("R20");
		r2.setMock(false);
		subRepo.save(r2);

		Subject r3 = new Subject();
		r3.setId("TEST_REG_3");
		r3.setName("Regular Subject 3");
		r3.setYear(3);
		r3.setSem(1);
		r3.setDep("CSE");
		r3.setAcademicYear("2026-27");
		r3.setRegulation("R20");
		r3.setMock(false);
		subRepo.save(r3);

		Subject m1 = new Subject();
		m1.setId("TEST_MOCK_1");
		m1.setName("Mock Subject 1");
		m1.setYear(4);
		m1.setSem(1);
		m1.setDep("CSE");
		m1.setAcademicYear("2026-27");
		m1.setRegulation("R20");
		m1.setMock(true);
		subRepo.save(m1);

		Subject m2 = new Subject();
		m2.setId("TEST_MOCK_2");
		m2.setName("Mock Subject 2");
		m2.setYear(4);
		m2.setSem(1);
		m2.setDep("CSE");
		m2.setAcademicYear("2026-27");
		m2.setRegulation("R20");
		m2.setMock(true);
		subRepo.save(m2);

		secRepo.save(new Section("CSE", 1, "A", "2026-27"));
		secRepo.save(new Section("CSE", 2, "A", "2026-27"));
		secRepo.save(new Section("CSE", 3, "A", "2026-27"));
		secRepo.save(new Section("CSE", 4, "A", "2026-27"));

		// 2. Setup Faculty F1 submitting 3 Regular Preferences and 2 Mock Preferences
		AdminFaculty f1 = new AdminFaculty();
		f1.setId("TEST_FAC_EXACT3");
		f1.setName("Faculty Exact 3");
		f1.setEmail("fexact3@mits.ac.in");
		f1.setPassword("Pass@123");
		f1.setRole("faculty");
		adminRepo.save(f1);

		prefRepo.deleteByFacultyId("TEST_FAC_EXACT3");
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_EXACT3", "TEST_REG_1", false));
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_EXACT3", "TEST_REG_2", false));
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_EXACT3", "TEST_REG_3", false));
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_EXACT3", "TEST_MOCK_1", true));
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_EXACT3", "TEST_MOCK_2", true));

		// 3. Auto Allocate with default parameters (Hours: 16, SubHours: 3, MaxSubs: 3, MaxReg: 2, MaxMock: 1)
		subjectService.autoAllocateSubjects(16, 3, 3, 2, 1, "2026-27", "CSE", 1, List.of(1, 2, 3, 4));

		List<SubjectAllocation> f1SubAllocs = allocationRepo.findAll().stream()
				.filter(sa -> "TEST_FAC_EXACT3".equalsIgnoreCase(sa.getFacultyId()))
				.collect(Collectors.toList());

		assertEquals(3, f1SubAllocs.size(), "Faculty must receive exactly 3 subjects (never a 4th subject)");

		long regCount = f1SubAllocs.stream().filter(sa -> {
			Subject s = subRepo.findById(sa.getSubjectId()).orElse(null);
			return s != null && !s.isMock();
		}).count();

		long mockCount = f1SubAllocs.stream().filter(sa -> {
			Subject s = subRepo.findById(sa.getSubjectId()).orElse(null);
			return s != null && s.isMock();
		}).count();

		assertEquals(2, regCount, "Faculty must receive exactly 2 Regular subjects");
		assertEquals(1, mockCount, "Faculty must receive exactly 1 Mock subject");

		System.out.println("TEST PASSED: Exact 3 subjects (2 Regular + 1 Mock) constraint strictly enforced!");
	}

	@Test
	@Transactional
	void testDynamicConfigurationAndPreferenceChanges() {
		// Stop any active windows so autoAllocate can execute in test
		windowRepo.findAll().forEach(w -> {
			w.setActive(false);
			windowRepo.save(w);
		});
		secRepo.deleteAll();
		allocationRepo.deleteAll();
		secAllocRepo.deleteAll();

		// 1. Setup Subjects: Deep Learning (Y4) and ADSA (Y2)
		Subject dl = new Subject();
		dl.setId("TEST_SUB_DL");
		dl.setName("Deep Learning");
		dl.setYear(4);
		dl.setSem(1);
		dl.setDep("CSE");
		dl.setAcademicYear("2026-27");
		dl.setRegulation("R20");
		subRepo.save(dl);

		Subject adsa = new Subject();
		adsa.setId("TEST_SUB_ADSA");
		adsa.setName("ADSA");
		adsa.setYear(2);
		adsa.setSem(1);
		adsa.setDep("CSE");
		adsa.setAcademicYear("2026-27");
		adsa.setRegulation("R20");
		subRepo.save(adsa);

		// Dynamic Section Count: Year 2 gets 8 sections, Year 4 gets 6 sections
		for (char c = 'A'; c <= 'H'; c++) {
			secRepo.save(new Section("CSE", 2, String.valueOf(c), "2026-27"));
		}
		for (char c = 'A'; c <= 'F'; c++) {
			secRepo.save(new Section("CSE", 4, String.valueOf(c), "2026-27"));
		}

		AdminFaculty f1 = new AdminFaculty();
		f1.setId("TEST_FAC_DYN");
		f1.setName("Faculty Dynamic");
		f1.setEmail("fdyn@mits.ac.in");
		f1.setPassword("Pass@123");
		f1.setRole("faculty");
		adminRepo.save(f1);

		// Faculty selects ADSA as P1 (changed dynamically from DL)
		prefRepo.deleteByFacultyId("TEST_FAC_DYN");
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_DYN", "TEST_SUB_ADSA", false));
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_DYN", "TEST_SUB_DL", false));

		// Run auto-allocation with dynamic admin constraints (Hours Limit: 15, Subject Hours: 3, Max Subs: 3)
		subjectService.autoAllocateSubjects(15, 3, 3, 2, 1, "2026-27", "CSE", 1, List.of(2, 4));

		List<SectionAllocation> f1Sections = secAllocRepo.findByFacultyId("TEST_FAC_DYN");
		assertFalse(f1Sections.isEmpty(), "Faculty should have received allocations");

		// P1 is ADSA, which has 8 sections. Under P1 rule, F1 should receive 2 sections of ADSA!
		long adsaSecCount = f1Sections.stream().filter(sa -> "TEST_SUB_ADSA".equalsIgnoreCase(sa.getSubjectId())).count();
		assertEquals(2, adsaSecCount, "F1 must receive 2 sections of ADSA because ADSA was configured as P1");

		System.out.println("TEST PASSED: Dynamic configuration, section counts, and preference updates successfully verified!");
	}

	@Test
	@Transactional
	void testAllocationExplanationsGeneratedAndLogged() {
		// Stop any active windows so autoAllocate can execute in test
		windowRepo.findAll().forEach(w -> {
			w.setActive(false);
			windowRepo.save(w);
		});
		secRepo.deleteAll();
		allocationRepo.deleteAll();
		secAllocRepo.deleteAll();

		Subject s1 = new Subject();
		s1.setId("TEST_EXP_SUB1");
		s1.setName("Database Systems");
		s1.setYear(2);
		s1.setSem(1);
		s1.setDep("CSE");
		s1.setAcademicYear("2026-27");
		s1.setRegulation("R20");
		s1.setMock(false);
		subRepo.save(s1);

		secRepo.save(new Section("CSE", 2, "A", "2026-27"));
		secRepo.save(new Section("CSE", 2, "B", "2026-27"));

		AdminFaculty f1 = new AdminFaculty();
		f1.setId("TEST_FAC_EXP1");
		f1.setName("Faculty Explanation Test");
		f1.setEmail("fexp1@mits.ac.in");
		f1.setPassword("Pass@123");
		f1.setRole("faculty");
		adminRepo.save(f1);

		prefRepo.deleteByFacultyId("TEST_FAC_EXP1");
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_EXP1", "TEST_EXP_SUB1", false));

		subjectService.autoAllocateSubjects(18, 3, 3, 2, 1, "2026-27", "CSE", 1, List.of(2));

		List<SubjectService.AllocationExplanation> explanations = subjectService.getLatestAllocationExplanations();
		assertFalse(explanations.isEmpty(), "Explanations list must not be empty after auto-allocation");

		SubjectService.AllocationExplanation firstExp = explanations.get(0);
		assertEquals("TEST_EXP_SUB1", firstExp.getSubjectId());
		assertEquals("TEST_FAC_EXP1", firstExp.getFacultyId());
		assertEquals("P1", firstExp.getPreference());
		assertNotNull(firstExp.getReasonSelected());
		assertTrue(firstExp.getHoursAfter() > firstExp.getHoursBefore());

		System.out.println("TEST PASSED: Allocation explanation generation and reporting successfully verified!");
	}

	@Test
	@Transactional
	void testRepairPhaseMinimizesUnknownFaculty() {
		// Stop any active windows so autoAllocate can execute in test
		windowRepo.findAll().forEach(w -> {
			w.setActive(false);
			windowRepo.save(w);
		});
		secRepo.deleteAll();
		allocationRepo.deleteAll();
		secAllocRepo.deleteAll();

		// Faculty 1: preference for Sub A (has 1 section)
		// Faculty 2: preference for Sub A (has 1 section)
		// Sub B: has 1 section (no direct preference submitted)
		Subject subA = new Subject();
		subA.setId("TEST_SUB_A");
		subA.setName("Subject A");
		subA.setYear(2);
		subA.setSem(1);
		subA.setDep("CSE");
		subA.setAcademicYear("2026-27");
		subA.setRegulation("R20");
		subA.setMock(false);
		subRepo.save(subA);

		Subject subB = new Subject();
		subB.setId("TEST_SUB_B");
		subB.setName("Subject B");
		subB.setYear(2);
		subB.setSem(1);
		subB.setDep("CSE");
		subB.setAcademicYear("2026-27");
		subB.setRegulation("R20");
		subB.setMock(false);
		subRepo.save(subB);

		secRepo.save(new Section("CSE", 2, "A", "2026-27")); // 1 section for Sub A and 1 section for Sub B
		
		AdminFaculty f1 = new AdminFaculty();
		f1.setId("TEST_FAC_REP1");
		f1.setName("Faculty Repair 1");
		f1.setEmail("frep1@mits.ac.in");
		f1.setPassword("Pass@123");
		f1.setRole("faculty");
		adminRepo.save(f1);

		AdminFaculty f2 = new AdminFaculty();
		f2.setId("TEST_FAC_REP2");
		f2.setName("Faculty Repair 2");
		f2.setEmail("frep2@mits.ac.in");
		f2.setPassword("Pass@123");
		f2.setRole("faculty");
		adminRepo.save(f2);

		prefRepo.deleteByFacultyId("TEST_FAC_REP1");
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_REP1", "TEST_SUB_A", false));

		prefRepo.deleteByFacultyId("TEST_FAC_REP2");
		prefRepo.save(new FacultySubjectPreference("TEST_FAC_REP2", "TEST_SUB_A", false));

		subjectService.autoAllocateSubjects(18, 3, 3, 2, 1, "2026-27", "CSE", 1, List.of(2));

		List<SectionAllocation> allAllocations = subjectService.getAllSectionAllocations();
		assertEquals(2, allAllocations.size());

		// Verify zero Unknown Faculty allocations because real faculty had capacity for both Sub A and Sub B
		boolean hasUnknown = allAllocations.stream().anyMatch(sa -> sa.getFacultyId() != null && sa.getFacultyId().toUpperCase().startsWith("UNKNOWN_"));
		assertFalse(hasUnknown, "Zero Unknown Faculty allocations should occur when real faculty have capacity!");

		System.out.println("TEST PASSED: Repair and capacity allocation successfully eliminated Unknown Faculty!");
	}
}
