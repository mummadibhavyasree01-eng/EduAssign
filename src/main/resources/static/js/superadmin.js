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
        loadReportYears();
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
  async function loadReportYears() {
    const select = document.getElementById('report-year-select');
    if (!select) return;
    select.innerHTML = '<option value="">Loading...</option>';
    
    try {
      const years = await apiRequest('/superadmin/reports/academic-years');
      select.innerHTML = '';
      const yearsList = Array.isArray(years) ? years : (years ? [years] : []);
      if (yearsList.length === 0) {
        select.innerHTML = '<option value="2026-27">2026-27</option>';
      } else {
        yearsList.forEach(yr => {
          const opt = document.createElement('option');
          opt.value = yr;
          opt.innerText = yr;
          select.appendChild(opt);
        });
      }
      loadReportData();
    } catch (error) {
      showToast('Error', 'Could not load academic years', 'error');
      select.innerHTML = '<option value="2026-27">2026-27</option>';
      loadReportData();
    }
  }

  async function loadReportData() {
    const tbody = document.getElementById('report-table-body');
    if (!tbody) return;
    const yearSelect = document.getElementById('report-year-select');
    const selectedYear = yearSelect ? yearSelect.value : '2026-27';
    
    if (!selectedYear) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted);">Please select a year.</td></tr>`;
      return;
    }
    
    tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 40px;"><i class="fas fa-spinner fa-spin" style="font-size: 1.5rem; margin-bottom: 12px; display: block;"></i> Loading report data for ${selectedYear}...</td></tr>`;
    
    try {
      const data = await apiRequest(`/superadmin/reports/allocations?academicYear=${encodeURIComponent(selectedYear)}`);
      reportData = Array.isArray(data) ? data : [];
      
      // Sort report data naturally by faculty ID
      reportData.sort((a, b) => (a.facultyId || '').localeCompare(b.facultyId || '', 'en', { numeric: true, sensitivity: 'base' }));
      
      renderReportTable(reportData);
    } catch (error) {
      showToast('Load Error', 'Could not fetch report data', 'error');
      tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--error);">Failed to load report data.</td></tr>`;
    }
  }

  function renderReportTable(list) {
    const tbody = document.getElementById('report-table-body');
    if (!tbody) return;
    tbody.innerHTML = '';
    
    if (list.length === 0) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 20px;">No matching records found.</td></tr>`;
      return;
    }
    
    list.forEach(fac => {
      const tr = document.createElement('tr');
      
      let subjectsHtml = '';
      let sectionsHtml = '';
      let statusHtml = '';
      
      if (fac.allocations && fac.allocations.length > 0) {
        subjectsHtml = fac.allocations.map(a => `<div style="margin-bottom: 6px;"><strong>${a.subjectId || ''}</strong> - ${a.subjectName || ''}</div>`).join('');
        sectionsHtml = fac.allocations.map(a => `<div style="margin-bottom: 6px;"><span class="badge badge-admin">${a.sectionName || ''}</span></div>`).join('');
        statusHtml = fac.allocations.map(a => {
          const badgeClass = a.status === 'Finalized' ? 'badge-finalized' : 'badge-draft';
          return `<div style="margin-bottom: 6px;"><span class="badge ${badgeClass}">${a.status || ''}</span></div>`;
        }).join('');
      } else {
        subjectsHtml = '<span style="color: var(--text-muted); font-style: italic;">No allocations</span>';
        sectionsHtml = '<span style="color: var(--text-disabled);">-</span>';
        statusHtml = '<span style="color: var(--text-disabled);">-</span>';
      }
      
      tr.innerHTML = `
        <td><strong>${fac.facultyId || ''}</strong></td>
        <td>${fac.name || ''}</td>
        <td>${fac.email || ''}</td>
        <td>${subjectsHtml}</td>
        <td>${sectionsHtml}</td>
        <td>${statusHtml}</td>
      `;
      tbody.appendChild(tr);
    });
  }

  // Bind change and search listeners with null guards
  const yearSelectEl = document.getElementById('report-year-select');
  if (yearSelectEl) {
    yearSelectEl.addEventListener('change', loadReportData);
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
    const yearSelect = document.getElementById('report-year-select');
    const selectedYear = yearSelect ? yearSelect.value : '2026-27';
    if (!reportData || reportData.length === 0) {
      showToast('No Data', 'No report data available to export', 'warning');
      return;
    }

    let csvRows = [];
    csvRows.push("Faculty ID,Faculty Name,Email ID,Subject Code,Subject Name,Department,Semester,Section Name,Status");

    reportData.forEach(fac => {
      if (fac.allocations && fac.allocations.length > 0) {
        fac.allocations.forEach(a => {
          const row = [
            `"${(fac.facultyId || '').replace(/"/g, '""')}"`,
            `"${(fac.name || '').replace(/"/g, '""')}"`,
            `"${(fac.email || '').replace(/"/g, '""')}"`,
            `"${(a.subjectId || '').replace(/"/g, '""')}"`,
            `"${(a.subjectName || '').replace(/"/g, '""')}"`,
            `"${(a.department || '').replace(/"/g, '""')}"`,
            `"${a.semester || ''}"`,
            `"${(a.sectionName || '').replace(/"/g, '""')}"`,
            `"${a.status || ''}"`
          ].join(",");
          csvRows.push(row);
        });
      } else {
        const row = [
          `"${(fac.facultyId || '').replace(/"/g, '""')}"`,
          `"${(fac.name || '').replace(/"/g, '""')}"`,
          `"${(fac.email || '').replace(/"/g, '""')}"`,
          `""`,
          `"No Allocation"`,
          `""`,
          `""`,
          `""`,
          `""`
        ].join(",");
        csvRows.push(row);
      }
    });

    const csvContent = csvRows.join("\n");
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", `EduAssign_Allocation_Report_${selectedYear}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('Export Success', 'CSV report downloaded successfully', 'success');
  };

  // Initial load
  loadUsers();
});
