# Mini-SGBD en Java : gestionnaire de l'espace disque

Implémentation en Java de la couche la plus basse d'un système de gestion de bases de données (SGBD) : le gestionnaire de l'espace disque. Ce dépôt est réalisé dans le cadre de notre première séance de TP pour le compte du cours Bases de Données Avancées.

## Présentation

Le gestionnaire de l'espace disque (`DiskManager`) isole les couches supérieures du SGBD des détails de stockage. Elles n'ont pas à savoir dans quel fichier ni à quel emplacement les données sont écrites. Elles manipulent uniquement des pages, c'est-à-dire des blocs de taille fixe :

- allouer une page
- lire le contenu d'une page
- écrire dans une page
- libérer une page pour la réutiliser plus tard

L'état du gestionnaire est sauvegardé dans un dossier de travail, ce qui garantit la persistance des données d'une exécution à l'autre.

## Fonctionnalités

- Allocation de pages avec réutilisation prioritaire des pages précédemment libérées
- Lecture et écriture de pages entières via des `ByteBuffer`
- Désallocation de pages
- Sauvegarde et rechargement de l'état (taille de page, nombre de pages, pages libres)
- Vérification de la compatibilité de la taille de page au rechargement

## Choix de conception

| Élément | Choix |
|---|---|
| Fichier des pages | `Data.bin`, dans le dossier de travail |
| Fichier de métadonnées | `dm.save`, dans le dossier de travail |
| Identifiant de page | `PageId`, qui contient le numéro de page `pageIdx` |
| Emplacement d'une page | offset `pageIdx * pageSize` dans `Data.bin` |
| Pages libérées | liste en mémoire, sauvegardée dans `dm.save` |
| Allocation | page libérée réutilisée en priorité, sinon ajout d'une page à la fin |

Les couches supérieures ne dépendent que de l'interface `IPageId`, ce qui permet de modifier la représentation d'un identifiant de page sans toucher au reste du code.

## Structure du projet

```
bdda/
  IPageId.java            interface d'identifiant de page
  PageId.java             implémentation de IPageId
  DiskManager.java        gestionnaire de l'espace disque
  DiskManagerTests.java   tests du gestionnaire
```

## API du DiskManager

```java
void Init(String dmDir, int pageSize)
void Save()
IPageId AllocPage()
void ReadPage(IPageId ipid, ByteBuffer buffer)
void WritePage(IPageId ipid, ByteBuffer buffer)
void DeallocPage(IPageId ipid)
```

## Compilation et exécution

Prérequis : JDK 11 ou supérieur.

```bash
# Compilation
javac -d out bdda/*.java

# Lancement des tests
java -cp out bdda.DiskManagerTests
```

## Tests

Les tests utilisent une petite taille de page (4 octets) afin de vérifier facilement le contenu des pages. Ils couvrent :

- l'écriture puis la lecture d'une page
- l'allocation de plusieurs pages distinctes
- la réutilisation d'une page libérée
- la persistance après `Save()` puis `Init()` sur le même dossier
- la détection d'une taille de page incompatible

## État d'avancement

- [x] Structure du projet et interfaces
- [x] Implémentation du `DiskManager`
- [x] Suite de tests
- [x] Gestion des cas d'erreur (dossier inexistant, données corrompues)

## Auteurs
- Marie-Paule Lima
- Pernel Djahou
