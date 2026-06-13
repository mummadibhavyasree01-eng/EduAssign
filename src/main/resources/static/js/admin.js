// Admin Dashboard Logic for EduAssign

document.addEventListener('DOMContentLoaded', () => {
  // 1. Session check
  const currentUser = checkSession(['ADMIN']);
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

  // State caches
  let facultyList = [];
  let subjectList = [];
  let departments = [];
  let academicYears = [];
  let sections = [];
  let selectionWindow = null;

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

      // Refresh data depending on target tab
      if (targetId === 'faculty-tab') loadFaculty();
      if (targetId === 'subject-tab') loadSubjects();
      if (targetId === 'config-tab') loadConfigData();
      if (targetId === 'deadline-tab') loadDeadlineStatus();
      if (targetId === 'allocation-tab') loadAllocationWorkspace();
    });
  });

  // ----------------------------------------------------
  // DRAG & DROP FOR EXCEL UPLOADS
  // ----------------------------------------------------
  
  setupDragAndDrop('faculty-dropzone', 'faculty-excel-file', 'faculty-file-name');
  setupDragAndDrop('subject-dropzone', 'subject-excel-file', 'subject-file-name');

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

  // Handle Excel Upload Forms
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

  document.getElementById('subject-upload-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fileInput = document.getElementById('subject-excel-file');
    if (fileInput.files.length === 0) {
      showToast('Select File', 'Please choose an Excel file first', 'warning');
      return;
    }

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);

    try {
      await apiRequest('/subject/uploadSubject', {
        method: 'POST',
        body: formData
      });
      showToast('Subjects Uploaded', 'Excel parsed and imported successfully', 'success');
      fileInput.value = '';
      document.getElementById('subject-file-name').innerText = 'No file chosen';
      loadSubjects();
    } catch (error) {
      showToast('Upload Failed', error.message || 'Error parsing Excel sheet', 'error');
    }
  });

  // ----------------------------------------------------
  // FACULTY CRUD
  // ----------------------------------------------------
  
  async function loadFaculty() {
    const tbody = document.getElementById('faculty-table-body');
    tbody.innerHTML = `<tr><td colspan="5" style="text-align: center;"><i class="fas fa-spinner fa-spin"></i> Fetching faculty list...</td></tr>`;

    try {
      const data = await apiRequest('/adminfaculty/viewfaculty');
      if (typeof data === 'string' && data.toLowerCase().includes('no faculty')) {
        facultyList = [];
        tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted);">No faculty registered.</td></tr>`;
        return;
      }
      facultyList = Array.isArray(data) ? data : [];
      renderFacultyTable(facultyList);
    } catch (error) {
      showToast('Load Error', 'Could not fetch faculty members', 'error');
      tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--error);">Failed to load faculty</td></tr>`;
    }
  }

  function renderFacultyTable(list) {
    const tbody = document.getElementById('faculty-table-body');
    tbody.innerHTML = '';

    if (list.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted);">No matching faculty found.</td></tr>`;
      return;
    }

    list.forEach(fac => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><strong>${fac.id}</strong></td>
        <td>${fac.name}</td>
        <td>${fac.email}</td>
        <td><code style="color: var(--text-muted); font-size: 0.85rem;">${fac.password}</code></td>
        <td style="text-align: right;">
          <button class="action-btn action-btn-edit" onclick="editFaculty('${fac.id}')" title="Edit"><i class="fas fa-edit"></i></button>
          <button class="action-btn action-btn-delete" onclick="deleteFaculty('${fac.id}')" title="Delete"><i class="fas fa-trash-alt"></i></button>
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
        pwdInput.value = fac.password;
      }
    } else {
      mode.value = 'add';
      title.innerText = 'Add Faculty';
      btn.innerText = 'Add Faculty';
    }

    modal.classList.add('active');
  };

  window.closeFacultyModal = function() {
    document.getElementById('faculty-modal').classList.remove('active');
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

  // ----------------------------------------------------
  // SUBJECT CRUD
  // ----------------------------------------------------

  async function loadSubjects() {
    const tbody = document.getElementById('subject-table-body');
    tbody.innerHTML = `<tr><td colspan="7" style="text-align: center;"><i class="fas fa-spinner fa-spin"></i> Fetching subject directory...</td></tr>`;

    try {
      const data = await apiRequest('/subject/viewAll');
      subjectList = Array.isArray(data) ? data : [];
      renderSubjectTable(subjectList);
    } catch (error) {
      showToast('Load Error', 'Could not fetch subjects', 'error');
      tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--error);">Failed to load subjects</td></tr>`;
    }
  }

  function renderSubjectTable(list) {
    const tbody = document.getElementById('subject-table-body');
    tbody.innerHTML = '';

    if (list.length === 0) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted);">No matching subjects found.</td></tr>`;
      return;
    }

    list.forEach(sub => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><strong>${sub.id}</strong></td>
        <td>${sub.name}</td>
        <td>${sub.year}</td>
        <td>Sem ${sub.sem}</td>
        <td><span class="badge badge-faculty" style="background: rgba(255,255,255,0.05); color: #fff; border-color: rgba(255,255,255,0.1)">${sub.dep}</span></td>
        <td>${sub.regulation}</td>
        <td style="text-align: right;">
          <button class="action-btn action-btn-edit" onclick="editSubject('${sub.id}')" title="Edit"><i class="fas fa-edit"></i></button>
          <button class="action-btn action-btn-delete" onclick="deleteSubject('${sub.id}')" title="Delete"><i class="fas fa-trash-alt"></i></button>
        </td>
      `;
      tbody.appendChild(tr);
    });
  }

  // Client-side Subject Search
  document.getElementById('subject-search').addEventListener('input', (e) => {
    const q = e.target.value.toLowerCase().trim();
    const filtered = subjectList.filter(s => 
      s.id.toLowerCase().includes(q) || 
      s.name.toLowerCase().includes(q) || 
      s.dep.toLowerCase().includes(q) ||
      s.regulation.toLowerCase().includes(q)
    );
    renderSubjectTable(filtered);
  });

  window.openSubjectModal = async function(subId = null) {
    const modal = document.getElementById('subject-modal');
    const title = document.getElementById('subject-modal-title');
    const btn = document.getElementById('subject-modal-btn');
    const mode = document.getElementById('subject-modal-mode');
    
    const idInput = document.getElementById('sub-id');
    const nameInput = document.getElementById('sub-name');
    const deptSelect = document.getElementById('sub-dept');
    const yearSelect = document.getElementById('sub-year');
    const semSelect = document.getElementById('sub-sem');
    const regInput = document.getElementById('sub-regulation');

    document.getElementById('subject-form').reset();
    idInput.disabled = false;

    // Load departments and years dynamically into selects first
    await loadConfigOptions();
    populateSelect(deptSelect, departments.map(d => ({ value: d.code, text: `${d.code} - ${d.name}` })), 'Select Department');
    populateSelect(yearSelect, academicYears.map(y => ({ value: y.yearNumber, text: y.name })), 'Select Year');

    if (subId) {
      const sub = subjectList.find(s => s.id === subId);
      if (sub) {
        mode.value = 'edit';
        title.innerText = 'Edit Subject';
        btn.innerText = 'Update Subject';
        idInput.value = sub.id;
        idInput.disabled = true; // Can't edit code
        nameInput.value = sub.name;
        deptSelect.value = sub.dep;
        yearSelect.value = sub.year;
        semSelect.value = sub.sem;
        regInput.value = sub.regulation;
      }
    } else {
      mode.value = 'add';
      title.innerText = 'Add Subject';
      btn.innerText = 'Add Subject';
    }

    modal.classList.add('active');
  };

  window.closeSubjectModal = function() {
    document.getElementById('subject-modal').classList.remove('active');
  };

  // Submit Add/Edit Subject Form
  document.getElementById('subject-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const mode = document.getElementById('subject-modal-mode').value;
    
    const subjectData = {
      id: document.getElementById('sub-id').value.trim(),
      name: document.getElementById('sub-name').value.trim(),
      dep: document.getElementById('sub-dept').value,
      year: parseInt(document.getElementById('sub-year').value),
      sem: parseInt(document.getElementById('sub-sem').value),
      regulation: document.getElementById('sub-regulation').value.trim()
    };

    try {
      if (mode === 'add') {
        await apiRequest('/subject/add', {
          method: 'POST',
          body: subjectData
        });
        showToast('Subject Added', 'New subject added to directory', 'success');
      } else {
        await apiRequest(`/subject/update/${subjectData.id}`, {
          method: 'PUT',
          body: subjectData
        });
        showToast('Subject Updated', 'Subject details updated successfully', 'success');
      }
      closeSubjectModal();
      loadSubjects();
    } catch (error) {
      showToast('Operation Failed', error.message || 'Could not save subject details', 'error');
    }
  });

  window.deleteSubject = function(id) {
    showConfirm('Delete Subject', `Are you sure you want to delete subject ${id}?`, async () => {
      try {
        await apiRequest(`/subject/delete/${id}`, {
          method: 'DELETE'
        });
        showToast('Subject Deleted', 'Subject removed from directory', 'success');
        loadSubjects();
      } catch (error) {
        showToast('Delete Failed', error.message || 'Could not delete subject', 'error');
      }
    });
  };

  // ----------------------------------------------------
  // CONFIG CRUD (DEPTS, YEARS, SECTIONS)
  // ----------------------------------------------------
  
  async function loadConfigData() {
    await loadConfigOptions();
    renderDeptTable();
    renderYearTable();
    renderSectionTable();

    // Populate Section addition form selectors
    const deptSelect = document.getElementById('section-dept-select');
    const yearSelect = document.getElementById('section-year-select');

    populateSelect(deptSelect, departments.map(d => ({ value: d.code, text: `${d.code} - ${d.name}` })), 'Select Dept');
    populateSelect(yearSelect, academicYears.map(y => ({ value: y.yearNumber, text: y.name })), 'Select Year');
  }

  async function loadConfigOptions() {
    try {
      departments = await apiRequest('/sections/departments');
      academicYears = await apiRequest('/sections/years');
      sections = await apiRequest('/sections/all');
    } catch (error) {
      console.error('Error fetching configuration values:', error);
    }
  }

  function populateSelect(selectEl, items, placeholder) {
    selectEl.innerHTML = `<option value="" disabled selected>${placeholder}</option>`;
    items.forEach(item => {
      const opt = document.createElement('option');
      opt.value = item.value;
      opt.innerText = item.text;
      selectEl.appendChild(opt);
    });
  }

  // Dept CRUD
  function renderDeptTable() {
    const tbody = document.getElementById('dept-table-body');
    tbody.innerHTML = '';
    departments.forEach(d => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><strong>${d.code}</strong></td>
        <td>${d.name}</td>
        <td>
          <button type="button" class="action-btn action-btn-delete" onclick="deleteDept('${d.code}')"><i class="fas fa-trash-alt"></i></button>
        </td>
      `;
      tbody.appendChild(tr);
    });
  }

  document.getElementById('dept-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const code = document.getElementById('dept-code').value.toUpperCase().trim();
    const name = document.getElementById('dept-name').value.trim();

    try {
      await apiRequest('/sections/departments', {
        method: 'POST',
        body: { code, name }
      });
      showToast('Department Added', `Created department ${code}`, 'success');
      document.getElementById('dept-form').reset();
      loadConfigData();
    } catch (error) {
      showToast('Error', error.message || 'Failed to add department', 'error');
    }
  });

  window.deleteDept = function(code) {
    showConfirm('Delete Department', `Delete department ${code}? All sections of this department will be impacted.`, async () => {
      try {
        await apiRequest(`/sections/departments/${code}`, {
          method: 'DELETE'
        });
        showToast('Department Deleted', `Deleted department ${code}`, 'success');
        loadConfigData();
      } catch (error) {
        showToast('Delete Failed', error.message || 'Could not delete department', 'error');
      }
    });
  };

  // Year CRUD
  function renderYearTable() {
    const tbody = document.getElementById('year-table-body');
    tbody.innerHTML = '';
    // Sort years numerically
    const sorted = [...academicYears].sort((a,b) => a.yearNumber - b.yearNumber);
    sorted.forEach(y => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><strong>Year ${y.yearNumber}</strong></td>
        <td>${y.name}</td>
        <td>
          <button type="button" class="action-btn action-btn-delete" onclick="deleteYear(${y.yearNumber})"><i class="fas fa-trash-alt"></i></button>
        </td>
      `;
      tbody.appendChild(tr);
    });
  }

  document.getElementById('year-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const yearNumber = parseInt(document.getElementById('year-num').value);
    const name = document.getElementById('year-name').value.trim();

    try {
      await apiRequest('/sections/years', {
        method: 'POST',
        body: { yearNumber, name }
      });
      showToast('Year Added', `Created Year ${yearNumber}`, 'success');
      document.getElementById('year-form').reset();
      loadConfigData();
    } catch (error) {
      showToast('Error', error.message || 'Failed to add year', 'error');
    }
  });

  window.deleteYear = function(yrNo) {
    showConfirm('Delete Year', `Delete curriculum year ${yrNo}? All sections of this year will be impacted.`, async () => {
      try {
        await apiRequest(`/sections/years/${yrNo}`, {
          method: 'DELETE'
        });
        showToast('Year Deleted', `Deleted year ${yrNo}`, 'success');
        loadConfigData();
      } catch (error) {
        showToast('Delete Failed', error.message || 'Could not delete year', 'error');
      }
    });
  };

  // Section CRUD
  function renderSectionTable() {
    const tbody = document.getElementById('section-table-body');
    tbody.innerHTML = '';
    
    // Sort by Dept -> Year -> Section name
    const sorted = [...sections].sort((a,b) => {
      if (a.departmentCode !== b.departmentCode) return a.departmentCode.localeCompare(b.departmentCode);
      if (a.yearNumber !== b.yearNumber) return a.yearNumber - b.yearNumber;
      return a.sectionName.localeCompare(b.sectionName);
    });

    sorted.forEach(s => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><strong>${s.departmentCode}</strong></td>
        <td>Year ${s.yearNumber}</td>
        <td>Section ${s.sectionName}</td>
        <td>
          <button type="button" class="action-btn action-btn-delete" onclick="deleteSection(${s.id})"><i class="fas fa-trash-alt"></i></button>
        </td>
      `;
      tbody.appendChild(tr);
    });
  }

  document.getElementById('section-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const departmentCode = document.getElementById('section-dept-select').value;
    const yearNumber = parseInt(document.getElementById('section-year-select').value);
    const sectionName = document.getElementById('section-name').value.toUpperCase().trim();

    try {
      await apiRequest('/sections/add', {
        method: 'POST',
        body: { departmentCode, yearNumber, sectionName }
      });
      showToast('Section Added', `Created section ${sectionName} for Year ${yearNumber} ${departmentCode}`, 'success');
      document.getElementById('section-name').value = '';
      loadConfigData();
    } catch (error) {
      showToast('Error', error.message || 'Failed to add section', 'error');
    }
  });

  window.deleteSection = function(id) {
    showConfirm('Delete Section', `Are you sure you want to delete this section configuration?`, async () => {
      try {
        await apiRequest(`/sections/delete/${id}`, {
          method: 'DELETE'
        });
        showToast('Section Deleted', `Section configuration removed`, 'success');
        loadConfigData();
      } catch (error) {
        showToast('Delete Failed', error.message || 'Could not delete section', 'error');
      }
    });
  };

  // ----------------------------------------------------
  // TIMELINE SELECTION DEADLINE
  // ----------------------------------------------------

  async function loadDeadlineStatus() {
    const statusContainer = document.getElementById('deadline-status-container');
    statusContainer.innerHTML = `<div style="color: var(--text-muted);"><i class="fas fa-circle-notch fa-spin"></i> Loading...</div>`;

    try {
      selectionWindow = await apiRequest('/adminfaculty/deadline');
      
      if (!selectionWindow || !selectionWindow.active) {
        statusContainer.innerHTML = `
          <div class="status-badge expired"><i class="fas fa-times-circle"></i> INACTIVE</div>
          <p style="margin-top: 10px; font-size: 0.9rem; color: var(--text-muted);">No subject selection window has been published yet. Faculty cannot submit preferences.</p>
        `;
        return;
      }

      const deadlineTime = new Date(selectionWindow.deadline);
      const now = new Date();
      const diffMs = deadlineTime - now;

      if (diffMs <= 0) {
        statusContainer.innerHTML = `
          <div class="status-badge expired"><i class="fas fa-history"></i> EXPIRED</div>
          <p style="margin-top: 10px; font-size: 0.95rem; font-weight: 500;">Deadline Passed on:</p>
          <p style="font-size: 0.85rem; color: var(--text-muted);">${deadlineTime.toLocaleString()}</p>
          <p style="margin-top: 10px; font-size: 0.88rem; font-style: italic; color: var(--secondary); border-top: 1px solid var(--panel-border); padding-top: 10px;">"${selectionWindow.message}"</p>
        `;
      } else {
        const days = Math.floor(diffMs / (1000 * 60 * 60 * 24));
        const hours = Math.floor((diffMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
        const mins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));

        statusContainer.innerHTML = `
          <div class="status-badge active"><i class="fas fa-hourglass-half"></i> RUNNING</div>
          <p style="margin-top: 12px; font-size: 0.95rem; font-weight: 600;">Time Remaining:</p>
          <h4 style="color: var(--secondary); font-size: 1.5rem; margin-top: 4px; font-weight: 700;">
            ${days}d ${hours}h ${mins}m
          </h4>
          <p style="font-size: 0.8rem; color: var(--text-muted); margin-top: 4px;">Deadline: ${deadlineTime.toLocaleString()}</p>
          <p style="margin-top: 12px; font-size: 0.88rem; font-style: italic; color: var(--text-muted); border-top: 1px solid var(--panel-border); padding-top: 10px;">"${selectionWindow.message}"</p>
        `;
      }
      
      // Populate fields if active/available
      document.getElementById('deadline-message').value = selectionWindow.message;

    } catch (error) {
      statusContainer.innerHTML = `<div style="color: var(--error);">Error checking status</div>`;
    }
  }

  document.getElementById('deadline-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const msg = document.getElementById('deadline-message').value.trim();
    const days = parseInt(document.getElementById('deadline-days').value);

    try {
      const params = new URLSearchParams();
      params.append('message', msg);
      params.append('days', days);

      await apiRequest('/adminfaculty/deadline', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: params
      });

      showToast('Selection Window Opened', `Published selection timeline for ${days} days`, 'success');
      loadDeadlineStatus();
    } catch (error) {
      showToast('Error', error.message || 'Failed to publish deadline', 'error');
    }
  });

  // ----------------------------------------------------
  // SUBJECT ALLOCATION
  // ----------------------------------------------------

  async function loadAllocationWorkspace() {
    // 1. Fetch deadline status to check if allocation is allowed
    const warningBanner = document.getElementById('allocation-warning');
    const workspace = document.getElementById('allocation-workspace');
    
    try {
      selectionWindow = await apiRequest('/adminfaculty/deadline');
      
      let isBefore = false;
      if (selectionWindow && selectionWindow.active) {
        const deadlineTime = new Date(selectionWindow.deadline);
        const now = new Date();
        if (now < deadlineTime) {
          isBefore = true;
        }
      }

      if (isBefore) {
        warningBanner.style.display = 'block';
        workspace.classList.add('disabled-workspace'); // Apply CSS filter blur/disable
        workspace.style.pointerEvents = 'none';
        workspace.style.opacity = '0.45';
      } else {
        warningBanner.style.display = 'none';
        workspace.classList.remove('disabled-workspace');
        workspace.style.pointerEvents = 'auto';
        workspace.style.opacity = '1';
      }

      // Load selections and allocations
      await loadConfigOptions(); // Get latest sections list
      await loadFacultyListForAlloc();
      await loadSubjectListForAlloc();
      loadAllocations();

    } catch (error) {
      console.error(error);
    }
  }

  async function loadFacultyListForAlloc() {
    const select = document.getElementById('alloc-faculty-select');
    select.innerHTML = '<option value="" disabled selected>Choose Faculty...</option>';
    
    try {
      const data = await apiRequest('/adminfaculty/viewfaculty');
      const list = Array.isArray(data) ? data : [];
      list.forEach(f => {
        const opt = document.createElement('option');
        opt.value = f.id;
        opt.innerText = `${f.id} - ${f.name}`;
        select.appendChild(opt);
      });
    } catch (error) {
      console.error(error);
    }
  }

  async function loadSubjectListForAlloc() {
    const select = document.getElementById('alloc-subject-select');
    select.innerHTML = '<option value="" disabled selected>Choose Subject...</option>';
    
    try {
      const data = await apiRequest('/subject/viewAll');
      const list = Array.isArray(data) ? data : [];
      list.forEach(s => {
        const opt = document.createElement('option');
        opt.value = s.id;
        opt.innerText = `${s.id} - ${s.name} (Year ${s.year} Sem ${s.sem} ${s.dep})`;
        select.appendChild(opt);
      });
    } catch (error) {
      console.error(error);
    }
  }

  // Monitor Faculty dropdown change to load their preference list
  document.getElementById('alloc-faculty-select').addEventListener('change', async (e) => {
    const facId = e.target.value;
    const prefList = document.getElementById('alloc-faculty-pref-list');
    prefList.innerHTML = '<li><i class="fas fa-spinner fa-spin"></i> Loading choices...</li>';

    try {
      const preferences = await apiRequest(`/faculty/preferences/${facId}`);
      if (preferences.length === 0) {
        prefList.innerHTML = '<li style="color: var(--text-muted); list-style-type: none; margin-left: -20px;">Faculty hasn\'t selected any subjects.</li>';
        return;
      }

      prefList.innerHTML = '';
      
      // Load all subjects to map name
      const allSubs = await apiRequest('/subject/viewAll');
      const subjectsMap = {};
      allSubs.forEach(s => { subjectsMap[s.id] = s.name; });

      preferences.forEach(p => {
        const li = document.createElement('li');
        const subName = subjectsMap[p.subjectId] || 'Unknown Subject';
        li.innerText = `${p.subjectId} - ${subName}`;
        prefList.appendChild(li);
      });

    } catch (error) {
      prefList.innerHTML = '<li style="color: var(--error)">Failed to load preferences</li>';
    }
  });

  // Monitor Subject dropdown change to load configured sections for that Subject's Year and Dept
  document.getElementById('alloc-subject-select').addEventListener('change', async (e) => {
    const subId = e.target.value;
    const secSelect = document.getElementById('alloc-section-select');
    secSelect.innerHTML = '<option value="" disabled selected>Loading sections...</option>';

    try {
      const subject = await apiRequest(`/subject/view/${subId}`);
      if (!subject) return;

      // Filter sections configured for this subject's Dept and Year
      // Note: Subject properties are year, dep. Section properties are yearNumber, departmentCode
      const filteredSections = sections.filter(sec => 
        sec.yearNumber === subject.year && 
        sec.departmentCode.toUpperCase() === subject.dep.toUpperCase()
      );

      if (filteredSections.length === 0) {
        secSelect.innerHTML = '<option value="" disabled selected>No sections configured for Year ' + subject.year + ' ' + subject.dep + '</option>';
        return;
      }

      populateSelect(secSelect, filteredSections.map(s => ({ value: s.sectionName, text: `Section ${s.sectionName}` })), 'Choose Section...');

    } catch (error) {
      secSelect.innerHTML = '<option value="" disabled selected>Error loading sections</option>';
    }
  });

  async function loadAllocations() {
    const tbody = document.getElementById('allocation-table-body');
    tbody.innerHTML = `<tr><td colspan="6" style="text-align: center;"><i class="fas fa-spinner fa-spin"></i> Loading allocations...</td></tr>`;

    try {
      const allocations = await apiRequest('/adminfaculty/allocations');
      if (allocations.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted);">No subjects allocated yet.</td></tr>`;
        return;
      }

      // Fetch helper data (faculty names and subject details to enrich row)
      const faculty = await apiRequest('/adminfaculty/viewfaculty');
      const subjects = await apiRequest('/subject/viewAll');

      const facultyMap = {};
      faculty.forEach(f => facultyMap[f.id] = f.name);
      
      const subjectsMap = {};
      subjects.forEach(s => subjectsMap[s.id] = s);

      tbody.innerHTML = '';
      allocations.forEach(alloc => {
        const sub = subjectsMap[alloc.subjectId] || { name: 'Unknown Subject', year: '?', dep: '?' };
        const facName = facultyMap[alloc.facultyId] || 'Unknown Faculty';
        
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td><strong>${alloc.facultyId}</strong><br><span style="font-size: 0.85rem; color: var(--text-muted);">${facName}</span></td>
          <td>${alloc.subjectId}</td>
          <td>${sub.name}</td>
          <td>Year ${sub.year} (${sub.dep})</td>
          <td><span class="badge badge-admin">Sec ${alloc.sectionName}</span></td>
          <td style="text-align: right;">
            <button class="action-btn action-btn-delete" onclick="deleteAllocation(${alloc.id})" title="Remove"><i class="fas fa-trash-alt"></i></button>
          </td>
        `;
        tbody.appendChild(tr);
      });

    } catch (error) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--error);">Failed to load allocations</td></tr>`;
    }
  }

  // Allocate submit
  document.getElementById('allocate-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const facultyId = document.getElementById('alloc-faculty-select').value;
    const subjectId = document.getElementById('alloc-subject-select').value;
    const sectionName = document.getElementById('alloc-section-select').value;

    const allocationData = { facultyId, subjectId, sectionName };

    const submitBtn = document.getElementById('alloc-submit-btn');
    submitBtn.disabled = true;

    try {
      await apiRequest('/adminfaculty/allocate', {
        method: 'POST',
        body: allocationData
      });
      showToast('Allocation Saved', 'Subject allocated to faculty section', 'success');
      loadAllocations();
      // Reset form selector for sections/subjects
      document.getElementById('alloc-subject-select').value = '';
      document.getElementById('alloc-section-select').innerHTML = '<option value="" disabled selected>Choose Section...</option>';
    } catch (error) {
      showToast('Allocation Blocked', error.message || 'Validation error saving allocation', 'error');
    } finally {
      submitBtn.disabled = false;
    }
  });

  window.deleteAllocation = function(id) {
    showConfirm('Delete Allocation', 'Are you sure you want to remove this subject allocation?', async () => {
      try {
        await apiRequest(`/adminfaculty/allocation/${id}`, {
          method: 'DELETE'
        });
        showToast('Allocation Removed', 'Subject allocation deleted successfully', 'success');
        loadAllocations();
      } catch (error) {
        showToast('Error', error.message || 'Could not delete allocation', 'error');
      }
    });
  };

  // ----------------------------------------------------
  // PROFILE UPDATE
  // ----------------------------------------------------
  
  const profileForm = document.getElementById('profile-form');
  profileForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const updatedData = {
      id: currentUser.id,
      name: profileNameInput.value.trim(),
      email: profileEmailInput.value.trim(),
      password: profilePasswordInput.value
    };

    try {
      const response = await apiRequest('/adminfaculty/update', {
        method: 'PUT',
        body: updatedData
      });

      showToast('Profile Updated', 'Administrator profile details saved', 'success');

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

  // Initial load on startup
  loadFaculty();
});
