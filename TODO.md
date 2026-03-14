# App Marina — TODO

## PHASE 1 — WebView de base ✅ TERMINÉE

| Fonctionnalité | État | Détail |
|---|---|---|
| Projet Capacitor créé | ✅ | `C:\dev\groupmaker-app` |
| WebView verrouillée sur marina.groupmaker.fr | ✅ | Tout autre domaine → navigateur externe |
| Cookies / sessions PHP | ✅ | Persistance activée, third-party cookies OK |
| Portrait forcé | ✅ | `screenOrientation="portrait"` |
| Zoom désactivé | ✅ | Comportement natif, pas navigateur |
| Géolocalisation | ✅ | Auto-accordée dans WebView |
| Upload fichiers (photos) | ✅ | File chooser natif |
| Bouton retour | ✅ | Navigue dans l'historique WebView |
| Liens externes | ✅ | Ouverts dans le navigateur système |
| User-Agent personnalisé | ✅ | `MarinaApp/1.0` détectable côté PHP |
| HTTPS only / sécurité | ✅ | `network_security_config.xml` |
| Permissions déclarées | ✅ | Internet, réseau, géoloc, caméra, notifs, vibration |
| Deep links marina | ✅ | `https://marina.groupmaker.fr` ouvre l'app |
| Splash screen | ✅ | Fond #20466e + icône animée (Material 3 SplashScreen) |
| Couleurs status bar / nav bar | ✅ | #163152 (bleu marine) |
| Icônes app (toutes résolutions) | ✅ | mdpi → xxxhdpi, adaptive icon |

---

## PHASE 2 — Push Notifications 🔲 À FAIRE (priorité haute)

> Clé pour l'acceptation Apple + vrai usage mobile

| Tâche | État |
|---|---|
| Créer projet Firebase + récupérer `google-services.json` | 🔲 |
| Ajouter Firebase/FCM dans `build.gradle` | 🔲 |
| Créer `MarinaFirebaseMessagingService.java` | 🔲 |
| Au login : enregistrer token FCM auprès du serveur PHP | 🔲 |
| Côté PHP : table `user_devices` (user_id, fcm_token, platform) | 🔲 |
| Côté PHP : endpoint `api/register_device.php` | 🔲 |
| Côté PHP : modifier `api/chat_send.php` pour envoyer push FCM | 🔲 |
| Notification native avec photo expéditeur + aperçu message | 🔲 |
| Clic sur notif → ouvre le chat dans l'app | 🔲 |
| Logique : ne pas pusher si destinataire est déjà en ligne (WS) | 🔲 |

---

## PHASE 3 — Fonctions natives (pour satisfaire Apple) 🔲 À FAIRE

| Tâche | État |
|---|---|
| Badge icône app (compteur notifs non lues) | 🔲 |
| Partage natif (share sheet OS au lieu du JS) | 🔲 |
| Cache offline (dernières pages visitées) | 🔲 |
| Deep links complets (lien événement / profil → ouvre l'app) | 🔲 |

---

## PHASE 4 — Publication 🔲 À FAIRE

| Tâche | État |
|---|---|
| Générer keystore de signature | 🔲 |
| Configurer `build.gradle` signing config | 🔲 |
| Build APK release / AAB | 🔲 |
| Google Play (compte 25$, review ~2j) | 🔲 |
| Apple App Store (compte 99$/an, nécessite Mac ou CI cloud) | 🔲 |

---

## NOTES TECHNIQUES

- **AppId** : `fr.groupmaker.marina`
- **Nom app** : Marina
- **URL** : `https://marina.groupmaker.fr`
- **Verrouillage** : 1 app = 1 univers (Marina). Holidays sera une app séparée plus tard.
- **Multi-univers** : même codebase Capacitor, point d'entrée + couleurs + appId différents
- **Test** : WiFi debugging Android 11+ si téléphone et PC sur même réseau local
