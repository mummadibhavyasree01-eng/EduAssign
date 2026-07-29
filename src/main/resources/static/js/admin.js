// Admin Dashboard Logic for EduAssign

document.addEventListener('DOMContentLoaded', () => {
  // 1. Session check
  const currentUser = checkSession(['ADMIN', 'SUPERADMIN']);
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
  let subjectList = [];
  let departments = [];
  let academicYears = [];
  let semesters = [];
  let sections = [];
  let selectionWindow = null;
  let allPreferences = [];
  let noPrefFaculty = [];

  function getSubjectDeptCode(sub) {
    if (!sub || !sub.dep) return '';
    const depNameOrCode = sub.dep.trim().toUpperCase();
    const dept = departments.find(d => d.code.toUpperCase() === depNameOrCode || d.name.toUpperCase() === depNameOrCode);
    return dept ? dept.code.toUpperCase() : depNameOrCode;
  }

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
      if (targetId === 'selections-tab') loadFacultySelections();
      if (targetId === 'subject-tab') loadSubjects();
      if (targetId === 'config-tab') loadConfigData();
      if (targetId === 'deadline-tab') loadDeadlineStatus();
      if (targetId === 'allocation-tab') loadAllocationWorkspace();
      if (targetId === 'section-allocation-tab') loadAllocationWorkspace();
    });
  });

  // ----------------------------------------------------
  // DRAG & DROP FOR EXCEL UPLOADS
  // ----------------------------------------------------
  
  setupDragAndDrop('subject-dropzone', 'subject-excel-file', 'subject-file-name');

  function setupDragAndDrop(zoneId, fileInputId, displayId) {
    const zone = document.getElementById(zoneId);
    const input = document.getElementById(fileInputId);
    const display = document.getElementById(displayId);

    if (!zone || !input || !display) return;

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
  // FACULTY SELECTIONS
  // ----------------------------------------------------

  async function loadFacultySelections() {
    const tbody = document.getElementById('preferences-table-body');
    const pendingTbody = document.getElementById('pending-table-body');
    tbody.innerHTML = `<tr><td colspan="5" style="text-align: center;"><i class="fas fa-spinner fa-spin"></i> Loading selections...</td></tr>`;
    pendingTbody.innerHTML = `<tr><td colspan="2" style="text-align: center;"><i class="fas fa-spinner fa-spin"></i> Loading...</td></tr>`;

    try {
      if (departments.length === 0 || academicYears.length === 0) {
        await loadConfigOptions();
      }
      
      populateFilterDropdowns();

      const faculty = await apiRequest('/adminfaculty/viewfaculty');
      const subjects = await apiRequest('/subject/viewAll');
      allPreferences = await apiRequest('/faculty/preferences/all');
      noPrefFaculty = await apiRequest('/adminfaculty/no-preferences-faculty');

      const facultyMap = {};
      const facList = Array.isArray(faculty) ? faculty : [];
      facList.forEach(f => facultyMap[f.id] = f);
      
      const subjectsMap = {};
      const subList = Array.isArray(subjects) ? subjects : [];
      subList.forEach(s => subjectsMap[s.id] = s);

      window.preferencesData = {
        allPreferences: Array.isArray(allPreferences) ? allPreferences : [],
        facultyMap,
        subjectsMap,
        facultyList: Array.isArray(faculty) ? faculty : []
      };

      renderPreferencesTable();
      renderPendingTable();

    } catch (error) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--error);">Failed to load preferences</td></tr>`;
      pendingTbody.innerHTML = `<tr><td colspan="2" style="text-align: center; color: var(--error);">Failed to load pending list</td></tr>`;
    }
  }

  function populateFilterDropdowns() {
    const deptFilter = document.getElementById('pref-dept-filter');
    const yearFilter = document.getElementById('pref-year-filter');
    const semFilter = document.getElementById('pref-sem-filter');

    const subDeptFilter = document.getElementById('sub-dept-filter');
    const subYearFilter = document.getElementById('sub-year-filter');
    const subSemFilter = document.getElementById('sub-sem-filter');

    if (deptFilter && deptFilter.options.length <= 1) {
      departments.forEach(d => {
        const opt = document.createElement('option');
        opt.value = d.code;
        opt.innerText = `${d.code} - ${d.name}`;
        deptFilter.appendChild(opt);
      });
    }
    if (subDeptFilter && subDeptFilter.options.length <= 1) {
      departments.forEach(d => {
        const opt = document.createElement('option');
        opt.value = d.code;
        opt.innerText = `${d.code} - ${d.name}`;
        subDeptFilter.appendChild(opt);
      });
    }

    if (yearFilter && yearFilter.options.length <= 1) {
      academicYears.forEach(y => {
        const opt = document.createElement('option');
        opt.value = y.yearNumber;
        opt.innerText = y.name;
        yearFilter.appendChild(opt);
      });
    }
    if (subYearFilter && subYearFilter.options.length <= 1) {
      academicYears.forEach(y => {
        const opt = document.createElement('option');
        opt.value = y.yearNumber;
        opt.innerText = y.name;
        subYearFilter.appendChild(opt);
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
    if (subSemFilter && subSemFilter.options.length <= 1) {
      const sortedSems = [...semesters].sort((a,b) => a.semNumber - b.semNumber);
      sortedSems.forEach(s => {
        const opt = document.createElement('option');
        opt.value = s.semNumber;
        opt.innerText = s.name;
        subSemFilter.appendChild(opt);
      });
    }
  }

  function renderPreferencesTable() {
    const tbody = document.getElementById('preferences-table-body');
    if (!tbody) return;
    tbody.innerHTML = '';

    const deptVal = document.getElementById('pref-dept-filter').value;
    const yearVal = document.getElementById('pref-year-filter').value;
    const semVal = document.getElementById('pref-sem-filter').value;

    if (!deptVal || !yearVal || !semVal) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 15px;">Please select Department, Academic Year, and Semester to view preferences.</td></tr>`;
      return;
    }

    const data = window.preferencesData;
    if (!data || !data.allPreferences) return;

    let filtered = data.allPreferences;

    // Filter out orphaned preferences where faculty or subject no longer exists
    filtered = filtered.filter(p => data.facultyMap[p.facultyId] && data.subjectsMap[p.subjectId]);

    if (deptVal) {
      filtered = filtered.filter(p => {
        const sub = data.subjectsMap[p.subjectId];
        return sub && getSubjectDeptCode(sub) === deptVal.toUpperCase();
      });
    }
    if (yearVal) {
      filtered = filtered.filter(p => {
        const sub = data.subjectsMap[p.subjectId];
        return sub && Number(sub.year) === Number(yearVal);
      });
    }
    if (semVal) {
      filtered = filtered.filter(p => {
        const sub = data.subjectsMap[p.subjectId];
        return sub && Number(sub.sem) === Number(semVal);
      });
    }

    if (filtered.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted);">No preferences found matching criteria.</td></tr>`;
      return;
    }

    filtered.forEach(p => {
      const fac = data.facultyMap[p.facultyId] || { name: 'Unknown Faculty' };
      const sub = data.subjectsMap[p.subjectId] || { name: 'Unknown Subject', year: '?', dep: '?', sem: '?' };
      
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><strong>${p.facultyId}</strong><br><span style="font-size: 0.85rem; color: var(--text-muted);">${fac.name}</span></td>
        <td>${p.subjectId}</td>
        <td>${sub.name}</td>
        <td>Year ${sub.year} (${sub.dep})</td>
        <td>Sem ${sub.sem}</td>
      `;
      tbody.appendChild(tr);
    });
  }

  function renderPendingTable() {
    const tbody = document.getElementById('pending-table-body');
    if (!tbody) return;
    tbody.innerHTML = '';

    const deptVal = document.getElementById('pref-dept-filter').value;
    const yearVal = document.getElementById('pref-year-filter').value;
    const semVal = document.getElementById('pref-sem-filter').value;

    if (!deptVal || !yearVal || !semVal) {
      tbody.innerHTML = `<tr><td colspan="2" style="text-align: center; color: var(--text-muted); padding: 15px;">Please select Department, Academic Year, and Semester to view pending submissions.</td></tr>`;
      return;
    }

    const data = window.preferencesData;
    if (!data || !data.facultyList) return;

    const allSubjects = Object.values(data.subjectsMap);

    // Filter subjects by selected criteria
    let matchingSubjects = allSubjects;
    if (deptVal) {
      matchingSubjects = matchingSubjects.filter(s => getSubjectDeptCode(s) === deptVal.toUpperCase());
    }
    if (yearVal) {
      matchingSubjects = matchingSubjects.filter(s => Number(s.year) === Number(yearVal));
    }
    if (semVal) {
      matchingSubjects = matchingSubjects.filter(s => Number(s.sem) === Number(semVal));
    }

    const matchingSubjectIds = new Set(matchingSubjects.map(s => s.id));

    // Find which faculty have submitted preferences for these filtered subjects
    const submittedFacultyIds = new Set();
    data.allPreferences.forEach(p => {
      if (matchingSubjectIds.has(p.subjectId)) {
        submittedFacultyIds.add(p.facultyId);
      }
    });

    // Pending faculty are those in the facultyList who have not selected any filtered subject
    // But if matchingSubjects is empty, it means no subjects match the filter, so nobody is pending.
    const pendingFaculty = (matchingSubjects.length === 0) 
      ? [] 
      : data.facultyList.filter(f => !submittedFacultyIds.has(f.id));

    if (pendingFaculty.length === 0) {
      tbody.innerHTML = `<tr><td colspan="2" style="text-align: center; color: var(--success); padding: 15px;"><i class="fas fa-check-circle"></i> All faculty submitted!</td></tr>`;
      return;
    }

    pendingFaculty.forEach(fac => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><strong>${fac.id}</strong></td>
        <td>${fac.name}</td>
      `;
      tbody.appendChild(tr);
    });
  }

  // Register filter change event listeners to update both tables
  function handleFilterChange() {
    renderPreferencesTable();
    renderPendingTable();
  }
  document.getElementById('pref-dept-filter').addEventListener('change', handleFilterChange);
  document.getElementById('pref-year-filter').addEventListener('change', handleFilterChange);
  document.getElementById('pref-sem-filter').addEventListener('change', handleFilterChange);

  // ----------------------------------------------------
  // SUBJECT CRUD
  // ----------------------------------------------------

  async function loadSubjects() {
    const tbody = document.getElementById('subject-table-body');
    tbody.innerHTML = `<tr><td colspan="8" style="text-align: center;"><i class="fas fa-spinner fa-spin"></i> Fetching subject directory...</td></tr>`;

    try {
      const data = await apiRequest('/subject/viewAll');
      subjectList = Array.isArray(data) ? data : [];
      if (departments.length === 0 || academicYears.length === 0) {
        await loadConfigOptions();
      }
      populateFilterDropdowns();
      renderSubjectTable(subjectList);
    } catch (error) {
      showToast('Load Error', 'Could not fetch subjects', 'error');
      tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--error);">Failed to load subjects</td></tr>`;
    }
  }

  function renderSubjectTable(list) {
    const tbody = document.getElementById('subject-table-body');
    tbody.innerHTML = '';

    const deptVal = document.getElementById('sub-dept-filter').value;
    const yearVal = document.getElementById('sub-year-filter').value;
    const semVal = document.getElementById('sub-sem-filter').value;

    if (!deptVal || !yearVal || !semVal) {
      tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 15px;">Please select Department, Academic Year, and Semester to view subjects.</td></tr>`;
      return;
    }

    let filteredList = list.filter(s => 
      getSubjectDeptCode(s) === deptVal.toUpperCase() &&
      Number(s.year) === Number(yearVal) &&
      Number(s.sem) === Number(semVal)
    );

    if (filteredList.length === 0) {
      tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--text-muted);">No matching subjects found.</td></tr>`;
      return;
    }

    // Group list by Year, then by Sem
    const grouped = {};
    filteredList.forEach(sub => {
      const y = sub.year;
      const s = sub.sem;
      if (!grouped[y]) grouped[y] = {};
      if (!grouped[y][s]) grouped[y][s] = [];
      grouped[y][s].push(sub);
    });

    // Sort years and semesters
    const years = Object.keys(grouped).sort((a,b) => Number(a) - Number(b));
    
    // Find year name label from academicYears
    const getYearLabel = (yrNo) => {
      const yObj = academicYears.find(y => Number(y.yearNumber) === Number(yrNo));
      return yObj ? yObj.name : `Year ${yrNo}`;
    };

    years.forEach(y => {
      const sems = Object.keys(grouped[y]).sort((a,b) => Number(a) - Number(b));
      sems.forEach(s => {
        // Render group header row
        const headerTr = document.createElement('tr');
        headerTr.style.background = 'rgba(99, 102, 241, 0.08)';
        headerTr.style.fontWeight = 'bold';
        headerTr.innerHTML = `
          <td colspan="8" style="color: var(--secondary); padding: 10px 16px;">
            <i class="fas fa-layer-group" style="margin-right: 8px;"></i>
            ${getYearLabel(y)} - Semester ${s}
          </td>
        `;
        tbody.appendChild(headerTr);

        // Render subjects in this group
        grouped[y][s].forEach(sub => {
          const tr = document.createElement('tr');
          tr.innerHTML = `
            <td><strong>${sub.id}</strong></td>
            <td>${sub.name}</td>
            <td>Year ${sub.year}</td>
            <td>Sem ${sub.sem}</td>
            <td><span class="badge badge-faculty" style="background: rgba(255,255,255,0.05); color: #fff; border-color: rgba(255,255,255,0.1)">${sub.dep}</span></td>
            <td>${sub.regulation}</td>
            <td>${sub.academicYear || '-'}</td>
            <td style="text-align: right;">
              <button class="action-btn action-btn-edit" onclick="editSubject('${sub.id}')" title="Edit"><i class="fas fa-edit"></i></button>
              <button class="action-btn action-btn-delete" onclick="deleteSubject('${sub.id}')" title="Delete"><i class="fas fa-trash-alt"></i></button>
            </td>
          `;
          tbody.appendChild(tr);
        });
      });
    });
  }

  // Client-side Subject Search and Filters
  function handleSubjectFilterChange() {
    const q = document.getElementById('subject-search').value.toLowerCase().trim();
    const filtered = subjectList.filter(s => 
      s.id.toLowerCase().includes(q) || 
      s.name.toLowerCase().includes(q) || 
      s.dep.toLowerCase().includes(q) ||
      s.regulation.toLowerCase().includes(q)
    );
    renderSubjectTable(filtered);
  }

  document.getElementById('subject-search').addEventListener('input', handleSubjectFilterChange);
  document.getElementById('sub-dept-filter').addEventListener('change', handleSubjectFilterChange);
  document.getElementById('sub-year-filter').addEventListener('change', handleSubjectFilterChange);
  document.getElementById('sub-sem-filter').addEventListener('change', handleSubjectFilterChange);

  document.getElementById('section-dept-select').addEventListener('change', renderSectionTable);

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
    const academicYearInput = document.getElementById('sub-academic-year');

    document.getElementById('subject-form').reset();
    idInput.disabled = false;

    // Load departments, years, and semesters dynamically into selects first
    await loadConfigOptions();
    populateSelect(deptSelect, departments.map(d => ({ value: d.code, text: `${d.code} - ${d.name}` })), 'Select Department');
    populateSelect(yearSelect, academicYears.map(y => ({ value: y.yearNumber, text: y.name })), 'Select Year');
    populateSelect(semSelect, semesters.map(s => ({ value: s.semNumber, text: s.name })), 'Select Semester');

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
        academicYearInput.value = sub.academicYear || '';
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

  window.editSubject = function(id) {
    window.openSubjectModal(id);
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
      regulation: document.getElementById('sub-regulation').value.trim(),
      academicYear: document.getElementById('sub-academic-year').value.trim()
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
      semesters = await apiRequest('/sections/semesters');
    } catch (error) {
      console.error('Error fetching configuration values:', error);
    }
  }

  function populateSelect(selectEl, items, placeholder) {
    const prevVal = selectEl.value;
    selectEl.innerHTML = `<option value="" disabled selected>${placeholder}</option>`;
    items.forEach(item => {
      const opt = document.createElement('option');
      opt.value = item.value;
      opt.innerText = item.text;
      selectEl.appendChild(opt);
    });
    if (prevVal && items.some(item => String(item.value) === String(prevVal))) {
      selectEl.value = prevVal;
    }
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

  // Semester CRUD
  function renderSemesterTable() {
    const tbody = document.getElementById('semester-table-body');
    if (!tbody) return;
    tbody.innerHTML = '';
    const sorted = [...semesters].sort((a,b) => a.semNumber - b.semNumber);
    sorted.forEach(s => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><strong>Sem ${s.semNumber}</strong></td>
        <td>${s.name}</td>
        <td>
          <button type="button" class="action-btn action-btn-delete" onclick="deleteSemester(${s.semNumber})"><i class="fas fa-trash-alt"></i></button>
        </td>
      `;
      tbody.appendChild(tr);
    });
  }

  const semForm = document.getElementById('semester-form');
  if (semForm) {
    semForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const semNumber = parseInt(document.getElementById('sem-num').value);
      const name = document.getElementById('sem-name').value.trim();

      try {
        await apiRequest('/sections/semesters', {
          method: 'POST',
          body: { semNumber, name }
        });
        showToast('Semester Added', `Created Semester ${semNumber}`, 'success');
        semForm.reset();
        loadConfigData();
      } catch (error) {
        showToast('Error', error.message || 'Failed to add semester', 'error');
      }
    });
  }

  window.deleteSemester = function(semNo) {
    showConfirm('Delete Semester', `Delete semester ${semNo}?`, async () => {
      try {
        await apiRequest(`/sections/semesters/${semNo}`, {
          method: 'DELETE'
        });
        showToast('Semester Deleted', `Deleted semester ${semNo}`, 'success');
        loadConfigData();
      } catch (error) {
        showToast('Delete Failed', error.message || 'Could not delete semester', 'error');
      }
    });
  };

  // Section CRUD
  function renderSectionTable() {
    const tbody = document.getElementById('section-table-body');
    if (!tbody) return;
    tbody.innerHTML = '';

    const deptSelect = document.getElementById('section-dept-select');
    const deptVal = deptSelect ? deptSelect.value : '';

    if (!deptVal) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted); padding: 15px;">Please select a Department to view sections.</td></tr>`;
      return;
    }

    const filtered = sections.filter(s => s.departmentCode && s.departmentCode.toUpperCase() === deptVal.toUpperCase());

    const sorted = [...filtered].sort((a,b) => {
      if (a.yearNumber !== b.yearNumber) return a.yearNumber - b.yearNumber;
      return a.sectionName.localeCompare(b.sectionName);
    });

    if (sorted.length === 0) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted); padding: 15px;">No sections configured for this department.</td></tr>`;
      return;
    }

    sorted.forEach(s => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><strong>${s.departmentCode}</strong></td>
        <td>Year ${s.yearNumber}</td>
        <td>Section ${s.sectionName}</td>
        <td>
          <button type="button" class="action-btn action-btn-edit" onclick="editSection(${s.id})" title="Edit" style="background:transparent; border:none; color:var(--secondary); cursor:pointer; padding:6px 10px;"><i class="fas fa-edit"></i></button>
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

  // Section Edit Modal Methods
  window.editSection = async function(id) {
    const modal = document.getElementById('section-modal');
    const deptSelect = document.getElementById('edit-section-dept');
    const yearSelect = document.getElementById('edit-section-year');
    const nameInput = document.getElementById('edit-section-name');
    const idInput = document.getElementById('edit-section-id');

    populateSelect(deptSelect, departments.map(d => ({ value: d.code, text: `${d.code} - ${d.name}` })), 'Select Dept');
    populateSelect(yearSelect, academicYears.map(y => ({ value: y.yearNumber, text: y.name })), 'Select Year');

    const sec = sections.find(s => s.id === id);
    if (sec) {
      idInput.value = sec.id;
      deptSelect.value = sec.departmentCode;
      yearSelect.value = sec.yearNumber;
      nameInput.value = sec.sectionName;
      modal.classList.add('active');
    }
  };

  window.closeSectionModal = function() {
    document.getElementById('section-modal').classList.remove('active');
  };

  document.getElementById('edit-section-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('edit-section-id').value;
    const departmentCode = document.getElementById('edit-section-dept').value;
    const yearNumber = parseInt(document.getElementById('edit-section-year').value);
    const sectionName = document.getElementById('edit-section-name').value.toUpperCase().trim();

    try {
      await apiRequest(`/sections/update/${id}`, {
        method: 'PUT',
        body: { departmentCode, yearNumber, sectionName }
      });
      showToast('Section Updated', `Updated section configuration`, 'success');
      closeSectionModal();
      loadConfigData();
    } catch (error) {
      showToast('Error', error.message || 'Failed to update section', 'error');
    }
  });

  // ----------------------------------------------------
  // TIMELINE SELECTION DEADLINE
  // ----------------------------------------------------

  async function populateDeadlineDropdowns() {
    const deadlineDept = document.getElementById('deadline-dept');
    const deadlineSem = document.getElementById('deadline-sem');
    if (!deadlineDept || !deadlineSem) return;

    if (departments.length === 0 || semesters.length === 0) {
      await loadConfigOptions();
    }

    const selectedDept = deadlineDept.value;
    deadlineDept.innerHTML = '<option value="" disabled selected>Select Dept</option>';
    departments.forEach(d => {
      const opt = document.createElement('option');
      opt.value = d.code;
      opt.innerText = `${d.code} - ${d.name}`;
      deadlineDept.appendChild(opt);
    });
    if (selectedDept) deadlineDept.value = selectedDept;

    const selectedSem = deadlineSem.value;
    deadlineSem.innerHTML = '<option value="" disabled selected>Select Semester</option>';
    const sortedSems = [...semesters].sort((a,b) => a.semNumber - b.semNumber);
    sortedSems.forEach(s => {
      const opt = document.createElement('option');
      opt.value = s.semNumber;
      opt.innerText = s.name;
      deadlineSem.appendChild(opt);
    });
    if (selectedSem) deadlineSem.value = selectedSem;
  }

  async function updateSubjectsPreview() {
    const sem = document.getElementById('deadline-sem').value;
    const yearVal = document.getElementById('deadline-year').value.trim();
    const dept = document.getElementById('deadline-dept').value;
    const previewContainer = document.getElementById('deadline-subjects-preview');

    if (!previewContainer) return;

    if (!sem || !yearVal || !dept) {
      previewContainer.innerHTML = '<p style="color: var(--text-muted); font-style: italic;">Select Department, Active Semester, and enter Academic Year above to preview subjects list.</p>';
      return;
    }

    try {
      if (subjectList.length === 0) {
        const data = await apiRequest('/subject/viewAll');
        subjectList = Array.isArray(data) ? data : [];
      }

      const filtered = subjectList.filter(s => 
        Number(s.sem) === Number(sem) &&
        s.academicYear && s.academicYear.toLowerCase() === yearVal.toLowerCase() &&
        getSubjectDeptCode(s) === dept.toUpperCase()
      );

      if (filtered.length === 0) {
        previewContainer.innerHTML = '<p style="color: var(--warning); font-style: italic;">No subjects match the selected Semester, Academic Year, and Department.</p>';
        return;
      }

      const grouped = {};
      filtered.forEach(sub => {
        const y = sub.year;
        if (!grouped[y]) grouped[y] = [];
        grouped[y].push(sub);
      });

      const years = Object.keys(grouped).sort((a, b) => Number(a) - Number(b));
      
      const getYearLabel = (yrNo) => {
        const yObj = academicYears.find(y => Number(y.yearNumber) === Number(yrNo));
        return yObj ? yObj.name : `Year ${yrNo}`;
      };

      previewContainer.innerHTML = '';
      
      years.forEach(y => {
        const groupDiv = document.createElement('div');
        groupDiv.style.border = '1px solid var(--panel-border)';
        groupDiv.style.borderRadius = '8px';
        groupDiv.style.padding = '12px';
        groupDiv.style.background = 'rgba(255, 255, 255, 0.02)';
        groupDiv.style.marginBottom = '12px';

        const groupTitle = document.createElement('h4');
        groupTitle.style.color = 'var(--secondary)';
        groupTitle.style.marginBottom = '10px';
        groupTitle.style.borderBottom = '1px solid rgba(255, 255, 255, 0.1)';
        groupTitle.style.paddingBottom = '6px';
        groupTitle.innerHTML = `<i class="fas fa-layer-group"></i> ${getYearLabel(y)}`;
        groupDiv.appendChild(groupTitle);

        const listDiv = document.createElement('div');
        listDiv.style.display = 'grid';
        listDiv.style.gridTemplateColumns = 'repeat(auto-fill, minmax(250px, 1fr))';
        listDiv.style.gap = '10px';

        grouped[y].forEach(sub => {
          const item = document.createElement('div');
          item.style.padding = '8px 12px';
          item.style.borderRadius = '4px';
          item.style.background = 'rgba(255, 255, 255, 0.05)';
          item.style.fontSize = '0.9rem';
          item.innerHTML = `
            <strong>${sub.id}</strong> - ${sub.name}<br>
            <small style="color: var(--text-muted)">Regulation: ${sub.regulation} • Dept: ${sub.dep}</small>
          `;
          listDiv.appendChild(item);
        });

        groupDiv.appendChild(listDiv);
        previewContainer.appendChild(groupDiv);
      });

    } catch (error) {
      previewContainer.innerHTML = '<p style="color: var(--error);">Error generating subject preview.</p>';
    }
  }

  // Attach change listeners to preview inputs
  document.getElementById('deadline-sem').addEventListener('change', updateSubjectsPreview);
  document.getElementById('deadline-year').addEventListener('input', updateSubjectsPreview);
  document.getElementById('deadline-dept').addEventListener('change', updateSubjectsPreview);

  async function loadDeadlineStatus() {
    const statusContainer = document.getElementById('deadline-status-container');
    statusContainer.innerHTML = `<div style="color: var(--text-muted);"><i class="fas fa-circle-notch fa-spin"></i> Loading...</div>`;

    try {
      selectionWindow = await apiRequest('/adminfaculty/deadline');
      await populateDeadlineDropdowns();
      
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
          <p style="margin-top: 12px; font-size: 0.95rem; font-weight: 600;">Target Semester:</p>
          <h5 style="color: var(--text-muted); margin-top: 4px; margin-bottom: 12px;">Semester ${selectionWindow.sem || 'N/A'}</h5>
          <p style="font-size: 0.95rem; font-weight: 500;">Deadline Passed on:</p>
          <p style="font-size: 0.85rem; color: var(--text-muted);">${deadlineTime.toLocaleString()}</p>
          <p style="margin-top: 10px; font-size: 0.88rem; font-style: italic; color: var(--secondary); border-top: 1px solid var(--panel-border); padding-top: 10px;">"${selectionWindow.message}"</p>
        `;
      } else {
        const days = Math.floor(diffMs / (1000 * 60 * 60 * 24));
        const hours = Math.floor((diffMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
        const mins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));

        statusContainer.innerHTML = `
          <div class="status-badge active"><i class="fas fa-hourglass-half"></i> RUNNING</div>
          <p style="margin-top: 12px; font-size: 0.95rem; font-weight: 600;">Target Semester:</p>
          <h5 style="color: var(--secondary); margin-top: 4px; margin-bottom: 12px;">Semester ${selectionWindow.sem}</h5>
          <p style="font-size: 0.95rem; font-weight: 600;">Time Remaining:</p>
          <h4 style="color: var(--secondary); font-size: 1.5rem; margin-top: 4px; font-weight: 700;">
            ${days}d ${hours}h ${mins}m
          </h4>
          <p style="font-size: 0.8rem; color: var(--text-muted); margin-top: 4px;">Deadline: ${deadlineTime.toLocaleString()}</p>
          <p style="margin-top: 12px; font-size: 0.88rem; font-style: italic; color: var(--text-muted); border-top: 1px solid var(--panel-border); padding-top: 10px;">"${selectionWindow.message}"</p>
          <button class="btn btn-danger btn-block" style="margin-top: 15px;" onclick="stopDeadlineWindow()"><i class="fas fa-stop-circle"></i> Stop Selection Window</button>
        `;
      }
      
      document.getElementById('deadline-message').value = selectionWindow.message;
      if (selectionWindow.sem) {
        document.getElementById('deadline-sem').value = selectionWindow.sem;
      }
      if (selectionWindow.academicYear) {
        document.getElementById('deadline-year').value = selectionWindow.academicYear;
      }
      if (selectionWindow.department) {
        document.getElementById('deadline-dept').value = selectionWindow.department;
      }

      updateSubjectsPreview();

    } catch (error) {
      statusContainer.innerHTML = `<div style="color: var(--error);">Error checking status</div>`;
    }
  }

  document.getElementById('deadline-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const msg = document.getElementById('deadline-message').value.trim();
    const days = parseInt(document.getElementById('deadline-days').value);
    const sem = parseInt(document.getElementById('deadline-sem').value);
    const academicYear = document.getElementById('deadline-year').value.trim();
    const department = document.getElementById('deadline-dept').value;

    try {
      const params = new URLSearchParams();
      params.append('message', msg);
      params.append('days', days);
      params.append('sem', sem);
      params.append('academicYear', academicYear);
      params.append('department', department);

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

  window.stopDeadlineWindow = function() {
    showConfirm('Stop Window', 'Are you sure you want to stop the subject selection window immediately? Faculty will no longer be able to select subjects.', async () => {
      try {
        await apiRequest('/adminfaculty/stop-deadline', {
          method: 'POST'
        });
        showToast('Deadline Stopped', 'The selection window has been closed immediately.', 'success');
        loadDeadlineStatus();
      } catch (error) {
        showToast('Error', error.message || 'Could not close selection window', 'error');
      }
    });
  };

  // ----------------------------------------------------
  // SUBJECT & SECTION ALLOCATION WORKSPACE
  // ----------------------------------------------------

  async function loadAllocationWorkspace() {
    const warningBanner = document.getElementById('allocation-warning');
    const workspace = document.getElementById('allocation-workspace');
    const secWarningBanner = document.getElementById('section-allocation-warning');
    const secWorkspace = document.getElementById('section-allocation-workspace');
    const autoAllocBtn = document.getElementById('auto-allocate-btn');
    const finalizeBtn = document.getElementById('finalize-allocations-btn');
    
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

      await loadConfigOptions();
      await loadFacultyListForAlloc();
      await loadSubjectListForAlloc();
      await loadAllocationsWorkspaceData();

      if (isBefore) {
        if (warningBanner) warningBanner.style.display = 'block';
        if (secWarningBanner) secWarningBanner.style.display = 'block';
        if (workspace) {
          workspace.style.pointerEvents = 'none';
          workspace.style.opacity = '0.5';
        }
        if (secWorkspace) {
          secWorkspace.style.pointerEvents = 'none';
          secWorkspace.style.opacity = '0.5';
        }
        if (autoAllocBtn) autoAllocBtn.disabled = true;
        if (finalizeBtn) finalizeBtn.disabled = true;
      } else {
        if (warningBanner) warningBanner.style.display = 'none';
        if (secWarningBanner) secWarningBanner.style.display = 'none';
        if (workspace) {
          workspace.style.pointerEvents = 'auto';
          workspace.style.opacity = '1';
        }
        if (secWorkspace) {
          secWorkspace.style.pointerEvents = 'auto';
          secWorkspace.style.opacity = '1';
        }
        if (autoAllocBtn) autoAllocBtn.disabled = false;
      }

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

  function populateAllocSecSubjectSelect(subjectAllocations, subjects) {
    const secSubSelect = document.getElementById('alloc-sec-subject-select');
    if (!secSubSelect) return;
    
    secSubSelect.innerHTML = '<option value="" disabled selected>Choose Subject...</option>';
    
    const allocList = Array.isArray(subjectAllocations) ? subjectAllocations : [];
    const subList = Array.isArray(subjects) ? subjects : [];
    
    const allocatedSubjectIds = [...new Set(allocList.map(a => a.subjectId))];
    const subjectsMap = {};
    subList.forEach(s => subjectsMap[s.id] = s);
    
    allocatedSubjectIds.forEach(subId => {
      const sub = subjectsMap[subId];
      if (sub) {
        const opt = document.createElement('option');
        opt.value = sub.id;
        opt.innerText = `${sub.id} - ${sub.name} (Year ${sub.year} Sem ${sub.sem} ${sub.dep})`;
        secSubSelect.appendChild(opt);
      }
    });
  }

  document.getElementById('alloc-sec-subject-select').addEventListener('change', async (e) => {
    const selectedSubId = e.target.value;
    const facSelect = document.getElementById('alloc-sec-faculty-select');
    const secSelect = document.getElementById('alloc-section-select');
    
    facSelect.innerHTML = '<option value="" disabled selected>Choose Faculty...</option>';
    secSelect.innerHTML = '<option value="" disabled selected>Choose Section...</option>';
    
    try {
      const subjectAllocations = await apiRequest('/adminfaculty/allocations');
      const faculty = await apiRequest('/adminfaculty/viewfaculty');
      const facultyMap = {};
      const facList = Array.isArray(faculty) ? faculty : [];
      facList.forEach(f => facultyMap[f.id] = f.name);
      
      const allocList = Array.isArray(subjectAllocations) ? subjectAllocations : [];
      const matchingAllocations = allocList.filter(a => a.subjectId === selectedSubId);
      matchingAllocations.forEach(a => {
        const opt = document.createElement('option');
        opt.value = a.facultyId;
        opt.innerText = `${a.facultyId} - ${facultyMap[a.facultyId] || 'Unknown'}`;
        facSelect.appendChild(opt);
      });
      
      const subject = await apiRequest(`/subject/view/${selectedSubId}`);
      if (subject && typeof subject === 'object' && subject.id) {
        const filteredSections = sections.filter(sec => 
          Number(sec.yearNumber) === Number(subject.year) && 
          sec.departmentCode.toUpperCase() === getSubjectDeptCode(subject)
        );
        
        if (filteredSections.length === 0) {
          secSelect.innerHTML = `<option value="" disabled selected>No sections configured for Year ${subject.year} ${subject.dep}</option>`;
        } else {
          populateSelect(secSelect, filteredSections.map(s => ({ value: s.sectionName, text: `Section ${s.sectionName}` })), 'Choose Section...');
        }
      }
    } catch (error) {
      console.error('Error handling allocated subject change:', error);
    }
  });

  async function loadSubjectAllocations() {
    const tbody = document.getElementById('subject-allocations-table-body');
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="3" style="text-align: center;"><i class="fas fa-spinner fa-spin"></i> Loading...</td></tr>`;

    try {
      const allocations = await apiRequest('/adminfaculty/allocations');
      const allocList = Array.isArray(allocations) ? allocations : [];
      if (allocList.length === 0) {
        tbody.innerHTML = `<tr><td colspan="3" style="text-align: center; color: var(--text-muted);">No subject allocations.</td></tr>`;
        return;
      }

      const faculty = await apiRequest('/adminfaculty/viewfaculty');
      const subjects = await apiRequest('/subject/viewAll');

      const facultyMap = {};
      const facList = Array.isArray(faculty) ? faculty : [];
      facList.forEach(f => facultyMap[f.id] = f.name);
      
      const subjectsMap = {};
      const subList = Array.isArray(subjects) ? subjects : [];
      subList.forEach(s => subjectsMap[s.id] = s);

      tbody.innerHTML = '';
      const validAllocList = allocList.filter(alloc => facultyMap[alloc.facultyId]);
      if (validAllocList.length === 0) {
        tbody.innerHTML = `<tr><td colspan="3" style="text-align: center; color: var(--text-muted);">No subject allocations.</td></tr>`;
        return;
      }
      validAllocList.forEach(alloc => {
        const sub = subjectsMap[alloc.subjectId] || { name: 'Unknown Subject' };
        const facName = facultyMap[alloc.facultyId];
        
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td><strong>${alloc.facultyId}</strong><br><span style="font-size: 0.85rem; color: var(--text-muted);">${facName}</span></td>
          <td><strong>${alloc.subjectId}</strong><br><span style="font-size: 0.85rem; color: var(--text-muted);">${sub.name}</span></td>
          <td style="text-align: right;">
            <button type="button" class="action-btn action-btn-delete" onclick="deleteSubjectAllocation(${alloc.id})" title="Remove"><i class="fas fa-trash-alt"></i></button>
          </td>
        `;
        tbody.appendChild(tr);
      });
    } catch (error) {
      tbody.innerHTML = `<tr><td colspan="3" style="text-align: center; color: var(--error);">Failed to load subject allocations</td></tr>`;
    }
  }

  async function loadSectionAllocations() {
    const tbody = document.getElementById('section-allocations-table-body');
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="4" style="text-align: center;"><i class="fas fa-spinner fa-spin"></i> Loading...</td></tr>`;

    try {
      const allocations = await apiRequest('/adminfaculty/section-allocations');
      const allocList = Array.isArray(allocations) ? allocations : [];
      if (allocList.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted);">No section allocations.</td></tr>`;
        return;
      }

      const faculty = await apiRequest('/adminfaculty/viewfaculty');
      const subjects = await apiRequest('/subject/viewAll');

      const facultyMap = {};
      const facList = Array.isArray(faculty) ? faculty : [];
      facList.forEach(f => facultyMap[f.id] = f.name);
      
      const subjectsMap = {};
      const subList = Array.isArray(subjects) ? subjects : [];
      subList.forEach(s => subjectsMap[s.id] = s);

      tbody.innerHTML = '';
      const validAllocList = allocList.filter(alloc => facultyMap[alloc.facultyId]);
      if (validAllocList.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted);">No section allocations.</td></tr>`;
        return;
      }
      validAllocList.forEach(alloc => {
        const sub = subjectsMap[alloc.subjectId] || { name: 'Unknown Subject' };
        const facName = facultyMap[alloc.facultyId];
        
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td><strong>${alloc.facultyId}</strong><br><span style="font-size: 0.85rem; color: var(--text-muted);">${facName}</span></td>
          <td><strong>${alloc.subjectId}</strong><br><span style="font-size: 0.85rem; color: var(--text-muted);">${sub.name}</span></td>
          <td><span class="badge badge-admin">Sec ${alloc.sectionName}</span></td>
          <td style="text-align: right;">
            <button type="button" class="action-btn action-btn-delete" onclick="deleteSectionAllocation(${alloc.id})" title="Remove"><i class="fas fa-trash-alt"></i></button>
          </td>
        `;
        tbody.appendChild(tr);
      });
    } catch (error) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--error);">Failed to load section allocations</td></tr>`;
    }
  }

  async function loadAllocationsWorkspaceData() {
    try {
      const subjectAllocations = await apiRequest('/adminfaculty/allocations');
      const allSubjects = await apiRequest('/subject/viewAll');
      
      populateAllocSecSubjectSelect(subjectAllocations, allSubjects);
      
      document.getElementById('allocate-subject-form').reset();
      document.getElementById('allocate-section-form').reset();
      document.getElementById('alloc-faculty-pref-list').innerHTML = '<li style="color: var(--text-muted); list-style-type: none; margin-left: -20px;">Select a faculty to see their choices</li>';
      
      document.getElementById('alloc-sec-faculty-select').innerHTML = '<option value="" disabled selected>Choose Faculty...</option>';
      document.getElementById('alloc-section-select').innerHTML = '<option value="" disabled selected>Choose Section...</option>';

      await loadSubjectAllocations();
      await loadSectionAllocations();
      await updateFinalizationStatus();
    } catch (error) {
      console.error('Error loading allocations workspace data:', error);
    }
  }

  document.getElementById('allocate-subject-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const facultyId = document.getElementById('alloc-faculty-select').value;
    const subjectId = document.getElementById('alloc-subject-select').value;
    
    const allocationData = { facultyId, subjectId };
    const submitBtn = e.target.querySelector('button[type="submit"]');
    submitBtn.disabled = true;

    try {
      await apiRequest('/adminfaculty/allocate', {
        method: 'POST',
        body: allocationData
      });
      showToast('Allocation Saved', 'Subject allocated to faculty', 'success');
      loadAllocationsWorkspaceData();
    } catch (error) {
      showToast('Allocation Blocked', error.message || 'Validation error saving allocation', 'error');
    } finally {
      submitBtn.disabled = false;
    }
  });

  document.getElementById('allocate-section-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const subjectId = document.getElementById('alloc-sec-subject-select').value;
    const facultyId = document.getElementById('alloc-sec-faculty-select').value;
    const sectionName = document.getElementById('alloc-section-select').value;
    
    const allocationData = { subjectId, facultyId, sectionName };
    const submitBtn = e.target.querySelector('button[type="submit"]');
    submitBtn.disabled = true;

    try {
      await apiRequest('/adminfaculty/allocate-section', {
        method: 'POST',
        body: allocationData
      });
      showToast('Section Allocated', 'Section allocation saved successfully', 'success');
      loadAllocationsWorkspaceData();
    } catch (error) {
      showToast('Section Allocation Blocked', error.message || 'Validation error saving section allocation', 'error');
    } finally {
      submitBtn.disabled = false;
    }
  });

  window.deleteSubjectAllocation = function(id) {
    showConfirm('Delete Subject Allocation', 'Are you sure you want to remove this subject allocation?', async () => {
      try {
        await apiRequest(`/adminfaculty/allocation/${id}`, {
          method: 'DELETE'
        });
        showToast('Allocation Removed', 'Subject allocation deleted successfully', 'success');
        loadAllocationsWorkspaceData();
      } catch (error) {
        showToast('Error', error.message || 'Could not delete allocation', 'error');
      }
    });
  };

  window.deleteSectionAllocation = function(id) {
    showConfirm('Delete Section Allocation', 'Are you sure you want to remove this section allocation?', async () => {
      try {
        await apiRequest(`/adminfaculty/section-allocation/${id}`, {
          method: 'DELETE'
        });
        showToast('Allocation Removed', 'Section allocation deleted successfully', 'success');
        loadAllocationsWorkspaceData();
      } catch (error) {
        showToast('Error', error.message || 'Could not delete allocation', 'error');
      }
    });
  };

  window.autoAllocateSubjects = async function() {
    try {
      const windowStatus = await apiRequest('/adminfaculty/deadline');
      let isActiveWindow = windowStatus && windowStatus.active && new Date() < new Date(windowStatus.deadline);

      let confirmMsg = 'Do you want to automatically allocate subjects based on submitted faculty preferences? You can inspect and modify the draft allocations before finalizing.';
      if (isActiveWindow) {
        confirmMsg = 'The Selection Window is currently active. Would you like to stop the selection window now and generate draft Auto-Allocations?';
      }

      showConfirm('Auto-Allocate Subjects', confirmMsg, async () => {
        const autoBtn = document.getElementById('auto-allocate-btn');
        if (autoBtn) {
          autoBtn.disabled = true;
          autoBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Allocating...';
        }
        try {
          if (isActiveWindow) {
            await apiRequest('/adminfaculty/stop-deadline', { method: 'POST' });
          }
          await apiRequest('/adminfaculty/auto-allocate', { method: 'POST' });
          showToast('Auto-Allocation Complete', 'Draft subject allocations generated based on preferences.', 'success');
          loadAllocationWorkspace();
        } catch (error) {
          showToast('Auto-Allocation Failed', error.message || 'Could not auto-allocate subjects', 'error');
        } finally {
          if (autoBtn) {
            autoBtn.disabled = false;
            autoBtn.innerHTML = '<i class="fas fa-bolt"></i> Auto-Allocate Subjects';
          }
        }
      });
    } catch (err) {
      showToast('Error', err.message || 'Could not check selection window status', 'error');
    }
  };

  // 3-Year History Upload form handler removed

  window.finalizeAllAllocations = async function() {
    showConfirm('Finalize Allocations', 'Are you sure you want to finalize all allocations? Once finalized, faculty members will be able to see their allocated subjects and sections.', async () => {
      try {
        await apiRequest('/adminfaculty/finalize-allocations', {
          method: 'POST'
        });
        showToast('Allocations Finalized', 'Allocations have been successfully finalized and published to faculty.', 'success');
        updateFinalizationStatus();
      } catch (error) {
        showToast('Error', error.message || 'Could not finalize allocations', 'error');
      }
    });
  };

  async function updateFinalizationStatus() {
    const badge = document.getElementById('finalize-status-badge');
    const btn = document.getElementById('finalize-allocations-btn');
    if (!badge || !btn) return;
    
    try {
      const isFinalized = await apiRequest('/adminfaculty/is-finalized');
      if (isFinalized) {
        badge.className = 'status-badge active';
        badge.innerText = 'Finalized';
        btn.disabled = true;
        btn.innerHTML = '<i class="fas fa-check-double"></i> Published';
      } else {
        badge.className = 'status-badge expired';
        badge.innerText = 'Draft Mode';
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-check-double"></i> Finalize Allocations';
      }
    } catch (error) {
      console.error('Error getting finalization status:', error);
    }
  }

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

      const newSession = {
        ...currentUser,
        name: response.name,
        email: response.email
      };
      sessionStorage.setItem('currentUser', JSON.stringify(newSession));
      
      document.getElementById('header-user-name').innerText = response.name;
      document.getElementById('header-user-email').innerText = response.email;
      
      profilePasswordInput.value = '';

    } catch (error) {
      showToast('Update Failed', error.message || 'Failed to update profile details', 'error');
    }
  });

  // Initial load on startup
  loadFacultySelections();
});