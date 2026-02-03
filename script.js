document.addEventListener('DOMContentLoaded', function () {
  const openBtn = document.getElementById('openSidebar');
  const closeBtn = document.getElementById('closeSidebar');
  const sidebar = document.getElementById('sidebar');
  const overlay = document.getElementById('overlay');
  const notificationBtn = document.getElementById('notificationBtn');
  const purchaseAlert = document.getElementById('purchaseAlert');
  const closePurchaseAlert = document.getElementById('closePurchaseAlert');

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

  function showPurchaseAlert(){
    if(!purchaseAlert) return;
    purchaseAlert.classList.add('show');
  }

  function hidePurchaseAlert(){
    if(!purchaseAlert) return;
    purchaseAlert.classList.remove('show');
  }

  function removeNotificationBadge(){
    if(!notificationBtn) return;
    const badge = notificationBtn.querySelector('.badge');
    if(badge) badge.remove();
  }

  async function markPurchaseRead(){
    if(!purchaseAlert) return;
    try{
      const csrfToken = document.querySelector('meta[name="csrf-token"]')?.getAttribute('content') || '';
      const response = await fetch('/purchase-status/read', {
        method:'POST',
        headers:{
          'X-Requested-With':'fetch',
          'X-CSRF-Token': csrfToken
        }
      });
      if(!response.ok){
        throw new Error(`Failed to mark purchase notification as read: ${response.status} ${response.statusText}`);
      }
      return true;
    }catch(e){
      console.error('Unable to update notification status', e);
    }
    return false;
  }

  if(notificationBtn && purchaseAlert){
    notificationBtn.addEventListener('click', async () => {
      showPurchaseAlert();
      const success = await markPurchaseRead();
      if(success){
        removeNotificationBadge();
      }
    });
  }

  if(closePurchaseAlert){
    closePurchaseAlert.addEventListener('click', async () => {
      hidePurchaseAlert();
      const success = await markPurchaseRead();
      if(success){
        removeNotificationBadge();
      }
    });
  }

  // close with Escape key
  document.addEventListener('keydown', function(e){
    if(e.key === 'Escape'){
      closeSidebar();
      hidePurchaseAlert();
    }
  });

});
