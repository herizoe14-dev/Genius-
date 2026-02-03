document.addEventListener('DOMContentLoaded', function () {
  const openBtn = document.getElementById('openSidebar');
  const closeBtn = document.getElementById('closeSidebar');
  const sidebar = document.getElementById('sidebar');
  const overlay = document.getElementById('overlay');

  // Sidebar management
  function openSidebar(){
    if(!sidebar) return;
    sidebar.classList.add('open');
    if(overlay) {
      overlay.classList.add('show');
      overlay.hidden = false;
    }
    sidebar.setAttribute('aria-hidden', 'false');
    // lock scroll
    document.body.style.overflow = 'hidden';
  }

  function closeSidebar(){
    if(!sidebar) return;
    sidebar.classList.remove('open');
    if(overlay) {
      overlay.classList.remove('show');
      // small timeout to allow transition before hiding overlay
      setTimeout(()=> { overlay.hidden = true; }, 280);
    }
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

  // Notification system
  const notifBtn = document.getElementById('notifBtn');
  const notifBadge = document.getElementById('notifBadge');

  if (notifBtn && notifBadge) {
    // Function to update notification count
    async function updateNotificationCount() {
      try {
        const response = await fetch('/notifications/count');
        if (response.ok) {
          const data = await response.json();
          const count = data.count || 0;
          
          if (count > 0) {
            notifBadge.textContent = count;
            notifBadge.hidden = false;
          } else {
            notifBadge.hidden = true;
          }
        }
      } catch (error) {
        console.error('Error fetching notification count:', error);
      }
    }

    // Function to show notifications
    async function showNotifications() {
      try {
        const response = await fetch('/notifications');
        if (response.ok) {
          const data = await response.json();
          const notifications = data.notifications || [];
          
          if (notifications.length === 0) {
            window.alert('Aucune nouvelle notification.');
            return;
          }

          // Build notification message
          let message = '🔔 NOTIFICATIONS\n\n';
          notifications.forEach((notif, idx) => {
            const statusEmoji = {
              'accepted': '✅',
              'refused': '❌',
              'off': '🚫'
            }[notif.status] || '❓';
            
            const statusText = {
              'accepted': 'ACCEPTÉ',
              'refused': 'REFUSÉ',
              'off': 'INDISPONIBLE'
            }[notif.status] || notif.status.toUpperCase();
            
            message += `${idx + 1}. ${statusEmoji} Pack ${notif.pack} - ${statusText}\n`;
            if (notif.message) {
              message += `   Message: ${notif.message}\n`;
            }
            message += '\n';
          });

          // Show in alert window
          window.alert(message);

          // Mark as seen
          await fetch('/notifications/ack', { method: 'POST' });
          
          // Update count immediately
          await updateNotificationCount();
        } else if (response.status === 401) {
          window.alert('Session expirée. Veuillez vous reconnecter.');
        }
      } catch (error) {
        console.error('Error fetching notifications:', error);
        window.alert('Erreur lors de la récupération des notifications.');
      }
    }

    // Click handler for notification bell
    notifBtn.addEventListener('click', showNotifications);

    // Poll for notification count every 15 seconds
    updateNotificationCount(); // Initial check
    setInterval(updateNotificationCount, 15000);
  }
});