# Confidentialité — Tropimon UI Battle

By FastedCorsi

## Règle permanente

[AGENTS.md](../AGENTS.md) fixe les obligations pour les futures corrections,
optimisations et versions du mod. L'attribution du développeur dans le manifeste
Fabric est exactement `"authors": ["By FastedCorsi"]`. Les licences, crédits
tiers, identifiants techniques, noms de joueurs fonctionnels et URLs publiques
nécessaires sont conservés.

Le nettoyage du 31 août 2026 modifie l'auteur du manifeste et rend portable le
manifeste de génération des icônes : il référence maintenant les 30 originaux
dans son dossier relatif `sources`. Le script de normalisation résout ce chemin
depuis le dossier du manifeste et évite de recopier un fichier sur lui-même.
Aucune image n'est régénérée et aucune logique de combat n'est modifiée.

## Vérifications automatiques

Depuis le dossier parent :

```powershell
.\gradlew.bat -p .\TropimonUIBattle verifyPrivacySources
.\gradlew.bat -p .\TropimonUIBattle test check build
.\gradlew.bat -p .\TropimonUIBattle -Pcoexistence test
```

- `privacySelfTest` teste le vérificateur avec des identités, chemins et
  credentials entièrement fictifs, construits en mémoire.
- `verifyPrivacySources` examine les fichiers publiables du module, dont les
  sources, ressources, images originales, scripts, exemples et documentation.
  Il précède la copie des ressources et la création des JAR.
- `verifyPrivacyArtifacts` ouvre le JAR remappé et le JAR de sources finaux. Il
  vérifie l'auteur Fabric, noms d'entrées, constantes UTF Java, chaînes JSON
  décodées, métadonnées textuelles PNG, commentaires ZIP et archives ZIP/JAR/GZIP
  imbriquées. Une entrée privée ou une archive non vérifiable bloque le contrôle.
- `check`, `build` et la tâche d'installation locale existante dépendent de la
  vérification des artefacts. Le contrôle n'installe ni ne publie rien lui-même.

Le vérificateur et Gson sont des outils de build, dans un source set distinct :
ils ne sont pas embarqués dans le mod et n'ajoutent aucune dépendance
d'exécution. Ils ne lisent aucun service ou état interne d'un autre mod Tropimon.

Les sorties indiquent uniquement emplacement, identifiant de règle et nombre
de détections ; les valeurs reconnues sont masquées. Les erreurs de lecture
n'affichent pas le contenu privé de l'entrée ou son chemin absolu.

## Recherche privée et crédits tiers

Les règles génériques détectent notamment chemins de comptes système, adresses
e-mail, clés privées et plusieurs formats de jetons ou affectations sensibles.
Le nom du compte local et les identités Git effectives sont lus uniquement en
mémoire, sans modification de la configuration Git ni copie dans le dépôt.

Pour compléter la recherche par un nom, un employeur ou une autre valeur
confidentielle qui ne peut pas être déduite localement, définir
`TROPIMON_PRIVACY_TERMS_FILE` vers un fichier UTF-8 **hors du module et du dépôt
Git parent**, contenant un terme par ligne. Ce fichier n'est ni copié ni
affiché. Ne jamais placer de véritables valeurs privées dans une fixture,
commande documentée, liste versionnée ou rapport public.

Une détection doit être corrigée ou examinée. Pour un crédit tiers réellement
public seulement, `tools/privacy/reviewed-public.tsv` accepte une revue explicite
par chemin relatif, empreinte SHA-256 du fichier entier, règle et justification
publique. Seules les règles d'e-mail et de crédit d'auteur sont révisables.
Toute modification du fichier invalide la revue ; un terme privé ne peut jamais
être neutralisé par cette table. La table est vide après ce nettoyage.

Ne jamais tester un jeton contre un service, le révoquer ou publier sa valeur
pour l'auditer. Une fuite avérée doit être signalée avec emplacement masqué et
actions proposées, sans action externe automatique.

## Contenu distribué et originaux

`.gitignore`, `.gitattributes` et les exclusions Gradle écartent les données
locales privées : configurations réelles, fichiers `.env`, logs, captures,
sauvegardes, certificats privés et dossiers Git/cache. Le JAR et le JAR de
sources n'embarquent que leurs contenus de production respectifs ; les outils
de contrôle et fixtures synthétiques restent dans les sources du projet.

Ces exclusions ne suppriment aucun original sur disque. Elles ne retirent pas
magiquement un fichier déjà suivi par Git ; le contenu exportable doit toujours
être contrôlé. Ne pas distribuer une copie brute de tout le dossier de travail.
Les sorties `build`, instances `run`, dossiers de mods et anciennes versions
locales ne font pas partie des sources destinées à la publication.

Les PNG du dossier `art/effects-v2` sont des assets et leur planche d'aperçu,
pas des captures personnelles de jeu. Ils restent conservés et contrôlés ;
les anciennes icônes de sauvegarde sont exclues.

## Historique et limites

### Validation du 31 août 2026

- Build réussi, 132 vérifications synthétiques du garde-fou réussies.
- 123 tests de combat/UI réussis en isolé et 123 en coexistence, aucun échec,
  erreur ou test ignoré ; syntaxe du script de normalisation vérifiée sans
  réexporter ni remplacer d'image.
- 208 fichiers publiables et 294 entrées des deux JAR finaux contrôlés : aucune
  occurrence des termes privés recherchés ni secret reconnu ; attribution
  exacte confirmée dans les deux manifestes. Aucune exception tiers nécessaire.
- Comparaison des 198 entrées du JAR avec la version locale immédiatement
  précédente : seul `fabric.mod.json` change. Les 159 classes et toutes les
  autres ressources sont identiques octet pour octet. Aucun outil de contrôle
  ni dépendance supplémentaire n'est embarqué.
- Le JAR du launcher est inchangé. Les logs, configurations, images originales
  et sauvegardes restent conservés sur disque et hors du contenu livré.

JAR : `build/libs/tropimon-ui-battle-0.1.0.jar` ; SHA-256 :
`c5c2c65bb31ebe0369edd354a8d889e059bff8c9a6e027b1509d1a966717e387`.

JAR de sources : `build/libs/tropimon-ui-battle-0.1.0-sources.jar` ; SHA-256 :
`a96944338239cd66a8aa39bf04018164c62b2ccff09de8134083a225cbfda1f5`.

### Copies antérieures et périmètre

Aucun commit du module n'apparaissait dans les références Git locales examinées
au moment du nettoyage. Cela ne vaut pas audit global des autres modules ou de
références distantes. Aucun commit, changement de configuration Git, réécriture,
push, publication ou installation n'est effectué par ce nettoyage.

Le JAR installé et la copie locale précédant le nettoyage conservent l'ancienne
attribution, vérifiée sans afficher sa valeur. Ils restent sur disque et hors de la
nouvelle distribution. Les anciens commits d'autres périmètres, releases
distantes et copies déjà distribuées ne sont pas effacés ou certifiés par ce
contrôle.

Le contrôle couvre les formats présents et les termes connus, pas toutes les
identités possibles ni tout secret imaginable. Il ne fait pas d'OCR, n'analyse
pas de stéganographie et ne garantit pas de détecter un secret chiffré ou
arbitrairement encodé. L'obfuscation n'est jamais une protection. Les archives
chiffrées, multi-volumes, ZIP64 ou dépassant les limites de taille/profondeur
nécessitent une revue explicite ; elles ne sont pas déclarées vérifiées.

Les tests isolés et de coexistence utilisent les registres Minecraft et Fabric
Loader JUnit. Ils ne remplacent pas un match réel ou une inspection visuelle en
jeu. Les détails fonctionnels se trouvent dans
[l'audit d'autonomie](autonomy-and-performance.md).
