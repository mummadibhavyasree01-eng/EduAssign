// Faculty Dashboard Logic for EduAssign

document.addEventListener('DOMContentLoaded', async () => {
  // 1. Session Check (allows both FACULTY and ADMIN for preview)
  const currentUser = checkSession(['FACULTY', 'ADMIN']);
  if (!currentUser) return;

  const isFaculty = currentUser.role.toUpperCase() === 'FACULTY';

  // Render header nav user chip
  document.getElementById('nav-user-name').innerText = currentUser.name;
  document.getElementById('nav-user-role').innerText = isFaculty ? `Role: Faculty (${currentUser.id})` : `Role: Admin (Preview)`;

  // Show "Back to Admin" button and top-left arrow if logged-in user is an Admin
  const brandHeader = document.getElementById('navbar-brand-header');
  const brandBackArrow = document.getElementById('brand-back-arrow');
  const brandCapIcon = document.getElementById('brand-cap-icon');

  if (!isFaculty) {
    document.getElementById('back-to-admin').style.display = 'inline-flex';
    if (brandBackArrow) brandBackArrow.style.display = 'inline-block';
    if (brandCapIcon) brandCapIcon.style.display = 'none';
    
    if (brandHeader) {
      brandHeader.addEventListener('click', () => {
        window.location.href = 'admin.html';
      });
    }
  }

  // Populate profile fields
  const profileNameInput = document.getElementById('profile-name');
  const profileEmailInput = document.getElementById('profile-email');
  const profilePasswordInput = document.getElementById('profile-password');

  profileNameInput.value = currentUser.name;
  profileEmailInput.value = currentUser.email;

  // State caches
  let subjects = [];
  let facultyPreferences = [];
  let selectedOrder = [];
  let isSelectionPeriodActive = false;

  // Profile Form update
  document.getElementById('profile-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const updatedData = {
      id: currentUser.id,
      name: profileNameInput.value.trim(),
      email: profileEmailInput.value.trim(),
      password: profilePasswordInput.value
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
          email: response.email
        };
        sessionStorage.setItem('currentUser', JSON.stringify(newSession));
        
        // Update header display
        document.getElementById('nav-user-name').innerText = response.name;
      }
      
      profilePasswordInput.value = '';

    } catch (error) {
      showToast('Save Failed', error.message || 'Could not update profile details', 'error');
    }
  });

  // ----------------------------------------------------
  // DEADLINE TIMER & SELECTION CONTROLS
  // ----------------------------------------------------

  let countdownInterval = null;

  async function checkDeadlineStatus() {
    try {
      const selectionWindow = await apiRequest('/adminfaculty/deadline');
      const badge = document.getElementById('selection-status-badge');
      const banner = document.getElementById('deadline-banner');
      
      if (!selectionWindow || !selectionWindow.active) {
        isSelectionPeriodActive = false;
        badge.className = 'status-badge closed';
        badge.innerText = 'Closed';
        banner.style.display = 'none';
        disableSelectionForm();
        return;
      }

      const deadlineTime = new Date(selectionWindow.deadline);
      const now = new Date();
      const diffMs = deadlineTime - now;

      if (diffMs <= 0) {
        isSelectionPeriodActive = false;
        badge.className = 'status-badge closed';
        badge.innerText = 'Closed';
        banner.style.display = 'none';
        disableSelectionForm();
      } else {
        isSelectionPeriodActive = true;
        badge.className = 'status-badge open';
        badge.innerText = 'Open';
        
        // Setup Info Banner
        banner.style.display = 'flex';
        document.getElementById('deadline-banner-message').innerText = selectionWindow.message;

        enableSelectionForm();

        // Start countdown clock
        if (countdownInterval) clearInterval(countdownInterval);
        
        updateCountdown(deadlineTime);
        countdownInterval = setInterval(() => {
          updateCountdown(deadlineTime);
        }, 1000); // Update every second
      }

    } catch (error) {
      console.error('Error checking deadline:', error);
    }
  }

  function updateCountdown(deadlineTime) {
    const now = new Date();
    const diffMs = deadlineTime - now;
    const timer = document.getElementById('deadline-timer');

    if (diffMs <= 0) {
      timer.innerText = 'Remaining: Selection Period Ended';
      clearInterval(countdownInterval);
      checkDeadlineStatus(); // Reload status
      return;
    }

    const days = Math.floor(diffMs / (1000 * 60 * 60 * 24));
    const hours = Math.floor((diffMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
    const mins = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
    const secs = Math.floor((diffMs % (1000 * 60)) / 1000);

    timer.innerHTML = `<i class="far fa-clock"></i> Remaining: ${days}d ${hours}h ${mins}m ${secs}s`;
  }

  function disableSelectionForm() {
    document.getElementById('preferences-submit-btn').disabled = true;
    document.getElementById('preferences-submit-btn').innerText = 'Selection Window Closed';
    document.getElementById('subject-checkboxes-container').classList.add('disabled-list');
  }

  function enableSelectionForm() {
    document.getElementById('preferences-submit-btn').disabled = false;
    document.getElementById('preferences-submit-btn').innerHTML = '<i class="fas fa-save"></i> Save Subject Preferences';
    document.getElementById('subject-checkboxes-container').classList.remove('disabled-list');
  }

  // ----------------------------------------------------
  // SUBJECT PREFERENCE LISTING & SAVING
  // ----------------------------------------------------

  async function loadSubjectPreferences() {
    const listContainer = document.getElementById('subject-checkboxes-container');
    listContainer.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 30px;"><i class="fas fa-spinner fa-spin"></i> Loading subjects checklist...</div>';

    try {
      // Fetch subjects, current selected preferences, and active selection window in parallel
      const [allSubjects, preferences, selectionWindow] = await Promise.all([
        apiRequest('/subject/viewAll'),
        apiRequest(`/faculty/preferences/${currentUser.id}`),
        apiRequest('/adminfaculty/deadline')
      ]);

      subjects = Array.isArray(allSubjects) ? allSubjects : [];
      facultyPreferences = Array.isArray(preferences) ? preferences : [];
      selectedOrder = facultyPreferences.map(p => p.subjectId);

      // Filter subjects according to active selection window filters
      if (selectionWindow && selectionWindow.active) {
        if (selectionWindow.sem) {
          subjects = subjects.filter(s => Number(s.sem) === Number(selectionWindow.sem));
        }
        if (selectionWindow.department) {
          subjects = subjects.filter(s => s.dep && s.dep.toUpperCase() === selectionWindow.department.toUpperCase());
        }
        if (selectionWindow.academicYear) {
          subjects = subjects.filter(s => s.academicYear && s.academicYear.toLowerCase() === selectionWindow.academicYear.toLowerCase());
        }
      }

      renderSubjectPreferencesList(subjects);
      
    } catch (error) {
      listContainer.innerHTML = '<div style="text-align: center; color: var(--error); padding: 20px;">Failed to load subjects directory</div>';
    }
  }

  function updateIndicators() {
    const subjectItems = document.querySelectorAll('.subject-item');
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
  }

  function renderSubjectPreferencesList(list) {
    const listContainer = document.getElementById('subject-checkboxes-container');
    listContainer.innerHTML = '';

    if (list.length === 0) {
      listContainer.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 30px;">No subjects available in the directory.</div>';
      return;
    }

    // Sort list by year, then sem, then ID
    const sortedList = [...list].sort((a, b) => {
      if (a.year !== b.year) return a.year - b.year;
      if (a.sem !== b.sem) return a.sem - b.sem;
      return a.id.localeCompare(b.id);
    });

    sortedList.forEach(sub => {
      const isChecked = selectedOrder.includes(sub.id);
      
      const item = document.createElement('div');
      item.className = `subject-item ${isChecked ? 'selected' : ''}`;
      
      item.innerHTML = `
        <input type="checkbox" id="chk-${sub.id}" value="${sub.id}" ${isChecked ? 'checked' : ''} style="display: none;">
        <div class="pref-index-indicator"></div>
        <div class="subject-details">
          <span>${sub.name} <code style="color: var(--text-muted); font-size: 0.82rem; font-weight: normal; margin-left: 6px;">${sub.id}</code></span>
          <small>Year ${sub.year} Sem ${sub.sem} • Dept: ${sub.dep} • Regulation: ${sub.regulation}</small>
        </div>
      `;

      const checkbox = item.querySelector('input[type="checkbox"]');
      
      checkbox.addEventListener('change', () => {
        if (checkbox.checked) {
          if (!selectedOrder.includes(sub.id)) {
            selectedOrder.push(sub.id);
          }
        } else {
          selectedOrder = selectedOrder.filter(id => id !== sub.id);
        }
        updateIndicators();
      });

      item.addEventListener('click', (e) => {
        if (e.target !== checkbox && !e.target.closest('label') && isSelectionPeriodActive) {
          checkbox.checked = !checkbox.checked;
          checkbox.dispatchEvent(new Event('change'));
        }
      });

      listContainer.appendChild(item);
    });

    updateIndicators();
  }

  // Client-side Subject Search
  document.getElementById('subject-search').addEventListener('input', (e) => {
    const q = e.target.value.toLowerCase().trim();
    const filtered = subjects.filter(s => 
      s.name.toLowerCase().includes(q) || 
      s.id.toLowerCase().includes(q) ||
      s.dep.toLowerCase().includes(q)
    );
    renderSubjectPreferencesList(filtered);
  });

  // Submit Preferences
  document.getElementById('preferences-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    if (!isSelectionPeriodActive) {
      showToast('Action Blocked', 'Subject selection window is closed', 'error');
      return;
    }

    const submitBtn = document.getElementById('preferences-submit-btn');
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving preferences...';

    try {
      await apiRequest(`/faculty/preferences?facultyId=${currentUser.id}`, {
        method: 'POST',
        body: selectedOrder
      });
      showToast('Preferences Saved', 'Subject preference selections saved', 'success');
      
      // Reload preferences to refresh local state cache
      const updatedPrefs = await apiRequest(`/faculty/preferences/${currentUser.id}`);
      facultyPreferences = Array.isArray(updatedPrefs) ? updatedPrefs : [];
      selectedOrder = facultyPreferences.map(p => p.subjectId);
      updateIndicators();
      
    } catch (error) {
      showToast('Save Failed', error.message || 'Error saving subject choices', 'error');
    } finally {
      enableSelectionForm();
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
        const sub = subMap[alloc.subjectId] || { name: 'Subject: ' + alloc.subjectId, year: '?', dep: '?' };
        const item = document.createElement('div');
        item.className = 'allocation-item';
        item.innerHTML = `
          <div class="alloc-details">
            <h5>${sub.name}</h5>
            <p>Code: ${alloc.subjectId} ${sub.year ? '• Year ' + sub.year + ' (' + sub.dep + ')' : ''}</p>
          </div>
          <span class="alloc-badge">Section ${alloc.sectionName}</span>
        `;
        container.appendChild(item);
      });

      // Next render Subject Allocations for subjects not yet assigned a section
      const secSubjectIds = new Set(secAllocList.map(a => a.subjectId));
      subAllocList.forEach(alloc => {
        if (!secSubjectIds.has(alloc.subjectId)) {
          const sub = subMap[alloc.subjectId] || { name: 'Subject: ' + alloc.subjectId, year: '?', dep: '?' };
          const item = document.createElement('div');
          item.className = 'allocation-item';
          item.innerHTML = `
            <div class="alloc-details">
              <h5>${sub.name}</h5>
              <p>Code: ${alloc.subjectId} ${sub.year ? '• Year ' + sub.year + ' (' + sub.dep + ')' : ''}</p>
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
