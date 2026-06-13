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

import com.mits.EduAssign.Entity.AdminFaculty;
import com.mits.EduAssign.Repository.AdminRepository;
@Service
public class AdminService {
@Autowired
AdminRepository adminRepository;
	public AdminFaculty login(String email, String password) {
		if (email == null) return null;
		AdminFaculty user = adminRepository.findByEmailIgnoreCaseAndPassword(email.trim(), password);
		if (user == null) {
			// Fallback: allow logging in with faculty/admin ID directly
			user = adminRepository.findById(email.trim()).orElse(null);
			if (user != null && !password.equals(user.getPassword())) {
				user = null;
			}
		}
		return user;
	}
	public AdminFaculty addFaculty(AdminFaculty faculty) {
		
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

		    if(updatedFaculty.getPassword() != null)
		        faculty.setPassword(updatedFaculty.getPassword());
		    
		    faculty.setRole("faculty");
		    return adminRepository.save(faculty);
		}
	public boolean deleteFaculty(String id) {

	    if (!adminRepository.existsById(id)) {
	        return false;
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

if(updatedFaculty.getPassword() != null)
faculty.setPassword(updatedFaculty.getPassword());


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

if(updatedAdmin.getPassword() != null)
admin.setPassword(updatedAdmin.getPassword());

return adminRepository.save(admin);
}
	public List<AdminFaculty> viewFaculty() {
		return adminRepository.findAll().stream()
				.filter(user -> "faculty".equalsIgnoreCase(user.getRole()) || 
				               ("ADMIN".equalsIgnoreCase(user.getRole()) && !"ADMIN01".equalsIgnoreCase(user.getId())))
				.collect(java.util.stream.Collectors.toList());
	}
	public void uploadFaculty(MultipartFile file) {
		    try {

		        Workbook workbook =
		                new XSSFWorkbook(file.getInputStream());

		        Sheet sheet = workbook.getSheetAt(0);

		        for (int i = 1; i <= sheet.getLastRowNum(); i++) {

		            Row row = sheet.getRow(i);

		            if (row == null || row.getCell(0) == null) {
		                continue;
		            }

		            AdminFaculty faculty = new AdminFaculty();

		            faculty.setId(getCellValueAsString(row.getCell(0)));
		            faculty.setName(getCellValueAsString(row.getCell(1)));
		            faculty.setEmail(getCellValueAsString(row.getCell(2)));
		            faculty.setPassword(getCellValueAsString(row.getCell(3)));
		            faculty.setRole("faculty");		            
                    adminRepository.save(faculty);
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
