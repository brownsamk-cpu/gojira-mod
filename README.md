# Gojira Mod

A Fabric mod for Minecraft 26.3 (Fabric Loader 0.19.5, Fabric API 0.163.0+26.3, Java 25), designed by June and Leon.
A separate mod from the Horror Mod.

## Gojira (`gojiramod:gojira`)
- A giant, dark grey, dinosaur-like kaiju that stands on two legs, with a long swaying tail, small arms, a long-snouted head
  and three rows of pale spikes down its back. Blocky Minecraft style, original design and textures.
- **50 blocks tall** ("50 meters", and 1 block = 1 meter). Nose to tail tip it is about 70 blocks long.
- **Always angry** at players in survival/adventure (creative and spectator players are ignored, like with any monster).
  It notices you up to 64 blocks away.
- **Can't be beaten.** It has 1000 health and heals 40 health every second (2 per tick), much faster than anyone can hurt it,
  and its health can never go below 1. A boss bar called **Gojira** shows within 96 blocks.
- **Never despawns.**
- **Lives in the ocean.** It breathes underwater, walks along the sea floor at full speed, swims up to the surface when it's
  chasing you, and wades onto land (it can step up 2.5 blocks).
- **Stomp**: if you're near its feet (within 12 blocks), it lifts a foot and slams it down. That does 3 hearts of damage
  (on Normal), knocks you away, throws up dust and clouds, and shakes your screen a little.
- **White beam**: if you're farther away and it can see you, its spikes glow white and it roars for 2 seconds. Then it
  breathes a glowing white beam from its mouth for about 2.4 seconds. The beam does 1.5 hearts per pulse (4 pulses) to
  everything it touches. The aim follows you slowly, so you can run sideways to dodge it, and walls block it. After that
  it waits 8 seconds before it can use the beam again.
- **Roar**: an original synthesized "SKREEONK!" (subtitle "Gojira roars: SKREEONK!"). It roars every 20 to 35 seconds when
  a player is near, and before every beam.
- **It never breaks blocks.**
- Spawn egg: **Gojira Spawn Egg** (`gojiramod:gojira_spawn_egg`), in the **Gojira Mod** creative tab and next to the Guardian
  egg in Spawn Eggs. It does not spawn naturally (see below).

### Grown-up off switches
- `/kill @e[type=gojiramod:gojira]` still works, because `/kill` bypasses the "can't be beaten" rule.
- Switching the world to **Peaceful** removes it, like any other monster.
- Players in creative mode are ignored.

### Size and performance tradeoffs
- The 50-block size is only how it **looks**. The renderer scales the model up about 13.8 times. Its real hitbox is
  **6 x 16 blocks**, because a 50-block hitbox couldn't fit through the world and would make collision checks very slow.
  The stomp and beam are measured from where the big model's feet and mouth are (mouth about 42 blocks up and 21 blocks
  in front), not from the hitbox. The catch: you can only hit it in the 6 x 16 area around its legs and lower body.
- It doesn't use pathfinding, which is slow and unreliable for a mob that wide. It walks straight at you and sidesteps when
  it bumps into something. In thick forests or narrow valleys it can get stuck.
- Render culling uses a box the size of the model, so it doesn't pop out of view when its feet are off screen. Server
  tracking range is 16 chunks, but your view distance still limits how far away you can see it.
- The beam sends up to about 80 particle packets every other tick while it fires, and they show up to 512 blocks away
  because the mouth is so high. It only fires for about 2.4 seconds at a time.
- **No natural spawning**: a mob that can't be beaten and never despawns would pile up in the oceans over time.

## Files
- `tools/gojira.py` generates everything from data: the model boxes (`GojiraModelLayer.java`), the textures (skin, spike
  glow, spawn egg, icon) and the roar `.ogg`. It also prints the model scale and mouth position used in `Gojira.java`.
- Build: `./gradlew build` runs 11 game tests (registration, can't die, regen outpaces damage, `/kill` off switch, stomp,
  beam hits, wall blocks the beam, boss bar, always hostile, no drowning).

License: CC0.
