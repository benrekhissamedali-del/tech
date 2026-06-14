/*
 * TechStore — page de commande : récapitulatif du panier + formulaire.
 * Aucune transaction réelle : la commande est confirmée côté navigateur.
 */

"use strict";

const $recap = document.getElementById("recap-commande");
const $form = document.getElementById("form-commande");
const FRAIS_PORT = 5.99;
const SEUIL_PORT_OFFERT = 100;

function lignesPanier() {
  const panier = lirePanier();
  return Object.entries(panier)
    .map(([id, q]) => ({ produit: trouverProduit(id), q }))
    .filter((l) => l.produit);
}

function rendreRecap() {
  const lignes = lignesPanier();
  if (lignes.length === 0) {
    $recap.innerHTML = `<p class="vide" style="padding:30px 0">
      Votre panier est vide.
      <a href="index.html">Retour à la boutique</a></p>`;
    $form.style.display = "none";
    return false;
  }

  const sousTotal = lignes.reduce((s, l) => s + l.produit.prix * l.q, 0);
  const port = sousTotal >= SEUIL_PORT_OFFERT ? 0 : FRAIS_PORT;

  $recap.innerHTML = `
    <div class="recap" style="margin:0 0 26px;max-width:none">
      ${lignes.map((l) => `
        <div class="recap-ligne">
          <span><img class="mini" src="${l.produit.image}" alt=""> ${l.produit.nom} × ${l.q}</span>
          <span>${formatPrix(l.produit.prix * l.q)}</span>
        </div>`).join("")}
      <div class="recap-ligne"><span>Livraison</span><span>${port === 0 ? "Offerts" : formatPrix(port)}</span></div>
      <div class="recap-total"><span>Total</span><span>${formatPrix(sousTotal + port)}</span></div>
    </div>`;
  return true;
}

function validerChamp(champ) {
  const ok = champ.value.trim() !== "" && (champ.type !== "email" || /.+@.+\..+/.test(champ.value));
  champ.style.borderColor = ok ? "" : "var(--danger)";
  return ok;
}

$form.addEventListener("submit", (e) => {
  e.preventDefault();

  const requis = $form.querySelectorAll("[required]");
  let valide = true;
  requis.forEach((c) => { if (!validerChamp(c)) valide = false; });

  if (!valide) {
    toast("Veuillez remplir les champs obligatoires");
    return;
  }

  const prenom = $form.prenom.value.trim();
  const numero = "TS-" + Date.now().toString().slice(-6);

  viderPanier();

  document.querySelector("main.conteneur").innerHTML = `
    <div class="confirmation">
      <div style="font-size:3rem">✅</div>
      <h2>Merci ${prenom}, votre commande est enregistrée !</h2>
      <p>Numéro de commande : <strong>${numero}</strong></p>
      <p style="color:var(--muted)">
        Un e-mail de confirmation vous sera envoyé. Nous vous recontactons
        rapidement pour le paiement et la livraison.
      </p>
      <a href="index.html" class="btn btn-primary" style="margin-top:10px">Retour à la boutique</a>
    </div>`;
});

// Validation à la volée une fois le champ quitté.
$form.querySelectorAll("[required]").forEach((c) =>
  c.addEventListener("blur", () => validerChamp(c)));

if (rendreRecap()) {
  // formulaire affiché
}
