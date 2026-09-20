# Tropimon UI Battle

By FastedCorsi

Interface de combat cliente greffée sur Cobblemon 1.7.2 pour Tropimon.

Le mod ne remplace aucune règle de combat : les actions, cibles, changements et
validations restent entièrement gérés par Cobblemon. L'interface ajoute :

- le tour et le temps dans l'en-tête de l'historique, plus des pictogrammes avec
  tours restants pour la météo, les terrains, les écrans et les effets d'équipe ;
- les effets globaux centrés en haut, sans cadre ni fond, avec leur compteur
  agrandi, en gras, coloré et détouré sous l'icône : `N` ou `N–M` pour les tours,
  `×N` uniquement pour les couches de hazards ; les durées incertaines conservent leur fourchette et les
  hazards restent sur leur côté du terrain ;
- les équipes révélées avec PV en pourcentage, statut, objet, talent et attaques connus ;
- en spectateur, les deux camps sont suivis avec la même orientation que les
  portraits natifs (camp 2 à gauche, camp 1 à droite), sans lire les équipes privées ;
  PV animés, talents possibles/révélés et PP observés sont conservés pour chaque camp ;
- les objets consommés, éclatés, sabotés, volés ou échangés restent dans l'historique
  du Pokémon concerné, mais ne sont plus utilisés comme objets tenus ; Ceinture Force
  et Ballon sont identifiés même lorsque le message natif omet l'argument d'objet ;
  Bandeau, Recyclage et les transferts conservent leur fonctionnement distinct ;
  Tour de Magie/Passe-Passe mettent à jour les deux propriétaires, même lorsqu'un
  emplacement était vide ou que les objets sont échangés une seconde fois ;
- la Baie Prine retire immédiatement le statut et la confusion de son consommateur
  dans l'interface, y compris en spectateur lorsque le snapshot natif tarde à suivre ;
- les pourcentages de PV actifs suivent l'animation native image par image, sans
  saut vers la valeur finale ni retour à une ancienne valeur lors des scans ; le
  compteur affiche les dixièmes pendant la variation (`99,9 %`, `99,8 %`, etc.)
  tout en conservant les centièmes sous 1 % pour les cas comme Ceinture Force ;
- au survol adverse, tous les talents possibles de la forme connue, y compris les
  talents cachés marqués `TC` (`HA` en anglais), la fourchette de Vitesse publique,
  l'historique des objets et celui des formes, de Transform et d'Illusion ;
- les portraits Cobblemon des Pokémon vus, avec un état visuel distinct pour les K.O. et les statuts ;
- les portraits agrandis et animés par le temps réel de rendu Cobblemon, sans
  emblème ni fond Tropimon ; les PV et statuts rapprochés du modèle, les PV sur
  la ligne suivante (en gras pour les Pokémon actifs), puis les bandeaux avec
  abréviations natives de statut Cobblemon ; l'équipe adverse se lit de droite
  à gauche, avec les emplacements non révélés vers la gauche ;
- une disposition responsive qui adapte cartes, panneaux, infobulles et retours à la ligne
  à la résolution de la fenêtre et à l'échelle GUI choisie dans Minecraft ;
- les types et faiblesses de type de chaque Pokémon connu au survol ;
- les paliers de statistiques actifs, lus directement depuis l'état de combat Cobblemon ;
  badges `Atq+2`, `Déf-1`, etc. juste sous les PV du Pokémon actif de chaque côté,
  avec les flèches hausse/baisse et le fond des PP de Cobblemon ; les badges se
  replient dans la largeur de la barre de PV, après le statut éventuel, et réservent
  l'espace nécessaire entre les portraits en double et au-dessus des effets de camp ;
- les actions et attaques déplaçables ensemble par la poignée `Déplacer` ; clic
  droit sur cette poignée pour restaurer la position native ; la position est
  conservée dans `config/tropimon_ui_battle/action-position.properties` et adaptée
  à la taille de fenêtre ; les clics, les gimmicks et les infobulles suivent le
  même déplacement, sans changer les réponses de combat ; les sélecteurs de
  changement de Pokémon, de cible et de confirmation restent les panneaux natifs ;
  la première capacité recouvre exactement la position du bouton `Attaque`, rendu
  et hitbox compris, tandis que l'espacement natif entre les capacités plus larges
  est préservé, y compris lorsque Mega Showdown remplace la gestion des clics ;
  Retour est placé dans la bande libre au-dessus des capacités et les autres contrôles
  restent bornés à l'écran ;
- les deux rangées d'équipe en haut sont déplaçables séparément avec leur propre
  poignée ; portraits, PV, statuts, positions et noms de dresseurs se déplacent
  ensemble, avec sauvegarde et clic droit de réinitialisation ;
- dans le sélecteur natif de changement, une analyse compacte apparaît au survol
  de chaque Pokémon disponible : risque défensif face aux attaques révélées (ou,
  à défaut, aux types connus) et efficacité de chacune de ses attaques contre
  chaque adversaire actif ; les noms des attaques sont en gras et colorés selon
  leur type pour être repérés immédiatement ; les cibles de double et triple restent séparées ;
- un journal agrandi inspiré de Pokémon Showdown, structuré par tours, horodaté,
  déroulable à la molette et conservant la position lors de nouveaux messages ;
- un historique déplaçable par son titre et redimensionnable par ses bords ou
  coins ; clic droit sur le titre pour réinitialiser, disposition sauvegardée
  dans son propre fichier `config/tropimon_ui_battle/history-window.properties` ;
  la fenêtre reste bornée à l'écran quand la résolution ou l'échelle GUI change,
  sans clic ni infobulle d'attaque traversant le panneau ;
- un bouton Cobblemon `TOUR` dans l'en-tête, avec survol et clic natifs : début du combat, accès direct aux tours,
  liste déroulable à la molette et `Derniers messages` pour reprendre le suivi ;
  clic extérieur ou Échap ferme la liste sans déclencher une action de combat ;
- des infobulles d'effets multilignes bornées à la fenêtre, avec titre en gras
  et description séparée, sans caractère parasite de retour à la ligne ;
- les pertes et soins en pourcentage, les variations de stats et les objets ;
- un journal à palette sobre : texte sombre sur fond clair, noms alliés bleu pétrole
  et adverses rouge sombre, fonds de camp très légers ; les pourcentages exacts
  restent en gras sans couleurs supplémentaires pour les soins, dégâts ou types ;
- lors d'une révélation d'Illusion, restauration de la fiche connue du Pokémon
  imité et réattribution des observations à l'identité réelle ; les PV et les PP
  utilisés sous le déguisement ne restent pas copiés sur deux Pokémon ;
- les capacités colorées selon leur type hors du journal ; dans l'historique,
  attaques, objets et talents sont en gras neutre avec descriptions contrastées
  au survol, y compris les objets consommés et les effets persistants identifiés ;
  toutes les lignes descriptives Cobblemon des objets sont proposées sans Maj ;
  les valeurs des attaques au survol du journal sont explicitement les valeurs
  de base, pas un recalcul rétroactif du combat avec son état actuel ;
- les états volatils ne sont pas présentés comme une attaque homonyme : une ligne
  de confusion sans source explicite n'affiche donc plus Choc Mental comme cause ;
- les PP estimés des capacités adverses déjà révélées, y compris Pression, Dépit,
  Rancune, Baie Mepo, attaques appelées, Danseuse, Saisie, Mimique et Transform ;
- les compteurs des effets individuels annoncés (sommeil, poison grave, Encore,
  Provoc, Entrave, Requiem, pièges, Clonage, Vampigraine et chaîne d'Abri), ainsi
  que Vœu, Prescience et Carnareket sur le bon côté du terrain ;
- les types modifiés en combat (Copie-Type, ajout/changement de type et Téracristallisation) ;
- les immunités publiques qui changent l'efficacité affichée (talent neutralisé,
  Gravité, Anti-Air, Atterrissage, Clairvoyance, Œil Miracle et objets révélés) ;
- le type, la puissance et la précision contextuels, la portée mono/zone et la
  raison exacte des attaques bloquées ou imposées ;
- les badges d'efficacité intégrés dans la partie basse de chaque attaque, sans
  recouvrir son nom, son icône de catégorie ou ses PP ; leur fond reprend
  exactement la bande noire native des PP, avec la même police, le même gras,
  la même taille et la même ligne de texte ; ils fonctionnent sans calculateur ;
- une efficacité recalculée depuis les cibles réellement présentes, leurs formes,
  types et immunités révélés, commune au badge et à l'infobulle ; en double et
  triple, les règles natives d'adjacence sont respectées et chaque cible touchée,
  y compris un allié atteint par une attaque de zone, est détaillée au survol ;
- les plages de dégâts estimées séparément pour chaque cible par un moteur embarqué
  autonome, avec son propre état et sans toucher aux réglages des autres mods ; une
  estimation dont le type ou l'efficacité contredit le contexte public est masquée ;
  en combat normal, les profils adverses proviennent uniquement de l'API Tropimon
  et aucune valeur numérique n'est affichée tant que leur nature et leur spread EV
  ne sont pas disponibles, afin de ne jamais inventer un profil Random ; les
  Random Battles utilisent exclusivement leur snapshot embarqué à 85 EV par statistique ;
- des sources explicites et survolables dans le journal pour les dégâts et soins
  causés par un talent, un objet, une attaque résiduelle ou un statut ;
- un journal mis en forme incrémentalement, des caches exacts de calcul et des
  infobulles qui ne sont repliées que si leur contenu ou leurs dimensions changent.

Les formats natifs simple, double, triple et multi de Cobblemon partagent la même
interface responsive. Le mod lit les emplacements et listes de cibles fournis par
Cobblemon : une cible non adjacente en triple n'est pas inventée, le malus de zone
utilise le nombre réel de Pokémon touchés, les PP sous Pression ne comptent que les
adversaires concernés et les talents des deux partenaires restent distincts.
En triple, les actifs portent un repère gauche/centre/droite vu depuis le joueur et
le bouton `Shift` reste l'action réseau native de Cobblemon. L'analyse détaillée
reste affichée sur l'écran natif de sélection de cible. Quick Guard, Mat Block,
Crafty Shield et Wide Guard sont appliqués au bon côté ; Follow Me, Rage Powder,
Lightning Rod et Storm Drain sont signalés comme redirections confirmées ou
possibles. Les groupes de plusieurs dresseurs sont séparés visuellement et un ordre
de Vitesse probable indique `?` dès que les plages se chevauchent ou qu'un objet ou
talent déterminant reste inconnu. Les répétitions par Sommation et les attaques
appelées/copiées conservent la réserve de PP qui leur appartient.

La checklist de validation ciblée est disponible dans
[docs/TRIPLE-LIVE-TEST.md](docs/TRIPLE-LIVE-TEST.md).

Les informations privées de l'équipe adverse ne sont jamais lues : seuls les
Pokémon vus et les objets, talents ou capacités réellement révélés sont mémorisés.
En spectateur, seules les informations transmises par le serveur depuis l'arrivée
sont disponibles : le client ne peut pas reconstituer les PP antérieurs, un objet
déjà consommé ou un effet dont il n'a reçu aucun événement. Ces inconnues ne sont
pas présentées comme des faits établis.
Cette mémoire est conservée pendant le match, y compris après un changement de
Pokémon, et remise à zéro au combat suivant. Un talent possible n'est jamais
présenté comme un talent confirmé ; les calculs conservent leurs avertissements
sur les hypothèses adverses inconnues.

Les multiplicateurs `×0`, `×0,5`, `×1`, `×2`, etc. décrivent l'efficacité, pas le
total des bonus de dégâts (STAB, météo, statistiques, écrans...). Les attaques
à dégâts fixes et les attaques OHKO ont un libellé distinct ; les attaques de
statut n'ont pas de badge de dégâts. `×?` indique des données manquantes, jamais
une neutralité supposée. Les objets, talents, natures et EV suggérés par l'API en
combat normal restent signalés comme des hypothèses ; les observations publiques
les remplacent dès leur révélation. Ces suggestions ne sont jamais utilisées en
Random Battle.

L'[audit d'efficacité](docs/effectiveness-audit.md) détaille les corrections,
les cas vérifiés et les limites de cette prévisualisation cliente.

En combat sauvage, le bouton `Run` termine immédiatement le combat avec la
réponse de forfait native de Cobblemon. Cette substitution est strictement
limitée aux combats joueur contre Pokémon sauvage ; l'interface affiche le
message de fuite de Cobblemon et masque les deux conclusions trompeuses de
forfait et d'équipe inutilisable. Les forfaits en PvP ne sont pas modifiés.

`Cobblemon Extended Battle UI` doit être retiré du dossier des mods pendant les
tests : il remplace lui aussi le journal natif et les deux rendus se superposeraient.

## Assets

Les contrôles réemploient les assets de Cobblemon (`textures/gui/battle` et le
fond Poké Ball du résumé). Seul le cadre d'historique Tropimon reste embarqué à
l'identique dans le namespace `tropimon_ui_battle` : Tropimon Client n'est pas requis. Les
effets de terrain utilisent 30 pictogrammes originaux générés individuellement,
exportés en PNG ARGB transparent 128 × 128 puis réduits à l’affichage. Aucun
pictogramme de terrain, climat, écran ou hazard n’est réutilisé pour un autre effet.

## Compilation locale

Depuis le dossier parent :

```powershell
.\gradlew.bat -p .\TropimonUIBattle build
```

Par défaut, le JAR Cobblemon configuré dans `gradle.properties` est recherché
dans l'instance Tropimon. Sur une autre machine, son chemin peut être fourni avec
`-Pcobblemon_jar_path=<jar>` ou la variable `COBBLEMON_JAR`. Le dossier de mods
utilisé pour les essais et l'installation locale peut également être défini avec
`-Ptropimon_mods_dir=<dossier>` ou `TROPIMON_MODS_DIR`. Les tests utilisent le
Java qui lance Gradle ; `-Ptropimon_java_path=<java>` ou `TROPIMON_JAVA` permet
d'en choisir un autre.

Les tests isolés et de coexistence sont décrits dans
[l'audit d'autonomie et de performance](docs/autonomy-and-performance.md).
Les dépendances d'exécution sont Minecraft 1.21.1, Java 21, Fabric Loader/API et
Cobblemon 1.7.2 (avec Fabric Language Kotlin requis par Cobblemon).
Aucun autre mod Tropimon n'est nécessaire.

## Confidentialité permanente

L'attribution publique du développeur reste **By FastedCorsi**. La règle durable
est conservée dans [AGENTS.md](AGENTS.md). Le build vérifie les fichiers publiables
et les deux JAR finaux, y compris les constantes compilées et archives imbriquées.
Les données locales restent sur disque et sont exclues de la distribution.
Voir [le contrôle de confidentialité](docs/privacy.md) pour les commandes,
les données fictives de test, les revues de crédits tiers et les limites.


## Mises à jour avec consentement

Aucun téléchargement de mise à jour sans accord. Le premier écran propose uniquement d'autoriser la consultation des métadonnées GitHub (au démarrage, au plus toutes les six heures). Une seconde confirmation montre la version et demande explicitement le téléchargement du JAR et de son SHA-256. L'ancien réglage `enabled: true` ne donne aucune autorisation.

Après accord et vérification, un installateur local utilise le Java de Minecraft, attend la fermeture du jeu, sauvegarde l'ancien JAR hors des mods chargés et remplace uniquement ce mod. Aucun autre mod Tropimon ni changement de launcher n'est requis. Le dossier `mods` classique et le stockage géré Tropimon reconnu sont pris en charge ; une disposition inconnue, un fichier modifié/verrouillé ou une incompatibilité bloque l'installation sans forcer. Le nom du JAR installé est conservé pour rester enregistré par le launcher ; la version réelle se lit dans les métadonnées Fabric.

Pour modifier le choix en jeu : `/tropimonupdates tropimon_ui_battle`. Refuser laisse le mod utilisable. Les anciennes versions dont l'updater est défectueux nécessitent un premier remplacement manuel, jeu fermé. L'accord donné pour ce mod ne s'applique pas aux autres mods. Les tests automatisés sont exécutés sous Windows ; les autres systèmes doivent encore être validés en situation réelle.

Les versions à consentement utilisent un canal de releases distinct du lien GitHub « latest » historique : sélectionner la version par son tag. Cela évite de déclencher les anciens updaters sans accord.
