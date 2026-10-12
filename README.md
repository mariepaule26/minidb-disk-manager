# Mini-SGBD en Java : gestionnaire de l'espace disque

Implémentation en Java de la couche la plus basse d'un système de gestion de bases de données (SGBD) : le gestionnaire de l'espace disque.  Ce dépôt est réalisé dans le cadre de notre premier TP pour le compte du cours Bases de Données Avancées.

## Présentation

Le gestionnaire de l'espace disque (`DiskManager`) isole les couches supérieures du SGBD des détails de stockage. Elles manipulent uniquement des pages, c'est-à-dire des blocs de taille fixe :

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
- Création du dossier de travail s'il n'existe pas
- Gestion des erreurs par une exception dédiée, `DiskManagerException` : taille de page incompatible, second appel à `Init`, identifiant de page invalide, erreurs d'entrée-sortie

## Structure du projet

```
src/
  IDiskManager.java          interface du gestionnaire (fournie)
  IPageId.java               interface d'identifiant de page (fournie)
  DiskManagerException.java  exception du gestionnaire (fournie)
  DiskManager.java           implémentation de IDiskManager
  PageId.java                implémentation de IPageId
  DiskManagerTests.java      tests du gestionnaire
```

Les trois fichiers fournis ne devaient pas être modifiés. Le projet utilise le paquetage par défaut, comme ces fichiers.

## API

```java
void Init(String dmDir, int pageSize) throws DiskManagerException
void Save() throws DiskManagerException
IPageId AllocPage() throws DiskManagerException
void ReadPage(IPageId ipid, ByteBuffer buffer) throws DiskManagerException
void WritePage(IPageId ipid, ByteBuffer buffer) throws DiskManagerException
void DeallocPage(IPageId ipid) throws DiskManagerException
int GetActivePageCount()
int GetPageSize()
```

`Init` ne peut être appelé qu'une seule fois par instance : un second appel lève une `DiskManagerException`.

## Compilation et exécution

Prérequis : JDK 11 ou supérieur.

```bash
# Linux et macOS
javac -d out src/*.java
java -cp out DiskManagerTests
```

```powershell
# Windows PowerShell
javac -d out (Get-ChildItem src\*.java).FullName
java -cp out DiskManagerTests
```

## Tests

Les tests ne dépendent que des interfaces fournies et du constructeur sans argument de `DiskManager`. Ils sont répartis en deux catégories :

- tests obligatoires, déduits de l'énoncé : un échec est une erreur
- tests de robustesse, pour des comportements raisonnables mais non imposés : un échec est un avertissement

Ils couvrent notamment l'écriture et la lecture sur des pages de 1 à 65536 octets, l'absence de débordement entre pages voisines, la réutilisation des pages libérées, le compteur de pages actives, la persistance après `Save()` puis `Init()`, le refus d'une taille de page incompatible, le refus d'un second `Init`, et un test de stress aléatoire comparé à un modèle en mémoire.

## État d'avancement

- [x] Structure du projet et interfaces
- [x] Suite de tests
- [x] Implémentation du `DiskManager`
- [x] Vérification finale : tous les tests obligatoires passent

## Auteurs

- Marie-Paule Lima
- Pernel Djahou


