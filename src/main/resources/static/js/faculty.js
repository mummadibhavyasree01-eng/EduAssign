// Faculty Dashboard Logic for EduAssign

document.addEventListener('DOMContentLoaded', async () => {
  // 1. Session Check (allows both FACULTY and ADMIN for preview)
  const currentUser = checkSession(['FACULTY', 'ADMIN']);
  if (!currentUser) return;

  const isFaculty = currentUser.role.toUpperCase() === 'FACULTY';

  // Render header nav user chip
  document.getElementById('nav-user-name').innerText = currentUser.name;
  document.getElementById('nav-user-role').innerText = isFaculty ? `Role: Faculty (${currentUser.id})` : `Role: Admin (Preview)`;

  // Show "Back to Admin" button if logged-in user is actually an Admin
  if (!isFaculty) {
    document.getElementById('back-to-admin').style.display = 'inline-flex';
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
        }, 1000 * 60); // Update every minute
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

    timer.innerHTML = `<i class="far fa-clock"></i> Remaining: ${days}d ${hours}h ${mins}m`;
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
      // Fetch subjects and current selected preferences in parallel
      const [allSubjects, preferences] = await Promise.all([
        apiRequest('/subject/viewAll'),
        apiRequest(`/faculty/preferences/${currentUser.id}`)
      ]);

      subjects = Array.isArray(allSubjects) ? allSubjects : [];
      facultyPreferences = Array.isArray(preferences) ? preferences : [];

      renderSubjectPreferencesList(subjects);
      
    } catch (error) {
      listContainer.innerHTML = '<div style="text-align: center; color: var(--error); padding: 20px;">Failed to load subjects directory</div>';
    }
  }

  function renderSubjectPreferencesList(list) {
    const listContainer = document.getElementById('subject-checkboxes-container');
    listContainer.innerHTML = '';

    if (list.length === 0) {
      listContainer.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 30px;">No subjects available in the directory.</div>';
      return;
    }

    // Set of currently preferred subject IDs for easy lookup
    const preferredIds = new Set(facultyPreferences.map(p => p.subjectId));

    list.forEach(sub => {
      const isChecked = preferredIds.has(sub.id);
      
      const item = document.createElement('div');
      item.className = `subject-item ${isChecked ? 'selected' : ''}`;
      
      item.innerHTML = `
        <input type="checkbox" id="chk-${sub.id}" value="${sub.id}" ${isChecked ? 'checked' : ''}>
        <div class="subject-details">
          <span>${sub.name} <code style="color: var(--text-muted); font-size: 0.82rem; font-weight: normal; margin-left: 6px;">${sub.id}</code></span>
          <small>Year ${sub.year} Sem ${sub.sem} • Dept: ${sub.dep} • Regulation: ${sub.regulation}</small>
        </div>
      `;

      // Handle item checkbox change visual styling
      const checkbox = item.querySelector('input[type="checkbox"]');
      
      checkbox.addEventListener('change', () => {
        if (checkbox.checked) {
          item.classList.add('selected');
        } else {
          item.classList.remove('selected');
        }
      });

      // Clicking the item container also checks/unchecks the box (except when selecting text)
      item.addEventListener('click', (e) => {
        if (e.target !== checkbox && !e.target.closest('label') && isSelectionPeriodActive) {
          checkbox.checked = !checkbox.checked;
          checkbox.dispatchEvent(new Event('change'));
        }
      });

      listContainer.appendChild(item);
    });
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

    const checkedBoxes = document.querySelectorAll('#subject-checkboxes-container input[type="checkbox"]:checked');
    const selectedSubjectIds = Array.from(checkedBoxes).map(box => box.value);

    const submitBtn = document.getElementById('preferences-submit-btn');
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving preferences...';

    try {
      await apiRequest(`/faculty/preferences?facultyId=${currentUser.id}`, {
        method: 'POST',
        body: selectedSubjectIds
      });
      showToast('Preferences Saved', 'Subject preference selections saved', 'success');
      
      // Reload preferences to refresh local state cache
      const updatedPrefs = await apiRequest(`/faculty/preferences/${currentUser.id}`);
      facultyPreferences = Array.isArray(updatedPrefs) ? updatedPrefs : [];
      
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
      allSubjects.forEach(s => subMap[s.id] = s);

      if (allocations.length === 0) {
        container.innerHTML = `
          <div class="no-allocations">
            <i class="fas fa-calendar-alt"></i>
            <p>No subjects allocated yet. Final subject allocations will be shown here once allocations are finalized by Admin.</p>
          </div>
        `;
        return;
      }

      container.innerHTML = '';
      allocations.forEach(alloc => {
        const sub = subMap[alloc.subjectId] || { name: 'Unknown Subject', year: '?', dep: '?' };
        
        const item = document.createElement('div');
        item.className = 'allocation-item';
        item.innerHTML = `
          <div class="alloc-details">
            <h5>${sub.name}</h5>
            <p>Code: ${alloc.subjectId} • Year ${sub.year} (${sub.dep})</p>
          </div>
          <span class="alloc-badge">Section ${alloc.sectionName}</span>
        `;
        container.appendChild(item);
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
