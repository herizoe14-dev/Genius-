document.addEventListener('DOMContentLoaded', function () {
  const openBtn = document.getElementById('openSidebar');
  const closeBtn = document.getElementById('closeSidebar');
  const sidebar = document.getElementById('sidebar');
  const overlay = document.getElementById('overlay');

  // === Sidebar management ===
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

  // === Notifications management ===
  const notifBtn = document.getElementById('notifBtn');
  const notifBadge = document.getElementById('notifBadge');
  
  // Poll for notification count every 10 seconds
  function pollNotificationCount() {
    fetch('/notifications/count')
      .then(response => response.json())
      .then(data => {
        const count = data.count || 0;
        if (count > 0) {
          notifBadge.textContent = count;
          notifBadge.hidden = false;
        } else {
          notifBadge.hidden = true;
        }
      })
      .catch(err => {
        console.error('Error polling notifications:', err);
      });
  }

  // Fetch and display notifications
  function showNotifications() {
    fetch('/notifications')
      .then(response => response.json())
      .then(data => {
        const notifications = data.notifications || [];
        if (notifications.length === 0) {
          window.alert('Aucune nouvelle notification.');
          return;
        }

        // Build notification message
        let message = 'NOTIFICATIONS D\'ACHAT:\n\n';
        notifications.forEach((notif, index) => {
          message += `${index + 1}. `;
          
          if (notif.status === 'accepted') {
            message += `✅ ACCEPTÉ - Pack ${notif.pack}\n`;
            message += `   ${notif.response_message || 'Votre achat a été validé!'}\n`;
          } else if (notif.status === 'rejected') {
            message += `❌ REFUSÉ - Pack ${notif.pack}\n`;
            message += `   ${notif.response_message || 'Votre demande a été refusée.'}\n`;
          } else if (notif.status === 'off') {
            message += `🚫 INDISPONIBLE - Pack ${notif.pack}\n`;
            message += `   ${notif.response_message || 'Achats temporairement indisponibles.'}\n`;
          }
          message += '\n';
        });

        // Show in alert
        window.alert(message);

        // Mark as seen
        const ids = notifications.map(n => n.id);
        fetch('/notifications/ack', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ ids: ids })
        })
        .then(() => {
          // Update badge after acknowledging
          pollNotificationCount();
        })
        .catch(err => {
          console.error('Error acknowledging notifications:', err);
        });
      })
      .catch(err => {
        console.error('Error fetching notifications:', err);
        window.alert('Erreur lors de la récupération des notifications.');
      });
  }

  // Setup notification button click handler
  if (notifBtn) {
    notifBtn.addEventListener('click', showNotifications);
    
    // Start polling
    pollNotificationCount(); // Initial check
    setInterval(pollNotificationCount, 10000); // Poll every 10 seconds
  }
});