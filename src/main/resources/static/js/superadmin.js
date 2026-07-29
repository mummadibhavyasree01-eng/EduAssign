// Super Admin Dashboard Logic for EduAssign

document.addEventListener('DOMContentLoaded', () => {
  // 1. Session check
  const currentUser = checkSession(['SUPERADMIN']);
  if (!currentUser) return;

  // Render header info
  document.getElementById('header-user-name').innerText = currentUser.name;
  document.getElementById('header-user-email').innerText = currentUser.email;

  // Initialize Profile form values
  const profileNameInput = document.getElementById('profile-name');
  const profileEmailInput = document.getElementById('profile-email');
  const profilePasswordInput = document.getElementById('profile-password');

  profileNameInput.value = currentUser.name;
  profileEmailInput.value = currentUser.email;

  // State cache for Faculty & Reports
  let facultyList = [];
  let reportData = [];

  // 2. Tab Navigation
  const navItems = document.querySelectorAll('.nav-item');
  navItems.forEach(item => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      
      // Deactivate current tab
      document.querySelector('.nav-item.active').classList.remove('active');
      document.querySelector('.tab-content.active').classList.remove('active');
      
      // Activate clicked tab
      item.classList.add('active');
      const targetId = item.getAttribute('data-target');
      document.getElementById(targetId).classList.add('active');

      if (targetId === 'users-section') {
        loadUsers();
      } else if (targetId === 'faculty-section') {
        loadFaculty();
      } else if (targetId === 'report-section') {
        initReportFilters();
      }
    });
  });

  // 3. Load Users list (Manage Roles)
  async function loadUsers() {
    const tableBody = document.getElementById('users-table-body');
    tableBody.innerHTML = `
      <tr>
        <td colspan="5" style="text-align: center; color: var(--text-muted);">
          <i class="fas fa-circle-notch fa-spin"></i> Fetching users list...
        </td>
      </tr>
    `;

    try {
      const users = await apiRequest('/superadmin/users');
      if (users.length === 0) {
        tableBody.innerHTML = `
          <tr>
            <td colspan="5" style="text-align: center; color: var(--text-muted); padding: 40px;">
              <i class="fas fa-users" style="font-size: 2rem; margin-bottom: 12px; display: block;"></i>
              No faculty or admin users registered in the system.
            </td>
          </tr>
        `;
        return;
      }

      // Sort users naturally by ID
      users.sort((a, b) => (a.id || '').localeCompare(b.id || '', 'en', { numeric: true, sensitivity: 'base' }));

      tableBody.innerHTML = '';
      users.forEach(user => {
        const tr = document.createElement('tr');
        const role = user.role.toUpperCase();
        
        let badgeClass = role === 'ADMIN' ? 'badge-admin' : 'badge-faculty';
        let btnText = role === 'ADMIN' ? 'Change to Faculty' : 'Change to Admin';
        let btnIcon = role === 'ADMIN' ? 'fa-user' : 'fa-user-tie';
        let btnClass = role === 'ADMIN' ? 'btn-ghost' : 'btn-secondary';
        let newRole = role === 'ADMIN' ? 'faculty' : 'admin';

        tr.innerHTML = `
          <td><strong>${user.id}</strong></td>
          <td>${user.name}</td>
          <td>${user.email}</td>
          <td><span class="badge ${badgeClass}">${role}</span></td>
          <td style="text-align: right;">
            <button class="btn btn-sm ${btnClass}" onclick="toggleUserRole('${user.id}', '${newRole}')" style="padding: 6px 12px; font-size: 0.82rem;">
              <i class="fas ${btnIcon}"></i> ${btnText}
            </button>
          </td>
        `;
        tableBody.appendChild(tr);
      });

    } catch (error) {
      showToast('Error Loading Users', error.message || 'Could not reach server', 'error');
      tableBody.innerHTML = `
        <tr>
          <td colspan="5" style="text-align: center; color: var(--error); padding: 30px;">
            <i class="fas fa-exclamation-triangle" style="font-size: 1.5rem; margin-bottom: 8px; display: block;"></i>
            Failed to load users: ${error.message}
          </td>
        </tr>
      `;
    }
  }

  // Define toggleUserRole on window scope
  window.toggleUserRole = function(userId, newRole) {
    const roleLabel = newRole === 'admin' ? 'ADMINISTRATOR' : 'FACULTY';
    
    showConfirm('Modify User Privilege', `Are you sure you want to make user ${userId} a ${roleLabel}?`, async () => {
      try {
        await apiRequest(`/superadmin/change-role/${userId}?newRole=${newRole}`, {
          method: 'PUT'
        });
        showToast('Role Updated', `User ${userId} is now a ${roleLabel}`, 'success');
        loadUsers();
      } catch (error) {
        showToast('Update Failed', error.message || 'Could not change role', 'error');
      }
    });
  };

  // ----------------------------------------------------
  // DRAG & DROP FOR EXCEL UPLOADS
  // ----------------------------------------------------
  
  setupDragAndDrop('faculty-dropzone', 'faculty-excel-file', 'faculty-file-name');

  function setupDragAndDrop(zoneId, fileInputId, displayId) {
    const zone = document.getElementById(zoneId);
    const input = document.getElementById(fileInputId);
    const display = document.getElementById(displayId);

    zone.addEventListener('click', () => input.click());

    zone.addEventListener('dragover', (e) => {
      e.preventDefault();
      zone.classList.add('dragover');
    });

    zone.addEventListener('dragleave', () => {
      zone.classList.remove('dragover');
    });

    zone.addEventListener('drop', (e) => {
      e.preventDefault();
      zone.classList.remove('dragover');
      if (e.dataTransfer.files.length > 0) {
        input.files = e.dataTransfer.files;
        display.innerText = e.dataTransfer.files[0].name;
      }
    });

    input.addEventListener('change', () => {
      if (input.files.length > 0) {
        display.innerText = input.files[0].name;
      } else {
        display.innerText = 'No file chosen';
      }
    });
  }

  // Handle Excel Upload Form
  document.getElementById('faculty-upload-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fileInput = document.getElementById('faculty-excel-file');
    if (fileInput.files.length === 0) {
      showToast('Select File', 'Please choose an Excel file first', 'warning');
      return;
    }

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);

    try {
      await apiRequest('/adminfaculty/uploadFaculty', {
        method: 'POST',
        body: formData
      });
      showToast('Faculty Uploaded', 'Excel parsed and imported successfully', 'success');
      fileInput.value = '';
      document.getElementById('faculty-file-name').innerText = 'No file chosen';
      loadFaculty();
    } catch (error) {
      showToast('Upload Failed', error.message || 'Error parsing Excel sheet', 'error');
    }
  });

  // ----------------------------------------------------
  // FACULTY CRUD
  // ----------------------------------------------------
  
  async function loadFaculty() {
    const tbody = document.getElementById('faculty-table-body');
    tbody.innerHTML = `<tr><td colspan="4" style="text-align: center;"><i class="fas fa-spinner fa-spin"></i> Fetching faculty list...</td></tr>`;

    try {
      const data = await apiRequest('/adminfaculty/viewfaculty');
      if (typeof data === 'string' && data.toLowerCase().includes('no faculty')) {
        facultyList = [];
        tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted);">No faculty registered.</td></tr>`;
        return;
      }
      facultyList = Array.isArray(data) ? data : [];
      
      // Sort faculty naturally by ID
      facultyList.sort((a, b) => (a.id || '').localeCompare(b.id || '', 'en', { numeric: true, sensitivity: 'base' }));
      
      renderFacultyTable(facultyList);
    } catch (error) {
      showToast('Load Error', 'Could not fetch faculty members', 'error');
      tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--error);">Failed to load faculty</td></tr>`;
    }
  }

  function renderFacultyTable(list) {
    const tbody = document.getElementById('faculty-table-body');
    tbody.innerHTML = '';

    if (list.length === 0) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted);">No matching faculty found.</td></tr>`;
      return;
    }

    list.forEach(fac => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><strong>${fac.id}</strong></td>
        <td>${fac.name}</td>
        <td>${fac.email}</td>
        <td style="text-align: right;">
          <button class="action-btn action-btn-edit" onclick="editFaculty('${fac.id}')" title="Edit" style="background:transparent; border:none; color:var(--secondary); cursor:pointer; padding:6px 10px;"><i class="fas fa-edit"></i></button>
          <button class="action-btn action-btn-delete" onclick="deleteFaculty('${fac.id}')" title="Delete" style="background:transparent; border:none; color:var(--error); cursor:pointer; padding:6px 10px;"><i class="fas fa-trash-alt"></i></button>
        </td>
      `;
      tbody.appendChild(tr);
    });
  }

  // Client-side Faculty Search
  document.getElementById('faculty-search').addEventListener('input', (e) => {
    const q = e.target.value.toLowerCase().trim();
    const filtered = facultyList.filter(f => 
      f.id.toLowerCase().includes(q) || 
      f.name.toLowerCase().includes(q) || 
      f.email.toLowerCase().includes(q)
    );
    renderFacultyTable(filtered);
  });

  // Modal open/close helpers
  window.openFacultyModal = function(facId = null) {
    const modal = document.getElementById('faculty-modal');
    const title = document.getElementById('faculty-modal-title');
    const btn = document.getElementById('faculty-modal-btn');
    const mode = document.getElementById('faculty-modal-mode');
    
    const idInput = document.getElementById('fac-id');
    const nameInput = document.getElementById('fac-name');
    const emailInput = document.getElementById('fac-email');
    const pwdInput = document.getElementById('fac-password');

    document.getElementById('faculty-form').reset();
    idInput.disabled = false;

    if (facId) {
      const fac = facultyList.find(f => f.id === facId);
      if (fac) {
        mode.value = 'edit';
        title.innerText = 'Edit Faculty Details';
        btn.innerText = 'Update Faculty';
        idInput.value = fac.id;
        idInput.disabled = true; // Can't edit ID
        nameInput.value = fac.name;
        emailInput.value = fac.email;
        pwdInput.value = fac.password || '';
      }
    } else {
      mode.value = 'add';
      title.innerText = 'Add Faculty';
      btn.innerText = 'Add Faculty';
      pwdInput.value = 'faculty@mits';
    }

    modal.classList.add('active');
  };

  window.closeFacultyModal = function() {
    document.getElementById('faculty-modal').classList.remove('active');
  };

  // Define editFaculty on window scope so it can be called from onclick
  window.editFaculty = function(facId) {
    window.openFacultyModal(facId);
  };

  // Submit Add/Edit Faculty Form
  document.getElementById('faculty-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const mode = document.getElementById('faculty-modal-mode').value;
    
    const facultyData = {
      id: document.getElementById('fac-id').value.trim(),
      name: document.getElementById('fac-name').value.trim(),
      email: document.getElementById('fac-email').value.trim(),
      password: document.getElementById('fac-password').value,
      role: 'faculty'
    };

    try {
      if (mode === 'add') {
        await apiRequest('/adminfaculty/addFaculty', {
          method: 'POST',
          body: facultyData
        });
        showToast('Faculty Added', 'New faculty account created', 'success');
      } else {
        await apiRequest(`/adminfaculty/updateFaculty/${facultyData.id}`, {
          method: 'PUT',
          body: facultyData
        });
        showToast('Faculty Updated', 'Faculty details updated successfully', 'success');
      }
      closeFacultyModal();
      loadFaculty();
    } catch (error) {
      showToast('Operation Failed', error.message || 'Could not save faculty details', 'error');
    }
  });

  window.deleteFaculty = function(id) {
    showConfirm('Delete Faculty', `Are you sure you want to delete faculty member ${id}?`, async () => {
      try {
        await apiRequest(`/adminfaculty/deleteFaculty/${id}`, {
          method: 'DELETE'
        });
        showToast('Faculty Deleted', 'Faculty account deleted successfully', 'success');
        loadFaculty();
      } catch (error) {
        showToast('Delete Failed', error.message || 'Could not delete faculty', 'error');
      }
    });
  };

  // 4. Update Profile
  const profileForm = document.getElementById('profile-form');
  profileForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const updatedData = {
      id: currentUser.id,
      name: profileNameInput.value.trim(),
      email: profileEmailInput.value.trim(),
      password: profilePasswordInput.value.trim()
    };

    try {
      const response = await apiRequest('/superadmin/update', {
        method: 'PUT',
        body: updatedData
      });

      showToast('Profile Updated', 'Super Admin profile details saved', 'success');

      // Update Session Storage
      const newSession = {
        ...currentUser,
        name: response.name,
        email: response.email
      };
      sessionStorage.setItem('currentUser', JSON.stringify(newSession));
      
      // Update header
      document.getElementById('header-user-name').innerText = response.name;
      document.getElementById('header-user-email').innerText = response.email;
      
      // Clear password field
      profilePasswordInput.value = '';

    } catch (error) {
      showToast('Update Failed', error.message || 'Failed to update profile details', 'error');
    }
  });

  // ----------------------------------------------------
  // REPORT LOGIC
  // ----------------------------------------------------
  let departments = [];
  let semesters = [];

  async function loadConfigOptions() {
    try {
      departments = await apiRequest('/sections/departments');
      semesters = await apiRequest('/sections/semesters');
    } catch (error) {
      showToast('Error', 'Failed to load configuration options', 'error');
    }
  }

  function populateReportFilterDropdowns() {
    const deptFilter = document.getElementById('report-dept-select');
    const semFilter = document.getElementById('report-sem-select');

    if (deptFilter && deptFilter.options.length <= 1) {
      departments.forEach(d => {
        const opt = document.createElement('option');
        opt.value = d.code;
        opt.innerText = `${d.code} - ${d.name}`;
        deptFilter.appendChild(opt);
      });
    }

    if (semFilter && semFilter.options.length <= 1) {
      const sortedSems = [...semesters].sort((a,b) => a.semNumber - b.semNumber);
      sortedSems.forEach(s => {
        const opt = document.createElement('option');
        opt.value = s.semNumber;
        opt.innerText = s.name;
        semFilter.appendChild(opt);
      });
    }
  }
  async function initReportFilters() {
    const tbody = document.getElementById('report-table-body');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="3" style="text-align: center; color: var(--text-muted); padding: 20px;">Please enter Academic Year, select Department, and select Semester to view the report.</td></tr>`;
    }
    
    if (departments.length === 0 || semesters.length === 0) {
      await loadConfigOptions();
    }
    populateReportFilterDropdowns();
  }

  async function loadReportData() {
    const tbody = document.getElementById('report-table-body');
    if (!tbody) return;

    const academicYearInput = document.getElementById('report-academic-year-input');
    const academicYearVal = academicYearInput ? academicYearInput.value.trim() : '';

    const deptSelect = document.getElementById('report-dept-select');
    const deptVal = deptSelect ? deptSelect.value : '';

    const semSelect = document.getElementById('report-sem-select');
    const semVal = semSelect ? semSelect.value : '';

    if (!academicYearVal || !deptVal || !semVal) {
      tbody.innerHTML = `<tr><td colspan="3" style="text-align: center; color: var(--text-muted); padding: 20px;">Please enter Academic Year, select Department, and select Semester to view the report.</td></tr>`;
      return;
    }

    tbody.innerHTML = `<tr><td colspan="3" style="text-align: center; color: var(--text-muted); padding: 40px;"><i class="fas fa-spinner fa-spin" style="font-size: 1.5rem; margin-bottom: 12px; display: block;"></i> Loading report data...</td></tr>`;

    try {
      const data = await apiRequest(`/superadmin/reports/allocations?academicYear=${encodeURIComponent(academicYearVal)}`);
      let fetchedReportData = Array.isArray(data) ? data : [];

      // Filter allocations matching the chosen department and semester
      reportData = fetchedReportData.map(fac => {
        const filteredAllocations = (fac.allocations || []).filter(a => {
          const matchDept = a.department && a.department.toUpperCase() === deptVal.toUpperCase();
          const matchSem = a.semester && Number(a.semester) === Number(semVal);
          return matchDept && matchSem;
        });

        return {
          ...fac,
          allocations: filteredAllocations
        };
      }).filter(fac => fac.allocations.length > 0);

      // Sort report data naturally by faculty ID
      reportData.sort((a, b) => (a.facultyId || '').localeCompare(b.facultyId || '', 'en', { numeric: true, sensitivity: 'base' }));

      renderReportTable(reportData);
    } catch (error) {
      showToast('Load Error', 'Could not fetch report data', 'error');
      tbody.innerHTML = `<tr><td colspan="3" style="text-align: center; color: var(--error);">Failed to load report data: ${error.message}</td></tr>`;
    }
  }

  function renderReportTable(list) {
    const tbody = document.getElementById('report-table-body');
    if (!tbody) return;
    tbody.innerHTML = '';
    
    if (list.length === 0) {
      tbody.innerHTML = `<tr><td colspan="3" style="text-align: center; color: var(--text-muted); padding: 20px;">No matching records found.</td></tr>`;
      return;
    }
    
    list.forEach(fac => {
      const tr = document.createElement('tr');
      
      let subjectsHtml = '';
      
      if (fac.allocations && fac.allocations.length > 0) {
        subjectsHtml = fac.allocations.map(a => `<div style="margin-bottom: 6px;"><strong>${a.subjectId || ''}</strong> - ${a.subjectName || ''}</div>`).join('');
      } else {
        subjectsHtml = '<span style="color: var(--text-muted); font-style: italic;">No allocations</span>';
      }
      
      tr.innerHTML = `
        <td><strong>${fac.facultyId || ''}</strong></td>
        <td>${fac.name || ''}</td>
        <td>${subjectsHtml}</td>
      `;
      tbody.appendChild(tr);
    });
  }

  // Bind change and search listeners with null guards
  const academicYearInputEl = document.getElementById('report-academic-year-input');
  if (academicYearInputEl) {
    academicYearInputEl.addEventListener('change', loadReportData);
    academicYearInputEl.addEventListener('keyup', (e) => {
      if (e.key === 'Enter') {
        loadReportData();
      }
    });
  }

  const deptSelectEl = document.getElementById('report-dept-select');
  if (deptSelectEl) {
    deptSelectEl.addEventListener('change', loadReportData);
  }

  const semSelectEl = document.getElementById('report-sem-select');
  if (semSelectEl) {
    semSelectEl.addEventListener('change', loadReportData);
  }

  const searchEl = document.getElementById('report-search');
  if (searchEl) {
    searchEl.addEventListener('input', (e) => {
      const q = e.target.value.toLowerCase().trim();
      if (!q) {
        renderReportTable(reportData);
        return;
      }
      const filtered = reportData.filter(fac => {
        const matchFaculty = (fac.facultyId || '').toLowerCase().includes(q) ||
                             (fac.name || '').toLowerCase().includes(q) ||
                             (fac.email || '').toLowerCase().includes(q);
        
        const matchAllocations = fac.allocations && fac.allocations.some(a => 
          (a.subjectId || '').toLowerCase().includes(q) ||
          (a.subjectName || '').toLowerCase().includes(q) ||
          (a.sectionName || '').toLowerCase().includes(q)
        );
        
        return matchFaculty || matchAllocations;
      });
      renderReportTable(filtered);
    });
  }

  window.exportReportToExcel = function() {
    const academicYearInput = document.getElementById('report-academic-year-input');
    const academicYearVal = academicYearInput ? academicYearInput.value.trim() : '';
    if (!academicYearVal) {
      showToast('Enter Year', 'Please enter an Academic Year first', 'warning');
      return;
    }

    if (!reportData || reportData.length === 0) {
      showToast('No Data', 'No report data available to export', 'warning');
      return;
    }

    // Find the maximum number of allocations selected by any faculty
    let maxAllocCount = 0;
    reportData.forEach(fac => {
      const allocs = fac.allocations || [];
      if (allocs.length > maxAllocCount) {
        maxAllocCount = allocs.length;
      }
    });

    let tableHtml = '<table border="1">';
    
    // Build Header
    tableHtml += '<thead><tr style="background-color: #14B8A6; color: #ffffff; font-weight: bold;">';
    tableHtml += '<th>Faculty ID</th><th>Faculty Name</th>';
    for (let i = 1; i <= maxAllocCount; i++) {
      tableHtml += `<th>Allocated Subject ${i}</th>`;
    }
    if (maxAllocCount === 0) {
      tableHtml += '<th>Allocated Subjects</th>';
    }
    tableHtml += '</tr></thead><tbody>';

    // Build Rows
    reportData.forEach(fac => {
      tableHtml += '<tr>';
      tableHtml += `<td style="vnd.ms-excel.numberformat:@">${fac.facultyId || ''}</td>`;
      tableHtml += `<td>${fac.name || ''}</td>`;

      const allocs = fac.allocations || [];
      if (maxAllocCount === 0) {
        tableHtml += '<td>No allocations</td>';
      } else {
        for (let i = 0; i < maxAllocCount; i++) {
          if (i < allocs.length) {
            const a = allocs[i];
            tableHtml += `<td>${a.subjectName || ''} (${a.subjectId || ''})</td>`;
          } else {
            tableHtml += '<td></td>';
          }
        }
      }
      tableHtml += '</tr>';
    });
    tableHtml += '</tbody></table>';

    const excelXml = `
      <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel" xmlns="http://www.w3.org/TR/REC-html40">
      <head>
        <meta http-equiv="content-type" content="text/html; charset=UTF-8">
        <!--[if gte mso 9]>
        <xml>
          <x:ExcelWorkbook>
            <x:ExcelWorksheets>
              <x:ExcelWorksheet>
                <x:Name>Allocation Report</x:Name>
                <x:WorksheetOptions>
                  <x:DisplayGridlines/>
                </x:WorksheetOptions>
              </x:ExcelWorksheet>
            </x:ExcelWorksheets>
          </x:ExcelWorkbook>
        </xml>
        <![endif]-->
      </head>
      <body>
        ${tableHtml}
      </body>
      </html>
    `;

    const blob = new Blob([excelXml], { type: 'application/vnd.ms-excel;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", `Allocation_Report_${academicYearVal}.xls`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('Export Success', 'Excel report downloaded successfully', 'success');
  };

  // Initial load
  loadUsers();
});
