# Règle permanente — Tropimon UI Battle

Ces consignes restent applicables à toute correction, optimisation, ajout et
version de ce mod, y compris si le module devient un dépôt ou worktree autonome.
Elles complètent les consignes du dépôt parent sans les remplacer.

- Attribution publique du développeur : exactement **By FastedCorsi**.
  Dans `fabric.mod.json`, conserver `"authors": ["By FastedCorsi"]` pour cette
  attribution. Préserver les licences et crédits des auteurs tiers.
- Ne pas introduire d'identité civile/professionnelle, employeur, adresse privée,
  compte système, chemin personnel absolu ou secret dans les sources, scripts,
  ressources, exemples, tests, documentation, commits ou livrables.
  Ne pas recopier les valeurs supprimées dans une règle, fixture ou rapport.
- Utiliser des chemins relatifs ou configurables par environnement. Examiner les
  occurrences avant modification ; préserver identifiants techniques, noms de
  joueurs fonctionnels, URLs publiques et crédits tiers.
- Avant chaque livraison, exécuter le contrôle de confidentialité des sources
  et des JAR finaux, y compris constantes compilées, ressources et archives
  imbriquées. Une détection bloque la diffusion jusqu'à correction ou revue
  explicite d'un crédit/publication tiers. Les fixtures doivent être fictives.
- Conserver les exclusions de distribution pour configurations privées, logs,
  captures personnelles, sauvegardes, fichiers `.env`, caches et dossiers `.git`.
  Ne pas supprimer les fichiers personnels originaux pour nettoyer un paquet.
- Les termes privés supplémentaires restent exclusivement dans un fichier hors
  dépôt indiqué par `TROPIMON_PRIVACY_TERMS_FILE`, jamais dans une liste versionnée.
  Ne pas afficher les valeurs détectées ; signaler uniquement la règle et
  l'emplacement masqué. Ne jamais tester, publier ou révoquer un secret détecté.
- Préserver la logique, les fonctionnalités, les animations et l'autonomie du
  mod : aucun accès aux classes, état, configurations ou services internes des
  autres mods Tropimon, aucune bibliothèque commune obligatoire. Maintenir les
  protections nécessaires avec un équivalent autonome.
- Avant un commit explicitement autorisé, vérifier les identités effectives
  d'auteur et de committer : FastedCorsi avec une adresse GitHub noreply valide
  déjà vérifiée. Ne pas inventer d'adresse ni modifier la configuration globale.
  En l'absence d'identité vérifiée, ne pas créer le commit.
- Ne pas réécrire l'historique, pousser, publier, remplacer le JAR du launcher ou
  révoquer une clé sans autorisation correspondante. Distinguer fichiers nettoyés
  et traces conservées dans anciens commits, releases et copies déjà diffusées.
  Signaler les limites du contrôle sans promettre un effacement rétroactif.
- Conserver cette règle dans les futurs `AGENTS.md` du mod. Team Hunt et Bid Maker
  restent hors périmètre tant que l'utilisateur ne demande pas leur reprise.

Vérification locale depuis le dossier parent :

```powershell
.\gradlew.bat -p .\TropimonUIBattle test check build
```

Le build doit continuer à exécuter `privacySelfTest`, `verifyPrivacySources` et
`verifyPrivacyArtifacts`. Voir `docs/privacy.md` pour le périmètre et les limites.

## Deux livraisons JAR à chaque version

- À chaque livraison d'une version ou d'un changement de code, fournir deux JAR clairement séparés : un JAR local accompagné du système de mise à jour différée de l'instance du launcher, et un JAR prêt à partager. Utiliser deux dossiers ou noms explicites ; ne jamais installer les deux exemplaires simultanément.
- Les deux JAR proviennent de la même version validée et offrent les mêmes fonctionnalités. Ils peuvent être identiques octet pour octet : privilégier un petit script externe pour l'installation locale, sans dupliquer le code du mod ni embarquer ce mécanisme dans le JAR public.
- Le launcher peut rester ouvert : seule l'exécution du jeu Minecraft concerné bloque la mise à jour locale. Attendre l'arrêt du jeu avant de remplacer le JAR dans la bonne instance. Ne jamais forcer l'arrêt du launcher ou du jeu, toucher aux autres mods ni remplacer un fichier utilisé ou verrouillé.
- Cette demande constitue l'autorisation permanente de préparer et d'armer cette installation différée lors d'une livraison, sauf consigne explicite contraire pour la tâche. Une demande de conseil, d'audit ou de mise à jour des règles ne déclenche ni compilation ni installation.
- Réutiliser et adapter les outils locaux existants. Vérifier la cible exacte, l'intégrité du JAR et le résultat de la copie ; conserver une sauvegarde de l'ancien JAR hors du dossier des mods chargés. En cas de cible ambiguë, d'accès impossible ou de verrouillage, conserver le fichier préparé et signaler le blocage sans forcer.
- Le JAR partageable ne contient ni chemin personnel, configuration locale, secret, donnée privée ni outil d'installation spécifique à la machine. Appliquer les contrôles de confidentialité aux deux JAR et aux éventuels fichiers qui les accompagnent. Conserver l'attribution « By FastedCorsi » et les crédits tiers.
- Dans la livraison, indiquer les deux JAR et leur version, les contrôles effectués et l'état réel de l'installation locale : préparée, en attente de fermeture ou installée après vérification. Ne pas annoncer une installation réussie parce qu'un script a seulement été lancé.

## Code simple, lisible et efficace

- Préserver strictement la logique, les fonctionnalités et les protections. Chercher les gains utiles de performance, mémoire et poids sans rendre le code difficile à comprendre.
- Choisir la solution la plus simple qui répond au besoin actuel. Éviter les classes, interfaces, factories, couches de services, méthodes relais et dépendances ajoutées sans utilité concrète ; ne pas bâtir un framework pour un cas isolé.
- Garder des classes cohérentes et des méthodes lisibles quand leur séparation aide réellement. Ne pas tout fusionner dans une classe géante ni compacter le code : moins de fichiers ou de lignes ne garantit pas de meilleures performances.
- Réutiliser ce qui existe dans le mod ; supprimer le code mort seulement après vérification des usages, y compris mixins, réflexion, événements, ressources et compatibilité. Pas de réécriture générale pour une optimisation locale.
- Cibler les coûts identifiés : travail répété par tick ou par frame, scans, allocations, entrées/sorties et caches sans limite. Justifier les gains et vérifier les comportements concernés ; ne pas ajouter de cache, de thread ou d'abstraction préventive sans besoin démontré.
- Chaque mod reste autonome : aucune dépendance aux classes, états ou services internes de nos autres mods. Recréer dans le mod concerné la petite implémentation nécessaire plutôt qu'imposer une bibliothèque commune ; préserver les dépendances officielles nécessaires.

## Détection des combats spectateur et participant

- Conserver des provenances et règles distinctes pour les observations reçues en spectateur et celles reçues en tant que participant. Ne jamais fusionner leurs états, déductions ou fenêtres de déduplication.
- Pour chaque changement de détection de combat, ajouter ou maintenir des tests dédiés aux deux modes. Les annonces sémantiquement identiques provenant d'une attaque, d'un talent et du message officiel de Cobblemon doivent être rapprochées sur tout le tour afin de ne compter chaque changement de terrain qu'une fois, même si des paquets intermédiaires les séparent.

## Publication et mise à jour autonome

- Chaque version livrée est poussée sur le dépôt GitHub public propre à ce mod, puis publiée dans une Release dont le tag correspond exactement à la version.
- La Release contient un seul JAR partageable vérifié et son fichier SHA-256. Les JAR LOCAL, configurations et scripts propres à une machine ne sont jamais publiés.
- Ce mod embarque sa propre implémentation de mise à jour. Elle ne dépend d'aucune classe, bibliothèque ou service interne d'un autre mod Tropimon.
- La mise à jour accepte uniquement la Release officielle de ce dépôt, exige le SHA-256, vérifie l'identifiant et la version de fabric.mod.json, prépare le fichier hors du dossier mods, puis remplace l'ancien JAR seulement après l'arrêt de Minecraft. Elle ne force jamais l'arrêt du jeu ou du launcher et conserve une sauvegarde hors des mods chargés.
- Une évolution de l'updater doit rester légère, asynchrone et sans travail répété par tick ou par frame.



## Installation locale prise en charge par l’agent

- Lors des prochaines livraisons Tropimon, l’agent réalise lui-même l’installation locale autorisée ; ne pas demander à l’utilisateur de recopier ou réimporter le JAR si l’opération peut être menée sûrement avec les outils disponibles.
- Détecter le profil et la gestion des mods du launcher. Une copie dans `instance/mods` seule ne constitue pas une installation valide lorsque le launcher utilise `instance/mods-user` et `user-mods-tracked.json`.
- Sur ce schéma vérifié, synchroniser la copie importée, la copie chargée et le suivi du seul mod livré. Préserver les autres mods, leurs désactivations et le manifeste officiel ; ne jamais désactiver le contrôle des mods non gérés ni assouplir une protection du launcher.
- Réutiliser l’installateur local `InstallManagedLocalMod.ps1` lorsqu’il est disponible et en joindre une copie autonome à la livraison LOCAL. Remplacer ou adapter l’ancienne entrée d’installation avant de la lancer sur un profil géré ; un ancien script limité au dossier `mods` ne doit pas être utilisé tel quel. Aucun outil local n’est embarqué dans le JAR partageable, aucune dépendance entre mods n’est ajoutée.
- Attendre l’arrêt du jeu concerné sans forcer le launcher ni Minecraft. Vérifier les SHA-256, l’identifiant et la version, empêcher doublons et retours de version, sauvegarder hors des dossiers chargés et refuser les cibles modifiées, verrouillées, redirigées ou ambiguës. Une évolution inconnue du format impose une nouvelle vérification, pas une modification forcée.
- Vérifier les deux copies et l’enregistrement du launcher après installation ; indiquer séparément l’état sur disque et une éventuelle validation en jeu. Les auto-updaters doivent respecter ce stockage géré lorsqu’ils sont adaptés ; une règle ou un installateur local corrigé ne répare pas rétroactivement les JAR déjà distribués.
- Ces consignes ne déclenchent pas à elles seules une compilation, une publication ni une modification des mods mis de côté. Tropimon Compagnon reste exclu tant que l’utilisateur demande de ne pas y toucher.


## Consentement et mise à jour indépendante du launcher

- Toute récupération de fichier, y compris JAR, empreinte et catalogue externe, exige un accord éclairé préalable du joueur. Ne jamais télécharger en arrière-plan avant cet accord.
- La vérification des métadonnées de mise à jour est désactivée sans consentement explicite ; un ancien `enabled: true` généré automatiquement ne vaut pas accord. L'autorisation de vérifier ne vaut jamais autorisation de télécharger ou installer une version.
- Présenter le mod, la version, la source officielle, les fichiers et le remplacement différé avec sauvegarde avant le bouton de téléchargement. Refuser, reporter ou fermer ne déclenche aucun téléchargement.
- Chaque mod contient sa propre implémentation. Utiliser le Java existant et le JAR réellement chargé ; ne demander aucune modification du launcher, installation d'un outil ou chemin personnel.
- Gérer le dossier mods classique et le stockage Tropimon reconnu. Conserver le nom enregistré, synchroniser les deux copies et préserver le suivi ainsi que les autres mods. Une disposition inconnue doit bloquer proprement, sans contourner une protection du launcher.
- Tester le helper réellement exporté : attente de Minecraft, fichiers modifiés/verrouillés, sauvegarde, deux types de stockage et absence de consentement. Ne pas confondre un installateur local validé avec l'updater livré aux joueurs.

- Canal de transition : publier les nouvelles releases stables avec `--latest=false` et la mention `<!-- tropimon-consent-updater:2 -->` dans leurs notes. Vérifier après publication que `/releases/latest` reste inchangé ; les anciens updaters non consentis ne doivent pas être déclenchés pour récupérer le correctif. Le nouvel updater sélectionne ce canal dans `/releases?per_page=20`. Une première installation manuelle peut être nécessaire depuis une version ancienne.

## Lisibilité aux quatre échelles GUI

- Gérer les échelles Minecraft 1, 2, 3 et 4 sans modifier le réglage global du joueur. Vérifier aussi les fenêtres réduites et le redimensionnement ; distinguer l'échelle demandée de celle réellement appliquée par Minecraft.
- Conserver des textes, valeurs, contrôles et infobulles lisibles. Adapter l'agencement et le défilement à l'espace disponible ; ne pas masquer une valeur essentielle ou remplacer sa lecture par une police minuscule.
- Rendu, clics, survol, glisser-déposer et découpe utilisent la même transformation. Contrôler les interactions et protections existantes, pas seulement une capture à l'échelle 2. Les mods restent indépendants, avec une implémentation locale simple.
