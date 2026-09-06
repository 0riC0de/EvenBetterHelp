// Deliberately no API/chat caching: sensitive messages never enter CacheStorage.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));
self.addEventListener('push', (event) => {
  const data = event.data?.json() ?? {};
  event.waitUntil(self.registration.showNotification('EvenBetterHelp', {
    body: data.type === 'call' ? 'Incoming call. Open your inbox to answer.' : 'Your inbox has a new update.',
    icon: '/icon.svg', tag: 'helpdesk-update', data: { url: '/' },
  }));
});
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(self.clients.matchAll({ type: 'window' }).then((clients) => clients[0]?.focus() ?? self.clients.openWindow('/')));
});
