# Infosphère

Une visualisation 360° en temps réel du monde invisible des signaux numériques
qui nous entourent — inspirée de l'application *The Architecture of Radio*.

Chaque fois que nous utilisons nos téléphones, nous entrons dans un monde
invisible de signaux sans fil. Cette application le rend visible : pointez
votre téléphone autour de vous et découvrez les **antennes cellulaires**,
les **satellites GPS** et les **routeurs Wi-Fi** qui vous permettent de
vivre votre vie numérique.

> Comme l'app originale, ce n'est **pas un outil de mesure** : les sources
> sont générées de façon plausible et déterministe à partir de votre
> position GPS. Le même lieu affiche toujours le même monde.

## Fonctionnalités

- **Vue 360°** pilotée par le gyroscope du téléphone (ou glissement du doigt)
- **Géolocalisation** : le monde des signaux est unique à votre position
- **Antennes cellulaires** près de l'horizon, **Wi-Fi** tout autour,
  **satellites** dérivant lentement dans le ciel
- **Touchez un signal** pour afficher ses détails (technologie, distance, azimut…)
- **Boussole** et compteurs en temps réel
- **PWA** : installable sur l'écran d'accueil, fonctionne hors connexion
- Aucune dépendance — HTML, CSS et JavaScript purs

## Application Android native

Le dossier [`android/`](android/) contient une **application Android native en
Kotlin** qui, contrairement à la version web, affiche de **vraies données** :

- **Scan Wi-Fi réel** (`WifiManager`) : SSID, bande, puissance du signal et
  distance estimée de chaque routeur autour de vous
- **Vraies antennes cellulaires** (`TelephonyManager`) : technologie
  (2G/3G/4G/5G), opérateur, puissance, antenne de rattachement
- **Vrais satellites GNSS** (`GnssStatus`) : GPS, Galileo, GLONASS, BeiDou…
  avec leur **position exacte dans le ciel** (azimut/élévation réels)
- Vue 360° pilotée par le capteur de rotation (gyroscope + boussole)

### Obtenir l'APK

À chaque push, GitHub Actions compile l'APK automatiquement :
onglet **Actions** du dépôt → workflow « Build Android APK » → dernier run →
artefact **infosphere-debug-apk**. Téléchargez-le sur votre téléphone et
installez-le (autorisez les sources inconnues).

Pour compiler localement : ouvrez `android/` dans Android Studio, ou
`cd android && gradle assembleDebug`.

## Version web — lancer l'application

Servez le dossier en HTTPS (requis pour la géolocalisation et les capteurs) :

```bash
npx serve .
# ou
python3 -m http.server 8000
```

Puis ouvrez l'adresse sur votre téléphone. Sur iOS, appuyez sur « Explorer »
pour autoriser l'accès aux capteurs de mouvement.

Le plus simple : activez **GitHub Pages** sur ce dépôt (Settings → Pages →
branche principale) et ouvrez l'URL fournie sur votre téléphone.
