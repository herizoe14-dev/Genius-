// Service Worker for Genius Bot PWA
const CACHE_VERSION = 'v1';
const STATIC_CACHE = 'genius-bot-static-' + CACHE_VERSION;
const DYNAMIC_CACHE = 'genius-bot-dynamic-' + CACHE_VERSION;

// Static assets to cache on install
const STATIC_ASSETS = [
  '/',
  '/login',
  '/register',
  '/about',
  '/offline',
  '/static/css/style.css',
  '/static/js/script.js',
  '/static/manifest.json',
  '/static/icons/icon-72x72.png',
  '/static/icons/icon-96x96.png',
  '/static/icons/icon-128x128.png',
  '/static/icons/icon-144x144.png',
  '/static/icons/icon-152x152.png',
  '/static/icons/icon-192x192.png',
  '/static/icons/icon-384x384.png',
  '/static/icons/icon-512x512.png'
];

// Pages that require network (dynamic content)
const NETWORK_FIRST_URLS = [
  '/api/',
  '/download',
  '/shop'
];

// Install event - cache static assets
self.addEventListener('install', (event) => {
  console.log('[Service Worker] Installing...');
  event.waitUntil(
    caches.open(STATIC_CACHE)
      .then((cache) => {
        console.log('[Service Worker] Caching static assets');
        // Cache static assets, but don't fail if some are unavailable
        return Promise.allSettled(
          STATIC_ASSETS.map(url => 
            cache.add(url).catch(err => {
              console.warn(`[Service Worker] Failed to cache: ${url}`, err);
            })
          )
        );
      })
      .then(() => {
        console.log('[Service Worker] Install complete');
        return self.skipWaiting();
      })
  );
});

// Activate event - clean up old caches
self.addEventListener('activate', (event) => {
  console.log('[Service Worker] Activating...');
  event.waitUntil(
    caches.keys()
      .then((cacheNames) => {
        return Promise.all(
          cacheNames.map((cacheName) => {
            if (cacheName !== STATIC_CACHE && cacheName !== DYNAMIC_CACHE) {
              console.log('[Service Worker] Deleting old cache:', cacheName);
              return caches.delete(cacheName);
            }
          })
        );
      })
      .then(() => {
        console.log('[Service Worker] Activation complete');
        return self.clients.claim();
      })
  );
});

// Fetch event - serve from cache or network
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // Skip non-GET requests
  if (request.method !== 'GET') {
    return;
  }

  // Skip external requests
  if (url.origin !== location.origin) {
    return;
  }

  // Network-first strategy for API calls and dynamic pages
  if (NETWORK_FIRST_URLS.some(path => url.pathname.startsWith(path))) {
    event.respondWith(networkFirst(request));
    return;
  }

  // Cache-first strategy for static assets
  if (url.pathname.startsWith('/static/')) {
    event.respondWith(cacheFirst(request));
    return;
  }

  // Stale-while-revalidate for HTML pages
  event.respondWith(staleWhileRevalidate(request));
});

// Cache-first strategy
async function cacheFirst(request) {
  const cachedResponse = await caches.match(request);
  if (cachedResponse) {
    return cachedResponse;
  }
  
  try {
    const networkResponse = await fetch(request);
    if (networkResponse.ok) {
      const cache = await caches.open(STATIC_CACHE);
      cache.put(request, networkResponse.clone());
    }
    return networkResponse;
  } catch (error) {
    console.error('[Service Worker] Cache-first fetch failed:', error);
    return new Response('Offline - Resource not available', {
      status: 503,
      statusText: 'Service Unavailable'
    });
  }
}

// Network-first strategy
async function networkFirst(request) {
  try {
    const networkResponse = await fetch(request);
    if (networkResponse.ok) {
      const cache = await caches.open(DYNAMIC_CACHE);
      cache.put(request, networkResponse.clone());
    }
    return networkResponse;
  } catch (error) {
    console.log('[Service Worker] Network-first falling back to cache');
    const cachedResponse = await caches.match(request);
    if (cachedResponse) {
      return cachedResponse;
    }
    
    // Return offline page for navigation requests
    if (request.mode === 'navigate') {
      return caches.match('/offline');
    }
    
    return new Response('Offline - Resource not available', {
      status: 503,
      statusText: 'Service Unavailable'
    });
  }
}

// Stale-while-revalidate strategy
async function staleWhileRevalidate(request) {
  const cache = await caches.open(DYNAMIC_CACHE);
  const cachedResponse = await cache.match(request);
  
  const fetchPromise = fetch(request)
    .then((networkResponse) => {
      if (networkResponse.ok) {
        cache.put(request, networkResponse.clone());
      }
      return networkResponse;
    })
    .catch((error) => {
      console.log('[Service Worker] Stale-while-revalidate fetch failed:', error);
      return null;
    });

  // Wait for network response if no cache, otherwise return cache immediately
  if (cachedResponse) {
    return cachedResponse;
  }
  
  const networkResult = await fetchPromise;
  if (networkResult) {
    return networkResult;
  }
  
  return new Response('Offline', { status: 503, statusText: 'Service Unavailable' });
}

// Handle push notifications (for future use)
self.addEventListener('push', (event) => {
  console.log('[Service Worker] Push received');
  
  const options = {
    body: event.data ? event.data.text() : 'Nouvelle notification de Genius Bot',
    icon: '/static/icons/icon-192x192.png',
    badge: '/static/icons/icon-72x72.png',
    vibrate: [100, 50, 100],
    data: {
      dateOfArrival: Date.now(),
      primaryKey: 1
    },
    actions: [
      { action: 'explore', title: 'Voir' },
      { action: 'close', title: 'Fermer' }
    ]
  };

  event.waitUntil(
    self.registration.showNotification('Genius Bot', options)
  );
});

// Handle notification clicks
self.addEventListener('notificationclick', (event) => {
  console.log('[Service Worker] Notification click received');
  event.notification.close();

  if (event.action === 'explore') {
    event.waitUntil(
      clients.openWindow('/')
    );
  }
});

// Background sync (for future use)
self.addEventListener('sync', (event) => {
  console.log('[Service Worker] Background sync:', event.tag);
  
  if (event.tag === 'sync-downloads') {
    event.waitUntil(
      // Handle background sync for pending downloads
      Promise.resolve()
    );
  }
});
