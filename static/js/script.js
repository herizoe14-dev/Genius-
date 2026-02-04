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

  // === Notification badge auto-refresh ===
  const notificationBadge = document.getElementById('notificationBadge');
  const menuBadge = document.getElementById('menuBadge');
  const NOTIFICATION_REFRESH_INTERVAL_MS = 30000; // 30 seconds

  function updateNotificationBadges(count){
    // Update notification icon badge
    if(notificationBadge){
      if(count > 0){
        notificationBadge.textContent = count > 99 ? '99+' : count;
        notificationBadge.hidden = false;
      } else {
        notificationBadge.hidden = true;
      }
    }
    // Update menu/hamburger badge
    if(menuBadge){
      if(count > 0){
        menuBadge.textContent = count > 99 ? '99+' : count;
        menuBadge.hidden = false;
      } else {
        menuBadge.hidden = true;
      }
    }
  }

  function fetchNotifications(){
    fetch('/api/notifications')
      .then(function(response){
        if(!response.ok) throw new Error('Network response was not ok');
        return response.json();
      })
      .then(function(data){
        updateNotificationBadges(data.count || 0);
      })
      .catch(function(error){
        console.error('Error fetching notifications:', error);
      });
  }

  // Initial fetch and set interval for auto-refresh
  if(notificationBadge || menuBadge){
    fetchNotifications();
    setInterval(fetchNotifications, NOTIFICATION_REFRESH_INTERVAL_MS);
  }

  // === Bag Alert Modal ===
  const notificationBtn = document.getElementById('notificationBtn');
  const bagAlertModal = document.getElementById('bagAlertModal');
  const closeBagAlert = document.getElementById('closeBagAlert');

  function openBagAlertModal(){
    if(!bagAlertModal) return;
    bagAlertModal.classList.add('show');
    document.body.style.overflow = 'hidden';
  }

  function closeBagAlertModal(){
    if(!bagAlertModal) return;
    bagAlertModal.classList.remove('show');
    document.body.style.overflow = '';
  }

  if(notificationBtn) notificationBtn.addEventListener('click', openBagAlertModal);
  if(closeBagAlert) closeBagAlert.addEventListener('click', closeBagAlertModal);
  if(bagAlertModal) bagAlertModal.addEventListener('click', function(e){
    if(e.target === bagAlertModal) closeBagAlertModal();
  });

  // Close bag alert with Escape key
  document.addEventListener('keydown', function(e){
    if(e.key === 'Escape' && bagAlertModal && bagAlertModal.classList.contains('show')){
      closeBagAlertModal();
    }
  });
});