<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHDetecteur

*Détecteur de collectibles TheSearch : une flèche vers le prochain, puis un petit bruit quand il est tout près.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Le joueur active le détecteur d'une collection (menu des collectibles ou `/detecteur <collection>`) :

1. Une flèche l'emmène vers le collectible restant le plus proche, avec sa distance.
2. À moins de `distances.proche` blocs, la flèche disparaît et une barre d'action le dit une fois. Le collectible fait un petit bruit à sa place, toutes les `bruit.intervalle-ticks` ticks.
3. Une fois trouvé, la flèche repart vers le suivant. Le détecteur s'arrête seul quand la collection est complète.

Un seul détecteur à la fois : en activer un autre remplace le premier, réactiver le même l'arrête. Il s'arrête aussi à la déconnexion. Un joueur hors du monde des collectibles lance `commande-autre-monde` à l'activation.

Les collectibles, leurs positions et les trouvailles sont lus dans TheSearch : une tête déplacée ou ajoutée est prise en compte sans rien changer ici. Les conditions de TheSearch restent exigées (permission `thesearch.search.<collection>`).

La cible ne change pour un collectible plus proche que s'il l'est d'au moins `distances.changement` blocs, pour que la flèche n'hésite pas entre deux.

Utilisé sur : **Opale**.

## Dépendances

Requises : `TheSearch`, `PlaceholderAPI`.

La flèche est dessinée par un plugin qui lit `%shdetecteur_cible%`, par exemple [SHGuide](https://github.com/CryLegende/SHGuide) :

```yaml
fleches:
  detecteur:
    cible: "%shdetecteur_cible%"
    nom: "%shdetecteur_nom%"
```

Pour compiler, installer le jar de TheSearch dans le dépôt Maven local :

```
mvn install:install-file -Dfile=TheSearch-2.14.2.jar -DgroupId=ts.ajneb97 -DartifactId=thesearch -Dversion=2.14.2 -Dpackaging=jar
```

## Commandes

| Commande | Permission | Effet |
|---|---|---|
| `/detecteur <collection>` | `shdetecteur.use` | Active le détecteur de la collection, l'arrête s'il était déjà actif |
| `/detecteur` | `shdetecteur.use` | Arrête le détecteur |
| `/shdetecteur reload` | `shdetecteur.admin.reload` | Recharge `config.yml` et `messages.yml` ; en cas d'erreur, rien ne change et le message nomme le fichier |
| `/shdetecteur info <joueur>` | `shdetecteur.admin.info` | Collection, cible et distance d'un joueur |

`shdetecteur.admin` donne toutes les sous-commandes.

## Placeholders

| Placeholder | Valeur |
|---|---|
| `%shdetecteur_cible%` | Lieu que la flèche doit montrer, `monde x y z`. Vide quand le détecteur est arrêté, que le collectible est tout près ou dans un autre monde |
| `%shdetecteur_nom%` | Nom du collectible cherché, vide si arrêté |
| `%shdetecteur_collection%` | Collection du détecteur actif, vide si arrêté |
| `%shdetecteur_menu_<collection>%` | Ligne de menu : activer, arrêter ou collection complète (`messages.yml`) |

Exemple de bouton DeluxeMenus :

```yaml
lore:
- "%shdetecteur_menu_Cookies%"
left_click_commands:
- "[player] detecteur Cookies"
```

## Configuration

Fichiers par défaut, avec des valeurs d'exemple :

- [`config.yml`](src/main/resources/config.yml) : distances, bruit et collections qui ont un détecteur
- [`messages.yml`](src/main/resources/messages.yml) : messages, barre d'action et lignes de menu
