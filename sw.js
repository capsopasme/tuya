// 离线缓存：优先走网络，失败时用缓存，所以更新代码后刷新即可生效
const CACHE = 'tuya-v1';
const FILES = [
  './',
  'index.html',
  'css/style.css',
  'js/config.js',
  'js/audio.js',
  'js/world.js',
  'js/render.js',
  'js/ui.js',
  'js/input.js',
  'js/main.js',
  'manifest.webmanifest',
  'icon.svg',
];

self.addEventListener('install', function (e) {
  e.waitUntil(caches.open(CACHE).then(function (c) { return c.addAll(FILES); }));
  self.skipWaiting();
});

self.addEventListener('activate', function (e) {
  e.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.filter(function (k) { return k !== CACHE; }).map(function (k) { return caches.delete(k); }));
    })
  );
  self.clients.claim();
});

self.addEventListener('fetch', function (e) {
  if (e.request.method !== 'GET') return;
  e.respondWith(
    fetch(e.request)
      .then(function (res) {
        const copy = res.clone();
        caches.open(CACHE).then(function (c) { c.put(e.request, copy); });
        return res;
      })
      .catch(function () { return caches.match(e.request); })
  );
});
