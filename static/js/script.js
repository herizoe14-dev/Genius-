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
    if(e.key === 'Escape') {
      closeSidebar();
      closeNotificationDropdown();
    }
  });

  // Notification Bell Dropdown
  const notificationBell = document.getElementById('notificationBell');
  const notificationDropdown = document.getElementById('notificationDropdown');

  function toggleNotificationDropdown() {
    if (!notificationDropdown) return;
    const isHidden = notificationDropdown.hidden;
    if (isHidden) {
      notificationDropdown.hidden = false;
      notificationBell.setAttribute('aria-expanded', 'true');
    } else {
      closeNotificationDropdown();
    }
  }

  function closeNotificationDropdown() {
    if (!notificationDropdown) return;
    notificationDropdown.hidden = true;
    if (notificationBell) {
      notificationBell.setAttribute('aria-expanded', 'false');
    }
  }

  if (notificationBell) {
    notificationBell.addEventListener('click', function(e) {
      e.stopPropagation();
      toggleNotificationDropdown();
    });
  }

  // Close dropdown when clicking outside
  document.addEventListener('click', function(e) {
    if (notificationDropdown && !notificationDropdown.hidden) {
      if (!notificationDropdown.contains(e.target) && e.target !== notificationBell) {
        closeNotificationDropdown();
      }
    }
  });
});