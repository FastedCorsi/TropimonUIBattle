# Audit des multiplicateurs d'attaque — 30 août 2026

## Corrections

- Le badge de la deuxième ligne recouvrait les PP de la première. Les badges
  occupent maintenant une poche de 30 × 10 pixels GUI à l'intérieur des boutons
  Cobblemon, sous le nom et avant l'icône de catégorie ; le texte est centré.
- L'efficacité dépendait du calculateur optionnel, avec une méthode qui ignorait
  l'attaque et le terrain. Une même fonction de règles produit désormais les
  badges et les lignes d'efficacité de l'infobulle, sans cette dépendance.
- Les adversaires étaient sélectionnés dans l'ordre de révélation, avec un cache
  d'équipe rafraîchi tous les cinq ticks. Les badges lisent les emplacements actifs,
  les formes et les cibles légales de Cobblemon au rendu. L'attaquant est celui de
  la requête d'action, y compris lors de la sélection du deuxième Pokémon en double.
- Les aspects d'une forme de départ ne sont plus réappliqués sur une forme courante
  sans aspects. Les changements de type composés et la Téracristallisation sont suivis.
- Le survol n'appelle plus l'état partagé du calculateur : il utilise une instance
  privée. Une cible demandée absente ne déclenche plus un calcul contre la première
  cible disponible. Un résultat numérique/type incompatible n'est pas présenté.
- Les faibles dégâts positifs ne sont pas arrondis à zéro ; les multiplicateurs
  exacts, dont ×0,125 après ajout d'un troisième type, ne sont pas tronqués à deux décimales.
- Distorsion, Zone Magique et Zone Étrange peuvent coexister : activer l'une ne
  retire plus les autres du contexte de calcul.

## Cas couverts par les règles et tests

Table standard complète : 324 combinaisons simples et 2 754 combinaisons doubles.
Tests supplémentaires : Atterrissage, Gravité, Anti-Air, Racines, Lévitation,
Ballon, Vol Magnétik, Télékinésie, Balle Fer et Point de Mire ; Brise Moule,
Turbobrasier, Téra-Voltage et Garde-Talent ; talents d'absorption, Garde Mystik,
Pare-Balles, Anti-Bruit, Aéroporté et Téra-Carapace ; Gaz Inhibiteur et talents
qui neutralisent la météo ; protection des partenaires contre les priorités.

Attaques particulières : Lyophilisation, Flying Press, Myria-Flèches,
Ball'Météo, Champ Pulsé, Puissance Cachée, Don Naturel, Jugement, Coup Varia-Type,
Techno-Buster, Danse Éveil, Roue Libre, Taurogne, Massue Liane, Téra Explosion
et Pluie Térastrale. Conversions de type par talents, Électrisation et Déluge
Plasmique ; Téracristallisation normale/Stellaire ; dégâts fixes et OHKO.

Les tests de géométrie vérifient que les badges ne recouvrent ni les noms, ni
les PP, ni les boutons voisins. Les tests du pont de calcul rejettent les résultats
non finis, inversés et incompatibles avec le type ou l'efficacité affichés.

## Références

Règles confrontées au code officiel de Pokémon Showdown consulté le 30 août 2026 :

- [Table des types](https://github.com/smogon/pokemon-showdown/blob/master/data/typechart.ts)
- [Attaques](https://github.com/smogon/pokemon-showdown/blob/master/data/moves.ts)
- [Talents](https://github.com/smogon/pokemon-showdown/blob/master/data/abilities.ts)
- [Objets](https://github.com/smogon/pokemon-showdown/blob/master/data/items.ts)
- [Types et immunités du Pokémon](https://github.com/smogon/pokemon-showdown/blob/master/sim/pokemon.ts)

## Limites et vérification en jeu

Ce n'est pas un second moteur de combat. Les badges sont une prévisualisation de
l'efficacité contre chaque cible légale, pas une garantie de toucher ou de mettre
K.O. : l'ordre des actions, une redirection, un changement adverse, Abri, la précision
et les événements encore inconnus peuvent modifier l'issue. Un talent ou objet
non révélé n'est pas déduit des données privées. Les règles personnalisées serveur
et les transformations non communiquées au client ne peuvent pas être garanties.

Les dégâts détaillés restent ceux du calculateur installé et de ses hypothèses.
Le contrôle de cohérence évite certaines contradictions mais ne prouve pas toute
sa simulation. Les types/baies personnalisés non reconnus restent indéterminés.

Validation automatisée : `..\gradlew.bat check build` depuis ce module. Reste à
valider visuellement dans une vraie bataille après installation et redémarrage :
échelles GUI 1–4/auto, doubles, changement adverse, météo et formes en direct,
calculateur ouvert avant/après survol (réglages manuels conservés).
