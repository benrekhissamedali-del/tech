/*
 * Infosphère — une visualisation 360° des signaux numériques invisibles
 * (antennes cellulaires, satellites GPS, routeurs Wi-Fi) autour de vous.
 *
 * Comme l'application originale « The Architecture of Radio », ceci n'est
 * pas un outil de mesure : les sources sont générées de façon plausible et
 * déterministe à partir de votre position GPS.
 */

"use strict";

// ---------------------------------------------------------------------------
// Utilitaires
// ---------------------------------------------------------------------------

const TAU = Math.PI * 2;
const DEG = Math.PI / 180;

function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function seedFromLocation(lat, lon) {
  // Quantifie la position (~100 m) pour que le même lieu donne le même monde.
  const a = Math.round(lat * 1000);
  const b = Math.round(lon * 1000);
  return ((a * 73856093) ^ (b * 19349663)) >>> 0;
}

function lerp(a, b, t) { return a + (b - a) * t; }

// Direction (azimut en degrés, élévation en degrés, distance visuelle) -> xyz
// Repère : x = est, y = haut, z = nord.
function sph(azDeg, elDeg, r) {
  const az = azDeg * DEG, el = elDeg * DEG;
  return {
    x: r * Math.cos(el) * Math.sin(az),
    y: r * Math.sin(el),
    z: r * Math.cos(el) * Math.cos(az),
  };
}

// ---------------------------------------------------------------------------
// Génération du « monde » de signaux
// ---------------------------------------------------------------------------

const OPERATORS = ["Orange", "SFR", "Bouygues Telecom", "Free Mobile"];
const TECHS = ["3G · 2100 MHz", "4G · 800 MHz", "4G · 1800 MHz", "4G · 2600 MHz", "5G · 3500 MHz"];
const SSID_PREFIXES = ["Livebox-", "Freebox-", "SFR_", "Bbox-", "WiFi-Invite-", "NETGEAR", "TP-Link_"];
const CONSTELLATIONS = [
  { name: "GPS (NAVSTAR)", alt: "20 180 km" },
  { name: "Galileo", alt: "23 222 km" },
  { name: "GLONASS", alt: "19 130 km" },
  { name: "BeiDou", alt: "21 528 km" },
];

function hex4(rand) {
  return Math.floor(rand() * 0xffff).toString(16).toUpperCase().padStart(4, "0");
}

function buildWorld(seed) {
  const rand = mulberry32(seed);
  const sources = [];

  // Antennes cellulaires : proches de l'horizon, distances de 150 m à 3 km.
  const nCell = 8 + Math.floor(rand() * 8);
  for (let i = 0; i < nCell; i++) {
    const az = rand() * 360;
    const distM = 150 + rand() * 2850;
    const el = lerp(6, 0.5, Math.min(distM / 3000, 1)) + rand() * 2;
    const r = lerp(30, 58, Math.min(distM / 3000, 1));
    sources.push({
      type: "cell",
      pos: sph(az, el, r),
      az, el,
      color: "#ff8a5c",
      size: 5,
      title: `Antenne ${OPERATORS[Math.floor(rand() * OPERATORS.length)]}`,
      detail: `${TECHS[Math.floor(rand() * TECHS.length)]} · ≈ ${Math.round(distM / 10) * 10} m · azimut ${Math.round(az)}°\nStation de base du réseau mobile. Votre téléphone lui parle en permanence, même en veille.`,
    });
  }

  // Routeurs Wi-Fi : tout proches, tout autour, souvent un peu au-dessus
  // ou en dessous (immeubles voisins).
  const nWifi = 24 + Math.floor(rand() * 30);
  for (let i = 0; i < nWifi; i++) {
    const az = rand() * 360;
    const el = (rand() - 0.45) * 50;
    const distM = 5 + rand() * 70;
    const r = lerp(12, 30, distM / 75);
    const prefix = SSID_PREFIXES[Math.floor(rand() * SSID_PREFIXES.length)];
    sources.push({
      type: "wifi",
      pos: sph(az, el, r),
      az, el,
      color: "#7ee8a2",
      size: 3.5,
      title: `${prefix}${hex4(rand)}`,
      detail: `Réseau Wi-Fi · 2,4 / 5 GHz · ≈ ${Math.round(distM)} m\nUn routeur domestique émet en continu des trames balises (~10 par seconde) pour annoncer sa présence.`,
    });
  }

  // Satellites GPS : très haut dans le ciel.
  const nSat = 9 + Math.floor(rand() * 5);
  for (let i = 0; i < nSat; i++) {
    const az = rand() * 360;
    const el = 22 + rand() * 62;
    const c = CONSTELLATIONS[Math.floor(rand() * CONSTELLATIONS.length)];
    const prn = 1 + Math.floor(rand() * 32);
    sources.push({
      type: "sat",
      pos: sph(az, el, 62),
      az, el,
      drift: (rand() - 0.5) * 0.25, // dérive lente en azimut (°/s)
      color: "#f5d76e",
      size: 4,
      title: `Satellite ${c.name} · PRN ${prn}`,
      detail: `Altitude ${c.alt} · élévation ${Math.round(el)}°\nSon signal met ~70 ms pour vous atteindre. Il transporte l'heure atomique qui permet à votre téléphone de se localiser.`,
    });
  }

  return sources;
}

// ---------------------------------------------------------------------------
// Caméra : orientation de l'appareil + glissement tactile en secours
// ---------------------------------------------------------------------------

const camera = {
  yaw: 0,          // cap en radians (0 = nord)
  pitch: 0,        // radians, positif = vers le haut
  fov: 70 * DEG,
  hasSensor: false,
  targetYaw: 0,
  targetPitch: 0,
};

function onOrientation(e) {
  let heading = null;
  if (typeof e.webkitCompassHeading === "number") {
    heading = e.webkitCompassHeading; // iOS : déjà un cap boussole
  } else if (e.alpha != null) {
    heading = e.absolute === false ? 360 - e.alpha : 360 - e.alpha;
  }
  if (heading == null || e.beta == null) return;
  camera.hasSensor = true;
  camera.targetYaw = heading * DEG;
  camera.targetPitch = (e.beta - 90) * -DEG; // téléphone vertical = horizon
}

function setupSensors() {
  const evt = "ondeviceorientationabsolute" in window
    ? "deviceorientationabsolute" : "deviceorientation";
  window.addEventListener(evt, onOrientation, true);
}

async function requestSensorPermission() {
  // iOS 13+ exige une demande explicite depuis un geste utilisateur.
  if (typeof DeviceOrientationEvent !== "undefined" &&
      typeof DeviceOrientationEvent.requestPermission === "function") {
    try {
      const res = await DeviceOrientationEvent.requestPermission();
      if (res !== "granted") return false;
    } catch { return false; }
  }
  setupSensors();
  return true;
}

function setupDrag(canvas) {
  let dragging = false, lastX = 0, lastY = 0;
  const start = (x, y) => { dragging = true; lastX = x; lastY = y; };
  const move = (x, y) => {
    if (!dragging) return;
    const k = camera.fov / canvas.clientHeight;
    camera.targetYaw -= (x - lastX) * k;
    camera.targetPitch += (y - lastY) * k;
    camera.targetPitch = Math.max(-1.45, Math.min(1.45, camera.targetPitch));
    lastX = x; lastY = y;
  };
  canvas.addEventListener("pointerdown", (e) => start(e.clientX, e.clientY));
  canvas.addEventListener("pointermove", (e) => move(e.clientX, e.clientY));
  window.addEventListener("pointerup", () => { dragging = false; });
}

// ---------------------------------------------------------------------------
// Rendu
// ---------------------------------------------------------------------------

const state = {
  sources: [],
  pulses: [],     // impulsions voyageant des sources vers le spectateur
  dome: [],       // points de la grille sphérique
  projected: [],  // positions écran des sources (pour le toucher)
  lastTime: 0,
};

function buildDome() {
  const pts = [];
  for (let el = -30; el <= 80; el += 9) {
    const circumference = Math.cos(el * DEG);
    const n = Math.max(8, Math.round(64 * circumference));
    for (let i = 0; i < n; i++) {
      pts.push(sph((i / n) * 360 + el * 1.7, el, 70));
    }
  }
  return pts;
}

function project(p, w, h) {
  // Rotation monde -> caméra (lacet puis tangage), puis perspective.
  const cy = Math.cos(-camera.yaw), sy = Math.sin(-camera.yaw);
  const x1 = p.x * cy + p.z * sy;
  const z1 = -p.x * sy + p.z * cy;
  const cp = Math.cos(-camera.pitch), sp = Math.sin(-camera.pitch);
  const y2 = p.y * cp - z1 * sp;
  const z2 = p.y * sp + z1 * cp;
  if (z2 <= 0.1) return null;
  const f = (h / 2) / Math.tan(camera.fov / 2);
  return { x: w / 2 + (x1 / z2) * f, y: h / 2 - (y2 / z2) * f, depth: z2 };
}

function spawnPulse(rand) {
  const src = state.sources[Math.floor(Math.random() * state.sources.length)];
  state.pulses.push({ src, t: 0, speed: 0.25 + Math.random() * 0.5 });
}

function drawScene(ctx, w, h, dt, time) {
  ctx.fillStyle = "#10106b";
  ctx.fillRect(0, 0, w, h);

  // Lissage de la caméra vers l'orientation cible.
  let dyaw = camera.targetYaw - camera.yaw;
  dyaw = ((dyaw + Math.PI) % TAU + TAU) % TAU - Math.PI;
  camera.yaw += dyaw * Math.min(1, dt * 8);
  camera.pitch += (camera.targetPitch - camera.pitch) * Math.min(1, dt * 8);

  // Dôme de points.
  ctx.fillStyle = "rgba(255,255,255,0.45)";
  for (const p of state.dome) {
    const s = project(p, w, h);
    if (!s) continue;
    const r = Math.max(0.4, 90 / s.depth * 0.9);
    ctx.globalAlpha = Math.min(0.5, 28 / s.depth);
    ctx.fillRect(s.x, s.y, r, r);
  }
  ctx.globalAlpha = 1;

  // Dérive lente des satellites.
  for (const src of state.sources) {
    if (src.type === "sat") {
      src.az = (src.az + src.drift * dt) % 360;
      src.pos = sph(src.az, src.el, 62);
    }
  }

  // Lignes des sources vers le bas de la sphère (effet « toile »).
  ctx.lineWidth = 1;
  state.projected.length = 0;
  for (const src of state.sources) {
    const s = project(src.pos, w, h);
    if (s) {
      state.projected.push({ src, x: s.x, y: s.y });

      // Halo pulsant.
      const pulse = 0.6 + 0.4 * Math.sin(time * 2 + src.az);
      ctx.beginPath();
      ctx.arc(s.x, s.y, src.size * (1.6 + pulse) * (40 / s.depth), 0, TAU);
      ctx.fillStyle = src.color + "22";
      ctx.fill();

      // Le nœud lui-même.
      ctx.beginPath();
      ctx.arc(s.x, s.y, Math.max(1.5, src.size * (40 / s.depth)), 0, TAU);
      ctx.fillStyle = src.color;
      ctx.fill();
    }
  }

  // Impulsions : éclats blancs voyageant de la source vers le spectateur.
  if (Math.random() < dt * 14 && state.sources.length) spawnPulse();
  for (let i = state.pulses.length - 1; i >= 0; i--) {
    const pu = state.pulses[i];
    pu.t += dt * pu.speed;
    if (pu.t >= 1) { state.pulses.splice(i, 1); continue; }
    // Position interpolée vers un point légèrement sous la caméra.
    const k = pu.t;
    const p = {
      x: pu.src.pos.x * (1 - k),
      y: pu.src.pos.y * (1 - k) - 2 * k,
      z: pu.src.pos.z * (1 - k),
    };
    const a = project(p, w, h);
    if (!a) continue;
    // Petit éclat triangulaire orienté vers le centre de l'écran.
    const ang = Math.atan2(h / 2 - a.y, w / 2 - a.x);
    const len = Math.min(46, 260 / a.depth) * (0.5 + k);
    ctx.save();
    ctx.translate(a.x, a.y);
    ctx.rotate(ang);
    ctx.globalAlpha = 0.85 * Math.sin(Math.PI * k);
    ctx.fillStyle = "#ffffff";
    ctx.beginPath();
    ctx.moveTo(len, 0);
    ctx.lineTo(-len * 0.4, -len * 0.16);
    ctx.lineTo(-len * 0.4, len * 0.16);
    ctx.closePath();
    ctx.fill();
    ctx.restore();
  }
  ctx.globalAlpha = 1;
}

// ---------------------------------------------------------------------------
// Splash : champ d'éclats animé pendant le chargement
// ---------------------------------------------------------------------------

function runSplash(canvas) {
  const ctx = canvas.getContext("2d");
  const shards = Array.from({ length: 60 }, () => ({
    x: Math.random(), y: Math.random(),
    a: Math.random() * TAU,
    v: 0.01 + Math.random() * 0.05,
    s: 4 + Math.random() * 26,
  }));
  let alive = true;
  function frame() {
    if (!alive) return;
    const w = canvas.width = canvas.clientWidth * devicePixelRatio;
    const h = canvas.height = canvas.clientHeight * devicePixelRatio;
    ctx.clearRect(0, 0, w, h);
    ctx.fillStyle = "#ffffff";
    for (const sh of shards) {
      sh.x = (sh.x + Math.cos(sh.a) * sh.v * 0.004 + 1) % 1;
      sh.y = (sh.y + Math.sin(sh.a) * sh.v * 0.004 + 1) % 1;
      const s = sh.s * devicePixelRatio;
      ctx.save();
      ctx.translate(sh.x * w, sh.y * h);
      ctx.rotate(sh.a);
      ctx.globalAlpha = 0.5 + 0.5 * Math.sin(sh.a * 7);
      ctx.beginPath();
      ctx.moveTo(s, 0);
      ctx.lineTo(-s * 0.35, -s * 0.13);
      ctx.lineTo(-s * 0.35, s * 0.13);
      ctx.closePath();
      ctx.fill();
      ctx.restore();
    }
    ctx.globalAlpha = 1;
    requestAnimationFrame(frame);
  }
  frame();
  return () => { alive = false; };
}

// ---------------------------------------------------------------------------
// Interface
// ---------------------------------------------------------------------------

const $ = (id) => document.getElementById(id);
const DIRS = ["N", "NE", "E", "SE", "S", "SO", "O", "NO"];

function showInfo(src) {
  $("info-title").textContent = src.title;
  $("info-detail").textContent = src.detail;
  $("info-card").hidden = false;
}

function setupTap(canvas) {
  let downX = 0, downY = 0, downT = 0;
  canvas.addEventListener("pointerdown", (e) => {
    downX = e.clientX; downY = e.clientY; downT = performance.now();
  });
  canvas.addEventListener("pointerup", (e) => {
    if (performance.now() - downT > 350) return;
    if (Math.hypot(e.clientX - downX, e.clientY - downY) > 12) return;
    const px = e.clientX * devicePixelRatio, py = e.clientY * devicePixelRatio;
    let best = null, bestD = 48 * devicePixelRatio;
    for (const p of state.projected) {
      const d = Math.hypot(p.x - px, p.y - py);
      if (d < bestD) { bestD = d; best = p.src; }
    }
    if (best) showInfo(best);
    else $("info-card").hidden = true;
  });
}

async function getLocation() {
  return new Promise((resolve) => {
    if (!("geolocation" in navigator)) return resolve(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lon: pos.coords.longitude }),
      () => resolve(null),
      { timeout: 8000, maximumAge: 600000 }
    );
  });
}

async function main() {
  const stopSplash = runSplash($("splash-canvas"));
  const status = $("splash-status");

  status.textContent = "recherche de votre position…";
  const loc = await getLocation();

  let seed;
  if (loc) {
    seed = seedFromLocation(loc.lat, loc.lon);
    $("hud-coords").textContent =
      `${loc.lat.toFixed(4)}°, ${loc.lon.toFixed(4)}°`;
    status.textContent = "cartographie des signaux autour de vous…";
  } else {
    seed = 0xC0FFEE;
    $("hud-coords").textContent = "position inconnue";
    status.textContent = "position indisponible — monde de démonstration";
  }

  state.sources = buildWorld(seed);
  state.dome = buildDome();

  $("count-cell").textContent = state.sources.filter(s => s.type === "cell").length;
  $("count-wifi").textContent = state.sources.filter(s => s.type === "wifi").length;
  $("count-sat").textContent = state.sources.filter(s => s.type === "sat").length;

  const btn = $("start-btn");
  btn.hidden = false;
  btn.addEventListener("click", async () => {
    await requestSensorPermission(); // doit venir d'un geste utilisateur (iOS)
    stopSplash();
    $("splash").hidden = true;
    $("app").hidden = false;
    startRenderLoop();
    setTimeout(() => $("hint").classList.add("faded"), 7000);
  }, { once: true });
}

function startRenderLoop() {
  const canvas = $("scene");
  const ctx = canvas.getContext("2d");
  setupDrag(canvas);
  setupTap(canvas);
  $("info-close").addEventListener("click", () => { $("info-card").hidden = true; });

  function frame(t) {
    const time = t / 1000;
    const dt = Math.min(0.05, time - (state.lastTime || time));
    state.lastTime = time;

    const w = canvas.width = canvas.clientWidth * devicePixelRatio;
    const h = canvas.height = canvas.clientHeight * devicePixelRatio;
    drawScene(ctx, w, h, dt, time);

    // Boussole.
    const deg = ((camera.yaw / DEG) % 360 + 360) % 360;
    $("compass-deg").textContent = `${Math.round(deg)}°`;
    $("compass-dir").textContent = DIRS[Math.round(deg / 45) % 8];

    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
}

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("sw.js").catch(() => {});
}

main();
