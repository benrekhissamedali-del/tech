# TechStore — Boutique de matériel informatique

Site e-commerce **statique** (HTML / CSS / JavaScript purs, sans dépendance ni
étape de build) pour vendre du matériel informatique : ordinateurs, écrans,
périphériques, stockage et réseau.

## Fonctionnalités

- **Catalogue** de produits avec catégories, notes, prix et stock
- **Fiches produits** détaillées (caractéristiques, description, sélecteur de quantité)
- **Recherche** instantanée et **filtres** par catégorie + **tri** (prix, note)
- **Panier** persistant (conservé dans le navigateur via `localStorage`)
- **Page de commande** avec récapitulatif, frais de port et formulaire validé

> Boutique de **démonstration** : aucun paiement réel n'est effectué et la
> commande est simplement confirmée côté navigateur.

## Lancer le site

Comme c'est un site purement statique, il suffit de servir le dossier :

```bash
cd boutique
python3 -m http.server 8000
# puis ouvrir http://localhost:8000
```

Ou via npm : `npx serve .`

On peut aussi simplement ouvrir `index.html` dans un navigateur, ou publier le
dossier sur **GitHub Pages** / tout hébergement statique.

## Structure

| Fichier | Rôle |
|---|---|
| `index.html` / `boutique.js` | Page d'accueil : catalogue, recherche, filtres, tri |
| `produit.html` / `produit.js` | Fiche d'un produit (`produit.html?id=...`) |
| `panier.html` / `panier.js` | Panier : quantités, total, livraison |
| `contact.html` / `contact.js` | Commande : récapitulatif + formulaire |
| `data.js` | Catalogue produits + logique de panier partagée |
| `style.css` | Styles communs à toutes les pages |

## Personnaliser

Pour modifier les produits, éditez le tableau `PRODUITS` dans
[`data.js`](data.js) : chaque entrée définit nom, catégorie, prix, stock,
icône, marque, description et caractéristiques.
