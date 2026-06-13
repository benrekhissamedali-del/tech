/*
 * TechStore — données produits + logique de panier partagées.
 * Tout est en JavaScript pur, sans dépendance. Le panier est conservé
 * dans le localStorage du navigateur.
 */

"use strict";

// ---------------------------------------------------------------------------
// Catalogue produits
// ---------------------------------------------------------------------------
// Chaque produit : id unique, nom, catégorie, prix en euros, note /5,
// stock, icône (emoji) et description. Les icônes évitent toute
// dépendance à des images externes — le site fonctionne hors connexion.

const PRODUITS = [
  {
    id: "pc-portable-pro15",
    nom: "Ordinateur portable Pro 15\"",
    categorie: "Ordinateurs",
    prix: 1099,
    note: 4.7,
    stock: 12,
    icone: "💻",
    marque: "NovaTech",
    description:
      "Ordinateur portable 15,6\" Full HD, processeur 8 cœurs, 16 Go de RAM " +
      "et SSD 512 Go. Idéal pour le travail, les études et la création.",
    specs: ["Écran 15,6\" Full HD IPS", "16 Go RAM DDR4", "SSD NVMe 512 Go", "Autonomie 10 h", "Windows 11"],
  },
  {
    id: "pc-bureau-gamer",
    nom: "PC de bureau Gamer RTX",
    categorie: "Ordinateurs",
    prix: 1599,
    note: 4.9,
    stock: 5,
    icone: "🖥️",
    marque: "TitanPC",
    description:
      "Tour gaming hautes performances avec carte graphique dédiée, " +
      "refroidissement liquide et éclairage RGB. Prête pour les jeux en 1440p.",
    specs: ["CPU 12 cœurs", "Carte graphique RTX 8 Go", "32 Go RAM", "SSD 1 To", "Watercooling RGB"],
  },
  {
    id: "ecran-27-4k",
    nom: "Écran 27\" 4K UHD",
    categorie: "Écrans",
    prix: 329,
    note: 4.6,
    stock: 20,
    icone: "🖥️",
    marque: "ViewMax",
    description:
      "Moniteur 27 pouces 4K UHD avec dalle IPS, 99 % sRGB et ports " +
      "HDMI / DisplayPort. Parfait pour la bureautique et le graphisme.",
    specs: ["27\" 3840×2160", "Dalle IPS", "60 Hz", "HDMI + DisplayPort", "Pied réglable"],
  },
  {
    id: "ecran-24-144hz",
    nom: "Écran Gaming 24\" 144 Hz",
    categorie: "Écrans",
    prix: 199,
    note: 4.5,
    stock: 15,
    icone: "🖥️",
    marque: "ViewMax",
    description:
      "Moniteur 24\" Full HD 144 Hz, temps de réponse 1 ms, FreeSync. " +
      "Conçu pour le jeu compétitif fluide et sans déchirure.",
    specs: ["24\" 1920×1080", "144 Hz", "1 ms", "FreeSync", "Sans bordure"],
  },
  {
    id: "clavier-meca",
    nom: "Clavier mécanique RGB",
    categorie: "Périphériques",
    prix: 79,
    note: 4.8,
    stock: 40,
    icone: "⌨️",
    marque: "KeyForce",
    description:
      "Clavier mécanique rétroéclairé RGB avec switches tactiles, " +
      "repose-poignets et touches anti-ghosting. Disposition AZERTY.",
    specs: ["Switches mécaniques", "Rétroéclairage RGB", "Anti-ghosting", "AZERTY", "Câble tressé USB-C"],
  },
  {
    id: "souris-sansfil",
    nom: "Souris sans fil ergonomique",
    categorie: "Périphériques",
    prix: 39,
    note: 4.4,
    stock: 60,
    icone: "🖱️",
    marque: "KeyForce",
    description:
      "Souris sans fil silencieuse, capteur 1600 DPI ajustable et " +
      "autonomie de 12 mois. Confortable pour un usage prolongé.",
    specs: ["Sans fil 2,4 GHz", "1600 DPI réglable", "Clics silencieux", "6 boutons", "Autonomie 12 mois"],
  },
  {
    id: "casque-audio",
    nom: "Casque audio sans fil",
    categorie: "Audio",
    prix: 89,
    note: 4.6,
    stock: 25,
    icone: "🎧",
    marque: "SoundWave",
    description:
      "Casque Bluetooth à réduction de bruit active, 30 h d'autonomie " +
      "et micro intégré. Son immersif pour la musique et les appels.",
    specs: ["Bluetooth 5.3", "Réduction de bruit active", "Autonomie 30 h", "Micro intégré", "Pliable"],
  },
  {
    id: "webcam-hd",
    nom: "Webcam Full HD 1080p",
    categorie: "Périphériques",
    prix: 49,
    note: 4.3,
    stock: 30,
    icone: "📷",
    marque: "ClearView",
    description:
      "Webcam 1080p à 30 ips avec micro stéréo et correction " +
      "automatique de la lumière. Idéale pour le télétravail et le streaming.",
    specs: ["1080p 30 ips", "Micro stéréo", "Autofocus", "Clip universel", "Plug & Play USB"],
  },
  {
    id: "ssd-1to",
    nom: "SSD externe 1 To",
    categorie: "Stockage",
    prix: 109,
    note: 4.8,
    stock: 35,
    icone: "💾",
    marque: "DataGo",
    description:
      "Disque SSD externe 1 To USB-C, jusqu'à 1050 Mo/s. Compact, " +
      "robuste et compatible PC, Mac et consoles.",
    specs: ["1 To", "USB-C 3.2", "1050 Mo/s", "Compact & antichoc", "PC / Mac / console"],
  },
  {
    id: "cle-usb-128",
    nom: "Clé USB 128 Go",
    categorie: "Stockage",
    prix: 19,
    note: 4.5,
    stock: 100,
    icone: "🔌",
    marque: "DataGo",
    description:
      "Clé USB 3.0 de 128 Go, lecture rapide jusqu'à 150 Mo/s. " +
      "Format compact avec capuchon coulissant.",
    specs: ["128 Go", "USB 3.0", "150 Mo/s", "Compatible USB 2.0", "Garantie 5 ans"],
  },
  {
    id: "routeur-wifi6",
    nom: "Routeur Wi-Fi 6",
    categorie: "Réseau",
    prix: 129,
    note: 4.7,
    stock: 18,
    icone: "📶",
    marque: "NetLink",
    description:
      "Routeur Wi-Fi 6 bi-bande jusqu'à 3000 Mbps, 4 antennes et " +
      "4 ports Gigabit. Couverture étendue pour toute la maison.",
    specs: ["Wi-Fi 6 (AX3000)", "Bi-bande", "4 antennes", "4 ports Gigabit", "Contrôle parental"],
  },
  {
    id: "imprimante-laser",
    nom: "Imprimante laser Wi-Fi",
    categorie: "Périphériques",
    prix: 159,
    note: 4.2,
    stock: 10,
    icone: "🖨️",
    marque: "PrintPro",
    description:
      "Imprimante laser monochrome rapide (30 ppm) avec Wi-Fi et " +
      "impression recto-verso automatique. Économique au quotidien.",
    specs: ["Laser monochrome", "30 pages/min", "Wi-Fi + USB", "Recto-verso auto", "Impression mobile"],
  },
];

const CATEGORIES = [...new Set(PRODUITS.map((p) => p.categorie))].sort();

function trouverProduit(id) {
  return PRODUITS.find((p) => p.id === id);
}

function formatPrix(euros) {
  return euros.toLocaleString("fr-FR", { style: "currency", currency: "EUR" });
}

// ---------------------------------------------------------------------------
// Panier — persisté dans localStorage sous forme { id: quantité }
// ---------------------------------------------------------------------------

const CLE_PANIER = "techstore.panier";

function lirePanier() {
  try {
    return JSON.parse(localStorage.getItem(CLE_PANIER)) || {};
  } catch {
    return {};
  }
}

function ecrirePanier(panier) {
  localStorage.setItem(CLE_PANIER, JSON.stringify(panier));
  majBadgePanier();
}

function ajouterAuPanier(id, quantite = 1) {
  const panier = lirePanier();
  panier[id] = (panier[id] || 0) + quantite;
  if (panier[id] < 1) delete panier[id];
  ecrirePanier(panier);
}

function definirQuantite(id, quantite) {
  const panier = lirePanier();
  if (quantite < 1) delete panier[id];
  else panier[id] = quantite;
  ecrirePanier(panier);
}

function retirerDuPanier(id) {
  const panier = lirePanier();
  delete panier[id];
  ecrirePanier(panier);
}

function viderPanier() {
  localStorage.removeItem(CLE_PANIER);
  majBadgePanier();
}

function nombreArticles() {
  return Object.values(lirePanier()).reduce((s, q) => s + q, 0);
}

function totalPanier() {
  const panier = lirePanier();
  return Object.entries(panier).reduce((total, [id, q]) => {
    const p = trouverProduit(id);
    return total + (p ? p.prix * q : 0);
  }, 0);
}

// Met à jour le petit compteur affiché à côté de l'icône panier.
function majBadgePanier() {
  const n = nombreArticles();
  document.querySelectorAll("[data-badge-panier]").forEach((el) => {
    el.textContent = n;
    el.hidden = n === 0;
  });
}

// Affiche une petite notification éphémère (« ajouté au panier »).
function toast(message) {
  let t = document.getElementById("toast");
  if (!t) {
    t = document.createElement("div");
    t.id = "toast";
    document.body.appendChild(t);
  }
  t.textContent = message;
  t.classList.add("visible");
  clearTimeout(toast._timer);
  toast._timer = setTimeout(() => t.classList.remove("visible"), 2200);
}

// Génère les étoiles de notation (★ pleines / ☆ vides).
function etoiles(note) {
  const pleines = Math.round(note);
  return "★".repeat(pleines) + "☆".repeat(5 - pleines);
}

document.addEventListener("DOMContentLoaded", majBadgePanier);
