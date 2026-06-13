/*
 * TechStore — page d'une fiche produit (produit.html?id=...).
 */

"use strict";

const params = new URLSearchParams(location.search);
const produit = trouverProduit(params.get("id"));
const $contenu = document.getElementById("contenu");

let quantite = 1;

if (!produit) {
  $contenu.innerHTML = `
    <p class="vide">
      Produit introuvable.<br>
      <a class="btn btn-primary" href="index.html" style="margin-top:16px">Retour à la boutique</a>
    </p>`;
} else {
  document.title = `${produit.nom} — TechStore`;
  const stock =
    produit.stock <= 5
      ? `<span class="stock-bas">Plus que ${produit.stock} en stock</span>`
      : `<span class="stock-ok">En stock — expédié sous 24 h</span>`;

  $contenu.innerHTML = `
    <nav class="fil">
      <a href="index.html">Accueil</a> ›
      <a href="index.html">${produit.categorie}</a> ›
      <span>${produit.nom}</span>
    </nav>

    <div class="produit-detail">
      <div class="produit-visuel">${produit.icone}</div>
      <div class="produit-info">
        <span class="carte-cat">${produit.categorie}</span>
        <h1>${produit.nom}</h1>
        <p class="marque">Marque : <strong>${produit.marque}</strong></p>
        <div class="carte-note" style="font-size:1rem">${etoiles(produit.note)}<span>${produit.note.toFixed(1)} / 5</span></div>
        <div class="prix-gros">${formatPrix(produit.prix)}</div>
        ${stock}
        <p>${produit.description}</p>
        <ul class="specs">${produit.specs.map((s) => `<li>${s}</li>`).join("")}</ul>

        <div class="qte-row">
          <div class="qte-ctrl">
            <button id="moins" aria-label="Diminuer">−</button>
            <span id="qte">1</span>
            <button id="plus" aria-label="Augmenter">+</button>
          </div>
          <button class="btn btn-accent" id="ajouter">Ajouter au panier</button>
        </div>

        <a href="index.html" class="btn btn-ghost">← Continuer mes achats</a>
      </div>
    </div>`;

  const $qte = document.getElementById("qte");
  const majQte = () => { $qte.textContent = quantite; };

  document.getElementById("moins").addEventListener("click", () => {
    if (quantite > 1) { quantite--; majQte(); }
  });
  document.getElementById("plus").addEventListener("click", () => {
    if (quantite < produit.stock) { quantite++; majQte(); }
  });
  document.getElementById("ajouter").addEventListener("click", () => {
    ajouterAuPanier(produit.id, quantite);
    toast(`${quantite} × ${produit.nom} ajouté${quantite > 1 ? "s" : ""} ✓`);
  });
}
