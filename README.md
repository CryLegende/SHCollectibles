<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHCollectibles

*Collections de têtes cachées dans le monde, récompenses, et un détecteur qui guide vers la prochaine.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale%20%C2%B7%20Lobby-3aa655?style=for-the-badge" alt="SkyHand Opale et Lobby">

</div>

---

## Présentation

| Partie | Effet |
|---|---|
| Trouvailles | Un clic droit sur une tête la compte et donne la récompense de la collection (textes, son, effets, commandes) ; la dernière donne celle de fin à la place. La récompense n'est donnée que si la base n'avait pas déjà la trouvaille : jamais deux fois, même après un arrêt brutal. |
| Effets | Objets qui jaillissent, hologramme et feu d'artifice, montrés au seul joueur concerné et jamais sauvegardés. |
| Particules | Un point de couleur sur les têtes proches, montré seulement aux joueurs qui ne les ont pas encore. |
| Protection | Une tête de collection ne se casse pas, même par un administrateur (`/shcollectibles remove` d'abord). |
| Détecteur | `/detecteur <collection>` guide vers le collectible restant dont le trajet est le plus court. |

Le détecteur :

1. Une flèche mène à l'entrée de la zone du collectible (ses `etapes`, l'entrée en dernier), puis vers lui une fois l'entrée passée. Une étape déjà dépassée est sautée, une étape quittée revient ; un joueur à l'abri du ciel et à la hauteur du collectible est considéré dans sa zone.
2. À moins de `detecteur.distances.proche` blocs, la flèche disparaît et une barre d'action le dit une fois. Le collectible fait un petit bruit à sa place ; jamais avant l'entrée, pour ne pas s'entendre à travers le sol.
3. Une fois trouvé, la flèche repart vers le suivant. Le détecteur s'arrête quand la collection est complète ou à la déconnexion.

Une tête pas encore posée (case vide) reste dans la collection, mais le détecteur n'y emmène personne.

Utilisé sur : **Opale**, **lobby**.

## Données

MariaDB, une ligne par trouvaille (`shcollectibles_trouvailles`), par collection complète (`shcollectibles_completes`) et par joueur (`shcollectibles_joueurs`, son pseudo). Plusieurs serveurs partagent la base sous leur nom (`serveur`). Les trouvailles d'un joueur sont lues avant son entrée en jeu et écrites aussitôt, hors du fil principal.

Un collectible retiré d'une collection reste compté à ceux qui l'avaient trouvé, sans jamais faire croire que tout est trouvé.

`/shcollectibles import <dossier players>` (console) reprend les trouvailles de TheSearch, en une seule transaction ; une ligne déjà présente n'est pas touchée. Les identifiants des collections et les numéros des collectibles doivent être les mêmes.

## Dépendances

Requises : `PlaceholderAPI`, une base MariaDB.

La flèche est dessinée par un plugin qui lit `%shcollectibles_cible%`, par exemple [SHGuide](https://github.com/CryLegende/SHGuide) :

```yaml
fleches:
  collectibles:
    cible: "%shcollectibles_cible%"
    nom: "%shcollectibles_nom%"
```

## Commandes

| Commande | Permission | Effet |
|---|---|---|
| `/detecteur <collection>` | `shcollectibles.detecteur` | Active le détecteur de la collection, l'arrête s'il était déjà actif |
| `/detecteur` | `shcollectibles.detecteur` | Arrête le détecteur |
| `/shcollectibles reload` | `shcollectibles.admin.reload` | Recharge la configuration et les collections ; en cas d'erreur, rien ne change et le message nomme le fichier |
| `/shcollectibles list [collection]` | `shcollectibles.admin.list` | Collections, ou collectibles d'une collection |
| `/shcollectibles info <joueur>` | `shcollectibles.admin.info` | Trouvailles et détecteur d'un joueur connecté |
| `/shcollectibles add <collection>` | `shcollectibles.admin.add` | Ajoute le bloc visé à la collection |
| `/shcollectibles remove` | `shcollectibles.admin.remove` | Retire le bloc visé de sa collection |
| `/shcollectibles step <collection> <numéro> <add\|clear>` | `shcollectibles.admin.step` | Ajoute une étape du détecteur là où vous êtes, ou efface les étapes |
| `/shcollectibles tp <collection> <numéro>` | `shcollectibles.admin.tp` | Se téléporter au collectible |
| `/shcollectibles give <joueur> <collection> <numéro>` | `shcollectibles.admin.give` | Donne un collectible, avec sa récompense |
| `/shcollectibles reset <joueur\|*> <collection\|*>` | `shcollectibles.admin.reset` | Efface des trouvailles |
| `/shcollectibles enable\|disable <collection>` | `shcollectibles.admin.enable`, `.disable` | Ouvre ou ferme une collection |
| `/shcollectibles import <dossier players>` | `shcollectibles.admin.import` | Console : reprend les trouvailles de TheSearch |

`shcollectibles.admin` donne toutes les sous-commandes. La permission exigée pour chaque collection est réglée dans `config.yml` (`permission`) ou dans son fichier.

## Placeholders

`<c>` est l'identifiant de la collection (nom de son fichier). `alias-placeholders` les rend aussi disponibles sous un autre nom.

| Placeholder | Valeur |
|---|---|
| `%shcollectibles_total_found_objects_<c>%` | Collectibles trouvés |
| `%shcollectibles_remaining_objects_<c>%` | Collectibles restants |
| `%shcollectibles_total_objects_<c>%` | Collectibles de la collection |
| `%shcollectibles_has_completed_<c>%` | `true` si la collection est complète |
| `%shcollectibles_has_found_object_<c>_<numéro>%` | `true` si ce collectible est trouvé |
| `%shcollectibles_total_players_with_completed_<c>%` | Joueurs qui ont complété la collection |
| `%shcollectibles_total_completed_searches%` | Collections complètes du joueur |
| `%shcollectibles_is_enabled_<c>%` | `true` si la collection est ouverte |
| `%shcollectibles_menu_<c>%` | Ligne de menu du détecteur : activer, arrêter ou complète |
| `%shcollectibles_cible%`, `%shcollectibles_nom%` | Lieu que la flèche doit montrer (`monde x y z`, vide sinon) et texte au-dessus |
| `%shcollectibles_collection%` | Collection du détecteur actif |

## Configuration

Fichiers par défaut, avec des valeurs d'exemple :

- [`config.yml`](src/main/resources/config.yml) : base, permission, particules, détecteur, et la description des fichiers de collection
- [`collections/exemple.yml`](src/main/resources/collections/exemple.yml) : une collection
- [`messages.yml`](src/main/resources/messages.yml) : messages, barre d'action et lignes de menu
