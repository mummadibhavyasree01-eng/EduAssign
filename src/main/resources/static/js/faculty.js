// Faculty Dashboard Logic for EduAssign

document.addEventListener('DOMContentLoaded', async () => {
  // 1. Session Check (allows both FACULTY and ADMIN for preview)
  const currentUser = checkSession(['FACULTY', 'ADMIN']);
  if (!currentUser) return;

  const isFaculty = currentUser.role.toUpperCase() === 'FACULTY';

  // Render header nav user chip
  document.getElementById('nav-user-name').innerText = currentUser.name;
  document.getElementById('nav-user-role').innerText = isFaculty ? `Role: Faculty (${currentUser.id})` : `Role: Admin (Preview)`;
  
  // Render header avatar
  updateHeaderAvatar(currentUser);

  // Sidebar tab switching logic
  const navItems = document.querySelectorAll('.nav-item');
  const sections = document.querySelectorAll('.tab-content');
  
  navItems.forEach(item => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      const target = item.getAttribute('data-target');
      
      navItems.forEach(i => i.classList.remove('active'));
      item.classList.add('active');
      
      sections.forEach(s => {
        if (s.id === target) {
          s.style.display = 'block';
        } else {
          s.style.display = 'none';
        }
      });
    });
  });

  // Initialize default active tab on startup
  const activeNavItem = document.querySelector('.nav-item.active');
  if (activeNavItem) {
    const defaultTarget = activeNavItem.getAttribute('data-target');
    sections.forEach(s => {
      if (s.id === defaultTarget) {
        s.style.display = 'block';
      } else {
        s.style.display = 'none';
      }
    });
  }

  // Show "Back to Admin" button if logged-in user is an Admin visiting this page
  if (!isFaculty) {
    const backToAdminBtn = document.getElementById('back-to-admin');
    if (backToAdminBtn) backToAdminBtn.style.display = 'inline-flex';
  }

  // Populate profile fields
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
      const nameParts = (currentUser.name || 'U').trim().split(/\s+/);
      const nameInitials = nameParts.map(n => n[0]).join('').substring(0, 2).toUpperCase();
      profileAvatarInitials.innerText = nameInitials || 'U';
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
  let subjects = [];
  let facultyPreferences = [];
  let selectedOrder = [];
  let selectedMockOrder = [];
  let originalOrder = [];
  let originalMockOrder = [];
  let selectionWindow = null;
  let currentStep = 1;
  let isSelectionPeriodActive = false;
  let facultyHasMockAllocation = false;
  let allocatedSubjectIds = new Set();
  let isRegularSelectionDisabled = false;
  let isMockSelectionDisabled = false;

  // Profile Form update
  document.getElementById('profile-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const newPassword = profilePasswordInput.value;
    
    const saveProfileDetails = async () => {
      const updatedData = {
        id: currentUser.id,
        name: profileNameInput.value.trim(),
        email: profileEmailInput.value.trim(),
        password: newPassword,
        profileImage: currentProfileImageBase64
      };

      try {
        const response = await apiRequest('/faculty/update', {
          method: 'PUT',
          body: updatedData
        });

        showToast('Profile Saved', 'Profile details updated successfully', 'success');

        if (isFaculty) {
          // Update session storage details
          const newSession = {
            ...currentUser,
            name: response.name,
            email: response.email,
            profileImage: response.profileImage
          };
          sessionStorage.setItem('currentUser', JSON.stringify(newSession));
          
          // Update header display
          document.getElementById('nav-user-name').innerText = response.name;
          updateHeaderAvatar(newSession);
        }
        
        profilePasswordInput.value = '';

      } catch (error) {
        showToast('Save Failed', error.message || 'Could not update profile details', 'error');
      }
    };

    if (newPassword && newPassword.trim() !== '') {
      showConfirm('Confirm Password Change', 'Are you sure you want to change your password?', () => {
        saveProfileDetails();
      });
    } else {
      saveProfileDetails();
    }
  });

  // ----------------------------------------------------
  // DEADLINE TIMER & SELECTION CONTROLS
  // ----------------------------------------------------

  let countdownInterval = null;

  async function checkDeadlineStatus() {
    try {
      const allWindows = await apiRequest('/adminfaculty/deadline');
      const badge = document.getElementById('selection-status-badge');
      const banner = document.getElementById('deadline-banner');
      
      const activeWindows = Array.isArray(allWindows) ? allWindows.filter(w => w.active && [1, 2, 3, 4].includes(w.id) && new Date(w.deadline) > new Date()) : (allWindows && allWindows.active && [1, 2, 3, 4].includes(allWindows.id) && new Date(allWindows.deadline) > new Date() ? [allWindows] : []);
      
      if (activeWindows.length === 0) {
        isSelectionPeriodActive = false;
        badge.className = 'status-badge closed';
        badge.innerText = 'Closed';
        banner.style.display = 'none';
        disableSelectionForm();
        return;
      }

      isSelectionPeriodActive = true;
      badge.className = 'status-badge open';
      badge.innerText = 'Open';
      banner.style.display = 'flex';
      
      // We can use the message from the first active window
      document.getElementById('deadline-banner-message').innerText = activeWindows[0].message || 'Preference selection window is active.';

      enableSelectionForm();

      // Start countdown clock for all active windows
      if (countdownInterval) clearInterval(countdownInterval);
      
      const updateAllCountdowns = () => {
        const timer = document.getElementById('deadline-timer');
        const now = new Date();
        let countdownHTML = '<div style="display:flex; flex-direction:column; gap:6px; margin-top:8px;">';
        let anyActive = false;
        
        activeWindows.forEach(w => {
          const diffMs = new Date(w.deadline) - now;
          let timeText = '';
          if (diffMs <= 0) {
            timeText = 'Ended';
          } else {
            anyActive = true;
            const days = Math.floor(diffMs / (1000 * 60 * 60 * 24));
            const hours = Math.floor((diffMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
            const mins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
            const secs = Math.floor((diffMs % (1000 * 60)) / 1000);
            timeText = `${days}d ${hours}h ${mins}m ${secs}s`;
          }
          countdownHTML += `<div style="font-size:0.9rem; font-weight:700; color:var(--secondary);"><i class="fas fa-clock"></i> Year ${w.year} (${w.department}): ${timeText}</div>`;
        });
        
        countdownHTML += '</div>';
        timer.innerHTML = countdownHTML;
        
        if (!anyActive) {
          clearInterval(countdownInterval);
          checkDeadlineStatus();
        }
      };

      updateAllCountdowns();
      countdownInterval = setInterval(updateAllCountdowns, 1000);

    } catch (error) {
      console.error('Error checking deadline:', error);
    }
  }

  function disableSelectionForm() {
    const nextBtn = document.getElementById('preferences-next-btn');
    if (nextBtn) {
      nextBtn.disabled = true;
      nextBtn.innerText = 'Selection Window Closed';
    }
    const mockSubmitBtn = document.getElementById('mock-submit-btn');
    if (mockSubmitBtn) mockSubmitBtn.disabled = true;
    const confirmBtn = document.getElementById('preferences-confirm-btn');
    if (confirmBtn) confirmBtn.disabled = true;

    document.getElementById('subject-checkboxes-container').classList.add('disabled-list');
    const mockContainer = document.getElementById('mock-checkboxes-container');
    if (mockContainer) {
      mockContainer.classList.add('disabled-list');
    }
  }

  function enableSelectionForm() {
    const nextBtn = document.getElementById('preferences-next-btn');
    if (nextBtn) {
      nextBtn.disabled = false;
      nextBtn.innerHTML = 'Next <i class="fas fa-arrow-right" style="margin-left: 6px;"></i>';
    }
    const mockSubmitBtn = document.getElementById('mock-submit-btn');
    if (mockSubmitBtn) mockSubmitBtn.disabled = false;
    const confirmBtn = document.getElementById('preferences-confirm-btn');
    if (confirmBtn) confirmBtn.disabled = false;

    document.getElementById('subject-checkboxes-container').classList.remove('disabled-list');
    const mockContainer = document.getElementById('mock-checkboxes-container');
    if (mockContainer) {
      mockContainer.classList.remove('disabled-list');
    }
  }

  // ----------------------------------------------------
  // SUBJECT PREFERENCE LISTING & SAVING
  // ----------------------------------------------------

  // ----------------------------------------------------
  // SUBJECT PREFERENCE LISTING & SAVING (WIZARD FLOW)
  // ----------------------------------------------------

  async function loadSubjectPreferences() {
    const listContainer = document.getElementById('subject-checkboxes-container');
    listContainer.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 30px;"><i class="fas fa-spinner fa-spin"></i> Loading subjects checklist...</div>';

     try {
       const res = await Promise.all([
         apiRequest('/subject/viewAll'),
         apiRequest(`/faculty/preferences/${currentUser.id}`),
         apiRequest('/adminfaculty/deadline'),
         apiRequest(`/faculty/has-mock-allocation/${currentUser.id}`).catch(err => {
           console.error('Failed to check past mock allocation status:', err);
           return false;
         }),
         apiRequest(`/faculty/allocations/${currentUser.id}`).catch(() => null)
       ]);
       const allSubjects = res[0];
       const preferences = res[1];
       selectionWindow = res[2];
       facultyHasMockAllocation = res[3];
       const myAllocations = res[4];

      subjects = Array.isArray(allSubjects) ? allSubjects : [];
      facultyPreferences = Array.isArray(preferences) ? preferences : [];

      const activeWindows = Array.isArray(res[2]) ? res[2].filter(w => w.active && [1, 2, 3, 4].includes(w.id)) : (res[2] && res[2].active && [1, 2, 3, 4].includes(res[2].id) ? [res[2]] : []);
      selectionWindow = activeWindows[0] || null;

      let isAlreadyFullyAllocated = false;
      isRegularSelectionDisabled = false;
      isMockSelectionDisabled = false;
      allocatedSubjectIds.clear();
      if (selectionWindow && myAllocations) {
        let subAllocList = [];
        if (Array.isArray(myAllocations)) {
          subAllocList = myAllocations;
        } else {
          subAllocList = Array.isArray(myAllocations.subjectAllocations) ? myAllocations.subjectAllocations : [];
        }

        const windowSubs = subjects.filter(s => {
          return activeWindows.some(win => 
            (win.academicYear == null || (s.academicYear && s.academicYear.toLowerCase() === win.academicYear.toLowerCase())) &&
            (win.department == null || (s.dep && s.dep.toUpperCase() === win.department.toUpperCase())) &&
            (win.year == null || (Number(s.year) === Number(win.year))) &&
            (win.sem == null || (Number(s.sem) === Number(win.sem)))
          );
        });
        const windowSubIds = new Set(windowSubs.map(s => s.id.toUpperCase()));

        const myWindowAllocs = subAllocList.filter(a => windowSubIds.has(a.subjectId.toUpperCase()));
        allocatedSubjectIds = new Set(myWindowAllocs.map(a => a.subjectId.toUpperCase()));

        let regularAllocatedCount = 0;
        let mockAllocatedCount = 0;
        subAllocList.forEach(alloc => {
          const sub = allSubjects.find(s => s.id.toUpperCase() === alloc.subjectId.toUpperCase());
          if (sub) {
            if (sub.mock) {
              mockAllocatedCount++;
            } else {
              regularAllocatedCount++;
            }
          }
        });

        const maxSubjectsLimit = (selectionWindow && selectionWindow.maxSubjectsAllocated) ? Number(selectionWindow.maxSubjectsAllocated) : 3;
        let maxRegularAllocatedLimit = maxSubjectsLimit;
        let maxMockAllocatedLimit = 0;
        if (maxSubjectsLimit === 3) {
          maxRegularAllocatedLimit = 2;
          maxMockAllocatedLimit = 1;
        }

        const regularLimitReached = (regularAllocatedCount >= maxRegularAllocatedLimit);
        const mockLimitReached = (mockAllocatedCount >= maxMockAllocatedLimit);

        isRegularSelectionDisabled = regularLimitReached;
        isMockSelectionDisabled = mockLimitReached;

        if (subAllocList.length >= maxSubjectsLimit || (regularLimitReached && mockLimitReached)) {
          isAlreadyFullyAllocated = true;
        }
      }

      if (activeWindows.length > 0) {
        subjects = subjects.filter(s => {
          return activeWindows.some(win => 
            (win.academicYear == null || (s.academicYear && s.academicYear.toLowerCase() === win.academicYear.toLowerCase())) &&
            (win.department == null || (s.dep && s.dep.toUpperCase() === win.department.toUpperCase())) &&
            (win.year == null || (Number(s.year) === Number(win.year))) &&
            (win.sem == null || (Number(s.sem) === Number(win.sem)))
          );
        });
      } else {
        subjects = [];
      }

      if (isAlreadyFullyAllocated) {
        disableSelectionForm();
        const nextBtn = document.getElementById('preferences-next-btn');
        if (nextBtn) {
          nextBtn.disabled = true;
          nextBtn.innerText = 'Allocations Already Completed';
        }
        showToast('Selection Disabled', 'You have already been allocated subjects for this period.', 'info');

        // Hide form and search
        const prefForm = document.getElementById('preferences-form');
        if (prefForm) prefForm.style.display = 'none';
        const searchBox = document.querySelector('.search-box-wrapper');
        if (searchBox) searchBox.style.display = 'none';

        // Show completion message
        let msgDiv = document.getElementById('fully-allocated-message');
        if (!msgDiv) {
          msgDiv = document.createElement('div');
          msgDiv.id = 'fully-allocated-message';
          msgDiv.style.padding = '30px';
          msgDiv.style.textAlign = 'center';
          msgDiv.style.background = 'rgba(20, 184, 166, 0.05)';
          msgDiv.style.border = '1px solid var(--primary)';
          msgDiv.style.borderRadius = 'var(--border-radius-md)';
          msgDiv.style.marginTop = '10px';
          msgDiv.innerHTML = `
            <i class="fas fa-check-circle" style="font-size: 2.5rem; color: var(--primary); margin-bottom: 15px; display: block;"></i>
            <h4 style="color: var(--text-main); margin-bottom: 10px; font-weight: 600;">Allocations Completed</h4>
            <p style="font-size: 0.88rem; color: var(--text-muted); line-height: 1.5;">You have already been allocated your maximum limit of subjects. There is no need to select any subject preferences.</p>
          `;
          const selectionCard = document.querySelector('.selection-card');
          if (selectionCard) selectionCard.appendChild(msgDiv);
        } else {
          msgDiv.style.display = 'block';
        }
      } else {
        // Reset visibility of form and search
        const prefForm = document.getElementById('preferences-form');
        if (prefForm) prefForm.style.display = 'block';
        const searchBox = document.querySelector('.search-box-wrapper');
        if (searchBox) searchBox.style.display = 'block';
        const msgDiv = document.getElementById('fully-allocated-message');
        if (msgDiv) msgDiv.style.display = 'none';
      }

      // Filter facultyPreferences to only keep preferences whose subjectId belongs to the currently active subjects
      facultyPreferences = facultyPreferences.filter(p => subjects.some(s => s.id === p.subjectId));

      // Separate normal and mock selections
      selectedOrder = facultyPreferences.filter(p => !p.mock).map(p => p.subjectId);
      selectedMockOrder = facultyPreferences.filter(p => p.mock).map(p => p.subjectId);
      originalOrder = [...selectedOrder];
      originalMockOrder = [...selectedMockOrder];

      if (facultyPreferences.length > 0) {
        currentStep = 3;
        prefSelectionSection.style.display = 'none';
        mockSection.style.display = 'none';
        previewSection.style.display = 'block';
        if (searchBoxWrapper) searchBoxWrapper.style.display = 'none';
        renderPreviewTable();
      } else {
        currentStep = 1;
        prefSelectionSection.style.display = 'block';
        mockSection.style.display = 'none';
        previewSection.style.display = 'none';
        if (searchBoxWrapper) searchBoxWrapper.style.display = 'block';
        renderSubjectPreferencesList(subjects);
      }
      updateStepIndicators();
      
    } catch (error) {
      listContainer.innerHTML = '<div style="text-align: center; color: var(--error); padding: 20px;">Failed to load subjects directory</div>';
    }
  }

  function updateIndicators() {
    const subjectItems = document.querySelectorAll('#subject-checkboxes-container .subject-item');
    subjectItems.forEach(item => {
      const checkbox = item.querySelector('input[type="checkbox"]');
      const indicator = item.querySelector('.pref-index-indicator');
      if (!checkbox || !indicator) return;
      const subId = checkbox.value;
      const index = selectedOrder.indexOf(subId);
      
      if (index !== -1) {
        checkbox.checked = true;
        item.classList.add('selected');
        indicator.innerText = index + 1;
      } else {
        checkbox.checked = false;
        item.classList.remove('selected');
        indicator.innerText = '';
      }
    });

    const nextBtn = document.getElementById('preferences-next-btn');
    if (nextBtn) {
      nextBtn.disabled = !isRegularSelectionDisabled && selectedOrder.length === 0;
    }

    renderRankedPreferences();
  }

  function renderRankedPreferences() {
    const rankedSection = document.getElementById('ranked-preferences-list');
    const container = document.getElementById('ranked-items-container');
    if (!rankedSection || !container) return;

    if (selectedOrder.length === 0) {
      rankedSection.style.display = 'none';
      container.innerHTML = '';
      return;
    }

    rankedSection.style.display = 'block';
    container.innerHTML = '';

    selectedOrder.forEach((subId, idx) => {
      const sub = subjects.find(s => s.id === subId);
      const name = sub ? sub.name : subId;
      const div = document.createElement('div');
      div.style.display = 'flex';
      div.style.alignItems = 'center';
      div.style.gap = '8px';
      div.style.fontSize = '0.85rem';
      div.style.color = 'var(--text-main)';
      div.innerHTML = `
        <span style="font-weight: 700; color: var(--secondary); background: rgba(20, 184, 166, 0.15); border-radius: 50%; width: 18px; height: 18px; display: inline-flex; align-items: center; justify-content: center; font-size: 0.7rem;">${idx + 1}</span>
        <span>${name} <code style="color: var(--text-muted); font-size: 0.78rem;">(${subId})</code></span>
      `;
      container.appendChild(div);
    });
  }

  function renderSubjectPreferencesList(list) {
    const listContainer = document.getElementById('subject-checkboxes-container');
    listContainer.innerHTML = '';

    const regularSubjects = list.filter(s => s.mock !== true);

    if (regularSubjects.length === 0) {
      listContainer.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 30px;">No regular subjects available.</div>';
      return;
    }

    // Group by year
    const grouped = {};
    regularSubjects.forEach(sub => {
      const yr = sub.year;
      if (!grouped[yr]) grouped[yr] = [];
      grouped[yr].push(sub);
    });

    const years = Object.keys(grouped).sort((a,b) => Number(a) - Number(b));

    years.forEach(y => {
      const yearHeader = document.createElement('div');
      yearHeader.className = 'year-divider';
      yearHeader.style.padding = '8px 12px';
      yearHeader.style.marginTop = '16px';
      yearHeader.style.marginBottom = '8px';
      yearHeader.style.background = 'rgba(20, 184, 166, 0.06)';
      yearHeader.style.borderLeft = '4px solid var(--secondary)';
      yearHeader.style.borderRadius = '0 4px 4px 0';
      yearHeader.style.fontWeight = '700';
      yearHeader.style.fontSize = '0.9rem';
      yearHeader.style.color = 'var(--secondary)';
      yearHeader.innerText = `Year ${y}`;
      listContainer.appendChild(yearHeader);

      const sortedSubs = grouped[y].sort((a, b) => a.id.localeCompare(b.id));

      sortedSubs.forEach(sub => {
        const isAllocated = allocatedSubjectIds.has(sub.id.toUpperCase());
        const isChecked = selectedOrder.includes(sub.id);
        const isSelDisabled = isRegularSelectionDisabled || isAllocated;
        
        const item = document.createElement('div');
        item.className = `subject-item ${isChecked ? 'selected' : ''} ${isSelDisabled ? 'allocated-disabled' : ''}`;
        if (isSelDisabled) {
          item.style.opacity = '0.7';
          item.style.cursor = 'not-allowed';
        }
        
        item.innerHTML = `
          <input type="checkbox" id="chk-${sub.id}" value="${sub.id}" ${isChecked ? 'checked' : ''} ${isSelDisabled ? 'disabled' : ''} style="display: none;">
          <div class="pref-index-indicator"></div>
          <div class="subject-details">
            <span>${sub.name} <code style="color: var(--text-muted); font-size: 0.82rem; font-weight: normal; margin-left: 6px;">${cleanSubjectCode(sub.id)}</code>
              ${isAllocated ? '<span class="status-badge expired" style="font-size: 0.72rem; padding: 2px 6px; margin-left: 8px; font-weight: bold;"><i class="fas fa-check-double"></i> Allocated</span>' : ''}
              ${isRegularSelectionDisabled && !isAllocated ? '<span class="status-badge progress" style="font-size: 0.72rem; padding: 2px 6px; margin-left: 8px; font-weight: bold;"><i class="fas fa-lock"></i> Regular Completed</span>' : ''}
            </span>
            <small>Year ${sub.year} Sem ${sub.sem} • Dept: ${sub.dep} • Regulation: ${sub.regulation}</small>
          </div>
        `;

        const checkbox = item.querySelector('input[type="checkbox"]');
        
        checkbox.addEventListener('change', () => {
          if (checkbox.checked) {
            if (selectionWindow && selectionWindow.maxRegularPreferences) {
              const sameYearSelectedCount = selectedOrder.filter(id => {
                const s = subjects.find(x => x.id === id);
                return s && Number(s.year) === Number(sub.year);
              }).length;
              if (sameYearSelectedCount >= selectionWindow.maxRegularPreferences) {
                checkbox.checked = false;
                showToast('Limit Exceeded', `You can select a maximum of ${selectionWindow.maxRegularPreferences} regular preferences for Year ${sub.year}.`, 'warning');
                return;
              }
            }
            if (!selectedOrder.includes(sub.id)) {
              selectedOrder.push(sub.id);
            }
          } else {
            selectedOrder = selectedOrder.filter(id => id !== sub.id);
          }
          updateIndicators();
        });

        item.addEventListener('click', (e) => {
          if (isSelDisabled) return;
          if (e.target !== checkbox && !e.target.closest('label') && isSelectionPeriodActive) {
            checkbox.checked = !checkbox.checked;
            checkbox.dispatchEvent(new Event('change'));
          }
        });

        listContainer.appendChild(item);
      });
    });

    updateIndicators();
  }

  function renderMockSubjectList(list) {
    const container = document.getElementById('mock-checkboxes-container');
    if (!container) return;

    container.innerHTML = '';

    const mockSubjects = list.filter(s => s.mock === true);

    if (mockSubjects.length === 0) {
      container.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 30px;">No mock subjects available.</div>';
      return;
    }

    const sortedList = [...mockSubjects].sort((a, b) => {
      if (a.year !== b.year) return a.year - b.year;
      if (a.sem !== b.sem) return a.sem - b.sem;
      return a.id.localeCompare(b.id);
    });

    sortedList.forEach(sub => {
      const isAllocated = allocatedSubjectIds.has(sub.id.toUpperCase());
      const isChecked = selectedMockOrder.includes(sub.id);
      const isSelDisabled = isMockSelectionDisabled || isAllocated;

      const item = document.createElement('div');
      item.className = `subject-item ${isChecked ? 'selected' : ''} ${isSelDisabled ? 'allocated-disabled' : ''}`;
      if (isSelDisabled) {
        item.style.opacity = '0.7';
        item.style.cursor = 'not-allowed';
      }
      
      item.innerHTML = `
        <input type="checkbox" id="chk-mock-${sub.id}" value="${sub.id}" ${isChecked ? 'checked' : ''} ${isSelDisabled ? 'disabled' : ''} style="display: none;">
        <div class="mock-pref-indicator" style="font-weight: 700; color: var(--primary); font-size: 0.85rem; margin-right: 12px; min-width: 20px; text-align: center;">${isChecked ? 'Mock' : ''}</div>
        <div class="subject-details">
          <span>${sub.name} <code style="color: var(--text-muted); font-size: 0.82rem; font-weight: normal; margin-left: 6px;">${cleanSubjectCode(sub.id)}</code>
            ${isAllocated ? '<span class="status-badge expired" style="font-size: 0.72rem; padding: 2px 6px; margin-left: 8px; font-weight: bold;"><i class="fas fa-check-double"></i> Allocated</span>' : ''}
            ${isMockSelectionDisabled && !isAllocated ? '<span class="status-badge progress" style="font-size: 0.72rem; padding: 2px 6px; margin-left: 8px; font-weight: bold;"><i class="fas fa-lock"></i> Mock Completed</span>' : ''}
          </span>
          <small>Year ${sub.year} Sem ${sub.sem} • Dept: ${sub.dep} • Regulation: ${sub.regulation}</small>
        </div>
      `;

      const checkbox = item.querySelector('input[type="checkbox"]');
      const indicator = item.querySelector('.mock-pref-indicator');

      checkbox.addEventListener('change', () => {
        if (checkbox.checked) {
          if (selectionWindow && selectionWindow.maxMockPreferences) {
            const sameYearMockSelectedCount = selectedMockOrder.filter(id => {
              const s = subjects.find(x => x.id === id);
              return s && Number(s.year) === Number(sub.year);
            }).length;
            if (sameYearMockSelectedCount >= selectionWindow.maxMockPreferences) {
              checkbox.checked = false;
              showToast('Limit Exceeded', `You can select a maximum of ${selectionWindow.maxMockPreferences} mock preferences for Year ${sub.year}.`, 'warning');
              return;
            }
          }
          if (!selectedMockOrder.includes(sub.id)) {
            selectedMockOrder.push(sub.id);
          }
          item.classList.add('selected');
          indicator.innerText = 'Mock';
        } else {
          selectedMockOrder = selectedMockOrder.filter(id => id !== sub.id);
          item.classList.remove('selected');
          indicator.innerText = '';
        }
      });

      item.addEventListener('click', (e) => {
        if (isSelDisabled) return;
        if (e.target !== checkbox && !e.target.closest('label') && isSelectionPeriodActive) {
          checkbox.checked = !checkbox.checked;
          checkbox.dispatchEvent(new Event('change'));
        }
      });

      container.appendChild(item);
    });
  }

  function hasChanges() {
    if (selectedOrder.length !== originalOrder.length) return true;
    if (selectedMockOrder.length !== originalMockOrder.length) return true;
    for (let i = 0; i < selectedOrder.length; i++) {
      if (selectedOrder[i] !== originalOrder[i]) return true;
    }
    for (let i = 0; i < selectedMockOrder.length; i++) {
      if (selectedMockOrder[i] !== originalMockOrder[i]) return true;
    }
    return false;
  }

  function renderPreviewTable() {
    const tbody = document.getElementById('preview-table-body');
    if (!tbody) return;

    tbody.innerHTML = '';

    // Normal preferences
    selectedOrder.forEach((subId, idx) => {
      const sub = subjects.find(s => s.id === subId) || { name: 'Unknown Subject', sem: '', dep: '' };
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td style="padding: 10px; border-bottom: 1px solid var(--panel-border); font-weight: 600; color: var(--secondary); font-size: 0.85rem;">
          <span style="font-weight: 700; color: var(--secondary); background: rgba(20, 184, 166, 0.15); border-radius: 50%; width: 18px; height: 18px; display: inline-flex; align-items: center; justify-content: center; font-size: 0.7rem;">${idx + 1}</span>
        </td>
        <td style="padding: 10px; border-bottom: 1px solid var(--panel-border); font-size: 0.85rem;">
          <strong>${sub.name}</strong> <code style="color: var(--text-muted); font-size: 0.78rem;">(${subId})</code>
        </td>
        <td style="padding: 10px; border-bottom: 1px solid var(--panel-border); font-size: 0.85rem;">
          <span class="badge" style="background: rgba(20, 184, 166, 0.1); color: var(--secondary); font-size: 0.75rem; padding: 2px 6px; border-radius: 4px;">Preference</span>
        </td>
      `;
      tbody.appendChild(tr);
    });

    // Mock preferences
    selectedMockOrder.forEach(subId => {
      const sub = subjects.find(s => s.id === subId) || { name: 'Unknown Subject', sem: '', dep: '' };
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td style="padding: 10px; border-bottom: 1px solid var(--panel-border); font-weight: 600; color: var(--primary); font-size: 0.85rem;">
          <span style="font-weight: 700; color: var(--primary); background: rgba(99, 102, 241, 0.15); border-radius: 50%; width: 18px; height: 18px; display: inline-flex; align-items: center; justify-content: center; font-size: 0.7rem;">M</span>
        </td>
        <td style="padding: 10px; border-bottom: 1px solid var(--panel-border); font-size: 0.85rem;">
          <strong>${sub.name}</strong> <code style="color: var(--text-muted); font-size: 0.78rem;">(${subId})</code>
        </td>
        <td style="padding: 10px; border-bottom: 1px solid var(--panel-border); font-size: 0.85rem;">
          <span class="badge" style="background: rgba(99, 102, 241, 0.1); color: var(--primary); font-size: 0.75rem; padding: 2px 6px; border-radius: 4px;">Mock</span>
        </td>
      `;
      tbody.appendChild(tr);
    });

    // Disable confirm submission button if there are no changes
    checkConfirmButtonStatus();
  }

  function checkConfirmButtonStatus() {
    const confirmBtn = document.getElementById('preferences-confirm-btn');
    if (!confirmBtn) return;
    
    if (!hasChanges()) {
      confirmBtn.disabled = true;
      confirmBtn.innerHTML = '<i class="fas fa-check-circle"></i> Saved (No Changes)';
      confirmBtn.style.opacity = '0.6';
      confirmBtn.style.cursor = 'not-allowed';
    } else {
      confirmBtn.disabled = false;
      confirmBtn.innerHTML = '<i class="fas fa-check-circle"></i> Confirm Submission';
      confirmBtn.style.opacity = '1';
      confirmBtn.style.cursor = 'pointer';
    }
  }

  function updateStepIndicators() {
    const ind1 = document.getElementById('step-ind-1');
    const ind2 = document.getElementById('step-ind-2');
    const ind3 = document.getElementById('step-ind-3');
    const line1 = document.getElementById('step-line-1');
    const line2 = document.getElementById('step-line-2');
    if (!ind1 || !ind2 || !ind3 || !line1 || !line2) return;

    const setInactive = (ind, num) => {
      num.style.background = 'rgba(255,255,255,0.1)';
      num.style.color = 'var(--text-muted)';
      ind.style.color = 'var(--text-muted)';
      ind.style.fontWeight = '500';
    };
    const setActive = (ind, num, colorClass) => {
      num.style.background = `var(--${colorClass})`;
      num.style.color = 'var(--bg-dark)';
      ind.style.color = `var(--${colorClass})`;
      ind.style.fontWeight = '600';
    };

    const num1 = ind1.querySelector('.step-num');
    const num2 = ind2.querySelector('.step-num');
    const num3 = ind3.querySelector('.step-num');

    setInactive(ind1, num1);
    setInactive(ind2, num2);
    setInactive(ind3, num3);
    line1.style.background = 'rgba(255,255,255,0.08)';
    line2.style.background = 'rgba(255,255,255,0.08)';

    if (currentStep === 1) {
      setActive(ind1, num1, 'secondary');
    } else if (currentStep === 2) {
      setActive(ind1, num1, 'secondary');
      setActive(ind2, num2, 'secondary');
      line1.style.background = 'var(--secondary)';
    } else if (currentStep === 3) {
      setActive(ind1, num1, 'secondary');
      setActive(ind2, num2, 'secondary');
      setActive(ind3, num3, 'primary');
      line1.style.background = 'var(--secondary)';
      line2.style.background = 'var(--secondary)';
    }
  }

  // Client-side Subject Search
  document.getElementById('subject-search').addEventListener('input', (e) => {
    const q = e.target.value.toLowerCase().trim();
    const filtered = subjects.filter(s => 
      s.name.toLowerCase().includes(q) || 
      s.id.toLowerCase().includes(q) ||
      s.dep.toLowerCase().includes(q)
    );
    if (currentStep === 1) {
      renderSubjectPreferencesList(filtered);
    } else if (currentStep === 2) {
      renderMockSubjectList(filtered);
    }
  });

  // Wizard Navigation Bindings
  const nextBtn = document.getElementById('preferences-next-btn');
  const mockBackBtn = document.getElementById('mock-back-btn');
  const mockSubmitBtn = document.getElementById('mock-submit-btn');
  const previewBackBtn = document.getElementById('preview-back-btn');

  const prefSelectionSection = document.getElementById('preferences-selection-section');
  const mockSection = document.getElementById('mock-section');
  const previewSection = document.getElementById('preview-section');
  const searchBoxWrapper = document.querySelector('.search-box-wrapper');

  if (nextBtn) {
    nextBtn.addEventListener('click', () => {
      if (!isRegularSelectionDisabled && selectedOrder.length === 0) {
        showToast('Selection Empty', 'Please select at least one subject preference.', 'warning');
        return;
      }
      
      const hasMockAvailable = subjects.some(s => s.mock === true);
      if (facultyHasMockAllocation || !hasMockAvailable) {
        currentStep = 3;
        prefSelectionSection.style.display = 'none';
        mockSection.style.display = 'none';
        previewSection.style.display = 'block';
        if (searchBoxWrapper) searchBoxWrapper.style.display = 'none';
        selectedMockOrder = [];
        renderPreviewTable();
      } else {
        currentStep = 2;
        prefSelectionSection.style.display = 'none';
        mockSection.style.display = 'block';
        previewSection.style.display = 'none';
        document.getElementById('subject-search').value = '';
        
        const mockDesc = document.querySelector('#mock-section p');
        if (mockDesc) {
          mockDesc.innerHTML = 'Please select at least one subject for mock allocation below <strong style="color: var(--warning);">(compulsory)</strong>.';
        }
        
        renderMockSubjectList(subjects);
      }
      updateStepIndicators();
    });
  }

  if (mockBackBtn) {
    mockBackBtn.addEventListener('click', () => {
      currentStep = 1;
      prefSelectionSection.style.display = 'block';
      mockSection.style.display = 'none';
      previewSection.style.display = 'none';
      document.getElementById('subject-search').value = '';
      renderSubjectPreferencesList(subjects);
      updateStepIndicators();
    });
  }

  if (mockSubmitBtn) {
    mockSubmitBtn.addEventListener('click', () => {
      const hasMockAvailable = subjects.some(s => s.mock === true);
      if (selectedMockOrder.length === 0 && !facultyHasMockAllocation && hasMockAvailable) {
        showToast('Mock Required', 'Every faculty must select at least one subject for Mock.', 'warning');
        return;
      }
      currentStep = 3;
      prefSelectionSection.style.display = 'none';
      mockSection.style.display = 'none';
      previewSection.style.display = 'block';
      if (searchBoxWrapper) searchBoxWrapper.style.display = 'none';
      renderPreviewTable();
      updateStepIndicators();
    });
  }

  if (previewBackBtn) {
    previewBackBtn.addEventListener('click', () => {
      currentStep = 1;
      prefSelectionSection.style.display = 'block';
      mockSection.style.display = 'none';
      previewSection.style.display = 'none';
      if (searchBoxWrapper) searchBoxWrapper.style.display = 'block';
      document.getElementById('subject-search').value = '';
      renderSubjectPreferencesList(subjects);
      updateStepIndicators();
    });
  }

  // Submit Preferences (Confirm Submission)
  document.getElementById('preferences-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    if (!isSelectionPeriodActive) {
      showToast('Action Blocked', 'Subject selection window is closed', 'error');
      return;
    }

    const hasMockAvailable = subjects.some(s => s.mock === true);
    if (selectedMockOrder.length === 0 && !facultyHasMockAllocation && hasMockAvailable) {
      showToast('Mock Required', 'Every faculty must select at least one subject for Mock.', 'warning');
      return;
    }

    const confirmBtn = document.getElementById('preferences-confirm-btn');
    if (confirmBtn) {
      confirmBtn.disabled = true;
      confirmBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
    }

    // Map combined list
    const payload = [];
    selectedOrder.forEach(id => {
      payload.push({ subjectId: id, mock: false });
    });
    selectedMockOrder.forEach(id => {
      payload.push({ subjectId: id, mock: true });
    });

    try {
      await apiRequest(`/faculty/preferences?facultyId=${currentUser.id}`, {
        method: 'POST',
        body: payload
      });
      showToast('Submission Confirmed', 'Preferences saved successfully', 'success');
      
      const updatedPrefs = await apiRequest(`/faculty/preferences/${currentUser.id}`);
      facultyPreferences = Array.isArray(updatedPrefs) ? updatedPrefs : [];
      facultyPreferences = facultyPreferences.filter(p => subjects.some(s => s.id === p.subjectId));
      selectedOrder = facultyPreferences.filter(p => !p.mock).map(p => p.subjectId);
      selectedMockOrder = facultyPreferences.filter(p => p.mock).map(p => p.subjectId);
      originalOrder = [...selectedOrder];
      originalMockOrder = [...selectedMockOrder];
      
      // Keep showing Step 3 Preview showing the submitted preferences
      currentStep = 3;
      prefSelectionSection.style.display = 'none';
      mockSection.style.display = 'none';
      previewSection.style.display = 'block';
      if (searchBoxWrapper) searchBoxWrapper.style.display = 'none';
      
      renderPreviewTable();
      
    } catch (error) {
      showToast('Submission Failed', error.message || 'Error saving selections', 'error');
    } finally {
      if (confirmBtn) {
        confirmBtn.innerHTML = '<i class="fas fa-check-circle"></i> Confirm Submission';
        checkConfirmButtonStatus();
      }
    }
  });

  // ----------------------------------------------------
  // LOAD ALLOCATIONS
  // ----------------------------------------------------

  async function loadAllocatedSubjects() {
    const container = document.getElementById('allocation-list-container');
    container.innerHTML = '<div style="text-align: center; padding: 20px; color: var(--text-muted);"><i class="fas fa-spinner fa-spin"></i> Checking assignments...</div>';

    try {
      const [allocations, allSubjects] = await Promise.all([
        apiRequest(`/faculty/allocations/${currentUser.id}`),
        apiRequest('/subject/viewAll')
      ]);

      const subMap = {};
      if (Array.isArray(allSubjects)) {
        allSubjects.forEach(s => subMap[s.id] = s);
      }

      let subAllocList = [];
      let secAllocList = [];

      if (allocations) {
        if (Array.isArray(allocations)) {
          secAllocList = allocations;
        } else {
          subAllocList = Array.isArray(allocations.subjectAllocations) ? allocations.subjectAllocations : [];
          secAllocList = Array.isArray(allocations.sectionAllocations) ? allocations.sectionAllocations : [];
        }
      }

      if (subAllocList.length === 0 && secAllocList.length === 0) {
        container.innerHTML = `
          <div class="no-allocations">
            <i class="fas fa-calendar-alt"></i>
            <p>No subjects allocated yet. Final subject allocations will be shown here once allocations are finalized by Admin.</p>
          </div>
        `;
        return;
      }

      container.innerHTML = '';

      // First render Section Allocations (with specific Section names)
      secAllocList.forEach(alloc => {
        const sub = subMap[alloc.subjectId] || { name: 'Subject: ' + cleanSubjectCode(alloc.subjectId), year: '?', dep: '?' };
        const item = document.createElement('div');
        item.className = 'allocation-item';
        item.innerHTML = `
          <div class="alloc-details">
            <h5>${sub.name}</h5>
            <p>Code: ${cleanSubjectCode(alloc.subjectId)} ${sub.year ? '• Year ' + sub.year + ' (' + sub.dep + ')' : ''}</p>
          </div>
          <span class="alloc-badge">Section ${alloc.sectionName}</span>
        `;
        container.appendChild(item);
      });

      // Next render Subject Allocations for subjects not yet assigned a section
      const secSubjectIds = new Set(secAllocList.map(a => a.subjectId));
      subAllocList.forEach(alloc => {
        if (!secSubjectIds.has(alloc.subjectId)) {
          const sub = subMap[alloc.subjectId] || { name: 'Subject: ' + cleanSubjectCode(alloc.subjectId), year: '?', dep: '?' };
          const item = document.createElement('div');
          item.className = 'allocation-item';
          item.innerHTML = `
            <div class="alloc-details">
              <h5>${sub.name}</h5>
              <p>Code: ${cleanSubjectCode(alloc.subjectId)} ${sub.year ? '• Year ' + sub.year + ' (' + sub.dep + ')' : ''}</p>
            </div>
            <span class="alloc-badge" style="background: rgba(99,102,241,0.15); color: var(--primary);">Allocated Subject</span>
          `;
          container.appendChild(item);
        }
      });

    } catch (error) {
      container.innerHTML = '<div style="text-align: center; color: var(--error); padding: 20px;">Error loading allocated classes</div>';
    }
  }

  // Initial load calls
  await checkDeadlineStatus();
  await loadSubjectPreferences();
  loadAllocatedSubjects();
});
