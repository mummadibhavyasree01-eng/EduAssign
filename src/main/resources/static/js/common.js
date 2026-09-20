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

  const dismissToast = () => {
    if (toast.classList.contains('show')) {
      toast.classList.remove('show');
      setTimeout(() => {
        if (toast.parentNode) {
          toast.remove();
        }
      }, 300);
    }
  };

  toast.addEventListener('click', dismissToast);

  container.appendChild(toast);
  
  // Trigger slide-in animation
  setTimeout(() => {
    toast.classList.add('show');
  }, 10);

  // Auto-remove toast after 0.75 seconds (750ms)
  setTimeout(dismissToast, 750);
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
  // Prevent GET caching
  const method = (options.method || 'GET').toUpperCase();
  if (method === 'GET') {
    const separator = url.includes('?') ? '&' : '?';
    url = `${url}${separator}_t=${Date.now()}`;
  }
  options.headers = {
    'Cache-Control': 'no-cache, no-store, must-revalidate',
    'Pragma': 'no-cache',
    'Expires': '0',
    ...options.headers
  };
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

// Strip semester suffix from subject names for display/reports
function cleanSubjectName(name) {
  return (name || '')
    .replace(/\s*\(?Sem(?:ester)?[\s-]*(?:\d+|[IVXLCDM]+)\)?/gi, '')
    .replace(/\s*-\s*$/, '')
    .trim();
}

function cleanSubjectCode(id) {
  if (!id) return '';
  return id.includes('_') ? id.split('_')[0] : id;
}

// Global Event Delegation for Password Toggle Eyes
document.addEventListener('click', function(e) {
  const toggle = e.target.closest('.password-toggle');
  if (toggle) {
    e.preventDefault();
    e.stopPropagation();
    const container = toggle.closest('.input-wrapper') || toggle.closest('.password-toggle-wrapper') || toggle.parentNode;
    const input = container ? container.querySelector('input') : null;
    if (input) {
      const isPassword = input.type === 'password';
      input.type = isPassword ? 'text' : 'password';
      const icon = toggle.querySelector('i');
      if (icon) {
        if (isPassword) {
          icon.className = 'fas fa-eye-slash';
        } else {
          icon.className = 'fas fa-eye';
        }
      }
    }
  }
});

// Update Header Avatar and Initials
function updateHeaderAvatar(user) {
  const avatarContainers = document.querySelectorAll('.user-avatar-wrapper');
  avatarContainers.forEach(container => {
    let img = container.querySelector('.header-avatar-img');
    let initials = container.querySelector('.header-avatar-initials');
    
    // Create elements dynamically if they don't exist
    if (!img) {
      img = document.createElement('img');
      img.className = 'header-avatar-img';
      img.style.width = '100%';
      img.style.height = '100%';
      img.style.objectFit = 'cover';
      img.style.borderRadius = '50%';
      img.style.display = 'none';
      container.appendChild(img);
    }
    if (!initials) {
      initials = document.createElement('div');
      initials.className = 'header-avatar-initials';
      initials.style.width = '100%';
      initials.style.height = '100%';
      initials.style.borderRadius = '50%';
      initials.style.display = 'none';
      initials.style.alignItems = 'center';
      initials.style.justifyContent = 'center';
      initials.style.fontWeight = 'bold';
      initials.style.background = 'var(--primary)';
      initials.style.color = '#fff';
      initials.style.fontSize = '0.9rem';
      container.appendChild(initials);
    }

    if (user && user.profileImage) {
      img.src = user.profileImage;
      img.style.display = 'block';
      initials.style.display = 'none';
    } else {
      img.style.display = 'none';
      if (user) {
        const nameParts = (user.name || 'U').trim().split(/\s+/);
        const nameInitials = nameParts.map(n => n[0]).join('').substring(0, 2).toUpperCase();
        initials.innerText = nameInitials || 'U';
      } else {
        initials.innerText = 'U';
      }
      initials.style.display = 'flex';
    }
  });
}

function resizeAndCropImage(file, callback) {
  const reader = new FileReader();
  reader.onload = (event) => {
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d');
      
      canvas.width = 300;
      canvas.height = 300;
      
      let srcX = 0;
      let srcY = 0;
      let srcWidth = img.width;
      let srcHeight = img.height;
      
      if (img.width > img.height) {
        srcWidth = img.height;
        srcX = (img.width - img.height) / 2;
      } else {
        srcHeight = img.width;
        srcY = (img.height - img.width) / 2;
      }
      
      ctx.drawImage(img, srcX, srcY, srcWidth, srcHeight, 0, 0, 300, 300);
      
      const base64 = canvas.toDataURL('image/jpeg', 0.85);
      callback(base64);
    };
    img.src = event.target.result;
  };
  reader.readAsDataURL(file);
}
