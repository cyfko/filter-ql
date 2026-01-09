---
sidebar_position: 5
---

# Spécification du Protocole FilterQL

Spécification formelle du protocole FilterQL définissant le format de message, la grammaire DSL, la syntaxe de projection, les règles de validation et les exigences d'interopérabilité.

---

## Introduction

FilterQL est un protocole pour l'expression, la validation et l'exécution de filtres dynamiques sur des entités métier. Il garantit l'interopérabilité, la sécurité de type et l'extensibilité du filtrage dans les systèmes distribués ou modulaires.

---

## Terminologie

| Terme | Définition |
|-------|------------|
| **Filter** | Expression logique définissant des critères de sélection sur des entités |
| **DSL** | Langage formel pour décrire les filtres (ex: JSON, chaîne structurée) |
| **Condition** | Représentation logique d'un filtre, combinable (and, or, not) |
| **Property Reference** | Identifiant unique d'un attribut filtrable d'une entité |
| **Adapter** | Composant responsable de la traduction du filtre vers un moteur d'exécution cible |

---

## Format de Message

### Structure Canonique

```json
{
  "filters": { ... },      // Dictionnaire de filtres atomiques nommés
  "combineWith": "...",    // Expression DSL booléenne sur les clés de filtre
  "projection": [ ... ],   // (optionnel) Liste des champs à retourner
  "pagination": { ... }    // (optionnel) Page, taille, tri
}
```

### Exemple Conceptuel

**Intention sémantique :** *"Trouver les utilisateurs nommés 'john' qui ont plus de 25 ans, retournant leur nom d'utilisateur, email et leurs 10 livres les plus récents (avec titre et année), affichant 20 utilisateurs par page à partir de la page 1."*

```json
{
  "filters": {
    "f1": { "ref": "USERNAME", "op": "EQ", "value": "john" },
    "f2": { "ref": "AGE", "op": "GT", "value": 25 }
  },
  "combineWith": "f1 & f2",
  "projection": [
    "username",
    "email",
    "books[size=10,sort=year:desc].title,year"
  ],
  "pagination": { "page": 1, "size": 20 }
}
```

**Cet exemple démontre :**
- Combinaison de filtres booléens (`f1 & f2`)
- Projection de collection avec pagination inline (`size=10`)
- Tri de collection (`sort=year:desc`)
- Syntaxe multi-champs compacte (`.title,year`)
- Pagination de niveau supérieur (page 1, 20 éléments par page)

### Détails des Composants

| Composant | Format |
|-----------|--------|
| **FilterDefinition** | `{ "ref": <PROPERTY_REF>, "op": <OP>, "value": <VAL> }` |
| **combineWith** | Expression DSL booléenne sur les clés de filtre |
| **projection** | Liste de champs avec pagination/tri inline optionnel |
| **pagination** | `{ "page": <int>, "size": <int>, "sort": [...] }` |

---

## Grammaire DSL Booléenne (combineWith)

Le champ `combineWith` contient une expression booléenne qui combine les filtres atomiques.

### Grammaire Formelle (EBNF)

```ebnf
(* Expression DSL Booléenne *)
expression           = term , { "|" , term } ;
term                 = factor , { "&" , factor } ;
factor               = [ "!" ] , ( identifier | "(" , expression , ")" ) ;

(* Identifiant de filtre *)
identifier           = ( letter | "_" ) , { letter | digit | "_" } ;

(* Terminaux *)
letter               = "a" .. "z" | "A" .. "Z" ;
digit                = "0" .. "9" ;
```

### Précédence des Opérateurs (Haute vers Basse)

| Priorité | Opérateur | Associativité | Type |
|----------|-----------|---------------|------|
| 3 | `!` (NOT) | Droite | Préfixe unaire |
| 2 | `&` (AND) | Gauche | Infixe binaire |
| 1 | `\|` (OR) | Gauche | Infixe binaire |
| - | `( )` (Parenthèses) | - | Groupement |

### Règles de Grammaire

1. **Identifiants** : Doivent commencer par lettre ou underscore, suivis d'alphanumériques ou underscore
2. **Espaces** : Optionnels autour des opérateurs et identifiants
3. **Associativité** : 
   - Gauche : `a & b & c` → `((a & b) & c)`
   - Droite : `!a & !b` → `((!a) & (!b))`
4. **Parenthèses** : Doivent être équilibrées et correctement imbriquées
5. **Identifiants valides** : Doivent référencer des clés définies dans `filters`

### Exemples d'Expressions DSL

```
// Expressions simples
"f1"                    // Filtre unique
"f1 & f2"               // Combinaison AND
"f1 | f2"               // Combinaison OR
"!f1"                   // Négation NOT

// Démonstration de précédence
"f1 & f2 | f3"          // ((f1 & f2) | f3) - AND lie plus fort que OR
"f1 | f2 & f3"          // (f1 | (f2 & f3)) - AND évalué en premier
"!f1 & f2"              // ((!f1) & f2) - NOT évalué en premier

// Groupement explicite avec parenthèses
"(f1 | f2) & f3"        // Override la précédence
"!(f1 & f2)"            // Négate toute la sous-expression

// Expressions complexes
"(f1 & f2) | (f3 & !f4)"               // Groupes multiples
"((a & b) | (c & d)) & !(e | f)"       // Logique imbriquée
"!deleted & (active | pending)"        // Pattern de filtrage commun
```

### Syntaxe Raccourcie

Les implémentations PEUVENT supporter ces expansions :

| Raccourci | Expansion |
|-----------|-----------|
| `"AND"` | Combine tous les filtres avec AND |
| `"OR"` | Combine tous les filtres avec OR |
| `"NOT"` | Combine avec AND puis négate : `!(f1 & f2 & ... & fn)` |

---

## Syntaxe des Champs de Projection

Les champs de projection supportent une syntaxe avancée pour la pagination et le tri de collection.

### Grammaire Formelle (EBNF)

```ebnf
(* Spécification de champ de projection *)
projection-field     = simple-field | nested-field | collection-field ;

(* Champ simple sans hiérarchie *)
simple-field         = field-name ;

(* Champ imbriqué avec notation point *)
nested-field         = field-path , "." , field-list ;

(* Champ de collection avec pagination/tri optionnel *)
collection-field     = field-path-with-options , "." , field-list ;

(* Chemin de champ *)
field-path           = field-segment , { "." , field-segment } ;
field-segment-with-options = field-name , [ collection-options ] ;

(* Liste multi-champs *)
field-list           = field-name , { "," , field-name } ;

(* Options de collection *)
collection-options   = "[" , option-list , "]" ;
option-list          = option , { "," , option } ;
option               = size-option | page-option | sort-option ;

(* Options individuelles *)
size-option          = "size=" , positive-integer ;
page-option          = "page=" , non-negative-integer ;
sort-option          = "sort=" , sort-spec , { "," , sort-spec } ;
sort-spec            = field-name , ":" , sort-direction ;
sort-direction       = "asc" | "desc" | "ASC" | "DESC" ;

(* Terminaux *)
field-name           = ( letter | "_" ) , { letter | digit | "_" | "-" } ;
positive-integer     = digit , { digit } ;  (* 1..10000 enforced *)
non-negative-integer = "0" | positive-integer ;
```

### Options Disponibles

| Option | Description | Défaut | Plage |
|--------|-------------|--------|-------|
| `size=N` | Taille de page | 10 | 1 à 10000 |
| `page=P` | Numéro de page (0-indexé) | 0 | 0+ |
| `sort=field:dir` | Tri par champ | - | asc/desc |

### Exemples de Syntaxe

```json
// Projection simple
{"projection": ["id", "name", "email"]}

// Champ imbriqué
{"projection": ["id", "address.city", "address.country"]}

// Multi-champs avec préfixe partagé
{"projection": ["id", "address.city,country,postalCode"]}
// Équivalent à: ["id", "address.city", "address.country", "address.postalCode"]

// Collection avec pagination
{"projection": [
  "id",
  "name",
  "books[size=10].title,year"
]}

// Collection avec tri
{"projection": [
  "id",
  "name",
  "books[sort=year:desc].title,year"
]}

// Collection avec pagination ET tri
{"projection": [
  "id",
  "name",
  "books[size=20,page=0,sort=year:desc].title,author,year"
]}

// Tri multi-colonnes
{"projection": [
  "id",
  "books[sort=year:desc,title:asc].title,year"
]}

// Pagination hiérarchique
{"projection": [
  "id",
  "name",
  "authors[size=10].name,books[size=5,sort=year:desc].title,year"
]}
```

---

## Règles de Validation

### Expressions DSL

1. **Références non définies** : Le DSL NE DOIT PAS référencer des clés non présentes dans `filters`
2. **Expression vide** : Les expressions vides ou composées uniquement d'espaces sont INVALIDES
3. **Parenthèses non équilibrées** : Les expressions avec `(` et `)` non appariés sont INVALIDES
4. **Opérateurs invalides** : Seuls `&`, `|`, `!`, `(`, `)` sont autorisés
5. **Placement des opérateurs** : Les opérateurs binaires requièrent des opérandes gauche et droit

### Projections de Collection

Les références multiples à la même collection DOIVENT utiliser des options identiques :

```json
// ❌ INVALIDE : options conflictuelles pour 'books'
{
  "projection": [
    "books[size=10].title",
    "books[size=20].author"
  ]
}

// ✅ VALIDE : options identiques
{
  "projection": [
    "books[size=10,sort=year:desc].title",
    "books[size=10,sort=year:desc].author"
  ]
}

// ✅ VALIDE : utiliser la syntaxe multi-champs
{
  "projection": [
    "books[size=10,sort=year:desc].title,author"
  ]
}
```

---

## Règles de Traitement

1. Parser le message de filtre selon la grammaire DSL
2. Valider chaque propriété et opérateur selon le schéma de contrat
3. Construire l'arbre de conditions logique
4. Passer la structure validée à un adaptateur d'exécution
5. L'adaptateur traduit la structure en requête native pour le moteur cible

---

## Exigences d'Interopérabilité

- Toute implémentation DOIT accepter et produire des filtres selon le format défini
- Les propriétés et opérateurs DOIVENT être explicitement documentés et validés
- Les extensions (nouveaux opérateurs, types, adaptateurs) DOIVENT respecter l'architecture en couches

---

## Sécurité et Robustesse

- Les entrées utilisateur DOIVENT être validées pour prévenir l'injection ou la corruption de requête
- Les références de propriété DOIVENT provenir d'un schéma de confiance
- Le protocole NE SUPPOSE PAS de langage ou technologie d'implémentation spécifique

---

## Vision Stratégique

Le protocole est conçu pour être extensible (nouveaux opérateurs, formats, adaptateurs) et garantir la portabilité des filtres à travers des systèmes hétérogènes.

---

## Résumé du Protocole

| Aspect | Valeur |
|--------|--------|
| **Nom** | Protocole FilterQL |
| **Objectif** | Standardiser l'expression, la validation et l'exécution de filtres dynamiques |
| **Format** | JSON |
| **Grammaires** | EBNF pour DSL booléen et syntaxe de projection |
| **Licence** | MIT |
