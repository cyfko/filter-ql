---
sidebar_position: 3
---

# Syntaxe DSL

Le DSL (Domain-Specific Language) booléen de FilterQL permet de composer des filtres atomiques en expressions logiques complexes.

---

## Grammaire Formelle (EBNF)

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

---

## Opérateurs

| Opérateur | Symbole | Précédence | Associativité | Description |
|-----------|---------|------------|---------------|-------------|
| NOT | `!` | 3 (haute) | Droite | Négation unaire préfixe |
| AND | `&` | 2 | Gauche | Conjonction binaire |
| OR | `\|` | 1 (basse) | Gauche | Disjonction binaire |
| Parenthèses | `( )` | - | - | Forcer le regroupement |

### Précédence

Les opérateurs sont évalués dans cet ordre (du plus prioritaire au moins prioritaire) :

1. **Parenthèses** `( )` - Forcent l'évaluation
2. **NOT** `!` - Évalué en premier
3. **AND** `&` - Évalué avant OR
4. **OR** `|` - Évalué en dernier

---

## Exemples

### Expressions Simples

```
"f1"                    // Filtre unique
"f1 & f2"               // f1 ET f2
"f1 | f2"               // f1 OU f2
"!f1"                   // NON f1
```

### Démonstration de Précédence

```
"f1 & f2 | f3"          // Équivalent à: ((f1 & f2) | f3)
                        // AND lie plus fort que OR

"f1 | f2 & f3"          // Équivalent à: (f1 | (f2 & f3))
                        // AND évalué en premier

"!f1 & f2"              // Équivalent à: ((!f1) & f2)
                        // NOT évalué en premier
```

### Regroupement Explicite

```
"(f1 | f2) & f3"        // Override la précédence
                        // OR évalué avant AND

"!(f1 & f2)"            // Négation de toute la sous-expression
                        // NON (f1 ET f2)

"((f1 & f2) | f3)"      // Parenthèses supplémentaires (valides)
```

### Expressions Complexes

```
"(f1 & f2) | (f3 & !f4)"
// (f1 ET f2) OU (f3 ET NON f4)

"((a & b) | (c & d)) & !(e | f)"
// ((a ET b) OU (c ET d)) ET NON (e OU f)

"!deleted & (active | pending)"
// Pattern commun : NON supprimé ET (actif OU en attente)

"(premium | vip) & !banned & verified"
// (premium OU vip) ET NON banni ET vérifié
```

---

## Syntaxe Raccourcie

FilterQL supporte des raccourcis pour les cas courants :

| Raccourci | Expansion |
|-----------|-----------|
| `"AND"` | Combine tous les filtres avec AND : `f1 & f2 & ... & fn` |
| `"OR"` | Combine tous les filtres avec OR : `f1 \| f2 \| ... \| fn` |
| `"NOT"` | Combine avec AND puis négate : `!(f1 & f2 & ... & fn)` |

```java
// Avec 3 filtres définis: f1, f2, f3

FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("f1", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .filter("f2", UserPropertyRef.AGE, Op.GTE, 18)
    .filter("f3", UserPropertyRef.EMAIL, Op.NOT_NULL, null)
    .combineWith("AND")  // Équivalent à: "f1 & f2 & f3"
    .build();
```

---

## Règles de Validation

### 1. Références Définies

Le DSL ne doit référencer que des clés de filtre définies dans `filters` :

```java
// ❌ INVALIDE : f2 non défini
FilterRequest.<UserPropertyRef>builder()
    .filter("f1", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("f1 & f2")  // f2 n'existe pas !
    .build();
// Throws: DSLSyntaxException
```

### 2. Expression Non-Vide

Une expression vide ou composée uniquement d'espaces est invalide :

```java
// ❌ INVALIDE : expression vide
.combineWith("")
// Throws: DSLSyntaxException
```

### 3. Parenthèses Équilibrées

Les parenthèses doivent être correctement ouvertes et fermées :

```java
// ❌ INVALIDE : parenthèse non fermée
.combineWith("(f1 & f2")
// Throws: DSLSyntaxException

// ❌ INVALIDE : parenthèse fermante orpheline
.combineWith("f1 & f2)")
// Throws: DSLSyntaxException
```

### 4. Opérateurs Valides

Seuls `&`, `|`, `!`, `(`, `)` sont autorisés :

```java
// ❌ INVALIDE : && n'est pas supporté
.combineWith("f1 && f2")
// Throws: DSLSyntaxException

// ❌ INVALIDE : || n'est pas supporté
.combineWith("f1 || f2")
// Throws: DSLSyntaxException
```

### 5. Opérateurs Binaires avec Opérandes

Les opérateurs binaires requièrent des opérandes gauche et droit :

```java
// ❌ INVALIDE : & sans opérande gauche
.combineWith("& f1")
// Throws: DSLSyntaxException

// ❌ INVALIDE : | sans opérande droit
.combineWith("f1 |")
// Throws: DSLSyntaxException
```

---

## Identifiants

### Règles de Nommage

- Doit commencer par une lettre ou underscore (`[a-zA-Z_]`)
- Peut contenir lettres, chiffres ou underscores (`[a-zA-Z0-9_]`)
- Sensible à la casse (`filter1` ≠ `Filter1`)

### Exemples Valides

```
f1
filter_name
UserFilter
active_users_2024
_privateFilter
```

### Exemples Invalides

```
1filter     // Commence par un chiffre
filter-name // Tiret non autorisé
filter.name // Point non autorisé
```

---

## Bonnes Pratiques

### 1. Noms Descriptifs

```java
// ✅ Bon : noms qui décrivent l'intention
.filter("activeUsers", ref, Op.EQ, Status.ACTIVE)
.filter("adultAge", ref, Op.GTE, 18)
.filter("premiumMembers", ref, Op.EQ, true)
.combineWith("activeUsers & adultAge & premiumMembers")

// ❌ Mauvais : noms cryptiques
.filter("f1", ref, Op.EQ, Status.ACTIVE)
.filter("f2", ref, Op.GTE, 18)
.combineWith("f1 & f2")
```

### 2. Expressions Lisibles

```java
// ✅ Bon : espaces et regroupement clair
.combineWith("(active | pending) & !deleted")

// ❌ Mauvais : compact et difficile à lire
.combineWith("(a|p)&!d")
```

### 3. Limiter la Complexité

```java
// ✅ Bon : expression modérément complexe
.combineWith("(status1 | status2) & dateRange & !excluded")

// ⚠️ Attention : expression très complexe (difficile à maintenir)
.combineWith("((a & b) | (c & d)) & ((e | f) & !(g & h)) | (i & !j)")
```

---

## Performances

Le parser DSL utilise :

- **Conversion infix-to-postfix** en O(n)
- **Évaluation single-pass** de l'expression
- **Cache structurel** pour réutiliser les expressions identiques

```java
// Les expressions identiques partagent le même cache
request1.combineWith("f1 & f2 & f3");
request2.combineWith("f1 & f2 & f3");  // Utilise le cache
```

---

## Prochaines Étapes

- [Projection](projection) - Projeter des champs spécifiques dans les résultats
- [Référence Core](../reference/core) - API complète du module core
