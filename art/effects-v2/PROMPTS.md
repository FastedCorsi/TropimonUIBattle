# Effets de combat v2

Générés individuellement le 30 août 2026 avec l'outil intégré `image_gen` (pas de CLI/API externe).

- `sources/` : les 30 originaux retenus, avec leur alpha natif.
- `icons/` : les 30 PNG séparés normalisés en 128 × 128 pour le mod.
- `previous-icons/` : sauvegarde des assets précédents avant remplacement.
- `preview.png` : contrôle sur fonds clair/sombre et aux tailles de HUD 24/32 px.
- `generation-manifest.json` : correspondance entre chaque effet et son fichier généré.

## Direction commune des prompts

Use case: stylized-concept. Game battle-effect UI icon for a Minecraft Cobblemon client mod.
Authentic hand-crafted pixel art for a Pokémon-like monster-battling interface and Cobblemon-like Minecraft UI.
Chunky pixel clusters, crisp hard edges, simplified readable sprite, restrained 16-bit palette,
subtle material shading, centered isolated symbol, generous transparent margin, readable at 24 × 24 pixels.
Genuinely transparent alpha background and corners. No checkerboard pattern, opaque backdrop,
tile, circular badge, external frame, glow, text, logo, watermark, neon, glossy mobile-game rendering,
painterly brushwork, smooth vector curves, or 3D render.

Les variantes finales de `magic_room`, `lucky_chant` et `primordial_sea` demandent en plus un sprite
plat, très simple, avec six couleurs visibles au maximum et presque aucune texture interne.
Les quatre terrains reprennent la construction d'un petit socle de terre ovale vu en isométrie,
avec une surface colorée et un symbole central distinct.

## Sujets des prompts retenus

| Fichier | Sujet |
| --- | --- |
| aurora_veil | Two short icy protective curtains arching inward, one tiny snowflake between the lower tips. |
| delta_stream | Compact slate-teal S-shaped wind spiral, two feather tips, three gray wind motes. |
| desolate_land | Heavy orange sun above cracked dry earth, three short heat waves. |
| electric_terrain | Oval brown soil platform, yellow-green top, short yellow lightning bolt, two square sparks. |
| generic_effect | Muted teal diamond marker, smaller pale diamond cutout, two tiny square motes. |
| grassy_terrain | Oval brown soil platform, medium-green top, short two-leaf sprout and three grass blades. |
| gravity | Dark-purple orb above gray-brown ground, three downward arrows, two small stones pulled inward. |
| hail | Five chunky pale-blue ice pellets falling diagonally with short pixel motion trails. |
| light_screen | Upright cyan energy pane in three-quarter view, diagonal pale reflection, two corner glints. |
| lucky_chant | Simple golden hand bell, muted blue eighth note on the left, tiny cream star on the right. |
| magic_room | Amber held-item diamond crossed by a pale-lavender slash, two violet corner brackets. |
| mist | Three compact white-blue fog curls and two tiny square mist motes. |
| misty_terrain | Oval brown soil platform, dusty lavender-pink top, short pink-white curling mist ribbon. |
| mud_sport | Irregular mud patch grounding a small yellow lightning bolt into two dim square sparks. |
| primordial_sea | Large dark-blue droplet, one pale-blue wave curl around its lower-right edge, three drops above. |
| psychic_terrain | Oval brown soil platform, muted magenta top, dark-violet psychic eye with pale-pink diamond pupil. |
| rain | Compact soft blue-gray rain cloud and exactly three large blue raindrops. |
| reflect | Upright coral-red energy pane, cream impact spark bouncing away from the left edge. |
| safeguard | Compact teal shield, pale mint center diamond, four small status-colored dots inside. |
| sandstorm | Tan wind spiral carrying four blocky grains and one tiny angular pebble. |
| snow | Six-arm blocky snowflake and two tiny pale-blue frost motes. |
| spikes | Three steel-gray conical ground spikes, large center and two smaller sides. |
| stealth_rock | Central jagged levitating rock shard and four smaller angular fragments. |
| sticky_web | Thick white-gray spider web with three muted green sticky droplets. |
| sun | Warm golden sun with solid center and eight short blocky rays. |
| tailwind | Pale-blue feather-shaped gust pointing right, three stepped wind bands and one trailing line. |
| toxic_spikes | Three purple poison-coated spikes in a small puddle with two tiny bubbles. |
| trick_room | Small violet isometric room corner with four checker squares and cyan/magenta opposing arrows. |
| water_sport | Compact blue water curl around a tiny orange flame reduced to an ember. |
| wonder_room | Blue and purple shield halves, two cream swapping arrows and one tiny star. |

## Préparation technique

`tools/NormalizeGeneratedEffects.ps1` effectue uniquement un recadrage des marges et une réduction
nearest-neighbor vers 128 × 128 ; il conserve le canal alpha des images générées.
Le symbole est cadré dans une zone maximale de 108 × 108, avec une marge transparente.
Le paramètre `-Apply` sauvegarde les anciennes icônes une seule fois et copie les nouvelles dans les
ressources du mod. Aucun code de combat ni correspondance effet/fichier n'est modifié.
