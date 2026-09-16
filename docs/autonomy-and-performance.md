# Autonomie et optimisation — 30 août 2026

## Périmètre

UI Battle reste une interface cliente de Cobblemon 1.7.2 : aucune règle, action
serveur, animation ou disposition graphique n'est remplacée par ce travail.
Les lectures des Pokémon, emplacements actifs, objets, attaques et registres
Minecraft restent sur le thread client. Le rappel de rechargement des ressources
ne fait qu'incrémenter un numéro d'invalidation ; il ne lit aucun objet vivant.

## Indépendance

- `TropimonDamageCalcBridge` utilise maintenant des classes, modèles et caches
  dans `fr.tropimon.battleui`, sans réflexion vers le calculateur séparé.
- Les formules du calculateur ont été reprises localement. Les descriptions du
  Dex viennent des API/registres et ressources publics de Cobblemon. Les appels
  réfléchis encore présents ne concernent que ces API officielles.
- Les observations de combat nécessaires au calcul sont possédées par UI Battle,
  indexées par UUID et emplacement, y compris lors de la révélation d'Illusion.
- Aucune dépendance de production à Tropimon Client, Damage Calculator, Chat
  Filter, Catch Preview ou une bibliothèque commune Tropimon.
- Aucun accès à leurs fichiers de configuration, services, prévisualisations
  privées ou état partagé. Pour les combats normaux uniquement, UI Battle possède
  sa propre copie minimale du client HTTP de suggestions de l'API Tropimon ; il
  ne charge ni n'appelle le mod Damage Calculator.
- Les données Random Battle nécessaires aux combattants sélectionnés et aux
  partenaires sont embarquées. Elles filtrent les sets avec les informations
  publiquement observées, appliquent le profil standard à 85 EV quand les
  statistiques sont inconnues et ne retiennent un objet, talent, type Téra ou
  mouvement que s'il est commun à tous les sets encore compatibles. Un remplacement éventuel est lu
  uniquement dans `config/tropimon_ui_battle/random-battle-sets.json`.
  Les données exactes connues restent prioritaires et aucun set arbitraire n'est
  choisi lorsque plusieurs possibilités subsistent.

La protection qui sauvegardait/restaurait l'état du calculateur séparé est
remplacée par une isolation stricte : UI Battle ne le lit et ne l'écrit plus.
L'avertissement de conflit avec Cobblemon Extended Battle UI est conservé.

Conséquence nécessaire de l'autonomie : les modifications de configuration ou
les hypothèses manuelles du calculateur séparé n'influencent plus UI Battle.
Ses réglages ne sont ni migrés ni modifiés automatiquement. Les valeurs publiques
de combat et les suggestions API restent distinctes. En Random Battle, le client
HTTP est ignoré et seul le snapshot Random Battle est appliqué. La parité chiffrée est
testée à entrées identiques ; cela ne constitue pas une preuve d'équivalence de
toutes les séquences réseau entre l'ancien pont externe et le nouvel adaptateur.

## Apparence et ressources

Les deux images auparavant cherchées dans Tropimon Client sont copiées **à
l'identique**, sans redessin, dans le namespace propre à UI Battle :

| Fichier dans `textures/gui/skin` | Dimensions | SHA-256 |
| --- | --- | --- |
| `team_emblem.png` | 32 × 32 | `3d08276fe73dde67e0b449795d7c41f872ed88361eee61b4af285d17de3bba85` |
| `history_frame.png` | 345 × 205 | `314929311012f3688597ff5fe160ade70acb3080c74431f70a64e21be0ec875b` |

Sources : `assets/tropimodclient/icons/icon_32x32.png` et
`assets/tropimodclient/guis/navigator/navmain/navigator.png`, extraits du JAR
local sans modifier celui-ci. Les 30 pictogrammes d'effets restent inchangés.
Le build vérifie le nombre, les dimensions et le canal alpha des images ; les
tests contrôlent également les empreintes exactes des deux copies.

## Journal incrémental

`RevisionedJournal` distingue ajout et réécriture. `IncrementalHistory` conserve
les lignes déjà repliées et les ancres de tours ; seuls les nouveaux messages
sont reformatés. Les couleurs, styles, sources survolables, horodatages et tours
passent par la même fonction de rendu qu'avant.

Une modification versionnée d'ancienne entrée (`replace`), une troncature, un
reset, un changement de largeur, de langue, de renderer ou de ressources force
la reconstruction. La navigation par tour et la position de lecture utilisent
toujours `BattleHistoryNavigation`, y compris lorsque le suivi automatique est
désactivé. Les textes archivés sont traités comme des valeurs : toute future
édition doit passer par `replace`, pas muter un `Text` déjà archivé en place.

Mesure déterministe du test : 10 000 entrées initiales, puis 50 ajouts séparés,
produisent **10 050 appels de mise en forme au total**, sans reformater les
10 000 premières à chaque ajout. Ce n'est pas une mesure de FPS en jeu.

## Calcul, équipes et infobulles

- Un contexte persiste par couple attaquant/cible (maximum 24). Les modèles sont
  actualisés depuis les objets natifs, puis comparés à des instantanés de valeurs.
- Une map EV/IV adverse allouée mais vide n'est plus prise pour une statistique
  connue à 0 EV. Le profil normal est enrichi de façon asynchrone par l'API
  Tropimon ; avant sa disponibilité, le résultat couvre 0 à 252 EV défensifs.
- Les clés ne dépendent ni du tick ni de la révision du journal. Elles prennent
  en compte espèces/formes/types/stats de base, niveau, EV/IV, boosts, PV observés
  et maximaux, statut, objets/talents et leur connaissance, Téra, PP, effets
  individuels, historique utile au calcul, météo, terrain, Gravité, Distorsion,
  écrans, partenaires, nombre de cibles et attaque effective.
- Les résultats partagés uniquement dans ce mod sont bornés à 128 entrées et
  leurs listes sont immuables. Nouvelle bataille, langue ou ressources invalident
  les données concernées. La cible affichée reste attachée à son UUID.
- Les fiches d'équipe sont réutilisées seulement si toutes leurs données sont
  égales, notamment PV minuscules positifs, objet et composants, forme, PP,
  statut et boosts. Les scans natifs ne sont pas supprimés.
- Les lignes d'infobulles et leurs retours à la ligne sont réutilisés selon leur
  contenu exact, langue, ressources et dimensions. Les calculs de position et les
  lectures des attaques/cibles restent dynamiques.
- Les portraits conservent leur état d'animation et réutilisent leur quaternion.
  Les aspects sont copiés seulement lorsqu'ils changent ; le portrait continue
  d'être rendu à chaque image, sans cache de bitmap ni gel d'animation.

Le compteur autonome réattribue seulement les coups reçus sous Illusion et
conserve l'historique antérieur des deux identités. Les annonces natives de coups
multiples, ratés sans sujet, Garde Large et transitions de tour sont testées.

## Nettoyage limité

Suppression après recherche des références, mixins et usages réfléchis :
`TropimonBattleTimeBridge` inutilisé, deux helpers de skin inutilisés et leur
constante, un double parcours de réutilisation d'équipe, le modèle de snapshot
inter-mods devenu inutile et les compteurs de cache remplacés. Aucun pictogramme
actif n'a été supprimé. Aucun mixin de protection ou d'identité n'a été retiré.

## Vérifications reproductibles

Depuis le dossier parent :

```powershell
.\gradlew.bat -p .\TropimonUIBattle test check build
.\gradlew.bat -p .\TropimonUIBattle -Pcoexistence test
```

Le premier mode charge UI Battle et ses dépendances officielles : Minecraft,
Fabric Loader/API, Cobblemon et Fabric Language Kotlin. Le second ajoute les
autres JAR Tropimon locaux, plus leurs dépendances Xaero World Map et GeckoLib,
sans inclure l'ancien JAR installé d'UI Battle. Les deux modes utilisent Java 21,
Fabric Loader JUnit, les registres Minecraft et les transformations des mixins.

Les rapports sont séparés : `build/reports/tests/isolated` et
`build/reports/tests/coexistence`, avec les XML correspondants sous
`build/test-results`. La suite comprend 119 tests ; le test de parité compare
85 scénarios, 680 résultats d'attaques et 1 020 totaux de statistiques entre
calcul direct et calcul avec cache. Les autres tests couvrent invalidation,
journal, navigation, PP, formes, Illusion et application des hooks natifs.

Résultat final : build réussi ; **119/119 tests en isolé et 119/119 en
coexistence**, sans échec, erreur ni test ignoré. Inspection des 158 classes du
JAR final : aucune référence aux packages internes Tropimon externes ou à
`tropimodclient`, anciens helpers supprimés absents de l'archive.
JAR compilé : `build/libs/tropimon-ui-battle-0.1.0.jar`, SHA-256
`f2b4c4a0212670e3b4fbec1cfda52720d9bd7e5f48198ef07b367aad96fe0c6f`.

Une tentative additionnelle avec tout le dossier de mods, au-delà des seuls
mods Tropimon, était bloquée avant les tests par deux versions de CristelLib
et des conflits de remappage Lootr déjà présents. Aucun JAR n'a été supprimé
pour contourner cela ; le profil de coexistence ciblé évite ce mélange.

Limite : ces tests ne lancent pas de fenêtre Minecraft et ne remplacent pas un
contrôle visuel ni un match réel après installation. Aucune installation ni
aucun push n'a été exécuté. Le JAR installé reste à l'empreinte SHA-256
`4d6b055fd670615d52e960cdf1c2af034558e20fd37068c38308d9f3e546e8e2`.
