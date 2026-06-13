/*
 * TechStore — génère une illustration SVG cohérente par produit.
 * Lancer avec : node build-images.js  →  écrit dans images/<id>.svg
 *
 * Les visuels sont des illustrations vectorielles plates (flat design),
 * dessinées dans un style homogène pour tout le catalogue. Aucune
 * dépendance ni accès réseau : le site reste rapide et hors-ligne.
 */

"use strict";

const fs = require("fs");
const path = require("path");

const OUT = path.join(__dirname, "images");
fs.mkdirSync(OUT, { recursive: true });

const W = 800, H = 600;

// Palette cohérente avec la feuille de style.
const C = {
  bg1: "#272f5e", bg2: "#171c38",
  metal: "#c7cee6", metalDark: "#9aa3c7", metalLight: "#eef1ff",
  screen1: "#5b8cff", screen2: "#38e1b0",
  dark: "#11152e", dark2: "#1c2244",
  accent: "#5b8cff", accent2: "#38e1b0", warm: "#ffc94d", red: "#ff6b6b",
};

// Fond commun : rectangle arrondi avec dégradé + lueur centrale douce.
function frame(id, inner, glow = C.accent) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}" role="img">
  <defs>
    <linearGradient id="bg-${id}" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="${C.bg1}"/>
      <stop offset="1" stop-color="${C.bg2}"/>
    </linearGradient>
    <radialGradient id="glow-${id}" cx="0.5" cy="0.42" r="0.55">
      <stop offset="0" stop-color="${glow}" stop-opacity="0.28"/>
      <stop offset="1" stop-color="${glow}" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="scr-${id}" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="${C.screen1}"/>
      <stop offset="1" stop-color="${C.screen2}"/>
    </linearGradient>
  </defs>
  <rect width="${W}" height="${H}" rx="28" fill="url(#bg-${id})"/>
  <rect width="${W}" height="${H}" rx="28" fill="url(#glow-${id})"/>
  <g stroke-linejoin="round" stroke-linecap="round">
${inner}
  </g>
</svg>`;
}

// Petite ombre portée elliptique sous l'objet.
function shadow(cx = 400, cy = 500, rx = 230, ry = 30) {
  return `    <ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="#000" opacity="0.22"/>`;
}

// ---------------------------------------------------------------------------
// Illustrations par produit
// ---------------------------------------------------------------------------

const ART = {};

// Ordinateur portable
ART["pc-portable-pro15"] = (id) => frame(id, `
${shadow(400, 470, 250, 26)}
    <path d="M250 200 h300 a14 14 0 0 1 14 14 v176 h-328 v-176 a14 14 0 0 1 14 -14 z" fill="${C.dark2}"/>
    <rect x="268" y="218" width="264" height="158" rx="6" fill="url(#scr-${id})"/>
    <rect x="288" y="240" width="150" height="12" rx="6" fill="#ffffff" opacity="0.85"/>
    <rect x="288" y="266" width="200" height="9" rx="4" fill="#ffffff" opacity="0.5"/>
    <rect x="288" y="286" width="170" height="9" rx="4" fill="#ffffff" opacity="0.5"/>
    <rect x="288" y="320" width="120" height="34" rx="8" fill="#ffffff" opacity="0.9"/>
    <path d="M196 390 h408 l34 56 a10 10 0 0 1 -9 15 h-458 a10 10 0 0 1 -9 -15 z" fill="${C.metal}"/>
    <rect x="356" y="392" width="88" height="10" rx="5" fill="${C.metalDark}"/>
`, C.accent);

// PC de bureau gamer
ART["pc-bureau-gamer"] = (id) => frame(id, `
${shadow(400, 510, 150, 22)}
    <rect x="300" y="150" width="200" height="350" rx="18" fill="${C.dark2}"/>
    <rect x="300" y="150" width="200" height="350" rx="18" fill="none" stroke="${C.accent}" stroke-width="3" opacity="0.5"/>
    <rect x="326" y="184" width="148" height="150" rx="10" fill="${C.dark}"/>
    <circle cx="370" cy="240" r="26" fill="none" stroke="${C.accent2}" stroke-width="6"/>
    <circle cx="430" cy="240" r="26" fill="none" stroke="${C.accent}" stroke-width="6"/>
    <rect x="326" y="360" width="148" height="12" rx="6" fill="${C.warm}"/>
    <rect x="326" y="386" width="148" height="12" rx="6" fill="${C.accent}"/>
    <rect x="326" y="412" width="148" height="12" rx="6" fill="${C.accent2}"/>
    <circle cx="340" cy="466" r="7" fill="${C.accent2}"/>
    <circle cx="364" cy="466" r="7" fill="${C.warm}"/>
`, C.accent2);

// Écran (générateur paramétrable pour les deux moniteurs)
function ecran(id, glow, withBadge) {
  return frame(id, `
${shadow(400, 520, 170, 22)}
    <rect x="210" y="150" width="380" height="232" rx="16" fill="${C.dark2}"/>
    <rect x="230" y="170" width="340" height="192" rx="8" fill="url(#scr-${id})"/>
    <rect x="252" y="196" width="150" height="16" rx="8" fill="#fff" opacity="0.9"/>
    <rect x="252" y="226" width="250" height="10" rx="5" fill="#fff" opacity="0.5"/>
    <rect x="252" y="248" width="210" height="10" rx="5" fill="#fff" opacity="0.5"/>
    <rect x="252" y="300" width="120" height="34" rx="8" fill="#fff" opacity="0.9"/>
    <rect x="384" y="382" width="32" height="60" fill="${C.metalDark}"/>
    <rect x="320" y="442" width="160" height="18" rx="9" fill="${C.metal}"/>
    ${withBadge ? `<g transform="translate(508,300)"><rect x="0" y="0" width="86" height="34" rx="17" fill="${C.warm}"/><text x="43" y="23" font-family="system-ui,sans-serif" font-size="17" font-weight="700" fill="#3a2c00" text-anchor="middle">144Hz</text></g>` : ""}
`, glow);
}
ART["ecran-27-4k"] = (id) => ecran(id, C.accent, false);
ART["ecran-24-144hz"] = (id) => ecran(id, C.accent2, true);

// Clavier mécanique RGB
ART["clavier-meca"] = (id) => {
  const colors = [C.red, C.warm, C.accent2, C.accent, "#b07bff"];
  let keys = "";
  const cols = 14, rows = 4, kx = 40, ky = 40, ox = 150, oy = 230;
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const col = colors[(r + c) % colors.length];
      keys += `<rect x="${ox + c * kx + 4}" y="${oy + r * ky + 4}" width="${kx - 8}" height="${ky - 8}" rx="6" fill="${C.dark}"/>` +
              `<rect x="${ox + c * kx + 4}" y="${oy + r * ky + 26}" width="${kx - 8}" height="6" rx="3" fill="${col}" opacity="0.9"/>`;
    }
  }
  return frame(id, `
${shadow(400, 430, 290, 24)}
    <rect x="120" y="210" width="560" height="200" rx="20" fill="${C.dark2}"/>
    ${keys}
    <rect x="${ox + 60}" y="${oy + 3 * ky + 4}" width="180" height="${ky - 8}" rx="6" fill="${C.dark}"/>
`, C.accent2);
};

// Souris sans fil
ART["souris-sansfil"] = (id) => frame(id, `
${shadow(400, 500, 130, 20)}
    <path d="M400 160 c70 0 110 60 110 150 c0 90 -40 150 -110 150 s-110 -60 -110 -150 c0 -90 40 -150 110 -150 z" fill="${C.metal}"/>
    <path d="M400 160 c-70 0 -110 60 -110 150 h110 z" fill="${C.metalLight}"/>
    <rect x="392" y="196" width="16" height="54" rx="8" fill="${C.dark2}"/>
    <circle cx="400" cy="234" r="9" fill="${C.accent2}"/>
    <line x1="400" y1="160" x2="400" y2="196" stroke="${C.metalDark}" stroke-width="3"/>
`, C.accent2);

// Casque audio
ART["casque-audio"] = (id) => frame(id, `
${shadow(400, 500, 150, 22)}
    <path d="M250 360 v-40 a150 150 0 0 1 300 0 v40" fill="none" stroke="${C.metal}" stroke-width="26"/>
    <rect x="214" y="346" width="86" height="150" rx="30" fill="${C.dark2}"/>
    <rect x="232" y="366" width="50" height="110" rx="22" fill="${C.accent}"/>
    <rect x="500" y="346" width="86" height="150" rx="30" fill="${C.dark2}"/>
    <rect x="518" y="366" width="50" height="110" rx="22" fill="${C.accent}"/>
`, C.accent);

// Webcam Full HD
ART["webcam-hd"] = (id) => frame(id, `
${shadow(400, 470, 150, 22)}
    <rect x="280" y="200" width="240" height="150" rx="40" fill="${C.dark2}"/>
    <circle cx="400" cy="275" r="64" fill="${C.dark}"/>
    <circle cx="400" cy="275" r="46" fill="url(#scr-${id})"/>
    <circle cx="400" cy="275" r="22" fill="${C.dark}"/>
    <circle cx="416" cy="262" r="9" fill="#fff" opacity="0.8"/>
    <circle cx="486" cy="224" r="7" fill="${C.red}"/>
    <path d="M300 350 h200 l40 70 h-280 z" fill="${C.metalDark}"/>
    <rect x="360" y="420" width="80" height="22" rx="6" fill="${C.metal}"/>
`, C.accent);

// SSD externe
ART["ssd-1to"] = (id) => frame(id, `
${shadow(400, 470, 200, 22)}
    <rect x="240" y="230" width="320" height="200" rx="22" fill="${C.metal}"/>
    <rect x="240" y="230" width="320" height="200" rx="22" fill="none" stroke="${C.metalDark}" stroke-width="3"/>
    <rect x="276" y="266" width="150" height="20" rx="6" fill="${C.dark2}"/>
    <rect x="276" y="300" width="248" height="10" rx="5" fill="${C.metalDark}"/>
    <rect x="276" y="320" width="200" height="10" rx="5" fill="${C.metalDark}"/>
    <circle cx="504" cy="392" r="12" fill="${C.accent2}"/>
    <rect x="208" y="320" width="34" height="40" rx="5" fill="${C.dark2}"/>
`, C.accent2);

// Clé USB
ART["cle-usb-128"] = (id) => frame(id, `
${shadow(400, 470, 170, 20)}
    <rect x="250" y="270" width="240" height="90" rx="18" fill="${C.dark2}"/>
    <rect x="276" y="292" width="120" height="14" rx="7" fill="${C.accent2}"/>
    <rect x="490" y="296" width="60" height="38" rx="6" fill="${C.metal}"/>
    <rect x="550" y="304" width="20" height="22" rx="3" fill="${C.metalDark}"/>
`, C.accent2);

// Routeur Wi-Fi
ART["routeur-wifi6"] = (id) => frame(id, `
${shadow(400, 480, 180, 22)}
    <g stroke="${C.accent2}" stroke-width="10" fill="none" opacity="0.85">
      <path d="M330 250 a90 90 0 0 1 140 0"/>
      <path d="M300 215 a140 140 0 0 1 200 0"/>
    </g>
    <circle cx="400" cy="262" r="14" fill="${C.accent2}"/>
    <rect x="318" y="296" width="20" height="80" rx="10" fill="${C.metalDark}"/>
    <rect x="462" y="296" width="20" height="80" rx="10" fill="${C.metalDark}"/>
    <rect x="270" y="360" width="260" height="110" rx="22" fill="${C.dark2}"/>
    <circle cx="312" cy="415" r="9" fill="${C.accent2}"/>
    <circle cx="344" cy="415" r="9" fill="${C.warm}"/>
    <circle cx="376" cy="415" r="9" fill="${C.accent}"/>
`, C.accent2);

// Imprimante laser
ART["imprimante-laser"] = (id) => frame(id, `
${shadow(400, 490, 200, 22)}
    <rect x="320" y="150" width="160" height="70" rx="8" fill="${C.metalLight}"/>
    <rect x="338" y="160" width="124" height="40" rx="4" fill="${C.metal}"/>
    <rect x="250" y="300" width="300" height="160" rx="20" fill="${C.dark2}"/>
    <rect x="280" y="250" width="240" height="70" rx="8" fill="${C.metalLight}"/>
    <rect x="296" y="332" width="208" height="20" rx="10" fill="${C.dark}"/>
    <rect x="300" y="356" width="200" height="44" rx="6" fill="${C.metalLight}"/>
    <circle cx="500" cy="430" r="9" fill="${C.accent2}"/>
    <rect x="290" y="420" width="60" height="10" rx="5" fill="${C.metalDark}"/>
`, C.accent);

// ---------------------------------------------------------------------------

let n = 0;
for (const [id, gen] of Object.entries(ART)) {
  fs.writeFileSync(path.join(OUT, id + ".svg"), gen(id));
  n++;
}
console.log(`${n} illustrations écrites dans images/`);
