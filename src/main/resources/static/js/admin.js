// Admin Dashboard Logic for EduAssign

function cleanSubjectCode(id) {
  if (!id) return '';
  return id.includes('_') ? id.split('_')[0] : id;
}

document.addEventListener('DOMContentLoaded', () => {
  // 1. Session check
  const currentUser = checkSession(['ADMIN', 'SUPERADMIN']);
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
      const nameParts = (currentUser.name || 'A').trim().split(/\s+/);
      const nameInitials = nameParts.map(n => n[0]).join('').substring(0, 2).toUpperCase();
      profileAvatarInitials.innerText = nameInitials || 'A';
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

  // State caches
  let currentSubjectViewType = 'regular';
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

      // Update floating buttons visibility based on active tab
      updateFloatingButtons();

      // Refresh data depending on target tab
      if (targetId === 'selections-tab') loadFacultySelections();
      if (targetId === 'subject-tab') loadSubjects();
      if (targetId === 'config-tab') loadConfigData();
      if (targetId === 'deadline-tab') loadDeadlineStatus();
      if (targetId === 'allocation-tab') loadAllocationWorkspace();
      if (targetId === 'allocation-report-tab') initReportFilters();
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
  document.getElementById('subject-upload-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fileInput = document.getElementById('subject-excel-file');
    if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
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
      const display = document.getElementById('subject-file-name');
      if (display) {
        display.innerText = 'No file chosen';
        display.style.color = '';
      }
      const clearBtn = document.querySelector('#subject-dropzone .file-clear-btn');
      if (clearBtn) clearBtn.style.display = 'none';
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
    tbody.innerHTML = `<tr><td colspan="4" style="text-align: center;"><i class="fas fa-spinner fa-spin"></i> Loading selections...</td></tr>`;
    pendingTbody.innerHTML = `<tr><td colspan="2" style="text-align: center;"><i class="fas fa-spinner fa-spin"></i> Loading...</td></tr>`;

    try {
      if (departments.length === 0 || academicYears.length === 0) {
        await loadConfigOptions();
      }
      
      populateFilterDropdowns();
      populateCustomPrefDropdown();

      const academicYearInput = document.getElementById('pref-academic-year-input');
      if (academicYearInput && !academicYearInput.value) {
        academicYearInput.value = '2025-26';
      }
      const academicYearVal = academicYearInput ? academicYearInput.value.trim() : '2025-26';

      const [faculty, subjects, allPreferences, sectionAllocs, selectionWindow] = await Promise.all([
        apiRequest('/adminfaculty/viewfaculty'),
        apiRequest('/subject/viewAll'),
        apiRequest(`/faculty/preferences/by-academic-year?academicYear=${encodeURIComponent(academicYearVal)}`),
        apiRequest('/adminfaculty/section-allocations'),
        apiRequest('/adminfaculty/deadline')
      ]);

      const facultyMap = {};
      const facList = Array.isArray(faculty) ? faculty : [];
      facList.forEach(f => {
        if (f.id) {
          facultyMap[f.id] = f;
          facultyMap[f.id.toLowerCase()] = f;
          facultyMap[f.id.toUpperCase()] = f;
        }
      });
      
      const subjectsMap = {};
      const subList = Array.isArray(subjects) ? subjects : [];
      subList.forEach(s => {
        if (s.id) {
          subjectsMap[s.id] = s;
          subjectsMap[s.id.toLowerCase()] = s;
          subjectsMap[s.id.toUpperCase()] = s;
        }
      });

      window.preferencesData = {
        allPreferences: Array.isArray(allPreferences) ? allPreferences : [],
        facultyMap,
        subjectsMap,
        subjectsList: subList,
        facultyList: Array.isArray(faculty) ? faculty : [],
        sectionAllocs: Array.isArray(sectionAllocs) ? sectionAllocs : [],
        selectionWindow: selectionWindow || null
      };

      const prefDeptFilter = document.getElementById('pref-dept-filter');
      const prefYearFilter = document.getElementById('pref-year-filter');
      const prefSemFilter = document.getElementById('pref-sem-filter');

      if (prefDeptFilter && !prefDeptFilter.value && departments.length > 0) {
        prefDeptFilter.value = departments[0].code;
      }
      if (prefYearFilter && (!prefYearFilter.value || prefYearFilter.value === '')) {
        prefYearFilter.value = 'ALL';
        const triggerLabel = document.getElementById('custom-pref-trigger-label');
        if (triggerLabel) {
          triggerLabel.innerText = 'All Years - Sem 1';
          triggerLabel.style.color = 'var(--text-main)';
        }
      }
      if (prefSemFilter && (!prefSemFilter.value || prefSemFilter.value === '')) {
        prefSemFilter.value = '1';
      }

      renderPreferencesTable();
      renderPendingTable();

    } catch (error) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--error);">Failed to load preferences: ${error.message}</td></tr>`;
      pendingTbody.innerHTML = `<tr><td colspan="2" style="text-align: center; color: var(--error);">Failed to load pending list</td></tr>`;
    }
  }

  function populateFilterDropdowns() {
    const deptFilter = document.getElementById('pref-dept-filter');
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
    if (subYearFilter) {
      subYearFilter.innerHTML = '<option value="" disabled selected>Select Year</option><option value="ALL">All Years</option>';
      academicYears.forEach(y => {
        const opt = document.createElement('option');
        opt.value = y.yearNumber;
        opt.innerText = y.name;
        subYearFilter.appendChild(opt);
      });
    }
    const prefYearFilter = document.getElementById('pref-year-filter');
    if (prefYearFilter) {
      prefYearFilter.innerHTML = '<option value="" disabled selected>Select Year</option><option value="ALL">All Years</option>';
      academicYears.forEach(y => {
        const opt = document.createElement('option');
        opt.value = y.yearNumber;
        opt.innerText = y.name;
        prefYearFilter.appendChild(opt);
      });
    }

    if (semFilter) {
      semFilter.innerHTML = '<option value="" disabled selected>Select Semester</option>';
      const sortedSems = [...semesters].sort((a,b) => a.semNumber - b.semNumber);
      sortedSems.forEach(s => {
        const opt = document.createElement('option');
        opt.value = s.semNumber;
        opt.innerText = s.name;
        semFilter.appendChild(opt);
      });
    }
    if (subSemFilter) {
      subSemFilter.innerHTML = '<option value="" disabled selected>Select Semester</option><option value="ALL">All Semesters</option>';
      const sortedSems = [...semesters].sort((a,b) => a.semNumber - b.semNumber);
      sortedSems.forEach(s => {
        const opt = document.createElement('option');
        opt.value = s.semNumber;
        opt.innerText = s.name;
        subSemFilter.appendChild(opt);
      });
    }
  }

  function populateCustomPrefDropdown() {
    const menuEl = document.getElementById('custom-pref-year-menu');
    if (!menuEl) return;
    
    menuEl.innerHTML = '';

    // Include "All Years" option at the top!
    const filteredAcademicYears = academicYears.filter(y => {
      if (!y || y.yearNumber === 'ALL') return false;
      const name = y.name ? y.name.toLowerCase().trim() : '';
      return name !== 'all years' && name !== 'all year' && name !== 'allyears';
    });

    const yearOptions = [
      { yearNumber: 'ALL', name: 'All Years' },
      ...filteredAcademicYears
    ];

    yearOptions.forEach(y => {
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
      
      const isAll = y.yearNumber === 'ALL';
      const icon = isAll ? '<i class="fas fa-layer-group" style="font-size: 0.8rem; color: var(--secondary); margin-right: 6px;"></i>' : '';

      item.innerHTML = `
        <div class="dept-row" style="display: flex; justify-content: space-between; align-items: center; width: 100%; pointer-events: none;">
          <span style="${isAll ? 'font-weight: 600; color: var(--secondary);' : ''}">${icon}${y.name}</span>
          <i class="fas fa-chevron-down" style="font-size: 0.75rem; color: var(--text-muted); transition: transform 0.2s ease;"></i>
        </div>
        <div class="custom-sem-submenu" style="display: none; flex-direction: column; gap: 4px; margin-top: 8px; width: 100%; padding-left: 12px; box-sizing: border-box;">
          ${semesters.map(s => `
            <button type="button" class="sem-opt-btn" data-year="${y.yearNumber}" data-sem="${s.semNumber}" style="background: rgba(255, 255, 255, 0.05); border: 1px solid var(--panel-border); border-radius: var(--border-radius-sm); color: var(--text-main); padding: 6px 12px; font-size: 0.82rem; text-align: left; width: 100%; cursor: pointer; transition: all 0.2s ease;">
              ${y.yearNumber === 'ALL' ? 'All Years - ' : ''}${s.name}
            </button>
          `).join('')}
        </div>
      `;
      
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
        const yearVal = btn.getAttribute('data-year');
        const semVal = btn.getAttribute('data-sem');
        
        // Update hidden native selects
        const yearSelect = document.getElementById('pref-year-filter');
        const semSelect = document.getElementById('pref-sem-filter');
        if (yearSelect) {
          yearSelect.value = yearVal;
        }
        if (semSelect) {
          semSelect.value = semVal;
        }
        
        // Update trigger button text
        let yearName = 'All Years';
        if (yearVal !== 'ALL') {
          const yearObj = academicYears.find(y => String(y.yearNumber) === String(yearVal));
          yearName = yearObj ? yearObj.name : `${yearVal} Year`;
        }
        const triggerLabel = document.getElementById('custom-pref-trigger-label');
        if (triggerLabel) {
          triggerLabel.innerText = `${yearName} - Sem ${semVal}`;
          triggerLabel.style.color = 'var(--text-main)';
        }
        
        // Hide dropdown
        const dropdownMenu = document.getElementById('custom-pref-year-menu');
        if (dropdownMenu) dropdownMenu.style.display = 'none';

        handleFilterChange();
      });
    });
  }

  function populateCustomSubDropdown() {
    const menuEl = document.getElementById('custom-sub-year-menu');
    if (!menuEl) return;
    
    menuEl.innerHTML = '';

    // Include "All Years" option at the top!
    const filteredAcademicYears = academicYears.filter(y => {
      if (!y || y.yearNumber === 'ALL') return false;
      const name = y.name ? y.name.toLowerCase().trim() : '';
      return name !== 'all years' && name !== 'all year' && name !== 'allyears';
    });

    const yearOptions = [
      { yearNumber: 'ALL', name: 'All Years' },
      ...filteredAcademicYears
    ];

    yearOptions.forEach(y => {
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
      
      const isAll = y.yearNumber === 'ALL';
      const icon = isAll ? '<i class="fas fa-layer-group" style="font-size: 0.8rem; color: var(--secondary); margin-right: 6px;"></i>' : '';

      item.innerHTML = `
        <div class="dept-row" style="display: flex; justify-content: space-between; align-items: center; width: 100%; pointer-events: none;">
          <span style="${isAll ? 'font-weight: 600; color: var(--secondary);' : ''}">${icon}${y.name}</span>
          <i class="fas fa-chevron-down" style="font-size: 0.75rem; color: var(--text-muted); transition: transform 0.2s ease;"></i>
        </div>
        <div class="custom-sem-submenu" style="display: none; flex-direction: column; gap: 4px; margin-top: 8px; width: 100%; padding-left: 12px; box-sizing: border-box;">
          ${semesters.map(s => `
            <button type="button" class="sem-opt-btn" data-year="${y.yearNumber}" data-sem="${s.semNumber}" style="background: rgba(255, 255, 255, 0.05); border: 1px solid var(--panel-border); border-radius: var(--border-radius-sm); color: var(--text-main); padding: 6px 12px; font-size: 0.82rem; text-align: left; width: 100%; cursor: pointer; transition: all 0.2s ease;">
              ${y.yearNumber === 'ALL' ? 'All Years - ' : ''}${s.name}
            </button>
          `).join('')}
        </div>
      `;
      
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
      
      item.addEventListener('click', (e) => {
        if (e.target.closest('.sem-opt-btn')) return;
        const yearVal = y.yearNumber;
        const semVal = 'ALL';
        
        const yearSelect = document.getElementById('sub-year-filter');
        const semSelect = document.getElementById('sub-sem-filter');
        if (yearSelect) {
          yearSelect.value = yearVal;
          yearSelect.dispatchEvent(new Event('change'));
        }
        if (semSelect) {
          semSelect.value = semVal;
          semSelect.dispatchEvent(new Event('change'));
        }
        
        const triggerLabel = document.getElementById('custom-sub-trigger-label');
        if (triggerLabel) {
          triggerLabel.innerText = yearVal === 'ALL' ? 'All Years' : `${y.name} - All Semesters`;
          triggerLabel.style.color = 'var(--text-main)';
        }
        
        const dropdownMenu = document.getElementById('custom-sub-year-menu');
        if (dropdownMenu) dropdownMenu.style.display = 'none';
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
        const yearVal = btn.getAttribute('data-year');
        const semVal = btn.getAttribute('data-sem');
        
        // Update hidden native selects
        const yearSelect = document.getElementById('sub-year-filter');
        const semSelect = document.getElementById('sub-sem-filter');
        if (yearSelect) {
          yearSelect.value = yearVal;
          yearSelect.dispatchEvent(new Event('change'));
        }
        if (semSelect) {
          semSelect.value = semVal;
          semSelect.dispatchEvent(new Event('change'));
        }
        
        // Update trigger button text
        const yearObj = academicYears.find(y => String(y.yearNumber) === String(yearVal));
        const yearName = yearVal === 'ALL' ? 'All Years' : (yearObj ? yearObj.name : `${yearVal} Year`);
        const triggerLabel = document.getElementById('custom-sub-trigger-label');
        if (triggerLabel) {
          triggerLabel.innerText = `${yearName} - Sem ${semVal}`;
          triggerLabel.style.color = 'var(--text-main)';
        }
        
        // Hide dropdown
        const dropdownMenu = document.getElementById('custom-sub-year-menu');
        if (dropdownMenu) dropdownMenu.style.display = 'none';
      });
    });
  }

  function renderPreferencesTable() {
    const tbody = document.getElementById('preferences-table-body');
    if (!tbody) return;
    tbody.innerHTML = '';

    const academicYearVal = document.getElementById('pref-academic-year-input').value.trim();
    const deptVal = document.getElementById('pref-dept-filter').value;
    const semVal = document.getElementById('pref-sem-filter').value;

    if (!academicYearVal || !deptVal || !semVal) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted); padding: 15px;">Please enter Academic Year, select Department, and select Semester to view preferences.</td></tr>`;
      return;
    }

    const data = window.preferencesData;
    if (!data || !data.allPreferences) return;

    let filtered = data.allPreferences;

    // Filter out orphaned preferences where faculty no longer exists (but keep subjects even if not in subjectsMap)
    filtered = filtered.filter(p => {
      if (!p.facultyId || !p.subjectId) return false;
      const fId = p.facultyId.toLowerCase();
      return !!data.facultyMap[fId];
    });

    // Apply optional filters
    const yearFilterEl = document.getElementById('pref-year-filter');
    const yearVal = yearFilterEl ? yearFilterEl.value : '';

    const getSubjectOrFallback = (subId) => {
      if (!subId) return null;
      const sId = subId.toLowerCase();
      if (data.subjectsMap[sId]) return data.subjectsMap[sId];
      
      // Try to find a subject that starts with sId + "_" (fallback for prefix mapping)
      const prefix = sId + "_";
      const matchedKey = Object.keys(data.subjectsMap).find(key => key.startsWith(prefix));
      if (matchedKey) return data.subjectsMap[matchedKey];
      
      let name = subId;
      let code = subId;
      let acadYear = '';
      if (subId.includes('_')) {
        const parts = subId.split('_');
        code = parts[0];
        acadYear = parts[1];
        name = parts[0] + " (Deleted/Imported)";
      }
      return {
        id: subId,
        name: name,
        dep: deptVal || 'N/A',
        sem: semVal ? Number(semVal) : 1,
        year: yearVal && yearVal !== 'ALL' ? Number(yearVal) : 1,
        academicYear: acadYear
      };
    };

    if (deptVal) {
      filtered = filtered.filter(p => {
        const sub = getSubjectOrFallback(p.subjectId);
        return sub && getSubjectDeptCode(sub) === deptVal.toUpperCase();
      });
    }
    if (semVal) {
      filtered = filtered.filter(p => {
        const sub = getSubjectOrFallback(p.subjectId);
        return sub && Number(sub.sem) === Number(semVal);
      });
    }
    if (yearVal && yearVal !== 'ALL') {
      filtered = filtered.filter(p => {
        const sub = getSubjectOrFallback(p.subjectId);
        return sub && Number(sub.year) === Number(yearVal);
      });
    }

    // Group by faculty
    const grouped = {};
    const facultyOrder = [];
    
    // Sort all preferences by ID (ascending) to maintain selection order
    filtered.sort((a, b) => Number(a.id) - Number(b.id));

    filtered.forEach(p => {
      const fId = p.facultyId.toLowerCase();
      if (!grouped[fId]) {
        grouped[fId] = [];
        facultyOrder.push(fId);
      }
      grouped[fId].push(p);
    });

    if (facultyOrder.length === 0) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted);">No preferences found matching criteria.</td></tr>`;
      return;
    }

    tbody.innerHTML = '';
    facultyOrder.forEach(fId => {
      const fac = data.facultyMap[fId] || { name: 'Unknown Faculty' };
      const prefsList = grouped[fId];
      
      const depts = [...new Set(prefsList.map(p => {
        const sub = getSubjectOrFallback(p.subjectId);
        return sub ? getSubjectDeptCode(sub) : '';
      }).filter(Boolean))].join(', ');

      const tr = document.createElement('tr');
      
      // Group the faculty's preferences by year
      const yearGroups = {};

      const normalPrefs = prefsList.filter(p => p.mock !== true);
      const mockPrefs = prefsList.filter(p => p.mock === true);

      normalPrefs.forEach((p, index) => {
        const sub = getSubjectOrFallback(p.subjectId);
        if (sub) {
          const yr = sub.year;
          if (!yearGroups[yr]) {
            yearGroups[yr] = [];
          }
          yearGroups[yr].push({
            sub,
            choiceNum: index + 1,
            isMock: false
          });
        }
      });

      mockPrefs.forEach(p => {
        const sub = getSubjectOrFallback(p.subjectId);
        if (sub) {
          const yr = sub.year;
          if (!yearGroups[yr]) {
            yearGroups[yr] = [];
          }
          yearGroups[yr].push({
            sub,
            choiceNum: 'M',
            isMock: true
          });
        }
      });

      // Get sorted list of years that actually have preferences
      const selectedYears = Object.keys(yearGroups).map(Number).sort((a, b) => a - b);

      let prefHtml = '<div style="display: flex; flex-direction: column; gap: 8px; width: 100%;">';
      if (selectedYears.length === 0) {
        prefHtml += `<span style="color: var(--text-muted); font-style: italic; font-size: 0.88rem;">No preferences selected</span>`;
      } else {
        selectedYears.forEach(yr => {
          const choices = yearGroups[yr];
          
          // Chunk choices into groups of 3
          const chunks = [];
          for (let i = 0; i < choices.length; i += 3) {
            chunks.push(choices.slice(i, i + 3));
          }

          const chunksHtml = chunks.map(chunk => {
            const rowHtml = chunk.map(c => {
              const isMock = c.isMock === true;
              const badgeBg = isMock ? 'rgba(99, 102, 241, 0.08)' : 'rgba(20, 184, 166, 0.08)';
              const badgeBorder = isMock ? 'rgba(99, 102, 241, 0.2)' : 'rgba(20, 184, 166, 0.2)';
              const numBg = isMock ? 'rgba(99, 102, 241, 0.15)' : 'rgba(20, 184, 166, 0.15)';
              const numColor = isMock ? 'var(--primary)' : 'var(--secondary)';
              const mockLabel = isMock ? ' (Mock)' : '';
              return `
                <div class="preference-badge" style="background: ${badgeBg}; border: 1px solid ${badgeBorder}; border-radius: 4px; padding: 4px 10px; font-size: 0.85rem; display: inline-flex; align-items: center; gap: 6px; margin-right: 6px; margin-bottom: 4px; box-sizing: border-box; max-width: calc(33.33% - 8px); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                  <span style="font-weight: 700; color: ${numColor}; background: ${numBg}; border-radius: 50%; width: 18px; height: 18px; display: inline-flex; align-items: center; justify-content: center; font-size: 0.7rem; flex-shrink: 0;">${c.choiceNum}</span>
                  <span style="color: var(--text-main); overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${c.sub.name} <code style="color: var(--text-muted); font-size: 0.78rem;">(${c.sub.id})${mockLabel}</code></span>
                </div>
              `;
            }).join('');
            return `<div style="display: flex; flex-wrap: nowrap; gap: 8px; width: 100%; align-items: center; margin-bottom: 4px;">${rowHtml}</div>`;
          }).join('');

          prefHtml += `
            <div style="display: flex; flex-direction: column; gap: 4px; border-bottom: 1px solid rgba(255, 255, 255, 0.03); padding-bottom: 6px; margin-bottom: 2px; width: 100%;">
              <span style="font-weight: 600; color: var(--secondary); font-size: 0.85rem; margin-bottom: 4px;">Year ${yr}:</span>
              <div style="width: 100%; display: flex; flex-direction: column; gap: 4px;">${chunksHtml}</div>
            </div>
          `;
        });
      }
      prefHtml += '</div>';

      tr.innerHTML = `
        <td><strong>${fId}</strong></td>
        <td>${fac.name}</td>
        <td><span class="badge badge-admin">${depts || 'N/A'}</span></td>
        <td>${prefHtml}</td>
      `;
      tbody.appendChild(tr);
    });
  }

  function renderPendingTable() {
    const tbody = document.getElementById('pending-table-body');
    if (!tbody) return;
    tbody.innerHTML = '';

    const deptVal = document.getElementById('pref-dept-filter').value;
    const semVal = document.getElementById('pref-sem-filter').value;

    const academicYearInput = document.getElementById('pref-academic-year-input');
    const academicYearVal = academicYearInput ? academicYearInput.value.trim() : '';

    if (!academicYearVal || !deptVal || !semVal) {
      tbody.innerHTML = `<tr><td colspan="2" style="text-align: center; color: var(--text-muted); padding: 15px;">Please enter Academic Year, select Department, and select Semester to view pending submissions.</td></tr>`;
      return;
    }

    const data = window.preferencesData;
    if (!data || !data.facultyList) return;

    let allSubjects = [];
    if (data.subjectsList) {
      allSubjects = data.subjectsList;
    } else if (data.subjectsMap) {
      const seenIds = new Set();
      Object.values(data.subjectsMap).forEach(s => {
        if (s && s.id && !seenIds.has(s.id.toLowerCase())) {
          allSubjects.push(s);
          seenIds.add(s.id.toLowerCase());
        }
      });
    }

    // Filter subjects by selected criteria
    const yearFilterEl = document.getElementById('pref-year-filter');
    const yearVal = yearFilterEl ? yearFilterEl.value : '';

    let matchingSubjects = allSubjects;
    if (academicYearVal) {
      matchingSubjects = matchingSubjects.filter(s => s.academicYear && s.academicYear.toLowerCase() === academicYearVal.toLowerCase());
    }
    if (deptVal) {
      matchingSubjects = matchingSubjects.filter(s => getSubjectDeptCode(s) === deptVal.toUpperCase());
    }
    if (semVal) {
      matchingSubjects = matchingSubjects.filter(s => Number(s.sem) === Number(semVal));
    }
    if (yearVal && yearVal !== 'ALL') {
      matchingSubjects = matchingSubjects.filter(s => Number(s.year) === Number(yearVal));
    }

    const matchingSubjectIds = new Set(matchingSubjects.map(s => s.id.toLowerCase()));

    // Find which faculty have submitted preferences for these filtered subjects
    const submittedFacultyIds = new Set();
    data.allPreferences.forEach(p => {
      if (p.subjectId && matchingSubjectIds.has(p.subjectId.toLowerCase())) {
        submittedFacultyIds.add(p.facultyId.toLowerCase());
      }
    });

    // Pending faculty are those in the facultyList who have not selected any filtered subject
    // AND who are not already fully allocated (i.e. number of distinct allocated subjects < maxSubjectsLimit)
    const maxSubjectsLimit = (data.selectionWindow && data.selectionWindow.maxSubjectsAllocated)
      ? Number(data.selectionWindow.maxSubjectsAllocated)
      : 3;

    const pendingFaculty = (matchingSubjects.length === 0) 
      ? [] 
      : data.facultyList.filter(f => {
          if (!f.id) return false;
          const hasPrefs = submittedFacultyIds.has(f.id.toLowerCase());
          if (hasPrefs) return false;

          const facAllocs = data.sectionAllocs.filter(sa => {
            if (!sa.facultyId || sa.facultyId.toLowerCase() !== f.id.toLowerCase()) return false;
            const sub = data.subjectsMap[sa.subjectId.toLowerCase()];
            return sub && sub.academicYear && sub.academicYear.toLowerCase() === academicYearVal.toLowerCase();
          });
          const uniqueAllocatedSubjectIds = new Set(facAllocs.map(sa => sa.subjectId.toLowerCase()));
          const isFullyAllocated = uniqueAllocatedSubjectIds.size >= maxSubjectsLimit;

          return !isFullyAllocated;
        });

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
  const academicYearInput = document.getElementById('pref-academic-year-input');
  if (academicYearInput) {
    academicYearInput.addEventListener('change', () => {
      loadFacultySelections();
    });
    academicYearInput.addEventListener('keyup', (e) => {
      if (e.key === 'Enter') {
        loadFacultySelections();
      }
    });
  }
  document.getElementById('pref-dept-filter').addEventListener('change', handleFilterChange);
  document.getElementById('pref-sem-filter').addEventListener('change', handleFilterChange);
  const prefYearFilter = document.getElementById('pref-year-filter');
  if (prefYearFilter) {
    prefYearFilter.addEventListener('change', handleFilterChange);
  }

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
      populateCustomSubDropdown();

      // Pre-fill filters if they are empty
      const subDeptFilter = document.getElementById('sub-dept-filter');
      const subYearFilter = document.getElementById('sub-year-filter');
      const subSemFilter = document.getElementById('sub-sem-filter');
      const subAcadYearFilter = document.getElementById('sub-academic-year-filter');

      if (subDeptFilter && !subDeptFilter.value && departments.length > 0) {
        subDeptFilter.value = departments[0].code;
      }
      if (subYearFilter && (!subYearFilter.value || subYearFilter.value === '')) {
        subYearFilter.value = 'ALL';
        const triggerLabel = document.getElementById('custom-sub-trigger-label');
        if (triggerLabel) {
          triggerLabel.innerText = 'All Years - Sem 1';
          triggerLabel.style.color = 'var(--text-main)';
        }
      }
      if (subSemFilter && (!subSemFilter.value || subSemFilter.value === '')) {
        subSemFilter.value = '1';
      }
      if (subAcadYearFilter && !subAcadYearFilter.value) {
        subAcadYearFilter.value = '2025-26';
      }

      renderSubjectTable(subjectList);
    } catch (error) {
      showToast('Load Error', 'Could not fetch subjects', 'error');
      tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--error);">Failed to load subjects</td></tr>`;
    }
  }

  window.switchSubjectView = function(type) {
    currentSubjectViewType = type;
    const btnRegular = document.getElementById('btn-view-regular');
    const btnMock = document.getElementById('btn-view-mock');
    if (btnRegular && btnMock) {
      if (type === 'regular') {
        btnRegular.className = 'btn btn-primary';
        btnMock.className = 'btn btn-ghost';
      } else {
        btnRegular.className = 'btn btn-ghost';
        btnMock.className = 'btn btn-primary';
      }
    }
    renderSubjectTable(subjectList);
  };

  function renderSubjectTable(list) {
    const tbody = document.getElementById('subject-table-body');
    tbody.innerHTML = '';

    const deptVal = document.getElementById('sub-dept-filter').value;
    const yearVal = document.getElementById('sub-year-filter').value;
    const semVal = document.getElementById('sub-sem-filter').value;
    const acadYearEl = document.getElementById('sub-academic-year-filter');
    const acadYearVal = acadYearEl ? acadYearEl.value.trim() : '';

    if (!deptVal || !yearVal || !semVal || !acadYearVal) {
      tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 15px;">Please select Department, Year & Semester, and enter Academic Year to view subjects.</td></tr>`;
      return;
    }

    // Filter by view type (regular vs mock)
    let filteredList = list.filter(s => {
      const isSubMock = s.mock === true;
      const isTargetMock = currentSubjectViewType === 'mock';
      return isSubMock === isTargetMock;
    });

    const yearValUpper = yearVal ? yearVal.toString().toUpperCase() : '';
    const semValUpper = semVal ? semVal.toString().toUpperCase() : '';

    filteredList = filteredList.filter(s => 
      getSubjectDeptCode(s) === deptVal.toUpperCase() &&
      (yearValUpper === 'ALL' || Number(s.year) === Number(yearVal)) &&
      (semValUpper === 'ALL' || Number(s.sem) === Number(semVal)) &&
      s.academicYear && s.academicYear.toLowerCase().includes(acadYearVal.toLowerCase())
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
            <td><strong>${cleanSubjectCode(sub.id)}</strong></td>
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
      (s.id && s.id.toLowerCase().includes(q)) || 
      (s.name && s.name.toLowerCase().includes(q)) || 
      (s.dep && s.dep.toLowerCase().includes(q)) ||
      (s.regulation && s.regulation.toLowerCase().includes(q))
    );
    renderSubjectTable(filtered);
  }

  document.getElementById('subject-search').addEventListener('input', handleSubjectFilterChange);
  document.getElementById('sub-dept-filter').addEventListener('change', handleSubjectFilterChange);
  document.getElementById('sub-year-filter').addEventListener('change', handleSubjectFilterChange);
  document.getElementById('sub-sem-filter').addEventListener('change', handleSubjectFilterChange);
  
  const subAcadYearFilter = document.getElementById('sub-academic-year-filter');
  if (subAcadYearFilter) {
    subAcadYearFilter.addEventListener('input', handleSubjectFilterChange);
  }

  // Removed change listener for section-dept-select to prevent loading table data when adding sections

  window.openSubjectModal = async function(subId = null) {
    const modal = document.getElementById('subject-modal');
    const title = document.getElementById('subject-modal-title');
    const btn = document.getElementById('subject-modal-btn');
    const mode = document.getElementById('subject-modal-mode');
    const oldIdInput = document.getElementById('subject-modal-old-id');
    const idInput = document.getElementById('sub-id');
    const nameInput = document.getElementById('sub-name');
    const deptSelect = document.getElementById('sub-dept');
    const yearSelect = document.getElementById('sub-year');
    const semSelect = document.getElementById('sub-sem');
    const typeSelect = document.getElementById('sub-type');
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
        oldIdInput.value = sub.id;
        title.innerText = 'Edit Subject';
        btn.innerText = 'Update Subject';
        idInput.value = cleanSubjectCode(sub.id);
        idInput.disabled = false; // Code is editable now!
        nameInput.value = sub.name;
        deptSelect.value = sub.dep;
        yearSelect.value = sub.year;
        semSelect.value = sub.sem;
        typeSelect.value = sub.mock ? 'mock' : 'regular';
        regInput.value = sub.regulation;
        academicYearInput.value = sub.academicYear || '';
      }
    } else {
      mode.value = 'add';
      oldIdInput.value = '';
      title.innerText = 'Add Subject';
      btn.innerText = 'Add Subject';
      typeSelect.value = currentSubjectViewType; // Default type to current tab view
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
    const oldId = document.getElementById('subject-modal-old-id').value;
    
    const subjectData = {
      id: document.getElementById('sub-id').value.trim(),
      name: document.getElementById('sub-name').value.trim(),
      dep: document.getElementById('sub-dept').value,
      year: parseInt(document.getElementById('sub-year').value),
      sem: parseInt(document.getElementById('sub-sem').value),
      regulation: document.getElementById('sub-regulation').value.trim(),
      academicYear: document.getElementById('sub-academic-year').value.trim(),
      mock: document.getElementById('sub-type').value === 'mock'
    };

    try {
      if (mode === 'add') {
        await apiRequest('/subject/add', {
          method: 'POST',
          body: subjectData
        });
        showToast('Subject Added', 'New subject added to directory', 'success');
      } else {
        await apiRequest(`/subject/update/${encodeURIComponent(oldId)}`, {
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

    // Populate Section addition form selectors
    const deptSelect = document.getElementById('section-dept-select');
    const yearSelect = document.getElementById('section-year-select');

    populateSelect(deptSelect, departments.map(d => ({ value: d.code, text: `${d.code} - ${d.name}` })), 'Select Dept');
    populateSelect(yearSelect, academicYears.map(y => ({ value: y.yearNumber, text: y.name })), 'Select Year');

    // Populate Section filter selectors
    const filterDept = document.getElementById('section-filter-dept');
    const filterYear = document.getElementById('section-filter-year');
    if (filterDept) populateFilterSelect(filterDept, departments.map(d => ({ value: d.code, text: `${d.code} - ${d.name}` })), 'Select Dept');
    if (filterYear) populateFilterSelect(filterYear, academicYears.map(y => ({ value: y.yearNumber, text: y.name })), 'Select Year');

    renderSectionTable(true);
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

  function populateFilterSelect(selectEl, items, allText) {
    const prevVal = selectEl.value;
    selectEl.innerHTML = `<option value="">${allText}</option>`;
    items.forEach(item => {
      const opt = document.createElement('option');
      opt.value = item.value;
      opt.innerText = item.text;
      selectEl.appendChild(opt);
    });
    selectEl.value = prevVal || "";
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
  function renderSectionTable(initial = false) {
    const tbody = document.getElementById('section-table-body');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (initial === true) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 15px;"><i class="fas fa-info-circle"></i> Select filter criteria above and click "View" to search sections.</td></tr>`;
      return;
    }

    const filterDept = document.getElementById('section-filter-dept').value;
    const filterYear = document.getElementById('section-filter-year').value;
    const filterAcademicYear = document.getElementById('section-filter-academic-year').value.trim();

    if (!filterDept || !filterYear || !filterAcademicYear) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 15px;"><i class="fas fa-info-circle"></i> Please select all filters (Department, Year, Academic Year) and click "View" to see section configurations.</td></tr>`;
      return;
    }

    let filtered = sections;
    if (filterDept) {
      filtered = filtered.filter(s => s.departmentCode && s.departmentCode.toUpperCase() === filterDept.toUpperCase());
    }
    if (filterYear) {
      filtered = filtered.filter(s => s.yearNumber === parseInt(filterYear));
    }
    if (filterAcademicYear) {
      filtered = filtered.filter(s => s.academicYear && s.academicYear.toLowerCase().includes(filterAcademicYear.toLowerCase()));
    }

    const sorted = [...filtered].sort((a,b) => {
      const aDept = a.departmentCode || '';
      const bDept = b.departmentCode || '';
      if (aDept !== bDept) return aDept.localeCompare(bDept);
      
      const aYear = a.yearNumber || 0;
      const bYear = b.yearNumber || 0;
      if (aYear !== bYear) return aYear - bYear;
      
      const aSec = a.sectionName || '';
      const bSec = b.sectionName || '';
      return aSec.localeCompare(bSec);
    });

    if (sorted.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 15px;">No sections found matching the filters.</td></tr>`;
      return;
    }

    sorted.forEach(s => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><strong>${s.departmentCode}</strong></td>
        <td>Year ${s.yearNumber}</td>
        <td>${s.academicYear || 'N/A'}</td>
        <td>Section ${s.sectionName}</td>
        <td>
          <button type="button" class="action-btn action-btn-edit" onclick="editSection(${s.id})" title="Edit" style="background:transparent; border:none; color:var(--secondary); cursor:pointer; padding:6px 10px;"><i class="fas fa-edit"></i></button>
          <button type="button" class="action-btn action-btn-delete" onclick="deleteSection(${s.id})"><i class="fas fa-trash-alt"></i></button>
        </td>
      `;
      tbody.appendChild(tr);
    });
  }

  const sectionFilterBtn = document.getElementById('section-filter-btn');
  if (sectionFilterBtn) {
    sectionFilterBtn.addEventListener('click', () => renderSectionTable(false));
  }

  document.getElementById('section-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const departmentCode = document.getElementById('section-dept-select').value;
    const yearNumber = parseInt(document.getElementById('section-year-select').value);
    const academicYear = document.getElementById('section-academic-year').value.trim();
    const sectionName = document.getElementById('section-name').value.toUpperCase().trim();

    try {
      await apiRequest('/sections/add', {
        method: 'POST',
        body: { departmentCode, yearNumber, sectionName, academicYear }
      });
      showToast('Section Added', `Created section ${sectionName} for Year ${yearNumber} ${departmentCode}`, 'success');
      document.getElementById('section-name').value = '';
      document.getElementById('section-academic-year').value = '';
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
    const acadYearInput = document.getElementById('edit-section-academic-year');
    const nameInput = document.getElementById('edit-section-name');
    const idInput = document.getElementById('edit-section-id');

    populateSelect(deptSelect, departments.map(d => ({ value: d.code, text: `${d.code} - ${d.name}` })), 'Select Dept');
    populateSelect(yearSelect, academicYears.map(y => ({ value: y.yearNumber, text: y.name })), 'Select Year');

    const sec = sections.find(s => s.id === id);
    if (sec) {
      idInput.value = sec.id;
      deptSelect.value = sec.departmentCode;
      yearSelect.value = sec.yearNumber;
      acadYearInput.value = sec.academicYear || '';
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
    const academicYear = document.getElementById('edit-section-academic-year').value.trim();
    const sectionName = document.getElementById('edit-section-name').value.toUpperCase().trim();

    try {
      await apiRequest(`/sections/update/${id}`, {
        method: 'PUT',
        body: { departmentCode, yearNumber, sectionName, academicYear }
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
    const checkedYearCbs = document.querySelectorAll('input[name="deadline-year-checkbox"]:checked');
    const classYears = Array.from(checkedYearCbs).map(cb => parseInt(cb.value));
    const previewContainer = document.getElementById('deadline-subjects-preview');

    if (!previewContainer) return;

    if (!sem || !yearVal || !dept || classYears.length === 0) {
      previewContainer.innerHTML = '<p style="color: var(--text-muted); font-style: italic;">Select Department, Active Semester, check Active Year(s), and enter Academic Year above to preview subjects list.</p>';
      return;
    }

    try {
      if (subjectList.length === 0) {
        const data = await apiRequest('/subject/viewAll');
        subjectList = Array.isArray(data) ? data : [];
      }

      const filtered = subjectList.filter(s => 
        Number(s.sem) === Number(sem) &&
        classYears.includes(Number(s.year)) &&
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
          const typeLabel = sub.mock ? 'MOOC' : 'Regular';
          const typeColor = sub.mock ? 'rgba(99, 102, 241, 0.15)' : 'rgba(20, 184, 166, 0.15)';
          const typeTextColor = sub.mock ? 'var(--primary)' : 'var(--secondary)';
          item.innerHTML = `
            <strong>${sub.id}</strong> - ${sub.name} 
            <span class="badge" style="background: ${typeColor}; color: ${typeTextColor}; font-size: 0.75rem; padding: 2px 6px; border-radius: 4px; font-weight: 500; margin-left: 6px;">${typeLabel}</span><br>
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
  document.querySelectorAll('input[name="deadline-year-checkbox"]').forEach(cb => {
    cb.addEventListener('change', updateSubjectsPreview);
  });

  let adminCountdownInterval = null;

  async function loadDeadlineStatus() {
    if (adminCountdownInterval) {
      clearInterval(adminCountdownInterval);
      adminCountdownInterval = null;
    }
    const statusContainer = document.getElementById('deadline-status-container');
    statusContainer.innerHTML = `<div style="color: var(--text-muted);"><i class="fas fa-circle-notch fa-spin"></i> Loading...</div>`;

    try {
      const allWindows = await apiRequest('/adminfaculty/deadline');
      await populateDeadlineDropdowns();

      // Disable/enable Publish button based on running status
      const submitBtn = document.querySelector('#deadline-form button[type="submit"]');
      let isRunning = false;
      
      const activeWindows = Array.isArray(allWindows) ? allWindows.filter(w => w.active && [1, 2, 3, 4].includes(w.id) && new Date(w.deadline) > new Date()) : (allWindows && allWindows.active && [1, 2, 3, 4].includes(allWindows.id) && new Date(allWindows.deadline) > new Date() ? [allWindows] : []);
      if (activeWindows.length > 0) {
        isRunning = true;
      }
      
      if (submitBtn) {
        if (isRunning) {
          submitBtn.disabled = true;
          submitBtn.innerHTML = '<i class="fas fa-lock"></i> Selection Window Running';
        } else {
          submitBtn.disabled = false;
          submitBtn.innerHTML = '<i class="fas fa-paper-plane"></i> Publish Selection Window';
        }
      }

      // Check/uncheck Active Year checkboxes based on activeWindows
      const yearCheckboxes = document.querySelectorAll('input[name="deadline-year-checkbox"]');
      yearCheckboxes.forEach(cb => {
        const val = parseInt(cb.value);
        const hasWin = activeWindows.some(w => w.year === val);
        cb.checked = hasWin;
      });

      const allocYearCheckboxes = document.querySelectorAll('.allocate-year-checkbox');
      if (allocYearCheckboxes.length > 0 && activeWindows.length > 0) {
        allocYearCheckboxes.forEach(cb => {
          const val = parseInt(cb.value);
          cb.checked = activeWindows.some(w => w.year === val);
        });
      }

      selectionWindow = activeWindows[0] || (Array.isArray(allWindows) ? allWindows[0] : allWindows) || null;

      if (selectionWindow) {
        document.getElementById('deadline-message').value = selectionWindow.message || '';
        if (selectionWindow.sem) {
          document.getElementById('deadline-sem').value = selectionWindow.sem;
        }
        if (selectionWindow.academicYear) {
          document.getElementById('deadline-year').value = selectionWindow.academicYear;
        }
        if (selectionWindow.department) {
          document.getElementById('deadline-dept').value = selectionWindow.department;
        }
        if (selectionWindow.maxRegularPreferences) {
          document.getElementById('deadline-max-regular-pref').value = selectionWindow.maxRegularPreferences;
        }
        if (selectionWindow.maxMockPreferences) {
          document.getElementById('deadline-max-mock-pref').value = selectionWindow.maxMockPreferences;
        }
        updateSubjectsPreview();
      }
      
      if (!allWindows || (Array.isArray(allWindows) && allWindows.length === 0)) {
        statusContainer.innerHTML = `
          <div class="status-badge expired"><i class="fas fa-times-circle"></i> INACTIVE</div>
          <p style="margin-top: 10px; font-size: 0.9rem; color: var(--text-muted);">No subject selection window has been published yet. Faculty cannot submit preferences.</p>
        `;
        return;
      }

      let statusHTML = '<div style="display: flex; flex-direction: column; gap: 16px;">';
      
      for (let y = 1; y <= 4; y++) {
        const win = Array.isArray(allWindows) ? allWindows.find(w => w.year === y) : (allWindows && allWindows.year === y ? allWindows : null);
        statusHTML += `<div class="glass-panel" style="padding: 16px; border: 1px solid var(--panel-border); background: rgba(255,255,255,0.01);">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;">
            <h4 style="color: var(--primary); font-weight: 700; font-size: 0.95rem;">Year ${y} Selection Window</h4>`;
        
        if (win && win.active) {
          const deadlineTime = new Date(win.deadline);
          const now = new Date();
          const diffMs = deadlineTime - now;
          
          if (diffMs <= 0) {
            statusHTML += `
              <span class="status-badge expired" style="font-size:0.7rem; padding: 2px 8px; border-radius: 12px; background: rgba(244,63,94,0.1); color: var(--error); border: 1px solid rgba(244,63,94,0.2);"><i class="fas fa-history"></i> EXPIRED</span>
            </div>
            <div style="font-size: 0.82rem; color: var(--text-muted); line-height: 1.4; display: grid; grid-template-columns: 1fr 1fr; gap: 4px;">
              <div>Semester: <strong>Sem ${win.sem}</strong></div>
              <div>Dept: <strong>${win.department}</strong></div>
              <div class="col-span-2" style="margin-top: 4px;">Expired On: <strong>${deadlineTime.toLocaleString()}</strong></div>
              <div class="col-span-2" style="margin-top: 4px; font-style: italic;">"${win.message}"</div>
            </div>`;
          } else {
            const days = Math.floor(diffMs / (1000 * 60 * 60 * 24));
            const hours = Math.floor((diffMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
            const mins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
            const secs = Math.floor((diffMs % (1000 * 60)) / 1000);
            statusHTML += `
              <span class="status-badge active" style="font-size:0.7rem; padding: 2px 8px; border-radius: 12px; background: rgba(20,184,166,0.1); color: var(--secondary); border: 1px solid rgba(20,184,166,0.2);"><i class="fas fa-hourglass-half animate-pulse"></i> RUNNING</span>
            </div>
            <div style="font-size: 0.82rem; color: var(--text-muted); line-height: 1.4;">
              <div class="admin-countdown-time" data-deadline="${win.deadline}" data-year="${y}" style="color: var(--secondary); font-size: 1.1rem; font-weight: 700; margin-bottom: 4px;">Remaining: ${days}d ${hours}h ${mins}m ${secs}s</div>
              <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 4px;">
                <div>Semester: <strong>Sem ${win.sem}</strong></div>
                <div>Dept: <strong>${win.department}</strong></div>
                <div class="col-span-2">Deadline: <strong>${deadlineTime.toLocaleString()}</strong></div>
              </div>
              <div style="margin-top: 4px; font-style: italic;">"${win.message}"</div>
              <button type="button" class="btn btn-ghost btn-block" style="margin-top: 8px; padding: 4px 10px; font-size: 0.78rem; border-color: rgba(244,63,94,0.4); color: var(--error);" onclick="stopDeadlineWindow(${y})">
                <i class="fas fa-stop-circle"></i> Stop Year ${y} Selection
              </button>
            </div>`;
          }
        } else {
          statusHTML += `
            <span class="status-badge inactive" style="font-size:0.7rem; padding: 2px 8px; border-radius: 12px; background: rgba(255,255,255,0.05); color: var(--text-muted); border: 1px solid var(--panel-border);"><i class="fas fa-times-circle"></i> INACTIVE</span>
          </div>
          <div style="font-size: 0.82rem; color: var(--text-disabled); font-style: italic;">
            No selection window currently active.
          </div>`;
        }
        
        statusHTML += `</div>`;
      }
      
      statusHTML += '</div>';
      statusContainer.innerHTML = statusHTML;

      const updateAdminCountdowns = () => {
        const timerElements = document.querySelectorAll('.admin-countdown-time');
        const now = new Date();
        let anyActive = false;
        
        timerElements.forEach(el => {
          const deadlineStr = el.getAttribute('data-deadline');
          const deadlineTime = new Date(deadlineStr);
          const diffMs = deadlineTime - now;
          
          if (diffMs <= 0) {
            el.innerHTML = 'Expired';
          } else {
            anyActive = true;
            const days = Math.floor(diffMs / (1000 * 60 * 60 * 24));
            const hours = Math.floor((diffMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
            const mins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
            const secs = Math.floor((diffMs % (1000 * 60)) / 1000);
            el.innerHTML = `Remaining: ${days}d ${hours}h ${mins}m ${secs}s`;
          }
        });
        
        if (!anyActive && timerElements.length > 0) {
          clearInterval(adminCountdownInterval);
          adminCountdownInterval = null;
          loadDeadlineStatus();
        }
      };
      
      const activeTimers = document.querySelectorAll('.admin-countdown-time');
      if (activeTimers.length > 0) {
        adminCountdownInterval = setInterval(updateAdminCountdowns, 1000);
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
    const maxRegularPreferences = parseInt(document.getElementById('deadline-max-regular-pref').value);
    const maxMockPreferences = parseInt(document.getElementById('deadline-max-mock-pref').value);

    const checkedYearCbs = document.querySelectorAll('input[name="deadline-year-checkbox"]:checked');
    if (checkedYearCbs.length === 0) {
      showToast('Error', 'Please select at least one Active Year checkbox.', 'warning');
      return;
    }
    const years = Array.from(checkedYearCbs).map(cb => parseInt(cb.value));

    try {
      const params = new URLSearchParams();
      params.append('message', msg);
      params.append('days', days);
      params.append('sem', sem);
      params.append('academicYear', academicYear);
      params.append('department', department);
      years.forEach(y => params.append('years', y));
      params.append('hoursPerWeek', 14);
      params.append('maxSubjectsAllocated', 3);
      params.append('subjectHoursPerWeek', 4);
      params.append('maxRegularPreferences', maxRegularPreferences);
      params.append('maxMockPreferences', maxMockPreferences);

      await apiRequest('/adminfaculty/deadline', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: params
      });

      showToast('Selection Window Opened', `Published selection timeline for ${days} days`, 'success');
      loadDeadlineStatus();

      // Sync selections filter inputs with the new window settings
      const prefYearInput = document.getElementById('pref-academic-year-input');
      if (prefYearInput) {
        prefYearInput.value = academicYear;
      }
      
      const prefDeptSelect = document.getElementById('pref-dept-filter');
      const prefYearFilter = document.getElementById('pref-year-filter');
      const prefSemSelect = document.getElementById('pref-sem-filter');
      if (prefDeptSelect) prefDeptSelect.value = department;
      if (prefYearFilter) prefYearFilter.value = years[0];
      if (prefSemSelect) prefSemSelect.value = sem;
      
      const prefTriggerLabel = document.getElementById('custom-pref-trigger-label');
      if (prefTriggerLabel) {
        prefTriggerLabel.innerText = `Year ${years.join(', ')} - Sem ${sem}`;
        prefTriggerLabel.style.color = 'var(--text-main)';
      }
      
      // Reload Faculty Selections table
      loadFacultySelections();
    } catch (error) {
      showToast('Error', error.message || 'Failed to publish deadline', 'error');
    }
  });

  window.stopDeadlineWindow = function(year) {
    let yearText = year ? `for Year ${year} ` : '';
    showConfirm('Stop Window', `Are you sure you want to stop the subject selection window ${yearText}immediately? Faculty will no longer be able to select subjects for it.`, async () => {
      try {
        const params = new URLSearchParams();
        if (year) params.append('year', year);
        await apiRequest('/adminfaculty/stop-deadline', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
          },
          body: params
        });
        showToast('Deadline Stopped', 'The selection window has been closed.', 'success');
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
    const autoAllocBtn = document.getElementById('auto-allocate-btn');
    const finalizeBtn = document.getElementById('finalize-allocations-btn');
    
    try {
      const allWindows = await apiRequest('/adminfaculty/deadline');
      const validWindows = Array.isArray(allWindows) ? allWindows.filter(w => [1, 2, 3, 4].includes(w.id)) : (allWindows && [1, 2, 3, 4].includes(allWindows.id) ? [allWindows] : []);
      selectionWindow = validWindows.find(w => w.active) || validWindows.find(w => w.academicYear && w.department) || validWindows[0] || allWindows;
      
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
        if (workspace) {
          workspace.style.pointerEvents = 'none';
          workspace.style.opacity = '0.5';
        }
        if (finalizeBtn) finalizeBtn.disabled = true;
      } else {
        if (warningBanner) warningBanner.style.display = 'none';
        if (workspace) {
          workspace.style.pointerEvents = 'auto';
          workspace.style.opacity = '1';
        }
      }

      await calculateAllocationStats();

      // Do not auto-populate query inputs from selectionWindow to keep them empty/0 by default

      initAutoAllocationPageInputs();

    } catch (error) {
      console.error(error);
    }
  }

  async function calculateAllocationStats() {
    try {
      const [subjects, allocations, sectionAllocs, allWindows, faculties] = await Promise.all([
        apiRequest('/subject/viewAll'),
        apiRequest('/adminfaculty/allocations'),
        apiRequest('/adminfaculty/section-allocations'),
        apiRequest('/adminfaculty/deadline'),
        apiRequest('/adminfaculty/viewfaculty')
      ]);
      const validWindows = Array.isArray(allWindows) ? allWindows.filter(w => [1, 2, 3, 4].includes(w.id)) : (allWindows && [1, 2, 3, 4].includes(allWindows.id) ? [allWindows] : []);
      const selectionWindow = validWindows.find(w => w.active) || validWindows.find(w => w.academicYear && w.department) || validWindows[0] || allWindows;

      const subList = Array.isArray(subjects) ? subjects : [];
      const allocList = Array.isArray(allocations) ? allocations : [];
      const secList = Array.isArray(sectionAllocs) ? sectionAllocs : [];
      const activeFacultyIds = new Set(Array.isArray(faculties) ? faculties.map(f => f.id.toLowerCase()) : []);

      const selectedYearCbs = document.querySelectorAll('.allocate-year-checkbox:checked');
      const selectedYears = Array.from(selectedYearCbs).map(cb => Number(cb.value));

      let subjectsInWindow = subList;
      if (selectionWindow) {
        subjectsInWindow = subList.filter(s => 
          (selectionWindow.sem == null || Number(s.sem) === Number(selectionWindow.sem)) &&
          (selectedYears.length > 0 ? selectedYears.includes(Number(s.year)) : (selectionWindow.year == null || Number(s.year) === Number(selectionWindow.year))) &&
          (selectionWindow.department == null || (s.dep && s.dep.toUpperCase() === selectionWindow.department.toUpperCase())) &&
          (selectionWindow.academicYear == null || (s.academicYear && s.academicYear.toLowerCase() === selectionWindow.academicYear.toLowerCase()))
        );
      }

      const activeSubjectIds = new Set(subjectsInWindow.map(s => s.id ? s.id.toUpperCase() : ''));

      // Total Subjects
      const totalSubjects = subjectsInWindow.length;
      document.getElementById('stats-total-subjects').innerText = totalSubjects;

      // Workload and Hours limits
      const workloadLimitInput = document.getElementById('page-hours-limit');
      const subjectHoursInput = document.getElementById('page-subject-hours');
      const workloadLimit = (workloadLimitInput && workloadLimitInput.value) ? Number(workloadLimitInput.value) : 0;
      const subjectHours = (subjectHoursInput && subjectHoursInput.value) ? Number(subjectHoursInput.value) : 0;
      document.getElementById('stats-workload-limit').innerText = workloadLimit;
      document.getElementById('stats-subject-hours').innerText = subjectHours;

      // Subject limits
      const maxSubsInput = document.getElementById('page-max-subjects');
      const maxRegularInput = document.getElementById('page-max-regular');
      const maxMockInput = document.getElementById('page-max-mock');
      const maxSubsLimit = (maxSubsInput && maxSubsInput.value) ? Number(maxSubsInput.value) : 0;
      const maxRegularLimit = (maxRegularInput && maxRegularInput.value) ? Number(maxRegularInput.value) : 0;
      const maxMockLimit = (maxMockInput && maxMockInput.value) ? Number(maxMockInput.value) : 0;
      
      const statsLimitTotal = document.getElementById('stats-limit-total');
      const statsLimitRegular = document.getElementById('stats-limit-regular');
      const statsLimitMock = document.getElementById('stats-limit-mock');
      if (statsLimitTotal) statsLimitTotal.innerText = maxSubsLimit;
      if (statsLimitRegular) statsLimitRegular.innerText = maxRegularLimit;
      if (statsLimitMock) statsLimitMock.innerText = maxMockLimit;

      // Count sections for active subjects and count pending sections
      let totalSections = 0;
      let pendingSections = 0;

      const secAllocMap = {};
      secList.forEach(sa => {
        if (sa.subjectId && sa.sectionName && sa.facultyId && (sa.facultyId.toUpperCase().startsWith("UNKNOWN_") || activeFacultyIds.has(sa.facultyId.toLowerCase()))) {
          secAllocMap[sa.subjectId.toUpperCase() + "_" + sa.sectionName.toUpperCase()] = sa;
        }
      });

      if (selectionWindow && selectionWindow.department) {
        const activeSections = sections.filter(sec => 
          (selectedYears.length > 0 ? selectedYears.includes(Number(sec.yearNumber)) : (selectionWindow.year == null || Number(sec.yearNumber) === Number(selectionWindow.year))) &&
          sec.departmentCode && sec.departmentCode.toUpperCase() === selectionWindow.department.toUpperCase()
        );
        
        totalSections = activeSections.length;

        activeSections.forEach(sec => {
          let isSectionFullyAllocated = true;
          subjectsInWindow.forEach(sub => {
            if (sub.id && sec.sectionName && Number(sub.year) === Number(sec.yearNumber)) {
              const key = sub.id.toUpperCase() + "_" + sec.sectionName.toUpperCase();
              if (!secAllocMap[key]) {
                isSectionFullyAllocated = false;
              }
            }
          });
          if (!isSectionFullyAllocated) {
            pendingSections++;
          }
        });
      }

      document.getElementById('stats-total-sections').innerText = totalSections;
      document.getElementById('stats-pending-sections').innerText = pendingSections;

      // Count allocated subjects in window (distinct)
      const allocatedInWindow = allocList.filter(a => a.subjectId && activeSubjectIds.has(a.subjectId.toUpperCase()));
      const distinctAllocatedSubjectIds = new Set(allocatedInWindow.map(a => a.subjectId ? a.subjectId.toUpperCase() : ''));
      document.getElementById('stats-allocated-subjects').innerText = distinctAllocatedSubjectIds.size;

      // Count Regular vs Mock allocated subjects in window
      let regAllocated = 0;
      let mockAllocated = 0;
      distinctAllocatedSubjectIds.forEach(subId => {
        const sub = subjectsInWindow.find(s => s.id && s.id.toUpperCase() === subId);
        if (sub) {
          if (sub.mock) {
            mockAllocated++;
          } else {
            regAllocated++;
          }
        }
      });
      document.getElementById('stats-allocated-regular').innerText = regAllocated;
      document.getElementById('stats-allocated-mock').innerText = mockAllocated;

    } catch (error) {
      console.error('Error calculating allocation stats:', error);
    }
  }

  async function loadFacultyLoadDetails(facId) {
    const loadDetailsDiv = document.getElementById('selected-faculty-load-details');
    const workloadHoursEl = document.getElementById('load-workload-hours');
    const subjectsCountEl = document.getElementById('load-subjects-count');
    const regularCountEl = document.getElementById('load-regular-count');
    const mockCountEl = document.getElementById('load-mock-count');
    const statusMsgEl = document.getElementById('load-status-msg');

    if (!loadDetailsDiv) return;

    try {
      const [allocations, sectionAllocs, allWindows, subjects, hasPastMock] = await Promise.all([
        apiRequest('/adminfaculty/allocations'),
        apiRequest('/adminfaculty/section-allocations'),
        apiRequest('/adminfaculty/deadline'),
        apiRequest('/subject/viewAll'),
        apiRequest(`/faculty/has-mock-allocation/${facId}`).catch(() => false)
      ]);
      const validWindows = Array.isArray(allWindows) ? allWindows.filter(w => [1, 2, 3, 4].includes(w.id)) : (allWindows && [1, 2, 3, 4].includes(allWindows.id) ? [allWindows] : []);
      const selectionWindow = validWindows.find(w => w.active) || validWindows.find(w => w.academicYear && w.department) || validWindows[0] || allWindows;

      const subList = Array.isArray(subjects) ? subjects : [];
      const allocList = Array.isArray(allocations) ? allocations : [];
      const secList = Array.isArray(sectionAllocs) ? sectionAllocs : [];

      let subjectsInWindow = subList;
      if (selectionWindow) {
        subjectsInWindow = subList.filter(s => 
          (selectionWindow.sem == null || Number(s.sem) === Number(selectionWindow.sem)) &&
          (selectionWindow.year == null || Number(s.year) === Number(selectionWindow.year)) &&
          (selectionWindow.department == null || (s.dep && s.dep.toUpperCase() === selectionWindow.department.toUpperCase())) &&
          (selectionWindow.academicYear == null || (s.academicYear && s.academicYear.toLowerCase() === selectionWindow.academicYear.toLowerCase()))
        );
      }
      const activeSubjectIds = new Set(subjectsInWindow.map(s => s.id ? s.id.toUpperCase() : ''));

      const facAllocs = allocList.filter(a => a.facultyId && a.subjectId && a.facultyId.toUpperCase() === facId.toUpperCase() && activeSubjectIds.has(a.subjectId.toUpperCase()));
      const facSecAllocs = secList.filter(sa => sa.facultyId && sa.subjectId && sa.facultyId.toUpperCase() === facId.toUpperCase() && activeSubjectIds.has(sa.subjectId.toUpperCase()));

      const workloadLimitInput = document.getElementById('page-hours-limit');
      const subjectHoursInput = document.getElementById('page-subject-hours');
      const hoursLimit = (workloadLimitInput && workloadLimitInput.value) ? Number(workloadLimitInput.value) : 0;
      const subjectHours = (subjectHoursInput && subjectHoursInput.value) ? Number(subjectHoursInput.value) : 0;

      const allocatedHours = facSecAllocs.length * subjectHours;
      const uniqueSubjectsCount = facAllocs.length;

      let regularCount = 0;
      let mockCount = 0;
      facAllocs.forEach(a => {
        const sub = subjectsInWindow.find(s => s.id && a.subjectId && s.id.toUpperCase() === a.subjectId.toUpperCase());
        if (sub) {
          if (sub.mock) {
            mockCount++;
          } else {
            regularCount++;
          }
        }
      });

      workloadHoursEl.innerText = `${allocatedHours} / ${hoursLimit}`;
      const maxSubjectsInput = document.getElementById('page-max-subjects');
      const maxSubjectsLimit = (maxSubjectsInput && maxSubjectsInput.value) ? Number(maxSubjectsInput.value) : 0;
      subjectsCountEl.innerText = `${uniqueSubjectsCount} / ${maxSubjectsLimit}`;
      regularCountEl.innerText = regularCount;
      mockCountEl.innerText = mockCount;

      let statusMsg = "Valid Load Status";
      let statusColor = "var(--secondary)";
      
      if (allocatedHours > hoursLimit) {
        statusMsg = "⚠️ Exceeds Workload Hours Limit!";
        statusColor = "var(--error)";
      } else if (uniqueSubjectsCount === 3 && mockCount === 0) {
        statusMsg = "⚠️ If 3 subjects allocated, 1 must be Mock!";
        statusColor = "var(--warning)";
      } else if (uniqueSubjectsCount === 2 && mockCount === 1) {
        statusMsg = "⚠️ If 2 subjects allocated, both must be Regular!";
        statusColor = "var(--warning)";
      } else if (uniqueSubjectsCount === 1 && mockCount === 1) {
        statusMsg = "⚠️ If 1 subject allocated, it must be Regular!";
        statusColor = "var(--warning)";
      } else if (hasPastMock && mockCount > 0) {
        statusMsg = "⚠️ Faculty has mock allocation in past years. Current mock is blocked!";
        statusColor = "var(--error)";
      }

      statusMsgEl.innerText = statusMsg;
      statusMsgEl.style.color = statusColor;

      loadDetailsDiv.style.display = 'block';

    } catch (error) {
      console.error('Error loading faculty load details:', error);
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
      
      let filteredList = list;
      if (selectionWindow) {
        filteredList = list.filter(s => 
          (selectionWindow.sem == null || Number(s.sem) === Number(selectionWindow.sem)) &&
          (selectionWindow.year == null || Number(s.year) === Number(selectionWindow.year)) &&
          (selectionWindow.department == null || (s.dep && s.dep.toUpperCase() === selectionWindow.department.toUpperCase())) &&
          (selectionWindow.academicYear == null || (s.academicYear && s.academicYear.toLowerCase() === selectionWindow.academicYear.toLowerCase()))
        );
      }

      filteredList.forEach(s => {
        const opt = document.createElement('option');
        opt.value = s.id;
        opt.innerText = `${s.id} - ${s.name} (Year ${s.year} Sem ${s.sem} ${s.dep})${s.mock ? ' [Mock]' : ''}`;
        select.appendChild(opt);
      });
    } catch (error) {
      console.error(error);
    }
  }

  document.getElementById('alloc-faculty-select').addEventListener('change', async (e) => {
    const facId = e.target.value;
    const prefList = document.getElementById('alloc-faculty-pref-list');
    prefList.innerHTML = '<li><i class="fas fa-spinner fa-spin"></i> Loading choices...</li>';

    try {
      const [preferences, sectionAllocs, allSubs] = await Promise.all([
        apiRequest(`/faculty/preferences/${facId}`),
        apiRequest(`/adminfaculty/section-allocations`),
        apiRequest('/subject/viewAll')
      ]);

      if (preferences.length === 0) {
        prefList.innerHTML = '<li style="color: var(--text-muted); list-style-type: none; margin-left: -20px;">Faculty hasn\'t selected any subjects.</li>';
      } else {
        prefList.innerHTML = '';
        const subjectsMap = {};
        allSubs.forEach(s => { subjectsMap[s.id] = s.name; });

        const mySecAllocs = (Array.isArray(sectionAllocs) ? sectionAllocs : [])
          .filter(sa => sa.facultyId.toUpperCase() === facId.toUpperCase());

        preferences.forEach(p => {
          const li = document.createElement('li');
          const subName = subjectsMap[p.subjectId] || 'Unknown Subject';
          const isMock = p.mock === true;

          const matchedSecs = mySecAllocs.filter(sa => sa.subjectId.toUpperCase() === p.subjectId.toUpperCase());
          let secLabel = "";
          if (matchedSecs.length > 0) {
            secLabel = ` (Allocated: Sec ${matchedSecs.map(s => s.sectionName).join(', ')})`;
          }

          li.innerText = `${cleanSubjectCode(p.subjectId)} - ${subName}${isMock ? ' (Mock)' : ''}${secLabel}`;
          if (isMock) {
            li.style.color = 'var(--primary)';
          }
          prefList.appendChild(li);
        });
      }

      await loadFacultyLoadDetails(facId);

    } catch (error) {
      prefList.innerHTML = '<li style="color: var(--error)">Failed to load preferences</li>';
    }
  });

  document.getElementById('alloc-subject-select').addEventListener('change', async (e) => {
    const selectedSubId = e.target.value;
    const secSelect = document.getElementById('alloc-section-select');
    secSelect.innerHTML = '<option value="" disabled selected>Loading sections...</option>';

    try {
      const subject = await apiRequest(`/subject/view/${selectedSubId}`);
      if (subject && typeof subject === 'object' && subject.id) {
        const subDeptCode = getSubjectDeptCode(subject);
        const filteredSections = sections.filter(sec => 
          Number(sec.yearNumber) === Number(subject.year) && 
          sec.departmentCode.toUpperCase() === subDeptCode
        );
        
        if (filteredSections.length === 0) {
          secSelect.innerHTML = `<option value="" disabled selected>No sections configured for Year ${subject.year} ${subject.dep}</option>`;
        } else {
          secSelect.innerHTML = '<option value="" disabled selected>Choose Section...</option>';
          
          const optAll = document.createElement('option');
          optAll.value = 'ALL';
          optAll.innerText = 'All Sections';
          secSelect.appendChild(optAll);

          filteredSections.forEach(s => {
            const opt = document.createElement('option');
            opt.value = s.sectionName;
            opt.innerText = `Section ${s.sectionName}`;
            secSelect.appendChild(opt);
          });
        }
      }
    } catch (error) {
      console.error('Error loading sections for subject:', error);
      secSelect.innerHTML = '<option value="" disabled selected>Error loading sections</option>';
    }
  });

  async function loadSubjectAllocations() {
    const tbody = document.getElementById('subject-allocations-table-body');
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="2" style="text-align: center;"><i class="fas fa-spinner fa-spin"></i> Loading...</td></tr>`;

    try {
      const [allocations, sectionAllocs, faculty, subjects, allWindows, preferences] = await Promise.all([
        apiRequest('/adminfaculty/allocations'),
        apiRequest('/adminfaculty/section-allocations'),
        apiRequest('/adminfaculty/viewfaculty'),
        apiRequest('/subject/viewAll'),
        apiRequest('/adminfaculty/deadline'),
        apiRequest('/faculty/preferences/all')
      ]);
      const validWindows = Array.isArray(allWindows) ? allWindows.filter(w => [1, 2, 3, 4].includes(w.id)) : (allWindows && [1, 2, 3, 4].includes(allWindows.id) ? [allWindows] : []);
      const selectionWindow = validWindows.find(w => w.active) || validWindows.find(w => w.academicYear && w.department) || validWindows[0] || allWindows;

      const subList = Array.isArray(subjects) ? subjects : [];
      
      const selectedYearCbs = document.querySelectorAll('.allocate-year-checkbox:checked');
      const selectedYears = Array.from(selectedYearCbs).map(cb => Number(cb.value));

      let filteredSubList = subList;
      if (selectionWindow) {
        filteredSubList = subList.filter(s => 
          (selectionWindow.sem == null || Number(s.sem) === Number(selectionWindow.sem)) &&
          (selectedYears.length > 0 ? selectedYears.includes(Number(s.year)) : (selectionWindow.year == null || Number(s.year) === Number(selectionWindow.year))) &&
          (selectionWindow.department == null || (s.dep && s.dep.toUpperCase() === selectionWindow.department.toUpperCase())) &&
          (selectionWindow.academicYear == null || (s.academicYear && s.academicYear.toLowerCase() === selectionWindow.academicYear.toLowerCase()))
        );
      }
      const activeSubjectIds = new Set(filteredSubList.map(s => s.id ? s.id.toUpperCase() : ''));

      const allocList = (Array.isArray(allocations) ? allocations : [])
        .filter(a => a.subjectId && activeSubjectIds.has(a.subjectId.toUpperCase()));
      const facList = Array.isArray(faculty) ? faculty : [];
      const activeFacultyIds = new Set(facList.map(f => f.id.toLowerCase()));
      const secList = (Array.isArray(sectionAllocs) ? sectionAllocs : [])
        .filter(sa => sa.subjectId && activeSubjectIds.has(sa.subjectId.toUpperCase()))
        .filter(sa => sa.facultyId && (sa.facultyId.toUpperCase().startsWith("UNKNOWN_") || activeFacultyIds.has(sa.facultyId.toLowerCase())));

      const facultyMap = {};
      facList.forEach(f => {
        if (f.id) facultyMap[f.id] = f.name;
      });

      const subjectsMap = {};
      subList.forEach(s => {
        if (s.id) subjectsMap[s.id] = s;
      });

      const sectionMap = {};
      secList.forEach(sa => {
        if (sa.facultyId && sa.subjectId) {
          const key = sa.facultyId.toUpperCase() + "_" + sa.subjectId.toUpperCase();
          if (!sectionMap[key]) {
            sectionMap[key] = [];
          }
          sectionMap[key].push({ id: sa.id, name: sa.sectionName });
        }
      });

      tbody.innerHTML = '';
      const validAllocList = allocList.filter(alloc => alloc.facultyId && facultyMap[alloc.facultyId]);
      if (secList.length === 0) {
        tbody.innerHTML = `<tr><td colspan="2" style="text-align: center; color: var(--text-muted);">No subject allocations.</td></tr>`;
        return;
      }

      const groupedBySection = {};
      secList.forEach(sa => {
        const secName = sa.sectionName || 'Unknown Section';
        if (!groupedBySection[secName]) {
          groupedBySection[secName] = [];
        }
        groupedBySection[secName].push(sa);
      });

      const sortedSections = Object.keys(groupedBySection).sort();
      sortedSections.forEach(secName => {
        const headerTr = document.createElement('tr');
        headerTr.innerHTML = `
          <td colspan="2" style="background: rgba(20, 184, 166, 0.15); font-weight: bold; color: var(--secondary); padding: 8px 12px;">
            <i class="fas fa-folder-open" style="margin-right: 6px;"></i> Section ${secName}
          </td>
        `;
        tbody.appendChild(headerTr);

        const list = groupedBySection[secName];
        list.forEach(sa => {
          const sub = subjectsMap[sa.subjectId] || { name: 'Unknown Subject' };
          const facName = facultyMap[sa.facultyId] || 'Unknown Faculty';

          const tr = document.createElement('tr');
          tr.innerHTML = `
            <td><strong>${sa.facultyId}</strong><br><span style="font-size: 0.85rem; color: var(--text-muted);">${facName}</span></td>
            <td>
              <strong>${cleanSubjectCode(sa.subjectId)}</strong><br>
              <span style="font-size: 0.85rem; color: var(--text-muted);">${sub.name}</span>
            </td>
          `;
          tbody.appendChild(tr);
        });
      });

      const swapSelect1 = document.getElementById('swap-alloc-1');
      const swapSelect2 = document.getElementById('swap-alloc-2');
      if (swapSelect1 && swapSelect2) {
        const swapOptions = validAllocList.map(alloc => {
          const sub = subjectsMap[alloc.subjectId] || { name: 'Unknown' };
          const facName = facultyMap[alloc.facultyId] || 'Unknown';
          return {
            value: alloc.id,
            text: `${facName} (${alloc.facultyId}) <-> ${sub.name} (${cleanSubjectCode(alloc.subjectId)})`
          };
        });
        populateSelect(swapSelect1, swapOptions, 'Allocation 1...');
        populateSelect(swapSelect2, swapOptions, 'Allocation 2...');
      }

      const unallocatedTbody = document.getElementById('unallocated-faculty-table-body');
      if (unallocatedTbody) {
        const history = await apiRequest('/adminfaculty/history/all');
        const allocatedIds = new Set();
        (Array.isArray(allocations) ? allocations : []).forEach(a => {
          if (a.facultyId && a.subjectId && activeSubjectIds.has(a.subjectId.toUpperCase())) {
            allocatedIds.add(a.facultyId.toUpperCase());
          }
        });
        if (Array.isArray(history)) {
          history.forEach(h => {
            if (h.facultyId && h.subjectId && activeSubjectIds.has(h.subjectId.toUpperCase())) {
              allocatedIds.add(h.facultyId.toUpperCase());
            }
          });
        }

        const activeAcademicYear = (selectionWindow && selectionWindow.academicYear) ? selectionWindow.academicYear.trim().toLowerCase() : '';
        const prefsForActiveYear = (Array.isArray(preferences) ? preferences : []).filter(p => {
          if (!activeAcademicYear) return true;
          if (!p.subjectId) return false;
          const sub = subList.find(s => s.id && s.id.toLowerCase() === p.subjectId.toLowerCase());
          return sub && sub.academicYear && sub.academicYear.toLowerCase() === activeAcademicYear;
        });
        const facultyWhoSubmittedPrefs = new Set(
          prefsForActiveYear
            .filter(p => p.facultyId)
            .map(p => p.facultyId.toUpperCase())
        );

        const unallocatedFaculties = facList.filter(f => {
          if (!f.id) return false;
          const isSuper = f.role && f.role.toUpperCase() === 'SUPERADMIN';
          const isNotAllocated = !allocatedIds.has(f.id.toUpperCase());
          const submittedPrefs = facultyWhoSubmittedPrefs.has(f.id.toUpperCase());
          return !isSuper && isNotAllocated && submittedPrefs;
        });

        if (unallocatedFaculties.length === 0) {
          unallocatedTbody.innerHTML = `<tr><td colspan="3" style="text-align: center; color: var(--text-muted);">All faculty members have allocations.</td></tr>`;
        } else {
          unallocatedTbody.innerHTML = '';
          unallocatedFaculties.forEach(f => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
              <td><strong>${f.id}</strong></td>
              <td>${f.name}</td>
              <td>${f.email || 'N/A'}</td>
            `;
            unallocatedTbody.appendChild(tr);
          });
        }
      }
    } catch (error) {
      console.error(error);
      tbody.innerHTML = `<tr><td colspan="2" style="text-align: center; color: var(--error);">Failed to load subject allocations: ${error.message}</td></tr>`;
    }
  }

  async function loadAllocationsWorkspaceData() {
    try {
      document.getElementById('allocate-subject-form').reset();
      document.getElementById('alloc-faculty-pref-list').innerHTML = '<li style="color: var(--text-muted); list-style-type: none; margin-left: -20px;">Select a faculty to see their choices</li>';
      document.getElementById('alloc-section-select').innerHTML = '<option value="" disabled selected>Choose Section...</option>';
      document.getElementById('selected-faculty-load-details').style.display = 'none';

      await loadSubjectAllocations();
      await updateFinalizationStatus();
    } catch (error) {
      console.error('Error loading allocations workspace data:', error);
    }
  }

  document.getElementById('allocate-subject-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const facultyId = document.getElementById('alloc-faculty-select').value;
    const subjectId = document.getElementById('alloc-subject-select').value;
    const sectionName = document.getElementById('alloc-section-select').value;
    
    const submitBtn = e.target.querySelector('button[type="submit"]');
    submitBtn.disabled = true;

    try {
      if (sectionName === 'ALL') {
        const subject = await apiRequest(`/subject/view/${subjectId}`);
        const subDeptCode = getSubjectDeptCode(subject);
        const filteredSections = sections.filter(sec => 
          Number(sec.yearNumber) === Number(subject.year) && 
          sec.departmentCode.toUpperCase() === subDeptCode
        );

        if (filteredSections.length === 0) {
          throw new Error('No sections configured for this subject.');
        }

        for (const sec of filteredSections) {
          await apiRequest('/adminfaculty/allocate-section', {
            method: 'POST',
            body: { facultyId, subjectId, sectionName: sec.sectionName }
          });
        }
        showToast('Sections Allocated', 'All sections allocated to faculty successfully', 'success');
      } else {
        await apiRequest('/adminfaculty/allocate-section', {
          method: 'POST',
          body: { facultyId, subjectId, sectionName }
        });
        showToast('Section Allocated', `Section ${sectionName} allocated successfully`, 'success');
      }
      
      await loadAllocationsWorkspaceData();
      await calculateAllocationStats();
      if (facultyId) {
        await loadFacultyLoadDetails(facultyId);
      }
    } catch (error) {
      showToast('Allocation Failed', error.message || 'Validation error saving allocation', 'error');
    } finally {
      submitBtn.disabled = false;
    }
  });

  window.deleteSubjectAllocation = function(id) {
    showConfirm('Delete Subject Allocation', 'Are you sure you want to remove this subject allocation and all its section assignments?', async () => {
      try {
        await apiRequest(`/adminfaculty/allocation/${id}`, {
          method: 'DELETE'
        });
        showToast('Allocation Removed', 'Subject allocation deleted successfully', 'success');
        
        const currentFacId = document.getElementById('alloc-faculty-select').value;
        await loadAllocationsWorkspaceData();
        await calculateAllocationStats();
        if (currentFacId) {
          await loadFacultyLoadDetails(currentFacId);
        }
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
        
        const currentFacId = document.getElementById('alloc-faculty-select').value;
        await loadAllocationsWorkspaceData();
        await calculateAllocationStats();
        if (currentFacId) {
          await loadFacultyLoadDetails(currentFacId);
        }
      } catch (error) {
        showToast('Error', error.message || 'Could not delete allocation', 'error');
      }
    });
  };

  function checkAutoAllocateEnable() {
    const yearCbs = document.querySelectorAll('.allocate-year-checkbox:checked');
    const btn = document.getElementById('auto-allocate-btn');
    if (btn) {
      btn.disabled = (yearCbs.length === 0);
    }
  }

  function initAutoAllocationPageInputs() {
    const hoursLimitEl = document.getElementById('page-hours-limit');
    const subjectHoursEl = document.getElementById('page-subject-hours');
    const maxSubjectsEl = document.getElementById('page-max-subjects');
    const maxRegularEl = document.getElementById('page-max-regular');
    const maxMockEl = document.getElementById('page-max-mock');

    if (hoursLimitEl && (!hoursLimitEl.value || hoursLimitEl.value === '0')) {
      hoursLimitEl.value = (selectionWindow && selectionWindow.hoursPerWeek) ? selectionWindow.hoursPerWeek : 18;
    }
    if (subjectHoursEl && (!subjectHoursEl.value || subjectHoursEl.value === '0')) {
      subjectHoursEl.value = (selectionWindow && selectionWindow.subjectHoursPerWeek) ? selectionWindow.subjectHoursPerWeek : 3;
    }
    if (maxSubjectsEl && (!maxSubjectsEl.value || maxSubjectsEl.value === '0')) {
      maxSubjectsEl.value = (selectionWindow && selectionWindow.maxSubjectsAllocated) ? selectionWindow.maxSubjectsAllocated : 3;
    }
    if (maxRegularEl && (!maxRegularEl.value || maxRegularEl.value === '0')) {
      maxRegularEl.value = (selectionWindow && selectionWindow.maxRegularPreferences) ? selectionWindow.maxRegularPreferences : 2;
    }
    if (maxMockEl && (!maxMockEl.value || maxMockEl.value === '0')) {
      maxMockEl.value = (selectionWindow && selectionWindow.maxMockPreferences) ? selectionWindow.maxMockPreferences : 1;
    }

    const inputs = [
      'page-hours-limit',
      'page-subject-hours',
      'page-max-subjects',
      'page-max-regular',
      'page-max-mock'
    ];
    inputs.forEach(id => {
      const el = document.getElementById(id);
      if (el) {
        el.removeEventListener('input', checkAutoAllocateEnable);
        el.removeEventListener('change', checkAutoAllocateEnable);
        el.addEventListener('input', checkAutoAllocateEnable);
        el.addEventListener('change', checkAutoAllocateEnable);

        const handleInputChange = () => {
          calculateAllocationStats();
          const currentFacId = document.getElementById('alloc-faculty-select').value;
          if (currentFacId) {
            loadFacultyLoadDetails(currentFacId);
          }
        };
        el.addEventListener('input', handleInputChange);
        el.addEventListener('change', handleInputChange);
      }
    });

    const yearCbs = document.querySelectorAll('.allocate-year-checkbox');
    yearCbs.forEach(cb => {
      cb.removeEventListener('change', handleYearCheckboxChange);
      cb.addEventListener('change', handleYearCheckboxChange);
    });

    checkAutoAllocateEnable();
  }

  function handleYearCheckboxChange() {
    calculateAllocationStats();
    loadSubjectAllocations();
    checkAutoAllocateEnable();
    const currentFacId = document.getElementById('alloc-faculty-select').value;
    if (currentFacId) {
      loadFacultyLoadDetails(currentFacId);
    }
  }

  async function runAutoAllocationFromPage() {
    let hoursLimit = document.getElementById('page-hours-limit') ? document.getElementById('page-hours-limit').value : '';
    let subjectHours = document.getElementById('page-subject-hours') ? document.getElementById('page-subject-hours').value : '';
    let maxSubjects = document.getElementById('page-max-subjects') ? document.getElementById('page-max-subjects').value : '';
    let maxRegular = document.getElementById('page-max-regular') ? document.getElementById('page-max-regular').value : '';
    let maxMock = document.getElementById('page-max-mock') ? document.getElementById('page-max-mock').value : '';

    if (!hoursLimit) hoursLimit = (selectionWindow && selectionWindow.hoursPerWeek) ? selectionWindow.hoursPerWeek : 18;
    if (!subjectHours) subjectHours = (selectionWindow && selectionWindow.subjectHoursPerWeek) ? selectionWindow.subjectHoursPerWeek : 3;
    if (!maxSubjects) maxSubjects = (selectionWindow && selectionWindow.maxSubjectsAllocated) ? selectionWindow.maxSubjectsAllocated : 3;
    if (!maxRegular) maxRegular = (selectionWindow && selectionWindow.maxRegularPreferences) ? selectionWindow.maxRegularPreferences : 2;
    if (!maxMock) maxMock = (selectionWindow && selectionWindow.maxMockPreferences) ? selectionWindow.maxMockPreferences : 1;

    // Confirm automatic subject allocation
    const userConfirmed = await new Promise((resolve) => {
      showConfirm(
        'Confirm Auto-Allocation',
        `Are you sure you want to run the automatic subject allocation with the selected settings? This will overwrite any existing non-finalized allocations for the active selection window.`,
        () => resolve(true)
      );
      
      const overlay = document.getElementById('confirm-modal-overlay');
      if (overlay) {
        const cancelBtn = overlay.querySelector('.btn-ghost');
        const closeBtn = overlay.querySelector('.modal-close');
        
        const cleanup = () => {
          if (cancelBtn) cancelBtn.removeEventListener('click', onCancel);
          if (closeBtn) closeBtn.removeEventListener('click', onCancel);
        };
        
        const onCancel = () => {
          cleanup();
          resolve(false);
        };
        
        if (cancelBtn) cancelBtn.addEventListener('click', onCancel);
        if (closeBtn) closeBtn.addEventListener('click', onCancel);
      }
    });

    if (!userConfirmed) return;

    const autoBtn = document.getElementById('auto-allocate-btn');
    if (autoBtn) {
      autoBtn.disabled = true;
      autoBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Allocating...';
    }

    const selectedYearCbs = document.querySelectorAll('.allocate-year-checkbox:checked');
    const selectedYears = Array.from(selectedYearCbs).map(cb => Number(cb.value));

    try {
      // Fetch windows and resolve active/reference window
      const allWindows = await apiRequest('/adminfaculty/deadline');
      const validWindows = Array.isArray(allWindows) ? allWindows.filter(w => [1, 2, 3, 4].includes(w.id)) : (allWindows && [1, 2, 3, 4].includes(allWindows.id) ? [allWindows] : []);
      const targetWin = validWindows.find(w => w.active) || validWindows.find(w => w.academicYear && w.department) || validWindows[0] || allWindows;
      if (targetWin) {
        selectionWindow = targetWin;
      }

      let isActiveWindow = false;
      if (Array.isArray(allWindows)) {
        isActiveWindow = allWindows.some(w => w.active && (selectedYears.length === 0 || selectedYears.includes(w.year)) && new Date(w.deadline) > new Date());
      } else if (allWindows) {
        isActiveWindow = allWindows.active && (selectedYears.length === 0 || selectedYears.includes(allWindows.year)) && new Date(allWindows.deadline) > new Date();
      }

      let proceed = true;
      if (isActiveWindow) {
        proceed = await new Promise((resolve) => {
          showConfirm('Stop Selection Window', 'The Selection Window is currently active. Would you like to stop the selection window now and proceed with Auto-Allocation?', () => resolve(true));
          
          const overlay = document.getElementById('confirm-modal-overlay');
          if (overlay) {
            const cancelBtn = overlay.querySelector('.btn-ghost');
            const closeBtn = overlay.querySelector('.modal-close');
            const cleanup = () => {
              if (cancelBtn) cancelBtn.removeEventListener('click', onCancel);
              if (closeBtn) closeBtn.removeEventListener('click', onCancel);
            };
            const onCancel = () => {
              cleanup();
              resolve(false);
            };
            if (cancelBtn) cancelBtn.addEventListener('click', onCancel);
            if (closeBtn) closeBtn.addEventListener('click', onCancel);
          }
        });
      }

      if (!proceed) {
        if (autoBtn) {
          autoBtn.disabled = false;
          autoBtn.innerHTML = '<i class="fas fa-bolt"></i> Auto-Allocate Subjects';
        }
        return;
      }

      if (isActiveWindow) {
        await apiRequest('/adminfaculty/stop-deadline', { method: 'POST' });
        loadDeadlineStatus();
      }

      // Call auto-allocate endpoint with params
      const params = new URLSearchParams();
      params.append('hoursLimit', hoursLimit);
      params.append('subjectHours', subjectHours);
      params.append('maxSubjects', maxSubjects);
      params.append('maxRegular', maxRegular);
      params.append('maxMock', maxMock);

      const winToUse = selectionWindow || targetWin;
      if (winToUse) {
        if (winToUse.academicYear) params.append('academicYear', winToUse.academicYear);
        if (winToUse.department) params.append('department', winToUse.department);
        if (winToUse.sem) params.append('sem', winToUse.sem);
      }
      selectedYears.forEach(y => params.append('years', y));

      await apiRequest(`/adminfaculty/auto-allocate?${params.toString()}`, { method: 'POST' });
      showToast('Auto-Allocation Complete', 'Draft subject allocations generated based on preferences.', 'success');
      await loadAllocationWorkspace();
    } catch (error) {
      showToast('Auto-Allocation Failed', error.message || 'Could not auto-allocate subjects', 'error');
    } finally {
      if (autoBtn) {
        autoBtn.disabled = false;
        autoBtn.innerHTML = '<i class="fas fa-bolt"></i> Auto-Allocate Subjects';
      }
    }
  }

  window.runAutoAllocationFromPage = runAutoAllocationFromPage;
  window.checkAutoAllocateEnable = checkAutoAllocateEnable;
  window.initAutoAllocationPageInputs = initAutoAllocationPageInputs;

  // ----------------------------------------------------
  // ALLOCATION REPORT LOGIC
  // ----------------------------------------------------
  let reportData = [];
  let rawReportData = [];

  function populateReportFilterDropdowns() {
    const deptFilter = document.getElementById('report-dept-select');
    const semFilter = document.getElementById('report-sem-select');
    const reportYearFilter = document.getElementById('report-year-filter');

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

    if (reportYearFilter && reportYearFilter.options.length <= 1) {
      academicYears.forEach(y => {
        const opt = document.createElement('option');
        opt.value = y.yearNumber;
        opt.innerText = y.name;
        reportYearFilter.appendChild(opt);
      });
    }
  }

  function populateCustomDeptDropdown() {
    const menuEl = document.getElementById('custom-dept-menu');
    if (!menuEl) return;
    
    menuEl.innerHTML = '';
    academicYears.forEach(y => {
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
          <span>${y.name}</span>
          <i class="fas fa-chevron-down" style="font-size: 0.75rem; color: var(--text-muted); transition: transform 0.2s ease;"></i>
        </div>
        <div class="custom-sem-submenu" style="display: none; flex-direction: column; gap: 4px; margin-top: 8px; width: 100%; padding-left: 12px; box-sizing: border-box;">
          ${semesters.map(s => `
            <button type="button" class="sem-opt-btn" data-year="${y.yearNumber}" data-sem="${s.semNumber}" style="background: rgba(255, 255, 255, 0.05); border: 1px solid var(--panel-border); border-radius: var(--border-radius-sm); color: var(--text-main); padding: 6px 12px; font-size: 0.82rem; text-align: left; width: 100%; cursor: pointer; transition: all 0.2s ease;">
              ${s.name}
            </button>
          `).join('')}
        </div>
      `;
      
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
        const yearVal = btn.getAttribute('data-year');
        const semVal = btn.getAttribute('data-sem');
        
        // Update hidden native selects
        const yearSelect = document.getElementById('report-year-filter');
        const semSelect = document.getElementById('report-sem-select');
        if (yearSelect) {
          yearSelect.value = yearVal;
          yearSelect.dispatchEvent(new Event('change'));
        }
        if (semSelect) {
          semSelect.value = semVal;
          semSelect.dispatchEvent(new Event('change'));
        }
        
        // Update trigger button text
        const yearObj = academicYears.find(y => String(y.yearNumber) === String(yearVal));
        const yearName = yearObj ? yearObj.name : `${yearVal} Year`;
        const triggerLabel = document.getElementById('custom-dept-trigger-label');
        if (triggerLabel) {
          triggerLabel.innerText = `${yearName} - Sem ${semVal}`;
          triggerLabel.style.color = 'var(--text-main)';
        }
        
        // Hide dropdown
        const dropdownMenu = document.getElementById('custom-dept-menu');
        if (dropdownMenu) dropdownMenu.style.display = 'none';
        
        loadReportData();
      });
    });
  }

  async function initReportFilters() {
    const tbody = document.getElementById('report-table-body');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 20px;">Please enter Academic Year, select Department, and select Semester to view the report.</td></tr>`;
    }
    
    if (departments.length === 0 || semesters.length === 0) {
      await loadConfigOptions();
    }
    populateReportFilterDropdowns();

    // Fetch the active selection window if not loaded
    if (!selectionWindow) {
      try {
        const allWindows = await apiRequest('/adminfaculty/deadline');
        const validWindows = Array.isArray(allWindows) ? allWindows.filter(w => [1, 2, 3, 4].includes(w.id)) : (allWindows && [1, 2, 3, 4].includes(allWindows.id) ? [allWindows] : []);
        selectionWindow = validWindows.find(w => w.active) || validWindows.find(w => w.academicYear && w.department) || validWindows[0] || allWindows;
      } catch (e) {
        console.error('Error fetching selection window for report:', e);
      }
    }

    const academicYearInput = document.getElementById('report-academic-year-input');
    const deptSelect = document.getElementById('report-dept-select');
    const semSelect = document.getElementById('report-sem-select');

    if (selectionWindow) {
      if (academicYearInput && selectionWindow.academicYear) {
        academicYearInput.value = selectionWindow.academicYear;
      }
      if (deptSelect && selectionWindow.department) {
        deptSelect.value = selectionWindow.department;
      }
      if (semSelect && selectionWindow.sem) {
        semSelect.value = selectionWindow.sem;
      }
    }

    const reportSemSelect = document.getElementById('report-sem-select');
    if (reportSemSelect) {
      reportSemSelect.removeEventListener('change', loadReportData);
      reportSemSelect.addEventListener('change', loadReportData);
    }
    
    const reportDeptSelect = document.getElementById('report-dept-select');
    if (reportDeptSelect) {
      reportDeptSelect.removeEventListener('change', loadReportData);
      reportDeptSelect.addEventListener('change', loadReportData);
    }

    const reportAcadYearInput = document.getElementById('report-academic-year-input');
    if (reportAcadYearInput) {
      reportAcadYearInput.removeEventListener('change', loadReportData);
      reportAcadYearInput.addEventListener('change', loadReportData);
    }

    // Automatically trigger loadReportData if we have the values pre-populated
    if (academicYearInput && academicYearInput.value && deptSelect && deptSelect.value && semSelect && semSelect.value) {
      loadReportData();
    }
  }

  async function loadReportData() {
    const tbody = document.getElementById('report-table-body');
    if (!tbody) return;

    const academicYearInput = document.getElementById('report-academic-year-input');
    const academicYearVal = academicYearInput ? academicYearInput.value.trim() : '';

    const deptVal = document.getElementById('report-dept-select').value;
    const semVal = document.getElementById('report-sem-select').value;

    if (!academicYearVal || !deptVal || !semVal) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 20px;">Please enter Academic Year, select Department, and select Semester to view the report.</td></tr>`;
      return;
    }

    tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 40px;"><i class="fas fa-spinner fa-spin" style="font-size: 1.5rem; margin-bottom: 12px; display: block;"></i> Loading report data...</td></tr>`;

    try {
      const data = await apiRequest(`/superadmin/reports/allocations?academicYear=${encodeURIComponent(academicYearVal)}`);
      rawReportData = Array.isArray(data) ? data : [];

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
      }).filter(fac => fac.allocations.length > 0 || fac.hasPreferences === true || fac.isUnknown === true);

      reportData.sort((a, b) => {
        const isUnknownA = a.isUnknown === true;
        const isUnknownB = b.isUnknown === true;
        if (isUnknownA !== isUnknownB) return isUnknownA ? 1 : -1;

        const orderA = a.submissionOrder != null ? a.submissionOrder : Number.MAX_SAFE_INTEGER;
        const orderB = b.submissionOrder != null ? b.submissionOrder : Number.MAX_SAFE_INTEGER;
        if (orderA !== orderB) return orderA - orderB;

        return (a.facultyId || '').localeCompare(b.facultyId || '', 'en', { numeric: true, sensitivity: 'base' });
      });

      renderReportTable(reportData);
      await renderReportPendingSections();
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
        : `<strong>${fac.facultyId}</strong>`;
        
      const editBtnHtml = isUnknown
        ? `
        <button onclick="openAssignFacultyModal('${fac.facultyId}')" style="background: none; border: none; padding: 4px 8px; margin-left: 8px; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; color: var(--error);" title="Assign Real Faculty">
          <i class="fas fa-user-plus" style="font-size: 0.95rem;"></i>
        </button>
        `
        : `
        <button onclick="openFacultyAllocationsEditModal('${fac.facultyId}', '${fac.name.replace(/'/g, "\\'")}', ${isUnknown})" style="background: none; border: none; padding: 4px 8px; margin-left: 8px; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; color: var(--secondary);" title="Edit Faculty Allocations">
          <i class="fas fa-edit" style="font-size: 0.95rem;"></i>
        </button>
        `;

      const facultyNameHtml = isUnknown 
        ? `<span style="color: var(--error); font-weight: 500;">${fac.name} (Pending Hire)</span>${editBtnHtml}` 
        : `<span>${fac.name}</span>${editBtnHtml}`;

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

  // Report Search Event Handler
  document.getElementById('report-search').addEventListener('input', (e) => {
    const q = e.target.value.toLowerCase().trim();
    if (!q) {
      renderReportTable(reportData);
      return;
    }
    const filtered = reportData.filter(fac => {
      const matchName = fac.name && fac.name.toLowerCase().includes(q);
      const matchId = fac.facultyId && fac.facultyId.toLowerCase().includes(q);
      const matchSub = fac.allocations && fac.allocations.some(a => 
        (a.subjectName && a.subjectName.toLowerCase().includes(q)) || 
        (a.subjectId && a.subjectId.toLowerCase().includes(q))
      );
      return matchName || matchId || matchSub;
    });
    renderReportTable(filtered);
  });

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
    
    // Render allocations if any exist
    if (fac && fac.allocations && fac.allocations.length > 0) {
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

        const deleteBtn = document.createElement('button');
        deleteBtn.className = "btn btn-danger btn-sm";
        deleteBtn.innerHTML = '<i class="fas fa-trash"></i>';
        deleteBtn.style.cssText = "height: 32px; width: 32px; display: inline-flex; align-items: center; justify-content: center; background: #EF4444; border: none; color: #fff; cursor: pointer; border-radius: var(--border-radius-sm);";
        deleteBtn.onclick = () => deleteFacultyAllocationFromModal(a.subjectId, a.sectionName, facultyId, facultyName, a.id);

        actionArea.appendChild(reassignBtn);
        actionArea.appendChild(deleteBtn);
        div.appendChild(infoSpan);
        div.appendChild(actionArea);
        listEl.appendChild(div);
      });
    } else {
      listEl.innerHTML = '<div style="color: var(--text-muted); text-align: center; padding: 20px;">No active allocations for this faculty.</div>';
    }

    // Dynamic Extensions: Add Subject / Pending Sections
    const existingExt = document.getElementById('modal-edit-extensions');
    if (existingExt) {
      existingExt.remove();
    }

    const extDiv = document.createElement('div');
    extDiv.id = 'modal-edit-extensions';
    extDiv.style.cssText = "margin-top: 20px; border-top: 1px solid var(--panel-border); padding-top: 15px;";
    
    extDiv.innerHTML = `
      <div style="margin-bottom: 20px;">
        <h4 style="color: var(--secondary); font-size: 0.95rem; margin: 0 0 8px 0; display: flex; align-items: center; gap: 6px;"><i class="fas fa-plus-circle"></i> Add Subject Allocation</h4>
        <div style="display: flex; gap: 8px; align-items: center;">
          <select id="modal-add-subject-select" class="form-control" style="height: 38px; font-size: 0.85rem; background: rgba(15, 23, 42, 0.6); border: 1px solid var(--panel-border); color: var(--text-main); border-radius: var(--border-radius-md); padding: 4px 8px; flex: 1;">
            <option value="" disabled selected>Loading available subjects and sections...</option>
          </select>
          <button id="modal-add-subject-btn" class="btn btn-primary" style="height: 38px; padding: 0 16px;">Add</button>
        </div>
        <div id="modal-warning-area" style="display: none; margin-top: 12px; background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.3); border-radius: var(--border-radius-md); padding: 12px; font-size: 0.85rem; color: var(--error);">
          <p id="modal-warning-text" style="margin: 0 0 8px 0; font-weight: 500;"></p>
          <button id="modal-ignore-btn" class="btn btn-danger btn-sm" style="background: #EF4444; border: none; font-size: 0.8rem; padding: 6px 12px; border-radius: 4px; cursor: pointer; color: white;">Ignore Constraints & Add</button>
        </div>
      </div>
    `;

    document.getElementById('faculty-edit-modal-body').appendChild(extDiv);

    // Populate lists asynchronously
    (async () => {
      try {
        const [allSecAllocs, allSections, allSubjects, faculties] = await Promise.all([
          apiRequest('/adminfaculty/section-allocations'),
          apiRequest('/sections/all'),
          apiRequest('/subject/viewAll'),
          apiRequest('/adminfaculty/viewfaculty')
        ]);

        const deptVal = document.getElementById('report-dept-select') ? document.getElementById('report-dept-select').value : '';
        const semVal = document.getElementById('report-sem-select') ? document.getElementById('report-sem-select').value : '';
        const academicYearInput = document.getElementById('report-academic-year-input');
        const academicYearVal = academicYearInput ? academicYearInput.value.trim() : '';

        let activeSubjects = allSubjects;
        if (academicYearVal) {
          activeSubjects = activeSubjects.filter(s => s.academicYear && s.academicYear.toLowerCase() === academicYearVal.toLowerCase());
        }
        if (deptVal) {
          activeSubjects = activeSubjects.filter(s => getSubjectDeptCode(s) === deptVal.toUpperCase());
        }
        if (semVal) {
          activeSubjects = activeSubjects.filter(s => Number(s.sem) === Number(semVal));
        }

        if (activeSubjects.length === 0) {
          activeSubjects = allSubjects;
        }

        const deptSections = allSections.filter(sec => !deptVal || (sec.departmentCode && sec.departmentCode.toUpperCase() === deptVal.toUpperCase()));

        // Dropdown options
        const selectEl = document.getElementById('modal-add-subject-select');
        selectEl.innerHTML = '<option value="" disabled selected>Select an allocation to add...</option>';
        const addBtnEl = document.getElementById('modal-add-subject-btn');
        if (addBtnEl) addBtnEl.disabled = false;

        const unknownAllocs = [];
        allSecAllocs.forEach(alloc => {
          if (alloc.facultyId && alloc.facultyId.toUpperCase().startsWith("UNKNOWN_")) {
            const sub = allSubjects.find(s => s.id && s.id.toLowerCase() === alloc.subjectId.toLowerCase());
            if (sub) {
              unknownAllocs.push({
                subjectId: alloc.subjectId,
                subjectName: sub.name,
                sectionName: alloc.sectionName,
                unknownFacultyId: alloc.facultyId,
                year: sub.year,
                sem: sub.sem
              });
            }
          }
        });

        // Gather pending / unallocated sections
        const allocatedKeys = new Set();
        (rawReportData || []).forEach(f => {
          (f.allocations || []).forEach(a => {
            if (a.subjectId && a.sectionName) {
              allocatedKeys.add(`${a.subjectId.toLowerCase()}_${a.sectionName.toUpperCase()}`);
            }
          });
        });
        (allSecAllocs || []).forEach(a => {
          if (a.subjectId && a.sectionName) {
            allocatedKeys.add(`${a.subjectId.toLowerCase()}_${a.sectionName.toUpperCase()}`);
          }
        });

        const pendingSections = [];
        const availableSections = [];

        activeSubjects.forEach(sub => {
          const subSections = deptSections.filter(sec => Number(sec.yearNumber) === Number(sub.year));
          if (subSections.length > 0) {
            subSections.forEach(sec => {
              const key = `${sub.id.toLowerCase()}_${sec.sectionName.toUpperCase()}`;
              if (!allocatedKeys.has(key)) {
                pendingSections.push({
                  subjectId: sub.id,
                  subjectName: sub.name,
                  sectionName: sec.sectionName,
                  year: sub.year,
                  sem: sub.sem
                });
              } else {
                availableSections.push({
                  subjectId: sub.id,
                  subjectName: sub.name,
                  sectionName: sec.sectionName,
                  year: sub.year,
                  sem: sub.sem
                });
              }
            });
          } else {
            // Default section A
            const key = `${sub.id.toLowerCase()}_A`;
            if (!allocatedKeys.has(key)) {
              pendingSections.push({
                subjectId: sub.id,
                subjectName: sub.name,
                sectionName: 'A',
                year: sub.year,
                sem: sub.sem
              });
            }
          }
        });

        let addedOptionCount = 0;

        // Group 1: Pending Unallocated Sections
        if (pendingSections.length > 0) {
          const grp = document.createElement('optgroup');
          grp.label = "─── Unallocated / Pending Sections ───";
          pendingSections.forEach(ps => {
            const opt = document.createElement('option');
            opt.value = `${ps.subjectId}|${ps.sectionName}|PENDING`;
            opt.text = `${cleanSubjectName(ps.subjectName)} (Year ${ps.year} - Sec ${ps.sectionName}) - Pending`;
            grp.appendChild(opt);
            addedOptionCount++;
          });
          selectEl.appendChild(grp);
        }

        // Group 2: Unknown Faculty allocations
        if (unknownAllocs.length > 0) {
          const grp = document.createElement('optgroup');
          grp.label = "─── From Unknown Faculty ───";
          unknownAllocs.forEach(alloc => {
            const opt = document.createElement('option');
            opt.value = `${alloc.subjectId}|${alloc.sectionName}|${alloc.unknownFacultyId}`;
            opt.text = `${cleanSubjectName(alloc.subjectName)} (Year ${alloc.year} - Sec ${alloc.sectionName}) - from ${alloc.unknownFacultyId}`;
            grp.appendChild(opt);
            addedOptionCount++;
          });
          selectEl.appendChild(grp);
        }

        // Group 3: All Available Curriculum Subjects
        if (activeSubjects.length > 0) {
          const grp = document.createElement('optgroup');
          grp.label = "─── All Active Subjects ───";
          activeSubjects.forEach(sub => {
            const subSections = deptSections.filter(sec => Number(sec.yearNumber) === Number(sub.year));
            const secNames = subSections.length > 0 ? subSections.map(s => s.sectionName) : ['A'];
            secNames.forEach(sName => {
              const opt = document.createElement('option');
              opt.value = `${sub.id}|${sName}|DIRECT`;
              opt.text = `${cleanSubjectName(sub.name)} (Year ${sub.year} - Sec ${sName})`;
              grp.appendChild(opt);
              addedOptionCount++;
            });
          });
          selectEl.appendChild(grp);
        }

        if (addedOptionCount === 0) {
          const opt = document.createElement('option');
          opt.text = "No subjects available to add";
          opt.disabled = true;
          selectEl.appendChild(opt);
          if (addBtnEl) addBtnEl.disabled = true;
        }

        // Dropdown actions
        if (addBtnEl) {
          addBtnEl.onclick = async () => {
            const val = selectEl.value;
            if (!val) {
              showToast('Selection Required', 'Please select a subject to add', 'warning');
              return;
            }
            const [subjectId, sectionName, fromFacultyId] = val.split('|');
            await attemptAddSubject(subjectId, sectionName, fromFacultyId, facultyId, facultyName, false);
          };
        }
      } catch (err) {
        console.error("Error loading extensions in modal:", err);
      }
    })();

    // Open Modal
    document.getElementById('faculty-edit-modal-overlay').classList.add('active');
  };

  window.closeFacultyEditModal = function() {
    document.getElementById('faculty-edit-modal-overlay').classList.remove('active');
    activeEditFacultyId = null;
    activeEditFacultyName = null;
    activeEditFacultyIsUnknown = false;
  };

  async function attemptAddSubject(subjectId, sectionName, fromFacultyId, toFacultyId, facultyName, ignoreConstraints) {
    const warningArea = document.getElementById('modal-warning-area');
    const warningText = document.getElementById('modal-warning-text');
    const ignoreBtn = document.getElementById('modal-ignore-btn');
    
    if (warningArea) warningArea.style.display = 'none';

    try {
      if (fromFacultyId === 'PENDING' || fromFacultyId === 'DIRECT') {
        await apiRequest(`/adminfaculty/allocate-section?ignoreConstraints=${ignoreConstraints}`, {
          method: 'POST',
          body: {
            subjectId: subjectId,
            sectionName: sectionName,
            facultyId: toFacultyId,
            finalized: true
          }
        });
      } else {
        await apiRequest(`/adminfaculty/reassign-allocation?subjectId=${encodeURIComponent(subjectId)}&sectionName=${encodeURIComponent(sectionName)}&fromFacultyId=${encodeURIComponent(fromFacultyId)}&toFacultyId=${encodeURIComponent(toFacultyId)}&ignoreConstraints=${ignoreConstraints}`, {
          method: 'POST'
        });
      }
      showToast('Success', 'Allocation added successfully', 'success');
      
      // Reload report and modal
      await loadReportData();
      openFacultyAllocationsEditModal(toFacultyId, facultyName, false);
    } catch (err) {
      let errMsg = err.message || 'The allocation violates one or more rules.';
      try {
        const parsed = JSON.parse(errMsg);
        if (parsed.message) errMsg = parsed.message;
      } catch (e) {}

      if (!ignoreConstraints) {
        if (warningArea && warningText && ignoreBtn) {
          warningArea.style.display = 'block';
          warningText.innerText = `Constraint Notice: ${errMsg}`;
          ignoreBtn.onclick = async () => {
            await attemptAddSubject(subjectId, sectionName, fromFacultyId, toFacultyId, facultyName, true);
          };
        } else {
          showConfirm('Constraint Notice', `${errMsg}\n\nDo you want to ignore constraints and add anyway?`, async () => {
            await attemptAddSubject(subjectId, sectionName, fromFacultyId, toFacultyId, facultyName, true);
          });
        }
      } else {
        showToast('Error', errMsg, 'error');
      }
    }
  }

  async function deleteFacultyAllocationFromModal(subjectId, sectionName, facultyId, facultyName, id) {
    showConfirm(
      'Delete Allocation',
      'Are you sure you want to delete this allocation? This section will become pending/unallocated.',
      async () => {
        try {
          const params = new URLSearchParams();
          if (subjectId) params.append('subjectId', subjectId);
          if (sectionName) params.append('sectionName', sectionName);
          if (facultyId) params.append('facultyId', facultyId);
          if (id) params.append('id', id);

          await apiRequest(`/adminfaculty/delete-allocation-by-details?${params.toString()}`, {
            method: 'POST'
          });
          showToast('Success', 'Allocation deleted successfully', 'success');
          await loadReportData();
          openFacultyAllocationsEditModal(facultyId, facultyName, false);
        } catch (err) {
          showToast('Delete Failed', err.message || 'Could not delete allocation', 'error');
        }
      }
    );
  }

  window.openAssignFacultyModal = function(unknownFacultyId) {
    document.getElementById('assign-unknown-id-info').innerText = unknownFacultyId;
    const select = document.getElementById('assign-new-faculty-select');
    select.innerHTML = '<option value="" disabled selected>Choose Faculty...</option>';

    // Filter real faculties with 0 allocations
    const unallocatedRealFaculties = reportData.filter(fac => fac.isUnknown !== true && (!fac.allocations || fac.allocations.length === 0));

    if (unallocatedRealFaculties.length === 0) {
      const opt = document.createElement('option');
      opt.text = "No unallocated faculty available";
      opt.disabled = true;
      select.appendChild(opt);
    } else {
      unallocatedRealFaculties.forEach(fac => {
        const opt = document.createElement('option');
        opt.value = fac.facultyId;
        opt.text = `${fac.name} (${fac.facultyId})`;
        select.appendChild(opt);
      });
    }

    document.getElementById('assign-faculty-modal-overlay').classList.add('active');
  };

  window.closeAssignFacultyModal = function() {
    document.getElementById('assign-faculty-modal-overlay').classList.remove('active');
  };

  window.submitAssignUnknownToFaculty = async function() {
    const unknownId = document.getElementById('assign-unknown-id-info').innerText;
    const newFacultyId = document.getElementById('assign-new-faculty-select').value;

    if (!newFacultyId) {
      showToast('Selection Required', 'Please select a new faculty member.', 'warning');
      return;
    }

    try {
      await apiRequest(`/adminfaculty/assign-unknown-to-faculty?unknownFacultyId=${encodeURIComponent(unknownId)}&newFacultyId=${encodeURIComponent(newFacultyId)}`, {
        method: 'POST'
      });
      showToast('Success', `Allocations assigned to ${newFacultyId} successfully!`, 'success');
      closeAssignFacultyModal();
      await loadReportData();
    } catch (err) {
      showToast('Assignment Failed', err.message || 'Could not assign unknown allocations', 'error');
    }
  };

  async function renderReportPendingSections() {
    const listEl = document.getElementById('report-pending-sections-list');
    const containerEl = document.getElementById('report-pending-sections-container');
    if (!listEl || !containerEl) return;

    try {
      const [allSecAllocs, allSections, allSubjects, faculties] = await Promise.all([
        apiRequest('/adminfaculty/section-allocations'),
        apiRequest('/sections/all'),
        apiRequest('/subject/viewAll'),
        apiRequest('/adminfaculty/viewfaculty')
      ]);

      const deptVal = document.getElementById('report-dept-select') ? document.getElementById('report-dept-select').value : '';
      const semVal = document.getElementById('report-sem-select') ? document.getElementById('report-sem-select').value : '';
      const academicYearInput = document.getElementById('report-academic-year-input');
      const academicYearVal = academicYearInput ? academicYearInput.value.trim() : '';

      if (!deptVal || !semVal || !academicYearVal) {
        containerEl.style.display = 'none';
        updateFloatingButtons();
        return;
      }

      const activeSubjects = allSubjects.filter(sub => 
        sub.dep && sub.dep.toUpperCase() === deptVal.toUpperCase() &&
        sub.sem && Number(sub.sem) === Number(semVal) &&
        sub.academicYear && sub.academicYear.toLowerCase() === academicYearVal.toLowerCase()
      );

      const deptSections = allSections.filter(sec => sec.departmentCode && sec.departmentCode.toUpperCase() === deptVal.toUpperCase());

      const activeFacultyIds = new Set(Array.isArray(faculties) ? faculties.map(f => f.id.toLowerCase()) : []);
      const pendingSections = [];
      activeSubjects.forEach(sub => {
        const subSections = deptSections.filter(sec => Number(sec.yearNumber) === Number(sub.year));
        subSections.forEach(sec => {
          const isAllocated = allSecAllocs.some(alloc => 
            alloc.subjectId && alloc.subjectId.toLowerCase() === sub.id.toLowerCase() &&
            alloc.sectionName && alloc.sectionName.toUpperCase() === sec.sectionName.toUpperCase() &&
            alloc.facultyId && (alloc.facultyId.toUpperCase().startsWith("UNKNOWN_") || activeFacultyIds.has(alloc.facultyId.toLowerCase()))
          );
          if (!isAllocated) {
            pendingSections.push({
              subjectId: sub.id,
              subjectName: sub.name,
              sectionName: sec.sectionName,
              year: sub.year,
              sem: sub.sem
            });
          }
        });
      });

      if (pendingSections.length === 0) {
        containerEl.style.display = 'none';
      } else {
        containerEl.style.display = 'block';
        listEl.innerHTML = '';
        pendingSections.forEach(ps => {
          const div = document.createElement('div');
          div.style.cssText = "display: flex; justify-content: space-between; align-items: center; background: rgba(245, 158, 11, 0.05); border: 1px solid rgba(245, 158, 11, 0.2); border-radius: 6px; padding: 10px 14px; font-size: 0.88rem; color: var(--text-main);";
          div.innerHTML = `<span><strong>${cleanSubjectName(ps.subjectName)}</strong> - Sec ${ps.sectionName} <span style="font-size: 0.8rem; color: var(--text-muted); margin-left: 6px;">(Year ${ps.year})</span></span>`;
          listEl.appendChild(div);
        });
      }
      updateFloatingButtons();
    } catch (err) {
      console.error("Error rendering report pending sections:", err);
      containerEl.style.display = 'none';
      updateFloatingButtons();
    }
  }

  function updateFloatingButtons() {
    const unknownBtn = document.getElementById('nav-unknown-btn');
    const pendingBtn = document.getElementById('nav-pending-btn');
    if (!unknownBtn || !pendingBtn) return;

    const activeTab = document.querySelector('.tab-content.active');
    const isReportTab = activeTab && activeTab.id === 'allocation-report-tab';

    if (!isReportTab) {
      unknownBtn.style.display = 'none';
      pendingBtn.style.display = 'none';
      return;
    }

    const hasUnknown = (reportData || []).some(fac => fac.isUnknown === true);
    unknownBtn.style.display = hasUnknown ? 'flex' : 'none';

    const pendingContainer = document.getElementById('report-pending-sections-container');
    const hasPending = pendingContainer && pendingContainer.style.display === 'block';
    pendingBtn.style.display = hasPending ? 'flex' : 'none';
  }

  // Floating nav buttons initialization
  const unknownBtnEl = document.getElementById('nav-unknown-btn');
  const pendingBtnEl = document.getElementById('nav-pending-btn');

  if (unknownBtnEl) {
    unknownBtnEl.onclick = () => {
      const unknownRow = document.querySelector('#report-table-body tr[style*="border-left: 4px solid var(--error)"]');
      if (unknownRow) {
        unknownRow.scrollIntoView({ behavior: 'smooth', block: 'center' });
        unknownRow.style.boxShadow = '0 0 20px var(--error)';
        setTimeout(() => {
          unknownRow.style.boxShadow = '';
        }, 1500);
      } else {
        showToast('Not Found', 'No unknown faculty allocations found', 'info');
      }
    };
  }

  if (pendingBtnEl) {
    pendingBtnEl.onclick = () => {
      const container = document.getElementById('report-pending-sections-container');
      if (container) {
        container.scrollIntoView({ behavior: 'smooth', block: 'center' });
        container.style.boxShadow = '0 0 20px var(--warning)';
        setTimeout(() => {
          container.style.boxShadow = '';
        }, 1500);
      }
    };
  }

  function showInlineReassignDropdown(container, subjectId, subjectName, sectionName, fromFacultyId, year, semester) {
    container.innerHTML = '';
    
    // 1. Gather other faculty members for direct transfer
    const facultyOptions = rawReportData.filter(fac => 
      fac.facultyId && String(fac.facultyId).toUpperCase() !== String(fromFacultyId).toUpperCase()
    );

    // 2. Gather eligible swaps (other allocations in same semester/year)
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
    select.style.cssText = "height: 32px; font-size: 0.85rem; background: rgba(15, 23, 42, 0.6); border: 1px solid var(--panel-border); border-radius: var(--border-radius-md); color: var(--text-main); padding: 4px 8px; width: 230px;";

    if (facultyOptions.length === 0 && eligibleSwaps.length === 0) {
      const opt = document.createElement('option');
      opt.text = "No reassign options available";
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
      optDefault.text = "Select Faculty or Swap...";
      optDefault.value = "";
      optDefault.disabled = true;
      optDefault.selected = true;
      select.appendChild(optDefault);

      // Group 1: Reassign / Transfer to Faculty
      if (facultyOptions.length > 0) {
        const facGroup = document.createElement('optgroup');
        facGroup.label = "─── Transfer to Faculty ───";
        facultyOptions.forEach(f => {
          const o = document.createElement('option');
          o.value = `FACULTY|${f.facultyId}`;
          const unknownSuffix = f.isUnknown ? ' (Unknown Faculty)' : '';
          o.text = `Assign to: ${f.name} (${f.facultyId})${unknownSuffix}`;
          facGroup.appendChild(o);
        });
        select.appendChild(facGroup);
      }

      // Group 2: Swap with Allocation
      if (eligibleSwaps.length > 0) {
        const swapGroup = document.createElement('optgroup');
        swapGroup.label = "─── Swap with Allocation ───";
        eligibleSwaps.sort((s1, s2) => s1.subjectName.localeCompare(s2.subjectName));
        eligibleSwaps.forEach(opt => {
          const o = document.createElement('option');
          o.value = `SWAP|${opt.subjectId}|${opt.sectionName}`;
          o.text = `Swap with: ${cleanSubjectName(opt.subjectName)} - Sec ${opt.sectionName} (${opt.facultyName})`;
          swapGroup.appendChild(o);
        });
        select.appendChild(swapGroup);
      }

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
          showToast('Selection Required', 'Please select a faculty member or swap target.', 'error');
          return;
        }

        saveBtn.disabled = true;
        saveBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';

        if (val.startsWith('FACULTY|')) {
          const toFacultyId = val.split('|')[1];
          async function executeReassign(ignoreConstraints) {
            try {
              await apiRequest(`/adminfaculty/reassign-allocation?subjectId=${encodeURIComponent(subjectId)}&sectionName=${encodeURIComponent(sectionName)}&fromFacultyId=${encodeURIComponent(fromFacultyId)}&toFacultyId=${encodeURIComponent(toFacultyId)}&ignoreConstraints=${ignoreConstraints}`, {
                method: 'POST'
              });
              showToast('Success', 'Allocation reassigned successfully!', 'success');
              await loadReportData();
              openFacultyAllocationsEditModal(activeEditFacultyId, activeEditFacultyName, activeEditFacultyIsUnknown);
            } catch (err) {
              let errMsg = err.message || 'Workload or constraint limit reached.';
              try {
                const parsed = JSON.parse(errMsg);
                if (parsed.message) errMsg = parsed.message;
              } catch (e) {}

              if (!ignoreConstraints) {
                showConfirm('Constraint Notice', `${errMsg}\n\nDo you want to ignore constraints and reassign anyway?`, async () => {
                  await executeReassign(true);
                });
                saveBtn.disabled = false;
                saveBtn.innerHTML = '<i class="fas fa-check"></i>';
              } else {
                showToast('Reassign Failed', errMsg, 'error');
                saveBtn.disabled = false;
                saveBtn.innerHTML = '<i class="fas fa-check"></i>';
              }
            }
          }
          await executeReassign(false);
        } else if (val.startsWith('SWAP|')) {
          const [_, newSubjectId, newSectionName] = val.split('|');
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
    const deptVal = document.getElementById('report-dept-select').value;
    const semVal = document.getElementById('report-sem-select').value;

    if (!academicYearVal || !deptVal || !semVal) {
      showToast('Filters Required', 'Please select Academic Year, Department and Semester to export', 'warning');
      return;
    }

    if (reportData.length === 0) {
      showToast('No Data', 'No report data available to export', 'warning');
      return;
    }

    let tableHtml = '<table border="1">';
    tableHtml += '<thead><tr style="background-color: #14B8A6; color: #ffffff; font-weight: bold;">';
    tableHtml += '<th>Faculty ID</th><th>Faculty Name</th><th>Year 1</th><th>Year 2</th><th>Year 3</th><th>Year 4</th>';
    tableHtml += '</tr></thead><tbody>';

    reportData.forEach(fac => {
      const yearAllocs = { 1: [], 2: [], 3: [], 4: [] };
      if (fac.allocations && fac.allocations.length > 0) {
        fac.allocations.forEach(a => {
          const yr = a.year;
          if (yearAllocs[yr]) {
            yearAllocs[yr].push(a);
          }
        });
      }

      const getYearString = (allocs) => {
        if (!allocs || allocs.length === 0) return '-';
        return allocs.map(a => {
          const secName = a.sectionName && a.sectionName !== 'N/A' ? ` - Sec ${a.sectionName}` : '';
          return `${cleanSubjectName(a.subjectName)} (${cleanSubjectCode(a.subjectId)}${secName})`;
        }).join('; ');
      };

      tableHtml += '<tr>';
      tableHtml += `<td style="vnd.ms-excel.numberformat:@">${fac.facultyId}</td>`;
      tableHtml += `<td>${fac.name}</td>`;
      tableHtml += `<td>${getYearString(yearAllocs[1])}</td>`;
      tableHtml += `<td>${getYearString(yearAllocs[2])}</td>`;
      tableHtml += `<td>${getYearString(yearAllocs[3])}</td>`;
      tableHtml += `<td>${getYearString(yearAllocs[4])}</td>`;
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
    link.setAttribute("download", `Allocation_Report_${deptVal}_Sem${semVal}_${academicYearVal}.xls`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('Export Success', 'Excel report downloaded successfully', 'success');
  };

  // Custom dropdown trigger for report dept/sem filter
  const customReportDropdown = document.getElementById('custom-dept-dropdown');
  if (customReportDropdown) {
    const triggerBtn = customReportDropdown.querySelector('.custom-dropdown-trigger');
    const menuEl = customReportDropdown.querySelector('.custom-dropdown-menu');
    
    if (triggerBtn && menuEl) {
      triggerBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        const isVisible = menuEl.style.display === 'block';
        menuEl.style.display = isVisible ? 'none' : 'block';
      });
      
      document.addEventListener('click', () => {
        menuEl.style.display = 'none';
      });
    }
  }

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

  async function finalizeAllAllocations() {
    const userConfirmed = await new Promise((resolve) => {
      showConfirm(
        'Finalize & Publish Allocations',
        'Are you sure you want to finalize and publish all current draft subject and section allocations? Once finalized, allocations will become visible to faculty members and cannot be changed.',
        () => resolve(true)
      );

      const overlay = document.getElementById('confirm-modal-overlay');
      if (overlay) {
        const cancelBtn = overlay.querySelector('.btn-ghost');
        const closeBtn = overlay.querySelector('.modal-close');

        const cleanup = () => {
          if (cancelBtn) cancelBtn.removeEventListener('click', onCancel);
          if (closeBtn) closeBtn.removeEventListener('click', onCancel);
        };

        const onCancel = () => {
          cleanup();
          resolve(false);
        };

        if (cancelBtn) cancelBtn.addEventListener('click', onCancel);
        if (closeBtn) closeBtn.addEventListener('click', onCancel);
      }
    });

    if (!userConfirmed) return;

    const btn = document.getElementById('finalize-allocations-btn');
    if (btn) {
      btn.disabled = true;
      btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Finalizing...';
    }

    try {
      await apiRequest('/adminfaculty/finalize-allocations', { method: 'POST' });
      showToast('Allocations Finalized', 'All subject allocations have been finalized and published.', 'success');
      await updateFinalizationStatus();
      await loadAllocationWorkspace();
    } catch (error) {
      showToast('Finalization Failed', error.message || 'Could not finalize allocations', 'error');
      if (btn) {
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-check-double"></i> Finalize Allocations';
      }
    }
  }

  window.finalizeAllAllocations = finalizeAllAllocations;

  window.clearAllAllocationsWorkspace = async function() {
    try {
      const allocatedYears = await apiRequest('/adminfaculty/allocated-years');
      if (!allocatedYears || allocatedYears.length === 0) {
        showToast('No Data', 'No active allocation data found to clear.', 'info');
        return;
      }
      
      let modalOverlay = document.getElementById('clear-allocations-modal-overlay');
      if (modalOverlay) {
        modalOverlay.remove();
      }
      
      modalOverlay = document.createElement('div');
      modalOverlay.id = 'clear-allocations-modal-overlay';
      modalOverlay.className = 'modal-overlay';
      modalOverlay.style.cssText = "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1100; backdrop-filter: blur(4px);";
      
      let checkboxesHtml = '';
      allocatedYears.forEach(year => {
        checkboxesHtml += `
          <label style="display: flex; align-items: center; gap: 10px; margin-bottom: 12px; font-weight: 500; color: #fff; cursor: pointer;">
            <input type="checkbox" class="clear-year-checkbox" value="${year}" checked style="width: 18px; height: 18px; accent-color: var(--secondary);">
            Year ${year} Allocation Data
          </label>
        `;
      });
      
      modalOverlay.innerHTML = `
        <div class="modal-container glass-panel" style="background: rgba(15, 23, 42, 0.95); border: 1px solid var(--panel-border); border-radius: var(--border-radius-lg); padding: 24px; max-width: 420px; width: 90%; box-shadow: var(--glow-shadow);">
          <div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; border-bottom: 1px solid var(--panel-border); padding-bottom: 12px;">
            <h3 class="modal-title" style="margin: 0; font-size: 1.2rem; font-weight: 600; color: var(--secondary);"><i class="fas fa-trash-alt"></i> Clear Allocation Data</h3>
            <button class="modal-close" style="background: transparent; border: none; color: var(--text-muted); cursor: pointer; font-size: 1.1rem;" onclick="document.getElementById('clear-allocations-modal-overlay').remove()"><i class="fas fa-times"></i></button>
          </div>
          <div class="modal-body" style="margin-bottom: 20px;">
            <p style="color: var(--text-muted); font-size: 0.9rem; margin-bottom: 16px; line-height: 1.4;">
              Select the study years to clear subject and section allocations for. Preferences will not be deleted.
            </p>
            <div style="background: rgba(255,255,255,0.02); border: 1px solid var(--panel-border); padding: 16px; border-radius: var(--border-radius-md);">
              ${checkboxesHtml}
            </div>
          </div>
          <div class="modal-footer" style="display: flex; justify-content: flex-end; gap: 12px;">
            <button class="btn btn-ghost" style="padding: 8px 16px; font-size: 0.88rem;" onclick="document.getElementById('clear-allocations-modal-overlay').remove()">Cancel</button>
            <button class="btn btn-danger" id="clear-allocations-confirm-btn" style="padding: 8px 20px; font-size: 0.88rem; background: var(--error); color: white; border: none; border-radius: var(--border-radius-sm); cursor: pointer;">Clear Selected Data</button>
          </div>
        </div>
      `;
      
      document.body.appendChild(modalOverlay);
      
      document.getElementById('clear-allocations-confirm-btn').addEventListener('click', async () => {
        const checkedBoxes = modalOverlay.querySelectorAll('.clear-year-checkbox:checked');
        if (checkedBoxes.length === 0) {
          showToast('Selection Empty', 'Please select at least one year to clear.', 'warning');
          return;
        }
        
        const selectedYears = Array.from(checkedBoxes).map(cb => parseInt(cb.value));
        
        try {
          const params = new URLSearchParams();
          selectedYears.forEach(y => params.append('years', y));
          
          await apiRequest(`/adminfaculty/clear-allocations?${params.toString()}`, {
            method: 'POST'
          });
          
          showToast('Allocations Cleared', `Successfully cleared allocation data for Year(s): ${selectedYears.join(', ')}.`, 'success');
          modalOverlay.remove();
          
          await loadAllocationsWorkspaceData();
          await calculateAllocationStats();
          
          const facSelect = document.getElementById('alloc-faculty-select');
          if (facSelect) {
            facSelect.value = '';
            const loadDetails = document.getElementById('selected-faculty-load-details');
            if (loadDetails) loadDetails.innerHTML = '';
          }
        } catch (err) {
          showToast('Error', err.message || 'Could not clear allocations', 'error');
        }
      });
      
    } catch (error) {
      showToast('Error', error.message || 'Failed to check allocated years', 'error');
    }
  };

  // ----------------------------------------------------
  // PROFILE UPDATE
  // ----------------------------------------------------
  
  const profileForm = document.getElementById('profile-form');
  profileForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const newPassword = profilePasswordInput.value;

    const saveAdminProfile = async () => {
      const updatedData = {
        id: currentUser.id,
        name: profileNameInput.value.trim(),
        email: profileEmailInput.value.trim(),
        password: newPassword,
        profileImage: currentProfileImageBase64
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
          email: response.email,
          profileImage: response.profileImage
        };
        sessionStorage.setItem('currentUser', JSON.stringify(newSession));
        
        document.getElementById('header-user-name').innerText = response.name;
        document.getElementById('header-user-email').innerText = response.email;
        updateHeaderAvatar(newSession);
        
        profilePasswordInput.value = '';

      } catch (error) {
        showToast('Update Failed', error.message || 'Failed to update profile details', 'error');
      }
    };

    if (newPassword && newPassword.trim() !== '') {
      showConfirm('Confirm Password Change', 'Are you sure you want to change your password?', () => {
        saveAdminProfile();
      });
    } else {
      saveAdminProfile();
    }
  });

  window.exportPreferencesToExcel = function() {
    const academicYearInput = document.getElementById('pref-academic-year-input');
    const academicYearVal = academicYearInput ? academicYearInput.value.trim() : '';
    if (!academicYearVal) {
      showToast('Enter Year', 'Please enter an Academic Year first', 'warning');
      return;
    }

    const data = window.preferencesData;
    if (!data || !data.allPreferences || data.allPreferences.length === 0) {
      showToast('No Data', 'No preference data available to export', 'warning');
      return;
    }

    // Filter out orphaned preferences where faculty no longer exists (but keep subjects even if not in subjectsMap)
    let validPrefs = data.allPreferences.filter(p => {
      const fId = p.facultyId.toLowerCase();
      return !!data.facultyMap[fId];
    });

    const deptVal = document.getElementById('pref-dept-filter') ? document.getElementById('pref-dept-filter').value : '';
    const semVal = document.getElementById('pref-sem-filter') ? document.getElementById('pref-sem-filter').value : '';
    const yearVal = document.getElementById('pref-year-filter') ? document.getElementById('pref-year-filter').value : '';

    const getSubjectOrFallback = (subId) => {
      if (!subId) return null;
      const sId = subId.toLowerCase();
      if (data.subjectsMap[sId]) return data.subjectsMap[sId];
      
      // Try to find a subject that starts with sId + "_" (fallback for prefix mapping)
      const prefix = sId + "_";
      const matchedKey = Object.keys(data.subjectsMap).find(key => key.startsWith(prefix));
      if (matchedKey) return data.subjectsMap[matchedKey];
      
      let name = subId;
      let code = subId;
      let acadYear = '';
      if (subId.includes('_')) {
        const parts = subId.split('_');
        code = parts[0];
        acadYear = parts[1];
        name = parts[0] + " (Deleted/Imported)";
      }
      return {
        id: subId,
        name: name,
        dep: deptVal || 'N/A',
        sem: semVal ? Number(semVal) : 1,
        year: yearVal && yearVal !== 'ALL' ? Number(yearVal) : 1,
        academicYear: acadYear
      };
    };

    if (deptVal) {
      validPrefs = validPrefs.filter(p => {
        const sub = getSubjectOrFallback(p.subjectId);
        return sub && getSubjectDeptCode(sub) === deptVal.toUpperCase();
      });
    }
    if (semVal) {
      validPrefs = validPrefs.filter(p => {
        const sub = getSubjectOrFallback(p.subjectId);
        return sub && Number(sub.sem) === Number(semVal);
      });
    }
    if (yearVal && yearVal !== 'ALL') {
      validPrefs = validPrefs.filter(p => {
        const sub = getSubjectOrFallback(p.subjectId);
        return sub && Number(sub.year) === Number(yearVal);
      });
    }

    // Group by faculty
    const grouped = {};
    const facultyOrder = [];
    
    // Sort all preferences by ID (ascending) to maintain selection order
    validPrefs.sort((a, b) => Number(a.id) - Number(b.id));

    validPrefs.forEach(p => {
      if (!grouped[p.facultyId]) {
        grouped[p.facultyId] = [];
        facultyOrder.push(p.facultyId);
      }
      grouped[p.facultyId].push(p);
    });

    // Sort facultyOrder naturally by faculty ID
    facultyOrder.sort((a, b) => a.localeCompare(b, 'en', { numeric: true, sensitivity: 'base' }));

    if (facultyOrder.length === 0) {
      showToast('No Preferences', 'No preferences found to export', 'warning');
      return;
    }

    facultyOrder.forEach(facId => {
      const list = grouped[facId] || [];
      const normal = list.filter(p => p.mock !== true);
      const mock = list.filter(p => p.mock === true);
      grouped[facId] = [...normal, ...mock];
    });

    // Find the maximum number of preferences selected by any faculty
    let maxPrefsCount = 0;
    facultyOrder.forEach(facId => {
      const prefsList = grouped[facId] || [];
      if (prefsList.length > maxPrefsCount) {
        maxPrefsCount = prefsList.length;
      }
    });

    let tableHtml = '<table border="1">';
    
    // Build Header
    tableHtml += '<thead><tr style="background-color: #14B8A6; color: #ffffff; font-weight: bold;">';
    tableHtml += '<th>Faculty ID</th><th>Faculty Name</th><th>Faculty Department</th>';
    for (let i = 1; i <= maxPrefsCount; i++) {
      tableHtml += `<th>Preference ${i}</th>`;
    }
    tableHtml += '</tr></thead><tbody>';

    // Build Rows
    facultyOrder.forEach(facId => {
      const fac = data.facultyMap[facId] || { name: 'Unknown Faculty' };
      const prefsList = grouped[facId] || [];
      
      const depts = [...new Set(prefsList.map(p => {
        const sub = getSubjectOrFallback(p.subjectId);
        return sub ? getSubjectDeptCode(sub) : '';
      }).filter(Boolean))].join('; ');

      tableHtml += '<tr>';
      tableHtml += `<td style="vnd.ms-excel.numberformat:@">${facId}</td>`;
      tableHtml += `<td>${fac.name}</td>`;
      tableHtml += `<td>${depts}</td>`;

      // Fill in preferences
      for (let i = 0; i < maxPrefsCount; i++) {
        if (i < prefsList.length) {
          const p = prefsList[i];
          const sub = getSubjectOrFallback(p.subjectId);
          if (sub) {
            tableHtml += `<td>${sub.name} (${cleanSubjectCode(sub.id)})</td>`;
          } else {
            tableHtml += '<td></td>';
          }
        } else {
          tableHtml += '<td></td>';
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
                <x:Name>Faculty Preferences</x:Name>
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
    link.setAttribute("download", `Faculty_Preferences_${academicYearVal}.xls`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('Export Success', 'Excel spreadsheet downloaded successfully', 'success');
  };

  // Custom dropdown trigger for selections dept/sem filter
  const customPrefDropdown = document.getElementById('custom-pref-dropdown');
  if (customPrefDropdown) {
    const triggerBtn = customPrefDropdown.querySelector('.custom-dropdown-trigger');
    const menuEl = customPrefDropdown.querySelector('.custom-dropdown-menu');
    
    if (triggerBtn && menuEl) {
      triggerBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        const isVisible = menuEl.style.display === 'block';
        menuEl.style.display = isVisible ? 'none' : 'block';
      });
      
      document.addEventListener('click', () => {
        menuEl.style.display = 'none';
      });
    }
  }

  // Custom dropdown trigger for subject directory year/sem filter
  const customSubDropdown = document.getElementById('custom-sub-year-dropdown');
  if (customSubDropdown) {
    const triggerBtn = customSubDropdown.querySelector('.custom-dropdown-trigger');
    const menuEl = customSubDropdown.querySelector('.custom-dropdown-menu');
    
    if (triggerBtn && menuEl) {
      triggerBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        const isVisible = menuEl.style.display === 'block';
        menuEl.style.display = isVisible ? 'none' : 'block';
      });
      
      document.addEventListener('click', () => {
        menuEl.style.display = 'none';
      });
    }
  }

  // Subject category selection query change handler
  const categoryFilter = document.getElementById('sub-category-filter');
  if (categoryFilter) {
    categoryFilter.addEventListener('change', (e) => {
      currentSubjectViewType = e.target.value;
      renderSubjectTable(subjectList);
    });
  }

  window.swapAllocationsBtn = async function() {
    const swapSelect1 = document.getElementById('swap-alloc-1');
    const swapSelect2 = document.getElementById('swap-alloc-2');
    if (!swapSelect1 || !swapSelect2) return;
    
    const id1 = swapSelect1.value;
    const id2 = swapSelect2.value;
    
    if (!id1 || !id2) {
      showToast('Selection Required', 'Please select two allocations to swap.', 'warning');
      return;
    }
    
    if (id1 === id2) {
      showToast('Invalid Swap', 'Cannot swap an allocation with itself.', 'warning');
      return;
    }
    
    try {
      await apiRequest(`/adminfaculty/swap-allocations?id1=${id1}&id2=${id2}`, {
        method: 'POST'
      });
      showToast('Allocations Swapped', 'Selected allocations swapped successfully.', 'success');
      loadSubjectAllocations();
      calculateAllocationStats();
    } catch (error) {
      showToast('Swap Failed', error.message || 'Could not swap allocations.', 'error');
    }
  };

  window.clearAllPreferencesAdmin = async function() {
    const academicYearInput = document.getElementById('pref-academic-year-input');
    const academicYearVal = academicYearInput ? academicYearInput.value.trim() : '';

    if (!academicYearVal) {
      showToast('Error', 'Please enter an Academic Year to clear preferences.', 'error');
      return;
    }

    if (!confirm(`Are you absolutely sure you want to clear/delete ALL faculty preferences for the academic year ${academicYearVal}? This cannot be undone.`)) {
      return;
    }

    try {
      await apiRequest(`/faculty/preferences/clear-by-year?academicYear=${encodeURIComponent(academicYearVal)}`, {
        method: 'DELETE'
      });
      showToast('Success', `All faculty preferences for ${academicYearVal} have been cleared.`, 'success');
      loadFacultySelections();
    } catch (error) {
      showToast('Clear Failed', error.message || 'Could not clear preferences.', 'error');
    }
  };

  // Initial load on startup
  loadFacultySelections();
});