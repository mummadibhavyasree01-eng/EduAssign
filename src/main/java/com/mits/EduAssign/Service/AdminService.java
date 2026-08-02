package com.mits.EduAssign.Service;

import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mits.EduAssign.Entity.AdminFaculty;
import com.mits.EduAssign.Repository.AdminRepository;
@Service
public class AdminService {
@Autowired
AdminRepository adminRepository;

@Autowired
private JdbcTemplate jdbcTemplate;
	public AdminFaculty login(String email, String password) {
		if (email == null || password == null) return null;
		String search = email.trim();
		String pass = password.trim();

		// 1. Direct email & password lookup
		AdminFaculty user = adminRepository.findByEmailIgnoreCaseAndPassword(search, pass);
		if (user != null) return user;

		List<AdminFaculty> allUsers = adminRepository.findAll();

		// 2. ID match (case-insensitive) & password match
		for (AdminFaculty u : allUsers) {
			if (u.getId() != null && u.getId().equalsIgnoreCase(search)) {
				if (u.getPassword() != null && pass.equals(u.getPassword().trim())) {
					return u;
				}
			}
		}

		// 3. Email match or Email prefix match (e.g. "faculty2" for "faculty2@gmail.com")
		for (AdminFaculty u : allUsers) {
			boolean emailMatches = u.getEmail() != null && (u.getEmail().equalsIgnoreCase(search) || u.getEmail().toLowerCase().startsWith(search.toLowerCase() + "@"));
			if (emailMatches) {
				if (u.getPassword() != null && pass.equals(u.getPassword().trim())) {
					return u;
				}
			}
		}

		return null;
	}
	@org.springframework.transaction.annotation.Transactional
	public AdminFaculty addFaculty(AdminFaculty faculty) {
		if (faculty == null || faculty.getId() == null || faculty.getId().trim().isEmpty()) {
			throw new IllegalArgumentException("Faculty ID cannot be empty.");
		}
		String idTrim = faculty.getId().trim();
		String emailTrim = (faculty.getEmail() != null) ? faculty.getEmail().trim() : "";

		// Check if a user with this ID already exists
		AdminFaculty existingById = adminRepository.findById(idTrim).orElse(null);
		if (existingById != null) {
			// Update the existing record with the same ID
			existingById.setName(faculty.getName());
			if (faculty.getEmail() != null) existingById.setEmail(faculty.getEmail());
			if (faculty.getPassword() != null && !faculty.getPassword().trim().isEmpty()) {
				existingById.setPassword(faculty.getPassword());
			}
			if (faculty.getRole() != null) existingById.setRole(faculty.getRole());
			return adminRepository.save(existingById);
		}

		// Check if a user with this email already exists but has a different ID
		if (!emailTrim.isEmpty()) {
			List<AdminFaculty> all = adminRepository.findAll();
			for (AdminFaculty existing : all) {
				if (existing.getEmail() != null && existing.getEmail().equalsIgnoreCase(emailTrim)) {
					String oldId = existing.getId();
					String newId = idTrim;

					System.out.println("Migrating email " + emailTrim + " from old ID " + oldId + " to new ID " + newId);

					// Preserve password and role from existing record if they are not explicitly provided
					if (faculty.getPassword() == null || faculty.getPassword().trim().isEmpty() || "faculty@mits".equals(faculty.getPassword())) {
						faculty.setPassword(existing.getPassword());
					}
					if (faculty.getRole() == null || faculty.getRole().trim().isEmpty() || "faculty".equals(faculty.getRole())) {
						faculty.setRole(existing.getRole());
					}

					// Re-link references in other tables
					jdbcTemplate.update("UPDATE faculty_subject_preference SET faculty_id = ? WHERE faculty_id = ?", newId, oldId);
					jdbcTemplate.update("UPDATE subject_allocation SET faculty_id = ? WHERE faculty_id = ?", newId, oldId);
					jdbcTemplate.update("UPDATE section_allocation SET faculty_id = ? WHERE faculty_id = ?", newId, oldId);
					jdbcTemplate.update("UPDATE allocation_history SET faculty_id = ? WHERE faculty_id = ?", newId, oldId);

					// Delete the old record
					adminRepository.delete(existing);
					adminRepository.flush();
					break;
				}
			}
		}

		faculty.setId(idTrim);
		if (faculty.getRole() == null || faculty.getRole().trim().isEmpty()) {
			faculty.setRole("faculty");
		}
		if (faculty.getPassword() == null || faculty.getPassword().trim().isEmpty()) {
			faculty.setPassword("faculty@mits");
		}
		return adminRepository.save(faculty);
	}
	
		public AdminFaculty updateFaculty(String id, AdminFaculty updatedFaculty) {

		    AdminFaculty faculty =
		            adminRepository.findById(id).orElse(null);

		    if (faculty == null) {
		        return null;
		    }

		    if(updatedFaculty.getName() != null)
		        faculty.setName(updatedFaculty.getName());

		    if(updatedFaculty.getEmail() != null)
		        faculty.setEmail(updatedFaculty.getEmail());

		    if (updatedFaculty.getPassword() != null && !updatedFaculty.getPassword().trim().isEmpty()) {
		        faculty.setPassword(updatedFaculty.getPassword().trim());
		    }
		    
		    faculty.setRole("faculty");
		    return adminRepository.save(faculty);
		}
	public boolean deleteFaculty(String id) {

	    if (!adminRepository.existsById(id)) {
	        return false;
	    }

	    // Clean up preferences, allocations, and history referencing the deleted faculty ID
	    try {
	        jdbcTemplate.update("DELETE FROM faculty_subject_preference WHERE faculty_id = ?", id);
	        jdbcTemplate.update("DELETE FROM subject_allocation WHERE faculty_id = ?", id);
	        jdbcTemplate.update("DELETE FROM section_allocation WHERE faculty_id = ?", id);
	        jdbcTemplate.update("DELETE FROM allocation_history WHERE faculty_id = ?", id);
	    } catch (Exception e) {
	        System.err.println("Error deleting faculty references: " + e.getMessage());
	    }

	    adminRepository.deleteById(id);
	    return true;
	}
	public AdminFaculty updateProfilef(String id,
            AdminFaculty updatedFaculty) {

AdminFaculty faculty =
adminRepository.findById(id).orElse(null);

if (faculty == null) {
return null;
}

if(updatedFaculty.getName() != null)
faculty.setName(updatedFaculty.getName());

if(updatedFaculty.getEmail() != null)
faculty.setEmail(updatedFaculty.getEmail());

if (updatedFaculty.getPassword() != null && !updatedFaculty.getPassword().trim().isEmpty()) {
    faculty.setPassword(updatedFaculty.getPassword().trim());
}


return adminRepository.save(faculty);
}
	public AdminFaculty updateProfilea(String id,
            AdminFaculty updatedAdmin) {

AdminFaculty admin=
adminRepository.findById(id).orElse(null);

if (admin == null) {
return null;
}

if(updatedAdmin.getName() != null)
admin.setName(updatedAdmin.getName());

if(updatedAdmin.getEmail() != null)
admin.setEmail(updatedAdmin.getEmail());

if (updatedAdmin.getPassword() != null && !updatedAdmin.getPassword().trim().isEmpty()) {
    admin.setPassword(updatedAdmin.getPassword().trim());
}

return adminRepository.save(admin);
}
	public List<AdminFaculty> viewFaculty() {
		List<AdminFaculty> list = adminRepository.findAll().stream()
				.filter(user -> "faculty".equalsIgnoreCase(user.getRole()) || 
				               ("admin".equalsIgnoreCase(user.getRole()) && !"ADMIN01".equalsIgnoreCase(user.getId())))
				.collect(java.util.stream.Collectors.toList());
		list.sort(new NaturalOrderComparator());
		return list;
	}
	@org.springframework.transaction.annotation.Transactional
	public void uploadFaculty(MultipartFile file) {
		    try {

		        Workbook workbook =
		                new XSSFWorkbook(file.getInputStream());

		        Sheet sheet = workbook.getSheetAt(0);

		        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
		            try {
		                Row row = sheet.getRow(i);
		                if (row == null) {
		                    continue;
		                }

		                String id = getCellValueAsString(row.getCell(0)).trim();
		                String name = getCellValueAsString(row.getCell(1)).trim();
		                String email = getCellValueAsString(row.getCell(2)).trim();

		                if (id.isEmpty() || name.isEmpty() || email.isEmpty()) {
		                    continue; // Skip blank/empty/incomplete rows silently
		                }

		                AdminFaculty faculty = new AdminFaculty();
		                faculty.setId(id);
		                faculty.setName(name);
		                faculty.setEmail(email);

		                // Keep existing password if faculty already exists, otherwise default to "faculty@mits"
		                AdminFaculty existing = adminRepository.findById(id).orElse(null);
		                if (existing != null) {
		                    faculty.setPassword(existing.getPassword());
		                } else {
		                    faculty.setPassword("faculty@mits");
		                }
		                addFaculty(faculty);
		            } catch (Exception e) {
		                System.err.println("Error importing Excel row " + i + ": " + e.getMessage());
		            }
		        }

		        workbook.close();

		    } catch (Exception e) {
		        e.printStackTrace();
		    }
		}

	private String getCellValueAsString(Cell cell) {
		if (cell == null) {
			return "";
		}
		switch (cell.getCellType()) {
			case STRING:
				return cell.getStringCellValue().trim();
			case NUMERIC:
				double val = cell.getNumericCellValue();
				if (val == (long) val) {
					return String.valueOf((long) val);
				}
				return String.valueOf(val);
			case BOOLEAN:
				return String.valueOf(cell.getBooleanCellValue());
			default:
				return "";
		}
	}
}
