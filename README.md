# Gojira Mod

A Fabric mod for Minecraft 26.3 (Fabric Loader 0.19.5, Fabric API 0.163.0+26.3, Java 25), designed by June and Leon.
A separate mod from the Horror Mod.

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

## Files
- `tools/gojira.py` generates everything from data: the model boxes (`GojiraModelLayer.java`), the textures (skin, spike
  glow, spawn egg, icon) and the roar `.ogg`. It also measures the model and writes `GojiraDimensions.java` (scale, mouth, feet, reach), which `Gojira.java` and the renderer use.
- Build: `./gradlew build` runs 15 game tests:
  - registration and model size/detail, and the detailed egg texture;
  - can't die, regen outpaces damage, and the `/kill` off switch with its death animation;
  - stomp, claw swipe and tail whip, and picking the swipe on its own;
  - beam hits, and a wall blocks the beam;
  - boss bar, always hostile to players, and targets the nearest mob (not armor stands or other Gojiras);
  - no drowning, and the swim flag in deep water.

License: CC0.
