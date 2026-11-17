# Gojira Mod

A Fabric mod for Minecraft 26.3 (Fabric Loader 0.19.5, Fabric API 0.163.0+26.3, Java 25), designed by June and Leon.
A separate mod from the Horror Mod. Two kaiju: **Gojira** (always angry) and, new in 0.2.0, **Showa Godzilla** (a hero).

## Gojira (`gojiramod:gojira`)
- A giant, charcoal-grey, dinosaur-like kaiju that stands upright on thick legs, with short clawed arms, a thick neck, a
  big rounded head and a long swaying tail. Three rows of jagged, pale, maple-leaf-shaped plates run down its back.
  Blocky Minecraft style, original design and textures.
- **164/3 = 54.67 blocks tall** (0.1.2; it was 50 before; 1 block = 1 meter). Nose to tail tip it is about 79 blocks long.
- **Always angry at every living thing** (0.1.4): animals, monsters, villagers, other mobs, and players in
  survival/adventure. It goes for whichever is **nearest**, up to 64 blocks away. It ignores creative and spectator players,
  other Gojiras, and armor stands.
- **Can't be beaten.** It has 1000 health and heals 40 health every second (2 per tick), much faster than anyone can hurt it,
  and its health can never go below 1. A boss bar called **Gojira** shows within 96 blocks.
- **Never despawns.**
- **Lives in the ocean.** It breathes underwater, walks along the sea floor at full speed, swims up to the surface when it's
  chasing you, and wades onto land (it can step up 2.5 blocks).
- **Stomp**: if you're near its feet (within about 13 blocks), it lifts a foot and slams it down. That does 3 hearts of damage
  (on Normal), knocks you away, throws up dust and clouds, and shakes your screen a little.
- **Claw swipe** (0.1.4): if something is in front of it (within 18 blocks) and the stomp isn't ready, it raises its right
  arm and rakes its claws down and across. It does 2.5 hearts (on Normal) and knocks things back.
- **Tail whip** (0.1.4): if something is behind it (within 26 blocks), it coils its tail to one side and lashes it across. It
  does 2 hearts and flings things sideways. Each melee attack has a 1.5-second cooldown.
- **White beam**: if you're farther away and it can see you, its spikes glow white and it roars for 2 seconds. Then it
  breathes a glowing white beam from its mouth for about 2.4 seconds. The beam does 1.5 hearts per pulse (4 pulses) to
  everything it touches. The aim follows you slowly, so you can run sideways to dodge it, and walls block it. After that
  it waits 8 seconds before it can use the beam again.
- **Roar**: an original synthesized "SKREEONK!" (subtitle "Gojira roars: SKREEONK!"). It roars every 20 to 35 seconds when
  a player is near, and before every beam.
- **It never breaks blocks.**
- Spawn egg: **Gojira Spawn Egg** (`gojiramod:gojira_spawn_egg`), in the **Gojira Mod** creative tab and next to the Guardian
  egg in Spawn Eggs. It does not spawn naturally (see below).
- 0.1.1: the spawn egg is "super detailed": a 64x64 custom texture with a dark grey scaly shell, pale spikes up the
  middle, glowing white-blue cracks, a highlight and a rim light. It uses its own item model, not the tinted vanilla
  spawn-egg template.
- 0.1.3: a much more detailed model and skin. It has 60 moving parts and 326 cubes:
  - a layered head with a heavy brow, nostrils, amber slit-pupil eyes, rows of teeth and a hinged jaw;
  - a thick neck, a barrel chest with belly ridges, and short arms with a forearm and four claws on each hand;
  - thighs, knees, shins and wide feet with clawed toes;
  - a six-segment tail that tapers to the tip;
  - 37 jagged plates in 3 rows.

  The skin texture is 4x higher resolution (1024 x 512). It has bumpy charcoal scales with shading and crevices, and pale
  striated plates. The walk now bends the knees and swings the forearms, the stomp tucks the shin up, and all six tail
  segments sway.

- 0.1.4 animations:
  - **Swimming** in deep water: its body tips flat like a crocodile, its head lifts, waves run down its tail in big
    side-to-side sweeps, its legs kick, and its arms tuck in.
  - **Claw swipe** and **tail whip** (see above), plus the stomp and beam wind-ups as before.
  - **Death**, only from `/kill` or the void: it lets out a last roar with its plates flaring white. Then its knees buckle
    and it slowly topples onto its side like a falling tree (3 seconds). Its plates' glow fades, and dust flies up from its
    feet and in a long line where it crashes down. It lies still for 2 seconds, then disappears in a puff. It never turns
    red.
- 0.1.5: **skeleton on the sea floor.** If it dies underwater (in water at least 6 blocks deep), then after the death
  animation it leaves a giant skeleton lying on its side on the sea floor, the way it fell. It's made of about 460 bone
  blocks and is 62 blocks long and 46 wide: a skull with an eye socket and teeth, a spine, ribs, legs with clawed feet,
  arms with claws, back plates, and a long tail of vertebrae. Each bone drops onto the sea floor below it. Bones only
  replace water, air or seagrass, never stone, sand or anything someone built, and bits with no floor underneath are
  skipped. If it dies on land there are no bones, just the topple.
- 0.1.6: **`/kill` is forever.** Fixed a bug where Gojira could come back after `/kill`: if the world saved him while he
  was toppling (for example `/kill @e` also killed you and you respawned far away, or you left the world), he loaded
  back in with 1 health and his regen healed him to full. Now once `/kill` (or the void) gets him, nothing can heal him,
  a reload just carries on the death animation, and he's removed at the end (leaving bones if he was underwater).

### Grown-up off switches
- `/kill @e[type=gojiramod:gojira]` still works, because `/kill` bypasses the "can't be beaten" rule. It plays the
  5-second death animation, then it's gone.
- Switching the world to **Peaceful** removes it, like any other monster.
- Players in creative mode are ignored.

### Size and performance tradeoffs
- The 54.67-block size is only how it **looks**. The renderer scales the model up about 13.9 times. Its real hitbox is
  **6 x 16 blocks**, because a 55-block hitbox couldn't fit through the world and would make collision checks very slow.
  The stomp and beam are measured from where the big model's feet and mouth are (mouth about 45.3 blocks up and 19.1 blocks
  in front; the toes reach about 12.6 blocks, the stomp about 13.1), not from the hitbox. The catch: you can only hit it in the 6 x 16 area around its legs and lower body.
- It doesn't use pathfinding, which is slow and unreliable for a mob that wide. It walks straight at you and sidesteps when
  it bumps into something. In thick forests or narrow valleys it can get stuck.
- Render culling uses a box the size of the model, so it doesn't pop out of view when its feet are off screen. Server
  tracking range is 16 chunks, but your view distance still limits how far away you can see it.
- The beam sends up to about 80 particle packets every other tick while it fires, and they show up to 512 blocks away
  because the mouth is so high. It only fires for about 2.4 seconds at a time.
- The 0.1.3 model has 326 cubes (about 2,000 faces). That's a lot for one mob, but it's fine for one or a few Gojiras.
  Spawning dozens at once may slow down older computers.
- **No natural spawning**: a mob that can't be beaten and never despawns would pile up in the oceans over time.

## Showa Godzilla (`gojiramod:showa_godzilla`), new in 0.2.0
June's hero kaiju. He's inspired by the friendlier 1960s/70s "Showa era" films, but he's our own original blocky
Minecraft design (no movie models, textures or sounds copied), and he looks clearly different from our scarier Gojira:
- **Look:** a stocky charcoal body with knobbly bumps, a big round belly (smooth grey-tan with soft ridges), very thick
  column legs with blunt toes, short stubby arms with mitten hands, a long seven-part tail that drags on the ground, and
  three rows of **bright white, rounded** back plates (Gojira's are pale and jagged). His head is **round and friendly**:
  a short rounded muzzle, **big round eyes** (white with a dark pupil and a shine), little ear bumps and a smiling lip
  line. 48 moving parts, 185 cubes, 24 plates, a 1024 x 1024 skin.
- **Super detailed (new in 0.2.1, June's request):** 67 moving parts, **615 cubes** and **43 layered white plates** (each
  plate is six boxes: a base, middle, upper and tip layer, a raised pale-grey core and a back lobe), running in three rows
  down his back and on every tail segment. Knobbly skin bumps all over his body, thighs, shins, arms, neck, head and tail,
  plus scale ridges along the tail. A detailed face: brow ridges, upper and lower eyelids, nostrils with rims, two-step
  ear bumps, jowls, smile corners, 11 top and 11 bottom teeth, a pink tongue and a chin. Three fingers and a thumb on
  each hand with two-part claws, four toes with toenails and a heel on each foot, knee caps, calf, thigh and shoulder
  bulges, and a tan belly made of separate segments with dark lines between them (and tan throat and tail-underside
  segments). The hand-made skin has shaded knobs with bright tops and dark rims, scale-ridge rows, light flecks, pores, a
  soft bevel highlight and shadowed edges. The plates have fine striations, a blue-grey rim and a cream base. The eyes have
  a brown iris, a lid shadow and two shines. The spawn egg is more detailed too: striped plates with shaded edges, shiny
  knobs, belly segment lines with pits, and sparkles.
- **Same size as Gojira:** 164/3 = 54.67 blocks tall (hitbox 6 x 16, like Gojira).
- **A hero of humanity:** he never targets or hurts players, villagers, iron golems or animals, even if you hit him by
  accident. He goes after **hostile mobs only** (zombies, skeletons, creepers, spiders, slimes, ... and **Gojira**),
  nearest first, up to 64 blocks away. All his attacks (stomp, swipe, whip, kick, beam) only damage hostile mobs, so
  standing on his toes is safe. He's not a monster to the game either: iron golems leave him alone, and he stays in
  Peaceful.
- **Same powers as Gojira:** the stomp, claw swipe, tail whip, glowing white beam, the same "SKREEONK!" roar, swimming in
  deep water, breathing underwater, and he can't be beaten (1000 health, heals 40 a second, never below 1). No boss bar.
- **Jet flight (new):** if his target is more than 40 blocks away, across a gap (no floor within 6 blocks below), or up a
  cliff more than 12 blocks high, he blasts his white beam out **backward like a rocket** and flies there **tail first**:
  his body tips flat, his head lifts so the beam points behind him, his plates glow blue-white and a long trail of beam
  sparkles streams out of his mouth. He cruises about 10 blocks above the target's height, climbs over things in the way,
  and **lands** when he's 16 blocks from it (no fall damage).
- **Drop kick (new):** for a hostile mob in front of him that's just too far for a swipe (18 to 36 blocks), he crouches,
  jumps forward, tips onto his back in mid-air and **kicks with both feet** (4 hearts on Normal, big knockback), then gets
  back up. 5-second cooldown.
- **Victory dance (new):** after he defeats a mob, he does a 3-second happy dance: hopping from foot to foot with both
  arms up in a V, wiggling his hips, bobbing his head, wagging his tail, with music notes and green sparkles.
- **Falling over:** only `/kill` (or the void) can stop him. He just **falls over** onto his side and is gone after 5
  seconds. **No bones**, on land or in water. The 0.1.6 "killed forever" fix is built in: nothing heals him once he's been
  killed, not even saving and reloading the world.
- Spawn egg: **Showa Godzilla Spawn Egg** (`gojiramod:showa_godzilla_spawn_egg`), a detailed 64 x 64 egg (a knobbly
  charcoal shell, a tan belly patch, rounded white plates, two friendly eyes, and a glowing blue-white jet streak). It's in
  the Gojira Mod creative tab and right after the Gojira egg in Spawn Eggs. He doesn't spawn naturally.
- `/kill @e[type=gojiramod:showa_godzilla]` removes him.

### Showa Godzilla limits
- Like Gojira, his attacks and flight aim from where the big model's feet and mouth are, but you can only hit him in his
  6 x 16 hitbox, and he walks straight at targets instead of pathfinding.
- He flies at anything far away, so in a big open world he may zip off after a distant hostile mob.
- While he's busy flying, kicking or dancing he doesn't start other attacks.
- If he fights Gojira, neither can win (they both heal faster than they're hurt); `/kill` one of them.
- His skin is a 1024 x 1024 texture, so the jar is now about 1.4 MB. 615 cubes is more than Gojira's 326. Big fights
  with several Showa Godzillas may run slower on weaker computers.

## Files
- `tools/skeleton.py` generates the skeleton layout (`GojiraSkeletonLayout.java`) and can save a top-down preview PNG.
- `tools/gojira.py` generates everything from data: the model boxes (`GojiraModelLayer.java`), the textures (skin, spike
  glow, spawn egg, icon) and the roar `.ogg`. It also measures the model and writes `GojiraDimensions.java` (scale, mouth, feet, reach), which `Gojira.java` and the renderer use.
- `tools/showa.py` generates Showa Godzilla's model boxes (`ShowaGodzillaModelLayer.java`), `ShowaDimensions.java`, his
  skin and glow textures and his spawn egg. `tools/showa_preview.py` draws preview pictures of him without OpenGL.
- Build: `./gradlew build` runs 28 game tests.
  - 17 Gojira tests:
    - registration and model size/detail, and the detailed egg texture;
    - can't die, regen outpaces damage, the `/kill` off switch with its death animation (no bones on land), and `/kill` is
      forever (even after a reload);
    - an underwater kill leaves the bone skeleton on the sea floor (and never replaces solid blocks); this one runs in its
      own batch (`gojiramod:skeleton`) and keeps its whole sea floor loaded;
    - stomp, claw swipe and tail whip, and picking the swipe on its own;
    - beam hits, and a wall blocks the beam;
    - boss bar, always hostile to players, and targets the nearest mob (not armor stands or other Gojiras);
    - no drowning, and the swim flag in deep water.
  - 11 Showa Godzilla tests (their own batch, `gojiramod:showa`, run after Gojira's):
    - registration, size, his own model and detailed egg; can't die and regens;
    - hero targeting (picks the husk, never the player, villager, iron golem or cow), and his stomp and swipe spare friends;
    - flies to a far target tail first with the beam trail and lands; flies across gaps and up cliffs, walks over bridges;
    - the drop kick hits with both feet, and he picks it on his own at mid range (both wait until the whole kick area is
      loaded and ticking);
    - the victory dance after a defeat;
    - `/kill` is forever (even after a reload) with no bones, and no bones when killed in deep water (after swimming).

License: CC0.
