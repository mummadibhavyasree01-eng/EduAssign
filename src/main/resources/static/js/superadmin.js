// Super Admin Dashboard Logic for EduAssign

document.addEventListener('DOMContentLoaded', () => {
  // 1. Session check
  const currentUser = checkSession(['SUPERADMIN']);
  if (!currentUser) return;

  // Render header info
  document.getElementById('header-user-name').innerText = currentUser.name || '';
  document.getElementById('header-user-email').innerText = currentUser.email || '';
  updateHeaderAvatar(currentUser);

  // Initialize Profile form values
  const profileNameInput = document.getElementById('profile-name');
  const profileEmailInput = document.getElementById('profile-email');
  const profilePasswordInput = document.getElementById('profile-password');

  profileNameInput.value = currentUser.name || '';
  profileEmailInput.value = currentUser.email || '';

  // Profile Picture management
  const profilePhotoInput = document.getElementById('profile-photo-input');
  const profileAvatarPreview = document.getElementById('profile-avatar-preview');
  const profileAvatarInitials = document.getElementById('profile-avatar-initials');
  const profilePhotoRemoveBtn = document.getElementById('profile-photo-remove-btn');
  let currentProfileImageBase64 = currentUser.profileImage || null;

  function renderProfilePhotoPreview() {
    if (currentProfileImageBase64) {
      profileAvatarPreview.src = currentProfileImageBase64;
      profileAvatarPreview.style.display = 'block';
      profileAvatarInitials.style.display = 'none';
      if (profilePhotoRemoveBtn) profilePhotoRemoveBtn.style.display = 'inline-flex';
    } else {
      profileAvatarPreview.style.display = 'none';
      const nameParts = (currentUser.name || 'S').trim().split(/\s+/);
      const nameInitials = nameParts.map(n => n[0]).join('').substring(0, 2).toUpperCase();
      profileAvatarInitials.innerText = nameInitials || 'S';
      profileAvatarInitials.style.display = 'flex';
      if (profilePhotoRemoveBtn) profilePhotoRemoveBtn.style.display = 'none';
    }
    updateHeaderAvatar({ ...currentUser, profileImage: currentProfileImageBase64 });
  }

  // Load initial photo
  renderProfilePhotoPreview();

  // Handle file select
  if (profilePhotoInput) {
    profilePhotoInput.addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (file) {
        if (file.size > 10 * 1024 * 1024) {
          showToast('Image Too Large', 'Maximum image size allowed is 10MB', 'error');
          profilePhotoInput.value = '';
          return;
        }
        resizeAndCropImage(file, (base64) => {
          currentProfileImageBase64 = base64;
          renderProfilePhotoPreview();
        });
      }
    });
  }

  // Handle photo remove
  if (profilePhotoRemoveBtn) {
    profilePhotoRemoveBtn.addEventListener('click', () => {
      currentProfileImageBase64 = ''; // empty string means remove
      if (profilePhotoInput) profilePhotoInput.value = '';
      renderProfilePhotoPreview();
    });
  }

  // State cache for Faculty & Reports
  let facultyList = [];
  let reportData = [];
  let rawReportData = [];

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

    if (!zone || !input || !display) return;

    // Create or locate clear button
    let clearBtn = zone.querySelector('.file-clear-btn');
    if (!clearBtn) {
      clearBtn = document.createElement('button');
      clearBtn.type = 'button';
      clearBtn.className = 'file-clear-btn';
      clearBtn.title = 'Remove selected file';
      clearBtn.innerHTML = '<i class="fas fa-times"></i>';
      clearBtn.style.cssText = "display: none; background: rgba(239, 68, 68, 0.2); color: #EF4444; border: 1px solid rgba(239, 68, 68, 0.4); border-radius: 50%; width: 26px; height: 26px; align-items: center; justify-content: center; cursor: pointer; margin-left: 8px; vertical-align: middle; transition: all 0.2s ease;";
      display.parentNode.insertBefore(clearBtn, display.nextSibling);
    }

    const resetFileInput = () => {
      input.value = '';
      display.innerText = 'No file chosen';
      display.style.color = '';
      clearBtn.style.display = 'none';
    };

    clearBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      e.preventDefault();
      resetFileInput();
    });

    zone.addEventListener('click', (e) => {
      if (e.target.closest('.file-clear-btn')) return;
      input.click();
    });

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
        display.style.color = 'var(--secondary)';
        clearBtn.style.display = 'inline-flex';
      }
    });

    input.addEventListener('change', () => {
      if (input.files.length > 0) {
        display.innerText = input.files[0].name;
        display.style.color = 'var(--secondary)';
        clearBtn.style.display = 'inline-flex';
      } else {
        resetFileInput();
      }
    });
  }

  // Handle Excel Upload Form
  document.getElementById('faculty-upload-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fileInput = document.getElementById('faculty-excel-file');
    if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
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
      const display = document.getElementById('faculty-file-name');
      if (display) {
        display.innerText = 'No file chosen';
        display.style.color = '';
      }
      const clearBtn = document.querySelector('#faculty-dropzone .file-clear-btn');
      if (clearBtn) clearBtn.style.display = 'none';
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
    const origIdInput = document.getElementById('faculty-original-id');
    
    const idInput = document.getElementById('fac-id');
    const nameInput = document.getElementById('fac-name');
    const emailInput = document.getElementById('fac-email');
    const pwdInput = document.getElementById('fac-password');

    document.getElementById('faculty-form').reset();
    idInput.disabled = false;

    if (facId) {
      const fac = facultyList.find(f => String(f.id).trim().toLowerCase() === String(facId).trim().toLowerCase());
      if (fac) {
        mode.value = 'edit';
        if (origIdInput) origIdInput.value = fac.id;
        title.innerText = 'Edit Faculty Details';
        btn.innerText = 'Update Faculty';
        idInput.value = fac.id;
        idInput.disabled = false;
        nameInput.value = fac.name;
        emailInput.value = fac.email;
        pwdInput.value = fac.password || '';
      }
    } else {
      mode.value = 'add';
      if (origIdInput) origIdInput.value = '';
      title.innerText = 'Add Faculty';
      btn.innerText = 'Add Faculty';
      pwdInput.value = 'faculty@mits';
    }

    modal.classList.add('active');
  };

  window.closeFacultyModal = function() {
    document.getElementById('faculty-modal').classList.remove('active');
    const origIdInput = document.getElementById('faculty-original-id');
    if (origIdInput) origIdInput.value = '';
  };

  // Define editFaculty on window scope so it can be called from onclick
  window.editFaculty = function(facId) {
    window.openFacultyModal(facId);
  };

  // Submit Add/Edit Faculty Form
  document.getElementById('faculty-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const mode = document.getElementById('faculty-modal-mode').value;
    const origIdInput = document.getElementById('faculty-original-id');
    const originalId = origIdInput && origIdInput.value ? origIdInput.value.trim() : null;
    
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
        const targetId = originalId || facultyData.id;
        await apiRequest(`/adminfaculty/updateFaculty/${encodeURIComponent(targetId)}`, {
          method: 'PUT',
          body: facultyData
        });
        showToast('Faculty Updated', 'Faculty details and ID updated successfully', 'success');
      }
      closeFacultyModal();
      await loadFaculty();
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

    const newPassword = profilePasswordInput.value.trim();

    const saveSuperadminProfile = async () => {
      const updatedData = {
        id: currentUser.id,
        name: profileNameInput.value.trim(),
        email: profileEmailInput.value.trim(),
        password: newPassword,
        profileImage: currentProfileImageBase64
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
          email: response.email,
          profileImage: response.profileImage
        };
        sessionStorage.setItem('currentUser', JSON.stringify(newSession));
        
        // Update header
        document.getElementById('header-user-name').innerText = response.name;
        document.getElementById('header-user-email').innerText = response.email;
        updateHeaderAvatar(newSession);
        
        // Clear password field
        profilePasswordInput.value = '';

      } catch (error) {
        showToast('Update Failed', error.message || 'Failed to update profile details', 'error');
      }
    };

    if (newPassword && newPassword.trim() !== '') {
      showConfirm('Confirm Password Change', 'Are you sure you want to change your password?', () => {
        saveSuperadminProfile();
      });
    } else {
      saveSuperadminProfile();
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

  function populateCustomDeptDropdown() {
    const menuEl = document.getElementById('custom-dept-menu');
    if (!menuEl) return;
    
    menuEl.innerHTML = '';
    departments.forEach(d => {
      const item = document.createElement('div');
      item.className = 'custom-dept-item';
      item.style.position = 'relative';
      item.style.padding = '10px 12px';
      item.style.cursor = 'pointer';
      item.style.display = 'flex';
      item.style.flexDirection = 'column';
      item.style.alignItems = 'flex-start';
      item.style.borderBottom = '1px solid rgba(255,255,255,0.05)';
      item.style.color = 'var(--text-main)';
      item.style.transition = 'all 0.2s ease';
      
      item.innerHTML = `
        <div class="dept-row" style="display: flex; justify-content: space-between; align-items: center; width: 100%; pointer-events: none;">
          <span>${d.code} - ${d.name}</span>
          <i class="fas fa-chevron-down" style="font-size: 0.75rem; color: var(--text-muted); transition: transform 0.2s ease;"></i>
        </div>
        <div class="custom-sem-submenu" style="display: none; justify-content: flex-start; gap: 8px; margin-top: 8px; width: 100%; box-sizing: border-box; padding: 4px 0;">
          <button class="sem-opt-btn" data-dept="${d.code}" data-sem="1" style="background: rgba(255, 255, 255, 0.05); border: 1px solid var(--panel-border); color: var(--text-main); padding: 6px 12px; text-align: left; font-size: 0.8rem; cursor: pointer; border-radius: var(--border-radius-sm); white-space: nowrap; transition: all 0.2s ease;">Sem 1</button>
          <button class="sem-opt-btn" data-dept="${d.code}" data-sem="2" style="background: rgba(255, 255, 255, 0.05); border: 1px solid var(--panel-border); color: var(--text-main); padding: 6px 12px; text-align: left; font-size: 0.8rem; cursor: pointer; border-radius: var(--border-radius-sm); white-space: nowrap; transition: all 0.2s ease;">Sem 2</button>
        </div>
      `;
      
      // Toggle submenu on hover or click
      const showSubmenu = () => {
        const submenu = item.querySelector('.custom-sem-submenu');
        const arrow = item.querySelector('.fa-chevron-down');
        if (submenu) submenu.style.display = 'flex';
        if (arrow) arrow.style.transform = 'rotate(180deg)';
        item.style.background = 'rgba(20, 184, 166, 0.05)';
      };
      
      const hideSubmenu = () => {
        const submenu = item.querySelector('.custom-sem-submenu');
        const arrow = item.querySelector('.fa-chevron-down');
        if (submenu) submenu.style.display = 'none';
        if (arrow) arrow.style.transform = '';
        item.style.background = '';
      };
      
      item.addEventListener('mouseenter', showSubmenu);
      item.addEventListener('mouseleave', hideSubmenu);
      
      item.addEventListener('click', (e) => {
        if (e.target.closest('.sem-opt-btn')) return;
        const submenu = item.querySelector('.custom-sem-submenu');
        const isVisible = submenu && submenu.style.display === 'flex';
        if (isVisible) {
          hideSubmenu();
        } else {
          showSubmenu();
        }
      });
      
      menuEl.appendChild(item);
    });

    // Add click and hover listeners to semester buttons
    menuEl.querySelectorAll('.sem-opt-btn').forEach(btn => {
      btn.addEventListener('mouseenter', (e) => {
        e.stopPropagation();
        btn.style.background = 'var(--primary-gradient)';
        btn.style.color = '#fff';
      });
      btn.addEventListener('mouseleave', (e) => {
        e.stopPropagation();
        btn.style.background = 'rgba(255, 255, 255, 0.05)';
        btn.style.color = 'var(--text-main)';
      });
      
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const deptCode = btn.getAttribute('data-dept');
        const semVal = btn.getAttribute('data-sem');
        
        // Update hidden native selects
        const deptSelect = document.getElementById('report-dept-select');
        const semSelect = document.getElementById('report-sem-select');
        if (deptSelect) deptSelect.value = deptCode;
        if (semSelect) semSelect.value = semVal;
        
        // Update trigger button text
        const triggerLabel = document.getElementById('custom-dept-trigger-label');
        if (triggerLabel) {
          triggerLabel.innerText = `${deptCode} - Sem ${semVal}`;
          triggerLabel.style.color = 'var(--text-main)';
        }
        
        // Hide dropdown
        menuEl.style.display = 'none';
        
        // Load report data
        loadReportData();
      });
    });
  }

  async function initReportFilters() {
    const tbody = document.getElementById('report-table-body');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 20px;">Please enter Academic Year, select Department, and select Semester to view the report.</td></tr>`;
    }
    
    // Clear selects
    const deptSelect = document.getElementById('report-dept-select');
    const semSelect = document.getElementById('report-sem-select');
    if (deptSelect) deptSelect.value = '';
    if (semSelect) semSelect.value = '';
    
    if (deptSelect) {
      deptSelect.removeEventListener('change', loadReportData);
      deptSelect.addEventListener('change', loadReportData);
    }
    if (semSelect) {
      semSelect.removeEventListener('change', loadReportData);
      semSelect.addEventListener('change', loadReportData);
    }

    const academicYearInput = document.getElementById('report-academic-year-input');
    if (academicYearInput) {
      academicYearInput.removeEventListener('change', loadReportData);
      academicYearInput.addEventListener('change', loadReportData);
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
      tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 20px;">Please enter Academic Year, select Department, and select Semester to view the report.</td></tr>`;
      return;
    }

    tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 40px;"><i class="fas fa-spinner fa-spin" style="font-size: 1.5rem; margin-bottom: 12px; display: block;"></i> Loading report data...</td></tr>`;

    try {
      const data = await apiRequest(`/superadmin/reports/allocations?academicYear=${encodeURIComponent(academicYearVal)}`);
      rawReportData = Array.isArray(data) ? data : [];

      // Filter allocations matching the chosen department and semester
      reportData = rawReportData.map(fac => {
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
      tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--error);">Failed to load report data: ${error.message}</td></tr>`;
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
      const isUnknown = fac.isUnknown === true;
      if (isUnknown) {
        tr.style.background = 'rgba(239, 68, 68, 0.05)';
        tr.style.borderLeft = '4px solid var(--error)';
      }
      
      const yearAllocs = { 1: [], 2: [], 3: [], 4: [] };
      if (fac.allocations && fac.allocations.length > 0) {
        fac.allocations.forEach(a => {
          const yr = a.year;
          if (yearAllocs[yr]) {
            yearAllocs[yr].push(a);
          }
        });
      }
      
      const renderYearCell = (allocs) => {
        if (!allocs || allocs.length === 0) return '<span style="color: var(--text-disabled);">-</span>';
        
        return allocs.map(a => {
          const secSuffix = a.sectionName && a.sectionName !== 'N/A' ? `-${a.sectionName}` : '';
          const badgeText = `${cleanSubjectName(a.subjectName)}${secSuffix}`;
          const badgeBg = isUnknown ? 'rgba(239, 68, 68, 0.12)' : 'rgba(20, 184, 166, 0.08)';
          const badgeBorder = isUnknown ? '1px solid rgba(239, 68, 68, 0.3)' : '1px solid rgba(20, 184, 166, 0.2)';
          const textStyle = isUnknown ? 'color: var(--error);' : 'color: var(--text-main);';

          return `
            <div class="allocation-badge" style="background: ${badgeBg}; border: ${badgeBorder}; border-radius: 4px; padding: 4px 10px; font-size: 0.85rem; display: block; margin-bottom: 6px; white-space: nowrap; width: fit-content; max-width: 100%; overflow: hidden; text-overflow: ellipsis;">
              <span style="${textStyle}">${badgeText}</span>
            </div>
          `;
        }).join('');
      };

      const facultyIdHtml = isUnknown 
        ? `<strong style="color: var(--error);"><i class="fas fa-exclamation-triangle" style="margin-right: 6px;"></i>${fac.facultyId}</strong>` 
        : `<strong>${fac.facultyId || ''}</strong>`;
        
      const editBtnHtml = `
        <button onclick="openFacultyAllocationsEditModal('${fac.facultyId}', '${(fac.name || '').replace(/'/g, "\\'")}', ${isUnknown})" style="background: none; border: none; padding: 4px 8px; margin-left: 8px; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; color: var(--secondary);" title="Edit Faculty Allocations">
          <i class="${isUnknown ? 'fas fa-user-plus' : 'fas fa-edit'}" style="font-size: 0.95rem; color: ${isUnknown ? 'var(--error)' : 'var(--secondary)'};"></i>
        </button>
      `;

      const facultyNameHtml = isUnknown 
        ? `<span style="color: var(--error); font-weight: 500;">${fac.name} (Pending Hire)</span>${editBtnHtml}` 
        : `<span>${fac.name || ''}</span>${editBtnHtml}`;

      tr.innerHTML = `
        <td>${facultyIdHtml}</td>
        <td>${facultyNameHtml}</td>
        <td>${renderYearCell(yearAllocs[1])}</td>
        <td>${renderYearCell(yearAllocs[2])}</td>
        <td>${renderYearCell(yearAllocs[3])}</td>
        <td>${renderYearCell(yearAllocs[4])}</td>
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

  let activeEditFacultyId = null;
  let activeEditFacultyName = null;
  let activeEditFacultyIsUnknown = false;

  window.openFacultyAllocationsEditModal = function(facultyId, facultyName, isUnknown) {
    activeEditFacultyId = facultyId;
    activeEditFacultyName = facultyName;
    activeEditFacultyIsUnknown = isUnknown;

    document.getElementById('fac-edit-name-info').innerText = facultyName;
    document.getElementById('fac-edit-id-info').innerText = facultyId;

    const listEl = document.getElementById('fac-allocs-list');
    listEl.innerHTML = '';

    const fac = rawReportData.find(x => x.facultyId && String(x.facultyId).toUpperCase() === String(facultyId).toUpperCase());
    if (!fac || !fac.allocations || fac.allocations.length === 0) {
      listEl.innerHTML = '<div style="color: var(--text-muted); text-align: center; padding: 20px;">No active allocations for this faculty.</div>';
      // Open Modal
      document.getElementById('faculty-edit-modal-overlay').classList.add('active');
      return;
    }

    fac.allocations.forEach(a => {
      const div = document.createElement('div');
      div.style.cssText = "display: flex; justify-content: space-between; align-items: center; background: rgba(255,255,255,0.03); border: 1px solid var(--panel-border); border-radius: var(--border-radius-md); padding: 12px 16px; gap: 12px;";

      const infoSpan = document.createElement('span');
      const secText = a.sectionName && a.sectionName !== 'N/A' ? ` - Sec ${a.sectionName}` : '';
      infoSpan.innerHTML = `<strong style="color: var(--text-main);">${cleanSubjectName(a.subjectName)}</strong>${secText} <span style="color: var(--text-muted); font-size: 0.82rem; margin-left: 8px;">(Year ${a.year})</span>`;

      const actionArea = document.createElement('div');
      actionArea.style.cssText = "display: flex; gap: 8px; align-items: center;";

      const reassignBtn = document.createElement('button');
      reassignBtn.className = "btn btn-primary btn-sm";
      reassignBtn.innerHTML = '<i class="fas fa-random"></i> Reassign';
      reassignBtn.style.cssText = "height: 32px; font-size: 0.85rem; padding: 4px 12px;";
      reassignBtn.onclick = () => showInlineReassignDropdown(actionArea, a.subjectId, a.subjectName, a.sectionName, facultyId, a.year, a.semester);

      actionArea.appendChild(reassignBtn);
      div.appendChild(infoSpan);
      div.appendChild(actionArea);
      listEl.appendChild(div);
    });

    // Open Modal
    document.getElementById('faculty-edit-modal-overlay').classList.add('active');
  };

  window.closeFacultyEditModal = function() {
    document.getElementById('faculty-edit-modal-overlay').classList.remove('active');
    activeEditFacultyId = null;
    activeEditFacultyName = null;
    activeEditFacultyIsUnknown = false;
  };

  function showInlineReassignDropdown(container, subjectId, subjectName, sectionName, fromFacultyId, year, semester) {
    container.innerHTML = '';
    
    const eligibleSwaps = [];
    rawReportData.forEach(fac => {
      if (fac.allocations && fac.allocations.length > 0) {
        fac.allocations.forEach(alloc => {
          if (Number(alloc.year) === Number(year) && Number(alloc.semester) === Number(semester)) {
            if (!(alloc.subjectId === subjectId && alloc.sectionName === sectionName && fac.facultyId === fromFacultyId)) {
              eligibleSwaps.push({
                subjectId: alloc.subjectId,
                subjectName: alloc.subjectName,
                sectionName: alloc.sectionName,
                facultyId: fac.facultyId,
                facultyName: fac.name
              });
            }
          }
        });
      }
    });

    const select = document.createElement('select');
    select.className = 'form-control';
    select.style.cssText = "height: 32px; font-size: 0.85rem; background: rgba(15, 23, 42, 0.6); border: 1px solid var(--panel-border); border-radius: var(--border-radius-md); color: var(--text-main); padding: 4px 8px; width: 220px;";

    if (eligibleSwaps.length === 0) {
      const opt = document.createElement('option');
      opt.text = "No swap options";
      opt.disabled = true;
      select.appendChild(opt);
      container.appendChild(select);
      
      const cancelBtn = document.createElement('button');
      cancelBtn.className = "btn btn-ghost btn-sm";
      cancelBtn.innerHTML = '<i class="fas fa-times"></i>';
      cancelBtn.style.cssText = "height: 32px; width: 32px; display: inline-flex; align-items: center; justify-content: center; border: 1px solid var(--panel-border); color: var(--text-muted); margin-left: 8px;";
      cancelBtn.onclick = () => restoreReassignBtn();
      container.appendChild(cancelBtn);
    } else {
      const optDefault = document.createElement('option');
      optDefault.text = "Swap with subject/section";
      optDefault.value = "";
      optDefault.disabled = true;
      optDefault.selected = true;
      select.appendChild(optDefault);

      eligibleSwaps.sort((s1, s2) => s1.subjectName.localeCompare(s2.subjectName));

      eligibleSwaps.forEach(opt => {
        const o = document.createElement('option');
        o.value = `${opt.subjectId}|${opt.sectionName}`;
        o.text = `${cleanSubjectName(opt.subjectName)} - Sec ${opt.sectionName} (${opt.facultyName})`;
        select.appendChild(o);
      });

      const saveBtn = document.createElement('button');
      saveBtn.className = "btn btn-primary btn-sm";
      saveBtn.innerHTML = '<i class="fas fa-check"></i>';
      saveBtn.style.cssText = "height: 32px; width: 32px; display: inline-flex; align-items: center; justify-content: center; margin-left: 8px;";

      const cancelBtn = document.createElement('button');
      cancelBtn.className = "btn btn-ghost btn-sm";
      cancelBtn.innerHTML = '<i class="fas fa-times"></i>';
      cancelBtn.style.cssText = "height: 32px; width: 32px; display: inline-flex; align-items: center; justify-content: center; border: 1px solid var(--panel-border); color: var(--text-muted); margin-left: 8px;";
      cancelBtn.onclick = () => restoreReassignBtn();

      saveBtn.onclick = async () => {
        const val = select.value;
        if (!val) {
          showToast('Selection Required', 'Please select a subject/section to swap with.', 'error');
          return;
        }

        const [newSubjectId, newSectionName] = val.split('|');

        saveBtn.disabled = true;
        saveBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';

        try {
          await apiRequest(`/adminfaculty/swap-subjects?facultyId=${encodeURIComponent(fromFacultyId)}&oldSubjectId=${encodeURIComponent(subjectId)}&oldSectionName=${encodeURIComponent(sectionName)}&newSubjectId=${encodeURIComponent(newSubjectId)}&newSectionName=${encodeURIComponent(newSectionName)}`, {
            method: 'POST'
          });
          showToast('Success', 'Allocations swapped successfully!', 'success');

          await loadReportData();
          openFacultyAllocationsEditModal(activeEditFacultyId, activeEditFacultyName, activeEditFacultyIsUnknown);
        } catch (err) {
          showToast('Swap Failed', err.message || 'Could not swap allocations', 'error');
          saveBtn.disabled = false;
          saveBtn.innerHTML = '<i class="fas fa-check"></i>';
        }
      };

      container.appendChild(select);
      container.appendChild(saveBtn);
      container.appendChild(cancelBtn);
    }

    function restoreReassignBtn() {
      container.innerHTML = '';
      const reassignBtn = document.createElement('button');
      reassignBtn.className = "btn btn-primary btn-sm";
      reassignBtn.innerHTML = '<i class="fas fa-random"></i> Reassign';
      reassignBtn.style.cssText = "height: 32px; font-size: 0.85rem; padding: 4px 12px;";
      reassignBtn.onclick = () => showInlineReassignDropdown(container, subjectId, subjectName, sectionName, fromFacultyId, year, semester);
      container.appendChild(reassignBtn);
    }
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

    let tableHtml = '<table border="1">';
    
    // Build Header
    tableHtml += '<thead><tr style="background-color: #14B8A6; color: #ffffff; font-weight: bold;">';
    tableHtml += '<th>Faculty ID</th><th>Faculty Name</th>';
    tableHtml += '<th>Year 1</th><th>Year 2</th><th>Year 3</th><th>Year 4</th>';
    tableHtml += '</tr></thead><tbody>';

    // Build Rows
    reportData.forEach(fac => {
      tableHtml += '<tr>';
      tableHtml += `<td style="vnd.ms-excel.numberformat:@">${fac.facultyId || ''}</td>`;
      tableHtml += `<td>${fac.name || ''}</td>`;

      // Group by year
      const yearAllocs = { 1: [], 2: [], 3: [], 4: [] };
      const allocs = fac.allocations || [];
      allocs.forEach(a => {
        const yr = a.year;
        if (yearAllocs[yr]) {
          yearAllocs[yr].push(a);
        }
      });

      const getExcelCellText = (allocsList) => {
        if (allocsList.length === 0) return '';
        return allocsList.map(a => {
          return `${cleanSubjectName(a.subjectName)} (${cleanSubjectCode(a.subjectId || '')})`;
        }).join(', ');
      };

      tableHtml += `<td>${getExcelCellText(yearAllocs[1])}</td>`;
      tableHtml += `<td>${getExcelCellText(yearAllocs[2])}</td>`;
      tableHtml += `<td>${getExcelCellText(yearAllocs[3])}</td>`;
      tableHtml += `<td>${getExcelCellText(yearAllocs[4])}</td>`;
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

  // Set up custom dropdown toggle behavior
  const customDeptDropdown = document.getElementById('custom-dept-dropdown');
  if (customDeptDropdown) {
    const triggerBtn = customDeptDropdown.querySelector('.custom-dropdown-trigger');
    const menuEl = customDeptDropdown.querySelector('.custom-dropdown-menu');
    
    triggerBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      const isVisible = menuEl.style.display === 'block';
      menuEl.style.display = isVisible ? 'none' : 'block';
    });
    
    document.addEventListener('click', (e) => {
      if (!customDeptDropdown.contains(e.target)) {
        menuEl.style.display = 'none';
      }
    });
  }

  // Initial load
  loadUsers();
});
