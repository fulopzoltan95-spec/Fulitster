const state = {
  audio: new Audio(),
  track: null,
  durationMs: 0,
  startTs: null,
  raf: null,
  stream: null,
  detector: 'BarcodeDetector' in window ? new BarcodeDetector({ formats: ['qr_code'] }) : null,
  scanning: false,
  installEvent: null,
  token: localStorage.getItem('spotifyToken') || '',
  theme: localStorage.getItem('hitsterTheme') || 'classic'
};

const els = {
  playPause: document.getElementById('playPause'),
  progressBar: document.querySelector('.progress__bar'),
  statusChip: document.getElementById('statusChip'),
  durationChip: document.getElementById('durationChip'),
  trackTitle: document.getElementById('trackTitle'),
  trackArtist: document.getElementById('trackArtist'),
  startScan: document.getElementById('startScan'),
  stopScan: document.getElementById('stopScan'),
  manualInput: document.getElementById('manualInput'),
  handleManual: document.getElementById('handleManual'),
  cameraFeed: document.getElementById('cameraFeed'),
  cameraCanvas: document.getElementById('cameraCanvas'),
  scanHint: document.getElementById('scanHint'),
  tokenInput: document.getElementById('tokenInput'),
  clearToken: document.getElementById('clearToken'),
  resetButton: document.getElementById('resetButton'),
  themePicker: document.getElementById('themePicker'),
  toast: document.getElementById('toast'),
  installButton: document.getElementById('installButton')
};

document.documentElement.dataset.theme = state.theme;
els.themePicker.value = state.theme;
els.tokenInput.value = state.token;

function setTheme(theme) {
  state.theme = theme;
  document.documentElement.dataset.theme = theme;
  localStorage.setItem('hitsterTheme', theme);
}

function toast(msg) {
  els.toast.textContent = msg;
  els.toast.classList.add('visible');
  setTimeout(() => els.toast.classList.remove('visible'), 2600);
}

function setStatus(text, highlight = false) {
  els.statusChip.textContent = text;
  els.statusChip.style.color = highlight ? '#0a0c14' : '';
  els.statusChip.style.background = highlight ? 'var(--accent)' : 'rgba(255,255,255,0.06)';
}

function formatMs(ms) {
  const total = Math.round(ms / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

function updateDuration(ms) {
  state.durationMs = ms;
  els.durationChip.textContent = formatMs(ms);
}

function resetProgress() {
  cancelAnimationFrame(state.raf);
  state.raf = null;
  state.startTs = null;
  els.progressBar.style.strokeDashoffset = 339.292;
}

function animateProgress(ts) {
  if (!state.startTs) state.startTs = ts;
  const elapsed = ts - state.startTs;
  const pct = Math.min(elapsed / state.durationMs, 1);
  const offset = 339.292 * (1 - pct);
  els.progressBar.style.strokeDashoffset = offset;
  if (pct < 1) {
    state.raf = requestAnimationFrame(animateProgress);
  } else {
    setStatus('Lejárt');
    els.playPause.textContent = '▶️';
  }
}

function stopAudio() {
  state.audio.pause();
  state.audio.currentTime = 0;
  resetProgress();
  setStatus('Állj');
  els.playPause.textContent = '▶️';
}

function play(url) {
  if (!url) {
    toast('Nincs elérhető preview URL.');
    return;
  }
  resetProgress();
  state.audio.src = url;
  state.audio.play().then(() => {
    setStatus('Lejátszás', true);
    els.playPause.textContent = '⏸';
    state.startTs = performance.now();
    state.raf = requestAnimationFrame(animateProgress);
  }).catch(() => toast('A lejátszás elutasítva (autoplay?).'));
}

function setTrack(meta) {
  state.track = meta;
  els.trackTitle.textContent = meta.title || 'Ismeretlen dal';
  els.trackArtist.textContent = meta.artist || '';
  updateDuration(meta.durationMs || 30000);
}

function parseHitster(url) {
  const base = /^(?:http:\/\/|https:\/\/)?www\.hitstergame\.com\/(.+?)\/(\d+)$/;
  const nordics = /^(?:http:\/\/|https:\/\/)?app\.hitsternordics\.com\/resources\/songs\/(\d+)\$/;
  const hu = /^(?:http:\/\/|https:\/\/)?www\.hitstergame\.com\/([^/]+)\/([^/]+)\/(\d+)$/;

  let match = url.match(base);
  if (match) return { lang: match[1].replace('/', '-'), id: match[2] };
  match = url.match(nordics);
  if (match) return { lang: 'nordics', id: match[1] };
  match = url.match(hu);
  if (match) return { lang: 'hu', id: match[1] };
  return null;
}

async function loadCsv(lang) {
  const file = `data/hitster-${lang}.csv`;
  const res = await fetch(file);
  if (!res.ok) throw new Error('CSV nem olvasható');
  return res.text();
}

async function findSong(lang, id) {
  try {
    const csv = await loadCsv(lang);
    const parsed = Papa.parse(csv, { header: true, skipEmptyLines: true });
    const row = parsed.data.find((r) => r['Card#']?.replace('\uFEFF', '').trim() === id);
    if (!row) return null;
    return { artist: row['Artist']?.trim() || '', title: row['Title']?.trim() || '', youtube: row['URL'] };
  } catch (e) {
    console.error(e);
    toast('Nem található a kártya a CSV-ben.');
    return null;
  }
}

function isSpotifyUri(value) {
  return /^spotify:(track|album|playlist|artist):[A-Za-z0-9]+$/.test(value);
}

function isSpotifyUrl(value) {
  return /^https?:\/\/open\.spotify\.com\/(track|album|playlist|artist)\//.test(value);
}

function spotifyUrlToUri(url) {
  const m = url.match(/^https?:\/\/open\.spotify\.com\/(track|album|playlist|artist)\/([A-Za-z0-9]+)/);
  return m ? `spotify:${m[1]}:${m[2]}` : url;
}

async function searchOnSpotify({ title, artist }) {
  const token = state.token || els.tokenInput.value.trim();
  if (!token) {
    toast('Adj meg Spotify tokent a kereséshez!');
    return null;
  }
  state.token = token;
  localStorage.setItem('spotifyToken', token);

  const query = `track:"${title}" artist:"${artist}"`;
  const url = `https://api.spotify.com/v1/search?q=${encodeURIComponent(query)}&type=track&limit=1`;
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) {
    toast('Spotify keresés sikertelen');
    return null;
  }
  const json = await res.json();
  const track = json.tracks?.items?.[0];
  if (!track) return null;
  return {
    title: track.name,
    artist: track.artists?.map((a) => a.name).join(', '),
    preview: track.preview_url,
    durationMs: track.duration_ms,
    uri: track.uri
  };
}

async function handlePayload(value) {
  if (!value) return;
  setStatus('Keresés');
  stopAudio();

  let meta = null;
  let payload = value.trim();
  if (parseHitster(payload)) {
    const { lang, id } = parseHitster(payload);
    meta = await findSong(lang, id);
    if (meta) toast(`Hitster kártya: ${meta.artist} - ${meta.title}`);
  } else if (isSpotifyUrl(payload)) {
    payload = spotifyUrlToUri(payload);
  }

  if (!meta && (isSpotifyUri(payload))) {
    const parts = payload.split(':');
    const type = parts[1];
    const id = parts[2];
    if (type === 'track') {
      meta = await searchOnSpotify({ title: id, artist: '' });
    }
  }

  if (meta && (!meta.preview || !meta.durationMs)) {
    const enriched = await searchOnSpotify(meta);
    meta = { ...meta, ...enriched };
  } else if (!meta) {
    const enriched = await searchOnSpotify({ title: payload, artist: '' });
    meta = enriched;
  }

  if (!meta) {
    toast('Nem sikerült feldolgozni a QR/link tartalmát.');
    return;
  }

  setTrack(meta);
  play(meta.preview);
}

async function startScan() {
  if (!navigator.mediaDevices?.getUserMedia) {
    toast('A böngésző nem támogatja a kamerát.');
    return;
  }
  try {
    state.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
    els.cameraFeed.srcObject = state.stream;
    await els.cameraFeed.play();
    state.scanning = true;
    els.startScan.disabled = true;
    els.stopScan.disabled = false;
    scanLoop();
  } catch (e) {
    toast('Kamera engedély megtagadva.');
  }
}

function stopScan() {
  state.scanning = false;
  els.startScan.disabled = false;
  els.stopScan.disabled = true;
  if (state.stream) {
    state.stream.getTracks().forEach((t) => t.stop());
    state.stream = null;
  }
}

async function scanLoop() {
  if (!state.scanning) return;
  if (!state.detector) {
    els.scanHint.textContent = 'A böngésző nem támogatja a QR detektálást, használj manuális bevitelt.';
    return;
  }
  const canvas = els.cameraCanvas;
  const video = els.cameraFeed;
  const ctx = canvas.getContext('2d');
  canvas.width = video.videoWidth;
  canvas.height = video.videoHeight;
  ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
  try {
    const codes = await state.detector.detect(canvas);
    const qr = codes[0];
    if (qr?.rawValue) {
      stopScan();
      await handlePayload(qr.rawValue);
      return;
    }
  } catch (e) {
    console.error('QR detect error', e);
  }
  requestAnimationFrame(scanLoop);
}

function registerSw() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/service-worker.js');
  }
}

function setupInstallPrompt() {
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    state.installEvent = e;
    els.installButton.style.display = 'inline-flex';
  });

  els.installButton.addEventListener('click', async () => {
    if (!state.installEvent) return;
    state.installEvent.prompt();
    const { outcome } = await state.installEvent.userChoice;
    toast(outcome === 'accepted' ? 'Telepítve a kezdőképernyőre!' : 'Telepítés megszakítva');
    state.installEvent = null;
  });
}

function bindUi() {
  els.themePicker.addEventListener('change', (e) => setTheme(e.target.value));
  els.playPause.addEventListener('click', () => {
    if (state.audio.paused) {
      state.audio.play();
      setStatus('Lejátszás', true);
      els.playPause.textContent = '⏸';
    } else {
      state.audio.pause();
      setStatus('Szünet');
      els.playPause.textContent = '▶️';
    }
  });

  els.startScan.addEventListener('click', startScan);
  els.stopScan.addEventListener('click', stopScan);
  els.handleManual.addEventListener('click', () => handlePayload(els.manualInput.value));
  els.tokenInput.addEventListener('change', (e) => {
    state.token = e.target.value;
    localStorage.setItem('spotifyToken', state.token);
  });
  els.clearToken.addEventListener('click', () => {
    state.token = '';
    els.tokenInput.value = '';
    localStorage.removeItem('spotifyToken');
  });
  els.resetButton.addEventListener('click', () => {
    stopScan();
    stopAudio();
    setTrack({ title: 'Várakozás…', artist: 'Szkenneld be a kártyát!', durationMs: 0 });
  });
}

function init() {
  setTheme(state.theme);
  bindUi();
  registerSw();
  setupInstallPrompt();
}

init();
