/*
 * TechStore — page panier : liste des articles, quantités, total.
 */

"use strict";

const $contenu = document.getElementById("contenu");
const FRAIS_PORT = 5.99;
const SEUIL_PORT_OFFERT = 100;

function rendrePanier() {
  const panier = lirePanier();
  const lignes = Object.entries(panier)
    .map(([id, q]) => ({ produit: trouverProduit(id), q }))
    .filter((l) => l.produit);

  if (lignes.length === 0) {
    $contenu.innerHTML = `
      <p class="vide">
        Votre panier est vide. 🛒<br>
        <a class="btn btn-primary" href="index.html" style="margin-top:16px">Découvrir nos produits</a>
      </p>`;
    return;
  }

  const sousTotal = lignes.reduce((s, l) => s + l.produit.prix * l.q, 0);
  const port = sousTotal >= SEUIL_PORT_OFFERT ? 0 : FRAIS_PORT;
  const total = sousTotal + port;

  const rangs = lignes.map((l) => `
    <tr>
      <td>
        <div class="panier-prod">
          <img class="ico" src="${l.produit.image}" alt="${l.produit.nom}">
          <div>
            <strong><a href="produit.html?id=${l.produit.id}">${l.produit.nom}</a></strong>
            <button class="lien-suppr" data-suppr="${l.produit.id}">Retirer</button>
          </div>
        </div>
      </td>
      <td>${formatPrix(l.produit.prix)}</td>
      <td>
        <div class="qte-ctrl">
          <button data-moins="${l.produit.id}" aria-label="Diminuer">−</button>
          <span>${l.q}</span>
          <button data-plus="${l.produit.id}" aria-label="Augmenter">+</button>
        </div>
      </td>
      <td><strong>${formatPrix(l.produit.prix * l.q)}</strong></td>
    </tr>`).join("");

  const portTexte = port === 0
    ? `<span class="stock-ok">Offerts</span>`
    : formatPrix(port);

  $contenu.innerHTML = `
    <table class="panier-table">
      <thead>
        <tr><th>Produit</th><th>Prix</th><th>Quantité</th><th>Sous-total</th></tr>
      </thead>
      <tbody>${rangs}</tbody>
    </table>

    <div class="recap">
      <div class="recap-ligne"><span>Sous-total</span><span>${formatPrix(sousTotal)}</span></div>
      <div class="recap-ligne"><span>Livraison</span><span>${portTexte}</span></div>
      ${port > 0 ? `<div class="recap-ligne" style="font-size:0.82rem">Livraison offerte dès ${formatPrix(SEUIL_PORT_OFFERT)} d'achat</div>` : ""}
      <div class="recap-total"><span>Total</span><span>${formatPrix(total)}</span></div>
      <a href="contact.html" class="btn btn-accent btn-block" style="margin-top:16px">Passer la commande</a>
      <button class="btn btn-ghost btn-block" id="vider" style="margin-top:10px">Vider le panier</button>
    </div>`;

  // Branchements
  $contenu.querySelectorAll("[data-suppr]").forEach((b) =>
    b.addEventListener("click", () => { retirerDuPanier(b.dataset.suppr); rendrePanier(); }));
  $contenu.querySelectorAll("[data-moins]").forEach((b) =>
    b.addEventListener("click", () => {
      ajouterAuPanier(b.dataset.moins, -1);
      rendrePanier();
    }));
  $contenu.querySelectorAll("[data-plus]").forEach((b) =>
    b.addEventListener("click", () => {
      const p = trouverProduit(b.dataset.plus);
      if ((lirePanier()[b.dataset.plus] || 0) < p.stock) {
        ajouterAuPanier(b.dataset.plus, 1);
        rendrePanier();
      } else {
        toast("Stock maximum atteint");
      }
    }));
  document.getElementById("vider").addEventListener("click", () => {
    viderPanier();
    rendrePanier();
  });
}

rendrePanier();
