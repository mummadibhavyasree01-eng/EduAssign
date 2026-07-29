// Common JavaScript Utilities for EduAssign

const CONFIG = {
  API_BASE: '' // Same origin
};

// Toast notification helper
function showToast(title, message, type = 'info') {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = `toast glass-panel ${type}`;
  
  let iconClass = 'fa-info-circle';
  if (type === 'success') iconClass = 'fa-check-circle';
  if (type === 'error') iconClass = 'fa-exclamation-circle';
  if (type === 'warning') iconClass = 'fa-exclamation-triangle';

  toast.innerHTML = `
    <i class="fas ${iconClass}"></i>
    <div class="toast-content">
      <div class="toast-title">${title}</div>
      <div class="toast-message">${message}</div>
    </div>
  `;

  container.appendChild(toast);
  
  // Trigger slide-in animation
  setTimeout(() => {
    toast.classList.add('show');
  }, 10);

  // Auto-remove toast after 4 seconds
  setTimeout(() => {
    toast.classList.remove('show');
    setTimeout(() => {
      toast.remove();
    }, 400);
  }, 4000);
}

// Custom Confirm Modal
function showConfirm(title, message, onConfirm) {
  // Check if modal container already exists, else create it
  let modalOverlay = document.getElementById('confirm-modal-overlay');
  if (!modalOverlay) {
    modalOverlay = document.createElement('div');
    modalOverlay.id = 'confirm-modal-overlay';
    modalOverlay.className = 'modal-overlay';
    modalOverlay.innerHTML = `
      <div class="modal-container glass-panel">
        <div class="modal-header">
          <h3 class="modal-title" id="confirm-modal-title">Confirm Action</h3>
          <button class="modal-close" onclick="closeConfirmModal()"><i class="fas fa-times"></i></button>
        </div>
        <div class="modal-body" id="confirm-modal-body" style="color: var(--text-muted)">
          Are you sure you want to perform this action?
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" onclick="closeConfirmModal()">Cancel</button>
          <button class="btn btn-danger" id="confirm-modal-btn">Confirm</button>
        </div>
      </div>
    `;
    document.body.appendChild(modalOverlay);
  }

  document.getElementById('confirm-modal-title').innerText = title;
  document.getElementById('confirm-modal-body').innerText = message;
  
  const confirmBtn = document.getElementById('confirm-modal-btn');
  // Remove existing listeners by cloning the button
  const newConfirmBtn = confirmBtn.cloneNode(true);
  confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);
  
  newConfirmBtn.addEventListener('click', () => {
    closeConfirmModal();
    if (onConfirm) onConfirm();
  });

  modalOverlay.classList.add('active');
}

function closeConfirmModal() {
  const modalOverlay = document.getElementById('confirm-modal-overlay');
  if (modalOverlay) {
    modalOverlay.classList.remove('active');
  }
}

// Session Checker
function checkSession(allowedRoles = []) {
  const currentUser = JSON.parse(sessionStorage.getItem('currentUser'));
  if (!currentUser) {
    window.location.href = 'login.html';
    return null;
  }

  // If roles specified, verify
  if (allowedRoles.length > 0) {
    const roleUpper = currentUser.role.toUpperCase();
    const isAllowed = allowedRoles.some(role => role.toUpperCase() === roleUpper);
    if (!isAllowed) {
      // Direct back to their correct dashboard
      if (roleUpper === 'SUPERADMIN') {
        window.location.href = 'superadmin.html';
      } else if (roleUpper === 'ADMIN') {
        window.location.href = 'admin.html';
      } else {
        window.location.href = 'faculty.html';
      }
      return null;
    }
  }

  return currentUser;
}

// Logout Utility
function logout() {
  sessionStorage.removeItem('currentUser');
  window.location.href = 'login.html';
}

// Enhanced Fetch wrapper
async function apiRequest(url, options = {}) {
  // Set JSON headers by default if body is passed and it's not FormData
  if (options.body && !(options.body instanceof FormData) && !(options.body instanceof URLSearchParams)) {
    options.headers = {
      'Content-Type': 'application/json',
      ...options.headers
    };
    if (typeof options.body === 'object') {
      options.body = JSON.stringify(options.body);
    }
  }

  try {
    const response = await fetch(url, options);
    
    // Handle unauthorized status (except for login requests)
    if (response.status === 401 && !url.includes('/login')) {
      showToast('Session Expired', 'Please login again', 'error');
      sessionStorage.removeItem('currentUser');
      setTimeout(() => {
        window.location.href = 'login.html';
      }, 1000);
      throw new Error('Unauthorized');
    }

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(errorText || `HTTP error! status: ${response.status}`);
    }

    // Attempt to parse JSON, if it fails, return text
    const text = await response.text();
    try {
      return JSON.parse(text);
    } catch (e) {
      return text; // Return raw text if not JSON
    }
  } catch (error) {
    console.error('API Error:', error);
    throw error;
  }
}
