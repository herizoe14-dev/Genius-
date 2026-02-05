document.addEventListener('DOMContentLoaded', function () {
  // === Service Worker Registration for PWA ===
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', function() {
      navigator.serviceWorker.register('/sw.js', { scope: '/' })
        .then(function(registration) {
          console.log('[PWA] Service Worker registered successfully:', registration.scope);
          
          // Check for updates
          registration.addEventListener('updatefound', function() {
            const newWorker = registration.installing;
            console.log('[PWA] Service Worker update found');
            
            newWorker.addEventListener('statechange', function() {
              if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
                console.log('[PWA] New content available, please refresh');
              }
            });
          });
        })
        .catch(function(error) {
          console.error('[PWA] Service Worker registration failed:', error);
        });
    });
  }

  // === PWA Install Prompt ===
  let deferredPrompt;
  const installBanner = document.getElementById('pwaInstallBanner');
  const installBtn = document.getElementById('pwaInstallBtn');
  const dismissBtn = document.getElementById('pwaInstallDismiss');

  window.addEventListener('beforeinstallprompt', function(e) {
    console.log('[PWA] beforeinstallprompt fired');
    e.preventDefault();
    deferredPrompt = e;
    
    // Show install banner if available
    if (installBanner) {
      installBanner.hidden = false;
      installBanner.classList.add('show');
    }
  });

  if (installBtn) {
    installBtn.addEventListener('click', function() {
      if (!deferredPrompt) return;
      
      deferredPrompt.prompt();
      deferredPrompt.userChoice.then(function(choiceResult) {
        console.log('[PWA] User choice:', choiceResult.outcome);
        if (choiceResult.outcome === 'accepted') {
          console.log('[PWA] User accepted the install prompt');
        }
        deferredPrompt = null;
        if (installBanner) {
          installBanner.hidden = true;
          installBanner.classList.remove('show');
        }
      });
    });
  }

  if (dismissBtn) {
    dismissBtn.addEventListener('click', function() {
      if (installBanner) {
        installBanner.hidden = true;
        installBanner.classList.remove('show');
      }
    });
  }

  window.addEventListener('appinstalled', function() {
    console.log('[PWA] App was installed');
    deferredPrompt = null;
    if (installBanner) {
      installBanner.hidden = true;
      installBanner.classList.remove('show');
    }
  });

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
  const NOTIFICATION_REFRESH_INTERVAL_MS = 30000; // 30 seconds
  
  // Store notifications data for modal display
  let notificationsData = [];

  function updateNotificationBadge(count){
    // Update notification icon badge only
    if(notificationBadge){
      if(count > 0){
        notificationBadge.textContent = count > 99 ? '99+' : count;
        notificationBadge.hidden = false;
      } else {
        notificationBadge.hidden = true;
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
        updateNotificationBadge(data.count || 0);
        notificationsData = data.notifications || [];
      })
      .catch(function(error){
        console.error('Error fetching notifications:', error);
      });
  }

  // Initial fetch and set interval for auto-refresh
  if(notificationBadge){
    fetchNotifications();
    setInterval(fetchNotifications, NOTIFICATION_REFRESH_INTERVAL_MS);
  }

  // === Bag Alert Modal ===
  const notificationBtn = document.getElementById('notificationBtn');
  const bagAlertModal = document.getElementById('bagAlertModal');
  const closeBagAlert = document.getElementById('closeBagAlert');
  const notificationList = document.getElementById('notificationList');

  function renderNotifications(){
    if(!notificationList) return;
    notificationList.innerHTML = '';
    
    if(notificationsData.length === 0){
      var emptyMsg = document.createElement('p');
      emptyMsg.className = 'notification-empty';
      emptyMsg.textContent = 'Aucune notification pour le moment.';
      notificationList.appendChild(emptyMsg);
      return;
    }
    
    notificationsData.forEach(function(notif){
      var item = document.createElement('div');
      item.className = 'notification-item';
      
      var icon = document.createElement('span');
      icon.className = 'notification-item-icon';
      if(notif.type === 'admin_message'){
        icon.textContent = '📩';
      } else if(notif.type === 'pending_purchase'){
        icon.textContent = '🛒';
      } else {
        icon.textContent = '🔔';
      }
      
      var content = document.createElement('div');
      content.className = 'notification-item-content';
      
      var message = document.createElement('p');
      message.className = 'notification-item-message';
      message.textContent = notif.message || 'Notification';
      
      content.appendChild(message);
      
      if(notif.timestamp){
        var time = document.createElement('span');
        time.className = 'notification-item-time';
        var date = new Date(notif.timestamp * 1000);
        time.textContent = date.toLocaleString('fr-FR');
        content.appendChild(time);
      }
      
      item.appendChild(icon);
      item.appendChild(content);
      notificationList.appendChild(item);
    });
  }

  function openBagAlertModal(){
    if(!bagAlertModal) return;
    renderNotifications();
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