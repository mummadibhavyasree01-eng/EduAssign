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
      }
    });
  });

  // 3. Load Users list
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

  // Define toggleUserRole on window scope so it can be called from HTML onclick
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

  // 4. Update Profile
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
      const response = await apiRequest('/faculty/update', {
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

  // Initial load
  loadUsers();
});
