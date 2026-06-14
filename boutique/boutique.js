/*
 * TechStore — logique de la page d'accueil :
 * affichage du catalogue, recherche, filtres par catégorie et tri.
 */

"use strict";

const etat = {
  categorie: "Toutes",
  recherche: "",
  tri: "pertinence",
};

const $grille = document.getElementById("grille");
const $compte = document.getElementById("compte");
const $filtres = document.getElementById("filtres");

// --- Chips de catégories -----------------------------------------------------

function construireFiltres() {
  const cats = ["Toutes", ...CATEGORIES];
  $filtres.innerHTML = "";
  cats.forEach((cat) => {
    const chip = document.createElement("button");
    chip.className = "chip" + (cat === etat.categorie ? " actif" : "");
    chip.textContent = cat;
    chip.addEventListener("click", () => {
      etat.categorie = cat;
      construireFiltres();
      rendre();
    });
    $filtres.appendChild(chip);
  });
}

// --- Filtrage + tri ----------------------------------------------------------

function produitsAffiches() {
  let liste = PRODUITS.slice();

  if (etat.categorie !== "Toutes") {
    liste = liste.filter((p) => p.categorie === etat.categorie);
  }

  const q = etat.recherche.trim().toLowerCase();
  if (q) {
    liste = liste.filter((p) =>
      (p.nom + " " + p.categorie + " " + p.marque + " " + p.description)
        .toLowerCase()
        .includes(q)
    );
  }

  switch (etat.tri) {
    case "prix-asc": liste.sort((a, b) => a.prix - b.prix); break;
    case "prix-desc": liste.sort((a, b) => b.prix - a.prix); break;
    case "note": liste.sort((a, b) => b.note - a.note); break;
  }

  return liste;
}

// --- Rendu des cartes --------------------------------------------------------

function carteHTML(p) {
  const stock =
    p.stock <= 5
      ? `<span class="stock-bas">Plus que ${p.stock} en stock</span>`
      : `<span class="stock-ok">En stock</span>`;

  return `
    <article class="carte">
      <a class="carte-img" href="produit.html?id=${p.id}" aria-label="${p.nom}"><img src="${p.image}" alt="${p.nom}" loading="lazy"></a>
      <div class="carte-corps">
        <span class="carte-cat">${p.categorie}</span>
        <a href="produit.html?id=${p.id}"><h3 class="carte-nom">${p.nom}</h3></a>
        <div class="carte-note">${etoiles(p.note)}<span>${p.note.toFixed(1)}</span></div>
        ${stock}
        <div class="carte-bas">
          <span class="carte-prix">${formatPrix(p.prix)}</span>
          <button class="btn btn-accent" data-ajouter="${p.id}">Ajouter</button>
        </div>
      </div>
    </article>`;
}

function rendre() {
  const liste = produitsAffiches();
  $compte.textContent =
    liste.length === 0
      ? "Aucun produit"
      : `${liste.length} produit${liste.length > 1 ? "s" : ""}`;

  if (liste.length === 0) {
    $grille.innerHTML = `<p class="vide">Aucun produit ne correspond à votre recherche.<br>Essayez un autre mot-clé ou une autre catégorie.</p>`;
    return;
  }

  $grille.innerHTML = liste.map(carteHTML).join("");
  $grille.querySelectorAll("[data-ajouter]").forEach((btn) => {
    btn.addEventListener("click", () => {
      ajouterAuPanier(btn.dataset.ajouter, 1);
      toast("Ajouté au panier ✓");
    });
  });
}

// --- Branchements ------------------------------------------------------------

document.getElementById("recherche").addEventListener("input", (e) => {
  etat.recherche = e.target.value;
  rendre();
});

document.getElementById("tri").addEventListener("change", (e) => {
  etat.tri = e.target.value;
  rendre();
});

construireFiltres();
rendre();
