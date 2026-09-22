package com.mits.EduAssign;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import com.mits.EduAssign.Service.SubjectService;
import com.mits.EduAssign.Repository.*;
import com.mits.EduAssign.Entity.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AutoAllocationEngineTest {

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
    @Transactional
    public void testFacultyByFacultyAndPhaseByPhaseAllocation() {
        System.out.println("==========================================================================================");
        System.out.println("            STARTING AUTO-ALLOCATION METHODOLOGY VERIFICATION TEST                        ");
        System.out.println("==========================================================================================");

        // 1. Clear test DB state
        secAllocRepo.deleteAll();
        allocationRepo.deleteAll();
        prefRepo.deleteAll();
        subRepo.deleteAll();
        secRepo.deleteAll();
        adminRepo.deleteAll();
        windowRepo.deleteAll();

        // 2. Setup Selection Window
        SubjectSelectionWindow win = new SubjectSelectionWindow();
        win.setId(1);
        win.setMessage("Selection Window Open");
        win.setAcademicYear("2026-27");
        win.setDepartment("CSE");
        win.setSem(1);
        win.setYear(1);
        win.setActive(true);
        win.setDeadline(java.time.LocalDateTime.now().plusDays(5));
        win.setHoursPerWeek(15);
        win.setSubjectHoursPerWeek(3);
        win.setMaxSubjectsAllocated(5);
        win.setMaxRegularPreferences(5);
        win.setMaxMockPreferences(2);
        windowRepo.save(win);

        // 3. Setup Subjects
        // Regular Subjects
        Subject subJava = new Subject();
        subJava.setId("JAVA_2026-27");
        subJava.setName("Java Programming");
        subJava.setYear(1);
        subJava.setSem(1);
        subJava.setRegulation("R23");
        subJava.setDep("CSE");
        subJava.setAcademicYear("2026-27");
        subJava.setMock(false);
        subRepo.save(subJava);

        Subject subDbms = new Subject();
        subDbms.setId("DBMS_2026-27");
        subDbms.setName("Database Systems");
        subDbms.setYear(1);
        subDbms.setSem(1);
        subDbms.setRegulation("R23");
        subDbms.setDep("CSE");
        subDbms.setAcademicYear("2026-27");
        subDbms.setMock(false);
        subRepo.save(subDbms);

        Subject subPy = new Subject();
        subPy.setId("PYTHON_2026-27");
        subPy.setName("Python Programming");
        subPy.setYear(1);
        subPy.setSem(1);
        subPy.setRegulation("R23");
        subPy.setDep("CSE");
        subPy.setAcademicYear("2026-27");
        subPy.setMock(false);
        subRepo.save(subPy);

        Subject subAi = new Subject();
        subAi.setId("AI_2026-27");
        subAi.setName("Artificial Intelligence");
        subAi.setYear(1);
        subAi.setSem(1);
        subAi.setRegulation("R23");
        subAi.setDep("CSE");
        subAi.setAcademicYear("2026-27");
        subAi.setMock(false);
        subRepo.save(subAi);

        // Mock Subject
        Subject subMock = new Subject();
        subMock.setId("AIMOCK_2026-27");
        subMock.setName("AI Mock Subject");
        subMock.setYear(1);
        subMock.setSem(1);
        subMock.setRegulation("R23");
        subMock.setDep("CSE");
        subMock.setAcademicYear("2026-27");
        subMock.setMock(true);
        subRepo.save(subMock);

        // 4. Setup Sections: Java has 3 sections (A, B, C), DBMS has 2 (A, B), Python has 2 (A, B), AI has 2 (A, B), Mock has 2 (A, B)
        secRepo.save(new Section("CSE", 1, "A", "2026-27"));
        secRepo.save(new Section("CSE", 1, "B", "2026-27"));
        secRepo.save(new Section("CSE", 1, "C", "2026-27"));

        // 5. Setup Faculty
        AdminFaculty f1 = new AdminFaculty();
        f1.setId("F101");
        f1.setName("Faculty One");
        f1.setEmail("f1@mits.ac.in");
        f1.setPassword("pass");
        f1.setRole("FACULTY");
        adminRepo.save(f1);

        AdminFaculty f2 = new AdminFaculty();
        f2.setId("F102");
        f2.setName("Faculty Two");
        f2.setEmail("f2@mits.ac.in");
        f2.setPassword("pass");
        f2.setRole("FACULTY");
        adminRepo.save(f2);

        AdminFaculty f3 = new AdminFaculty();
        f3.setId("F103");
        f3.setName("Faculty Three");
        f3.setEmail("f3@mits.ac.in");
        f3.setPassword("pass");
        f3.setRole("FACULTY");
        adminRepo.save(f3);

        AdminFaculty f4 = new AdminFaculty();
        f4.setId("F104");
        f4.setName("Faculty Four");
        f4.setEmail("f4@mits.ac.in");
        f4.setPassword("pass");
        f4.setRole("FACULTY");
        adminRepo.save(f4);

        AdminFaculty f5NoPref = new AdminFaculty();
        f5NoPref.setId("F105");
        f5NoPref.setName("Faculty Five (Pending)");
        f5NoPref.setEmail("f5@mits.ac.in");
        f5NoPref.setPassword("pass");
        f5NoPref.setRole("FACULTY");
        adminRepo.save(f5NoPref);

        // 6. Setup Faculty Preferences
        // F1: 1st = Java, 2nd = DBMS, 3rd = Python, Mock1 = AIMOCK
        prefRepo.save(new FacultySubjectPreference("F101", "JAVA_2026-27", false));
        prefRepo.save(new FacultySubjectPreference("F101", "DBMS_2026-27", false));
        prefRepo.save(new FacultySubjectPreference("F101", "PYTHON_2026-27", false));
        prefRepo.save(new FacultySubjectPreference("F101", "AIMOCK_2026-27", true));

        // F2: 1st = Java, 2nd = AI, 3rd = DBMS, Mock1 = AIMOCK
        prefRepo.save(new FacultySubjectPreference("F102", "JAVA_2026-27", false));
        prefRepo.save(new FacultySubjectPreference("F102", "AI_2026-27", false));
        prefRepo.save(new FacultySubjectPreference("F102", "DBMS_2026-27", false));
        prefRepo.save(new FacultySubjectPreference("F102", "AIMOCK_2026-27", true));

        // F3: 1st = Python, 2nd = Java, 3rd = AI, Mock1 = AIMOCK
        prefRepo.save(new FacultySubjectPreference("F103", "PYTHON_2026-27", false));
        prefRepo.save(new FacultySubjectPreference("F103", "JAVA_2026-27", false));
        prefRepo.save(new FacultySubjectPreference("F103", "AI_2026-27", false));

        // F4: 1st = Java, 2nd = DBMS, 3rd = Python
        prefRepo.save(new FacultySubjectPreference("F104", "JAVA_2026-27", false));
        prefRepo.save(new FacultySubjectPreference("F104", "DBMS_2026-27", false));

        // F105 has NO preferences submitted!

        // 7. Execute Auto-Allocation with Admin Settings: Regular = 2, Mock = 1, Hours Limit = 15, Subject Hours = 3, Max Subjects = 5
        System.out.println("Running autoAllocateSubjects(hoursLimit=15, subjectHours=3, maxSubjects=5, maxRegular=2, maxMock=1)...");
        subjectService.autoAllocateSubjects(15, 3, 5, 2, 1, "2026-27", "CSE", 1, List.of(1));

        // 8. Fetch Explanations and Report
        List<SubjectService.AllocationExplanation> explanations = subjectService.getLatestAllocationExplanations();
        SubjectService.AllocationSummaryReport report = subjectService.getLatestAllocationSummaryReport();

        System.out.println("==========================================================================================");
        System.out.println("                                ALLOCATION EXECUTION TRACE                                ");
        System.out.println("==========================================================================================");
        for (SubjectService.AllocationExplanation exp : explanations) {
            System.out.println(String.format("FACULTY: %-15s | SUBJECT: %-15s | SEC: %-3s | PREF: %-8s | REASON: %s",
                exp.getFacultyId(), exp.getSubjectId(), exp.getSectionName(), exp.getPreference(), exp.getReasonSelected()));
        }
        System.out.println("==========================================================================================");

        // 9. Verify 1st Preference Maximum 2 Sections Rule:
        // F101 chose Java as 1st preference. Java has 3 sections (A, B, C). F101 must receive AT MOST 2 sections of Java (A & B).
        List<SectionAllocation> f101JavaSecs = secAllocRepo.findByFacultyId("F101").stream()
                .filter(sa -> sa.getSubjectId().startsWith("JAVA"))
                .collect(java.util.stream.Collectors.toList());
        assertTrue(f101JavaSecs.size() <= 2, "F101 must receive at most 2 sections of 1st preference Java!");

        // 10. Verify Regular Phase Complete Before Mock Phase:
        // Mock allocations should happen after Regular allocations
        int firstMockIdx = -1;
        int lastRegIdx = -1;
        for (int i = 0; i < explanations.size(); i++) {
            SubjectService.AllocationExplanation exp = explanations.get(i);
            if (exp.getPreference() != null && exp.getPreference().startsWith("Mock")) {
                if (firstMockIdx == -1) firstMockIdx = i;
            } else if (exp.getPreference() != null && exp.getPreference().startsWith("P")) {
                lastRegIdx = i;
            }
        }
        if (firstMockIdx != -1 && lastRegIdx != -1) {
            assertTrue(firstMockIdx > lastRegIdx, "All Regular phase allocations must precede Mock phase allocations!");
        }

        // 11. Verify Faculty Status Partitioning:
        // F105 has no preferences -> Status must be PENDING_SUBMISSION
        SubjectService.FacultyReportRow f105Row = report.getFacultyReports().stream()
                .filter(r -> r.getFacultyId().equalsIgnoreCase("F105"))
                .findFirst().orElse(null);
        assertNotNull(f105Row, "F105 report row must exist");
        assertEquals("PENDING_SUBMISSION", f105Row.getStatus(), "F105 (no preferences) must have status PENDING_SUBMISSION!");

        // Real faculty with preferences (F101) -> Status must NOT be PENDING_SUBMISSION
        SubjectService.FacultyReportRow f101Row = report.getFacultyReports().stream()
                .filter(r -> r.getFacultyId().equalsIgnoreCase("F101"))
                .findFirst().orElse(null);
        assertNotNull(f101Row, "F101 report row must exist");
        assertNotEquals("PENDING_SUBMISSION", f101Row.getStatus(), "F101 (submitted preferences) must NOT be PENDING_SUBMISSION!");

        System.out.println("VERIFICATION COMPLETE: ALL METHODOLOGY RULES & CONSTRAINTS PASSED SUCCESSFULLY!");
    }
}
