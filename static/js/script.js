document.addEventListener('DOMContentLoaded', function () {
  const openBtn = document.getElementById('openSidebar');
  const closeBtn = document.getElementById('closeSidebar');
  const sidebar = document.getElementById('sidebar');
  const overlay = document.getElementById('overlay');

  function openSidebar(){
    if(!sidebar) return;
    sidebar.classList.add('open');
    overlay.classList.add('show');
    overlay.hidden = false;
    sidebar.setAttribute('aria-hidden', 'false');
    // lock scroll
    document.body.style.overflow = 'hidden';
  }

  function closeSidebar(){
    if(!sidebar) return;
    sidebar.classList.remove('open');
    overlay.classList.remove('show');
    // small timeout to allow transition before hiding overlay
    setTimeout(()=> { overlay.hidden = true; }, 280);
    sidebar.setAttribute('aria-hidden', 'true');
    document.body.style.overflow = '';
  }

  if(openBtn) openBtn.addEventListener('click', openSidebar);
  if(closeBtn) closeBtn.addEventListener('click', closeSidebar);
  if(overlay) overlay.addEventListener('click', closeSidebar);

  // close with Escape key
  document.addEventListener('keydown', function(e){
    if(e.key === 'Escape') closeSidebar();
  });
});
// Notification System
function toggleNotifications() {
  const panel = document.getElementById('notificationPanel');
  if (panel) {
    if (panel.classList.contains('show')) {
      panel.classList.remove('show');
    } else {
      panel.classList.add('show');
      loadNotifications();
    }
  }
}

function loadNotifications() {
  fetch('/api/notifications')
    .then(response => response.json())
    .then(data => {
      updateNotificationUI(data.notifications, data.unread_count);
    })
    .catch(error => console.error('Error loading notifications:', error));
}

function updateNotificationUI(notifications, unreadCount) {
  const badge = document.getElementById('notificationBadge');
  const list = document.getElementById('notificationList');
  
  if (badge) {
    if (unreadCount > 0) {
      badge.textContent = unreadCount > 99 ? '99+' : unreadCount;
      badge.style.display = 'block';
    } else {
      badge.style.display = 'none';
    }
  }
  
  if (list) {
    if (notifications.length === 0) {
      list.innerHTML = `
        <div class="notification-empty">
          <i class="fas fa-inbox" style="font-size: 48px; opacity: 0.3; margin-bottom: 12px; display: block;"></i>
          Aucune notification pour le moment
        </div>
      `;
    } else {
      list.innerHTML = notifications.map(notif => `
        <div class="notification-item ${notif.read ? '' : 'unread'}">
          <div class="notification-content">
            <div class="notification-title">${notif.title}</div>
            <p class="notification-message">${notif.message}</p>
            <div class="notification-time">${notif.time}</div>
          </div>
          <button class="notification-delete" onclick="deleteNotification(${notif.id})" title="Supprimer">
            <i class="fas fa-trash-alt"></i>
          </button>
        </div>
      `).join('');
    }
  }
}

function deleteNotification(notifId) {
  fetch(`/api/notifications/${notifId}`, {
    method: 'DELETE'
  })
  .then(response => response.json())
  .then(data => {
    if (data.success) {
      loadNotifications();
    }
  })
  .catch(error => console.error('Error deleting notification:', error));
}

function markNotificationAsRead(notifId) {
  fetch(`/api/notifications/${notifId}/read`, {
    method: 'POST'
  })
  .then(response => response.json())
  .then(data => {
    if (data.success) {
      loadNotifications();
    }
  })
  .catch(error => console.error('Error marking notification as read:', error));
}

// Load notifications on page load
document.addEventListener('DOMContentLoaded', function() {
  // Check for notifications every 30 seconds
  if (document.getElementById('notificationBell')) {
    loadNotifications();
    setInterval(loadNotifications, 30000);
  }
  
  // Load maintenance alerts
  loadMaintenanceAlerts();
  
  // Close notification panel when clicking outside
  document.addEventListener('click', function(e) {
    const panel = document.getElementById('notificationPanel');
    const bell = document.getElementById('notificationBell');
    if (panel && bell && !panel.contains(e.target) && !bell.contains(e.target)) {
      panel.classList.remove('show');
    }
  });
});

// Maintenance Alert System
function loadMaintenanceAlerts() {
  fetch('/api/maintenance-alert')
    .then(response => response.json())
    .then(data => {
      if (data.alert && data.alert.message) {
        showMaintenanceAlert(data.alert.message, data.alert.type || 'warning');
      }
    })
    .catch(error => console.error('Error loading maintenance alert:', error));
}

function showMaintenanceAlert(message, type = 'warning') {
  const container = document.getElementById('maintenanceAlertContainer');
  if (!container) return;
  
  const icons = {
    'warning': '<i class="fas fa-exclamation-triangle"></i>',
    'error': '<i class="fas fa-times-circle"></i>',
    'info': '<i class="fas fa-info-circle"></i>'
  };
  
  const alertHtml = `
    <div class="maintenance-alert ${type}" id="maintenanceAlert">
      <div class="maintenance-alert-content">
        <div class="maintenance-alert-icon">${icons[type] || icons['warning']}</div>
        <div class="maintenance-alert-text">${message}</div>
      </div>
      <button class="maintenance-alert-close" onclick="dismissMaintenanceAlert()">
        <i class="fas fa-times"></i>
      </button>
    </div>
  `;
  
  container.innerHTML = alertHtml;
}

function dismissMaintenanceAlert() {
  const alert = document.getElementById('maintenanceAlert');
  if (alert) {
    alert.style.animation = 'fadeOut 0.3s ease';
    setTimeout(() => {
      alert.remove();
    }, 300);
  }
}
