# Historique, emblèmes d'équipe et Illusion

## Affichage

- Le fond des lignes du journal suit leur sujet : joueur cyan, adversaire rouge,
  événements globaux neutres. Mentionner le joueur en tant que cible ne donne plus
  un fond bleu à l'action adverse. Toutes les lignes d'un message replié gardent le même côté.
- Chaque portrait réutilise l'emblème Poké Ball bleue de Tropimon,
  désormais embarqué à l'identique dans
  `tropimon_ui_battle:textures/gui/skin/team_emblem.png`, sans nouveau dessin.
  L'image source fait 32 × 32 pixels et possède 292 pixels complètement transparents,
  dont les quatre coins. Le rendu reste carré, ajusté à la taille GUI. Les
  métadonnées propres à cette texture activent le filtrage linéaire et le clamp,
  sans modifier l'image ni le filtrage des textures Cobblemon.
- Le modèle animé est réduit et centré dans un carré inscrit dans l'emblème.
  Les PV occupent une ligne dédiée sous la Poké Ball ; une seconde ligne reçoit
  le bandeau natif et le texte `cobblemon.ui.status.*` (BRN, PAR, PSN, SLP, etc.,
  selon la langue du jeu). Les modèles, PV et statuts ne se superposent plus.
- Les compteurs de terrain sont plus grands, en gras et avec un contour sombre.
  Cyan en temps normal, ambre à deux tours restants, rouge à un seul tour ; une
  fourchette n'est pas remplacée par une durée certaine. Les hazards conservent
  leurs couches et leur côté, sans avertissement d'expiration trompeur.

## Cause de la double attribution des dégâts

Dans Cobblemon 1.7.2, `ActiveBattlePokemonDTO.fromPokemon` peut fournir à l'adversaire
l'UUID, le nom et l'apparence du Pokémon imité, avec les PV du porteur d'Illusion.
`BattleReplacePokemonHandler` remplace ensuite l'occupant du même emplacement par
le vrai Pokémon. Le journal mémorisait les deux UUID indépendamment : la fiche du
Pokémon imité gardait donc les dégâts, et parfois le statut ou le K.O., de Zoroark.

La nouvelle observation mémorise l'état connu avant chaque entrée en jeu, au moment
où Cobblemon change effectivement l'occupant après ses animations. Lors du paquet de
révélation, cet état est restauré pour le Pokémon imité. Si celui-ci n'avait jamais
été vu auparavant, sa fiche provisoire est retirée au lieu d'inventer des PV.

Seules les nouvelles observations d'attaques/PP sont transférées à l'identité réelle,
en conservant ses PP précédemment connus. Les effets individuels et les nouvelles
observations d'objets suivent aussi cette identité. Les PV et le statut réels viennent
du paquet natif. Aucun dégât/soin supplémentaire n'est fabriqué par cette transition.

L'algorithme se base sur le remplacement explicite d'identité, pas sur le nom de
l'espèce : il couvre Zoroark normal et de Hisui sans dévoiler Illusion avant sa
révélation. Les remplacements conservant le même UUID ne déclenchent pas de rollback.

Cette distinction correspond au message
[`replace` du protocole Showdown](https://github.com/smogon/pokemon-showdown/blob/master/sim/SIM-PROTOCOL.md#major-actions).
Les détails Cobblemon ont été vérifiés dans le JAR 1.7.2 installé localement.

## Vérification

Les tests couvrent les deux formes de Zoroark, un Pokémon imité déjà blessé ou
jamais réellement vu, les petits PV positifs, les emplacements en double, les PP
antérieurs et nouveaux, les attaques appelées, les restaurations de PP et Rancune.
Un test Fabric vérifie que les hooks natifs sont bien appliqués. Les tests utilisent
Java 21 et Fabric Loader JUnit pour exécuter les transformations Minecraft requises
par les objets et registres natifs, sans lancer de fenêtre de jeu.

Le rendu final et une séquence complète de paquets d'un vrai combat restent à
vérifier en jeu après installation et redémarrage.
