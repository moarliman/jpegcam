self.addEventListener('install', function(e) {
    e.waitUntil(caches.open('jpegcam-v1').then(function(c) { return c.add('/'); }));
});
self.addEventListener('fetch', function(e) {
    e.respondWith(fetch(e.request).catch(function() { return caches.match('/'); }));
});
