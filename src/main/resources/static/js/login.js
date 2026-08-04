// Login logic for EduAssign Portal

document.addEventListener('DOMContentLoaded', () => {
  // Clear any existing session
  sessionStorage.removeItem('currentUser');

  const loginForm = document.getElementById('login-form');
  const emailInput = document.getElementById('email');
  const passwordInput = document.getElementById('password');
  const togglePassword = document.getElementById('toggle-password');

  // Toggle Password Visibility
  if (togglePassword && passwordInput) {
    togglePassword.addEventListener('click', function() {
      const type = passwordInput.type === 'password' ? 'text' : 'password';
      passwordInput.type = type;
      
      // Toggle eye icon
      const icon = this.querySelector('i');
      if (icon) {
        if (type === 'password') {
          icon.className = 'fas fa-eye';
        } else {
          icon.className = 'fas fa-eye-slash';
        }
      }
    });
  }

  // Handle Submit
  loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const email = emailInput.value.trim();
    const password = passwordInput.value;

    const loginBtn = document.getElementById('login-btn');
    const btnText = loginBtn.querySelector('span');
    const btnIcon = loginBtn.querySelector('i');

    // Show loading state
    btnText.innerText = 'Signing In...';
    loginBtn.disabled = true;
    btnIcon.className = 'fas fa-circle-notch fa-spin';

    try {
      // Send credentials as URLSearchParams (form data)
      const params = new URLSearchParams();
      params.append('email', email);
      params.append('password', password);

      const response = await apiRequest('/adminfaculty/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: params
      });

      showToast('Login Successful', response.message || 'Welcome to EduAssign', 'success');

      // Save user details
      const userSession = {
        id: response.id,
        name: response.name,
        email: response.email,
        role: response.role,
        profileImage: response.profileImage
      };
      sessionStorage.setItem('currentUser', JSON.stringify(userSession));

      // Redirect based on role
      setTimeout(() => {
        const role = response.role.toUpperCase();
        if (role === 'SUPERADMIN') {
          window.location.href = 'superadmin.html';
        } else if (role === 'ADMIN') {
          window.location.href = 'admin.html';
        } else {
          window.location.href = 'faculty.html';
        }
      }, 800);

    } catch (error) {
      showToast('Login Failed', error.message || 'Invalid Email or Password', 'error');
      
      // Reset button state
      btnText.innerText = 'Sign In';
      loginBtn.disabled = false;
      btnIcon.className = 'fas fa-arrow-right';
    }
  });
});
