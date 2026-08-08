# SMS Pro — app Android + backend (modèle crédits, comme MultiSMS)

Application d'envoi de SMS en masse **depuis la SIM du téléphone**, avec **compte utilisateur** et **système de crédits** que tu vends (Mobile Money via Chariow). Les vrais SMS partent du forfait de l'utilisateur ; toi tu factures les crédits.

Deux parties :
- `app/` — l'application Android (Kotlin + Jetpack Compose + Room).
- `backend/` — un serveur de référence (Node.js/Express) pour gérer comptes + crédits + paiement.

---

## 1. L'application Android

### Fonctionnalités
- **Connexion** par compte (e-mail + mot de passe), avec **mode démo** (e-mail `demo`) qui donne 1000 crédits hors ligne pour tester tout de suite.
- **Crédits** affichés en haut et dans la fiche compte (comme MultiSMS) ; bouton **« ACHETER DES CRÉDITS »** qui ouvre ta page Chariow.
- **1 crédit = 1 SMS envoyé.** L'envoi s'arrête automatiquement à 0 crédit (« Crédits épuisés »).
- **Listes d'envoi** : import depuis le téléphone, un fichier CSV/TXT, ou saisie manuelle ; sélection, recherche, compteur.
- **Messages** : plusieurs modèles, compteur de caractères/parties, personnalisation `{nom}`.
- **Panneau d'envoi** : délai réglable, double SIM, démarrage/pause/arrêt, progression et statut par contact.
- **Historique** des envois (onglet).
- **Service au premier plan** : l'envoi continue écran éteint et reprend après coupure (les SMS déjà envoyés ne sont pas renvoyés).

### Compiler
1. Ouvrir le dossier `app/`… en fait, ouvrir **la racine du projet** dans **Android Studio** (la synchro Gradle télécharge tout).
2. Brancher un **vrai téléphone** Android (pas un émulateur) avec débogage USB.
3. `Run ▶`, accorder les permissions.
4. Se connecter en tapant `demo` comme e-mail pour essayer immédiatement.

### Brancher le vrai serveur
Dans l'app : icône **⚙ Réglages** (en haut) → saisir l'URL de ton backend (ex. `https://api.emacdigital.com`).
Sinon, modifie les constantes dans `app/src/main/java/com/emac/multisms/session/SessionManager.kt` :
- `DEFAULT_SERVER` — l'URL de ton backend.
- `BUY_CREDITS_URL` — ta page d'achat de crédits (Chariow).

---

## 2. Le backend (Node.js)

```
cd backend
npm install
ADMIN_KEY=... CHARIOW_SECRET=... npm start
```

Endpoints utilisés par l'app :
- `POST /api/login` `{email, password}` → `{token, account, credits}`
- `GET  /api/balance?token=...` → `{account, credits}`
- `POST /api/usage` `{token, sent}` → `{credits}`

Gestion des crédits :
- `POST /api/register` `{email, password}` — créer un compte.
- `POST /api/admin/credits` (header `x-admin-key`) `{email, credits}` — créditer à la main.
- `POST /webhooks/chariow` `{email, product, secret}` — **créditer automatiquement après un paiement Mobile Money**.

Le mapping produit → crédits est dans `server.js` (`PRODUCT_CREDITS`). Exemple fourni : `abo-mensuel-5000` = 1000 crédits.

> Stockage : fichier `data.json`. Pour la production, remplace par une vraie base de données.

---

## 3. Le modèle économique (important)

- Les crédits sont ta **licence logicielle**, pas des SMS achetés à une passerelle. Coût par SMS pour toi ≈ 0 (l'utilisateur paie ses SMS via son opérateur). Ta marge sur les crédits est donc quasi totale.
- Vends les crédits sur **Chariow via Mobile Money** (Wave, Orange Money, MTN, Moov). À chaque paiement, Chariow appelle `/webhooks/chariow` et le compte est crédité.
- Ton offre « 5000 FCFA/mois » = un produit Chariow `abo-mensuel-5000` qui donne 1000 crédits/mois (à ajuster).

---

## 4. Play Store — à savoir

- La permission `SEND_SMS` est **restreinte** par Google. C'est publiable (MultiSMS y est), mais il faut remplir le **formulaire de déclaration des permissions SMS**, justifier l'usage, et accepter un **risque de refus/retrait**. Prévois une distribution **APK directe** en secours.
- Pour l'achat de crédits sur Play : soit passer par **Google Play Billing**, soit vendre les crédits **uniquement sur ton site** (l'app affiche juste le solde) pour rester conforme. En distribution APK hors Play, le bouton d'achat vers Chariow ne pose aucun problème.
- **Ne copie pas** le nom ni le logo « MultiSMS » : choisis ta propre marque (le projet s'appelle « SMS Pro », à renommer).

---

## Structure

```
app/src/main/java/com/emac/multisms/
├── MainActivity.kt            # login gate, barre compte/crédits, navigation
├── session/SessionManager.kt  # compte, jeton, crédits (persistés)
├── net/ApiClient.kt           # appels backend (login/balance/usage) + mode démo
├── data/                      # Room : listes, contacts, messages, journal
├── sms/SmsSenderService.kt    # envoi SIM + gating crédits + double SIM + reprise
├── util/Utils.kt              # import contacts/CSV, SIM, comptage SMS
└── ui/                        # écrans Login / Listes / Messages / Envoi / Historique + fiche compte
backend/                       # serveur Node.js (comptes, crédits, webhook Chariow)
```
