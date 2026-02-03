document.addEventListener('DOMContentLoaded', function () {
  const openBtn = document.getElementById('openSidebar');
  const closeBtn = document.getElementById('closeSidebar');
  const sidebar = document.getElementById('sidebar');
  const overlay = document.getElementById('overlay');
  const notificationPanel = document.getElementById('notificationPanel');
  const notificationButton = document.getElementById('notificationButton');
  const notificationButtonMobile = document.getElementById('notificationButtonMobile');
  const OVERLAY_TRANSITION_MS = 280; // matches CSS transition duration (0.28s)

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
    setTimeout(()=> { overlay.hidden = true; }, OVERLAY_TRANSITION_MS);
    sidebar.setAttribute('aria-hidden', 'true');
    document.body.style.overflow = '';
  }

  if(openBtn) openBtn.addEventListener('click', openSidebar);
  if(closeBtn) closeBtn.addEventListener('click', closeSidebar);
  if(overlay) overlay.addEventListener('click', closeSidebar);

  function toggleNotifications(){
    if(!notificationPanel) return;
    const isHidden = notificationPanel.hidden;
    notificationPanel.hidden = !isHidden;
    if (!notificationPanel.hidden) {
      notificationPanel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  if(notificationButton) notificationButton.addEventListener('click', toggleNotifications);
  if(notificationButtonMobile) notificationButtonMobile.addEventListener('click', () => {
    toggleNotifications();
    closeSidebar();
  });

  // close with Escape key
  document.addEventListener('keydown', function(e){
    if(e.key === 'Escape') {
      closeSidebar();
      if(notificationPanel) notificationPanel.hidden = true;
    }
  });
});
