#!/usr/bin/env python3
"""Gojira Mod: the giant bone-block skeleton Gojira leaves on the sea floor when it dies underwater (0.1.5).

The skeleton lies on its side, the way Gojira topples over. Layout coordinates:
  a = along the direction it fell (its feet at a=0, the top of its skull at a~45)
  b = along the way it was facing (its snout at b~+19, its tail tip at b~-42)
  y = layers above the sea floor (0 = resting on the floor; the bones on its upper side are 1-2 layers up)
  axis = which way the bone block's grain runs: 0 = along a, 1 = along b, 2 = up

Writes src/main/java/com/gojiramod/GojiraSkeletonLayout.java and (optionally) a preview PNG.
Usage: python3 tools/skeleton.py [preview.png]
"""
import math, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = ROOT + "/src/main/java/com/gojiramod/GojiraSkeletonLayout.java"
BONES = {}  # (a, b, y) -> (axis, kind)


def put(a, b, y, axis, kind):
    BONES.setdefault((int(a), int(b), int(y)), (axis, kind))


def line(a0, b0, a1, b1, y, kind, axis=None):
    """A straight bone from (a0,b0) to (a1,b1) on layer y (every cell along it, no gaps)."""
    da, db = a1 - a0, b1 - b0
    n = int(max(abs(da), abs(db))) or 1
    ax = axis if axis is not None else (0 if abs(da) > abs(db) else 1)
    for i in range(n + 1):
        put(round(a0 + da * i / n), round(b0 + db * i / n), y, ax, kind)


def fill(a0, a1, b0, b1, y, kind, axis=1):
    for a in range(a0, a1 + 1):
        for b in range(b0, b1 + 1):
            put(a, b, y, axis, kind)


# ---- legs: thigh, shin and a long clawed foot (the lower leg on the floor, the upper leg one layer up) ----
for y, off in ((0, 0), (1, 1)):
    line(17, 0 + off, 9, 3 + off, y, "leg")       # thigh
    line(9, 3 + off, 2, 0 + off, y, "leg")        # shin
    line(1, -3 + off, 1, 5 + off, y, "foot")      # foot
    for c in (0, 2):
        put(c, 6 + off, y, 1, "claw")             # toe claws
    put(1, 7 + off, y, 1, "claw")
    put(10, 3 + off, y, 0, "leg")                 # knee knob
# ---- pelvis ----
fill(15, 19, -3, 2, 0, "pelvis", 0)
fill(16, 18, -2, 1, 1, "pelvis", 0)
# ---- spine: hips -> shoulders -> neck -> skull ----
line(17, -1, 33, 3, 0, "spine", 0)
line(33, 3, 37, 6, 0, "spine", 0)
line(37, 6, 40, 9, 0, "spine", 0)


def spine_b(a):
    return -1 + (a - 17) * 4 / 16


# ---- rib cage: 7 pairs of curved ribs sweeping forward from the spine (lower ribs on the floor, upper ribs arching up) ----
for i in range(7):
    a = 19 + 2 * i
    bs = round(spine_b(a))
    length = 7 if 1 <= i <= 4 else 5
    line(a, bs + 1, a - 1, bs + 4, 0, "rib")
    line(a - 1, bs + 4, a - 3, bs + length, 0, "rib")
    line(a, bs + 1, a - 1, bs + 3, 1, "rib")
    line(a - 1, bs + 3, a - 2, bs + length - 1, 2, "rib")
    put(a - 3, bs + length, 1, 2, "rib")
line(17, 6, 29, 9, 0, "rib", 0)                    # sternum along the belly
# ---- arms: upper arm, forearm, clawed hand (lower arm on the floor, upper arm one layer up) ----
for y, off in ((0, 0), (1, -1)):
    line(31, 5, 27 + off, 10, y, "arm")
    line(27 + off, 10, 28 + off, 15, y, "arm")
    for c in (-1, 0, 1):
        put(28 + off + c, 16 + (0 if c == 0 else 0), y, 1, "claw")
    put(28 + off, 17, y, 1, "claw")
# ---- skull: big rounded head, eye socket, a row of teeth, a heavy jaw and brow ----
SKULL_ROWS = {45: (10, 16), 44: (9, 17), 43: (8, 18), 42: (8, 19), 41: (8, 19), 40: (8, 19), 39: (9, 19)}
for a, (b0, b1) in SKULL_ROWS.items():
    for b in range(b0, b1 + 1):
        eye = a in (42, 43) and b in (14, 15)
        mouth = a == 39 and 11 <= b <= 18
        if not (eye or mouth):
            put(a, b, 0, 1, "skull")
for b in (12, 14, 16, 18):
    put(39, b, 0, 0, "tooth")
line(37, 9, 37, 18, 0, "jaw", 1)
for b in (11, 13, 15, 17):
    put(38, b, 0, 0, "tooth")
fill(43, 44, 10, 16, 1, "skull")                    # brow and crown
put(44, 18, 1, 1, "skull")
fill(40, 42, 9, 12, 1, "skull")                     # cheek
put(41, 19, 1, 1, "skull")                          # nostril ridge
# ---- tail: a curving line of vertebrae with spines on top and chevrons underneath, tapering to the tip ----
TAIL = 42
prev = None
for i in range(TAIL + 1):
    t = i / TAIL
    a = 16 - 10 * t ** 1.3 + 2.0 * math.sin(t * math.pi) * 0.8
    b = -3 - 39 * t
    if prev:
        line(prev[0], prev[1], a, b, 0, "tail", 1)
    prev = (a, b)
    if i % 3 == 0 and i < TAIL - 2:
        h = 3 if t < 0.35 else (2 if t < 0.75 else 1)
        for k in range(1, h + 1):
            put(round(a) + k, round(b), 0, 0, "tail_spine")       # spine on top
        if t < 0.8:
            put(round(a) - 1, round(b), 0, 0, "chevron")           # chevron underneath
        if t < 0.5:
            put(round(a), round(b), 1, 2, "tail")                  # the thick base of the tail
# ---- dorsal plates along the back (shoulders to hips), pale bony points ----
for i, a in enumerate(range(19, 36, 3)):
    bs = round(spine_b(a)) if a <= 33 else 3 + (a - 33)
    size = [2, 3, 4, 4, 3, 3][min(i, 5)]
    for k in range(1, size + 1):
        put(a, bs - k, 0, 1, "plate")
    put(a + 1, bs - size + 1, 0, 1, "plate")

bones = sorted(BONES.items(), key=lambda kv: (kv[0][2], kv[0][0], kv[0][1]))
amin = min(k[0] for k, _ in bones); amax = max(k[0] for k, _ in bones)
bmin = min(k[1] for k, _ in bones); bmax = max(k[1] for k, _ in bones)
ymax = max(k[2] for k, _ in bones)

data = ",\n\t\t\t".join(", ".join(f"{a}, {b}, {y}, {ax}" for (a, b, y), (ax, _) in bones[i:i + 8]) for i in range(0, len(bones), 8))
os.makedirs(os.path.dirname(JAVA), exist_ok=True)
open(JAVA, "w").write(f"""package com.gojiramod;

/**
 * GENERATED by tools/skeleton.py: the {len(bones)} bone blocks of the skeleton Gojira leaves on the sea floor when it dies
 * underwater. It lies on its side the way Gojira topples: {{a, b, y, axis}} per bone, where a runs the way it fell (feet at 0,
 * skull top at {amax}), b runs the way it was facing (tail tip at {bmin}, snout at {bmax}), y is layers above the floor, and
 * axis is the grain (0 = along a, 1 = along b, 2 = up). It is {bmax - bmin + 1} blocks long and {amax - amin + 1} wide.
 */
public final class GojiraSkeletonLayout {{
	public static final int COUNT = {len(bones)}, LENGTH = {bmax - bmin + 1}, WIDTH = {amax - amin + 1}, LAYERS = {ymax + 1};
	public static final int MIN_A = {amin}, MAX_A = {amax}, MIN_B = {bmin}, MAX_B = {bmax};
	static final int[] BONES = {{
			{data}
	}};

	private GojiraSkeletonLayout() {{
	}}
}}
""")
print(f"{len(bones)} bone blocks, {bmax - bmin + 1} long (b {bmin}..{bmax}) x {amax - amin + 1} wide (a {amin}..{amax}), {ymax + 1} layers")

if len(sys.argv) > 1:
    from PIL import Image, ImageDraw, ImageFont
    C = 16
    W, H = (bmax - bmin + 1) * C, (amax - amin + 1) * C
    pad, top = 30, 100
    img = Image.new("RGB", (W + 2 * pad, H + top + 2 * pad + 110), (34, 72, 96))
    d = ImageDraw.Draw(img)
    sand = (194, 178, 128)
    d.rectangle((pad, top, pad + W, top + H), fill=(176, 160, 112))
    for x in range(0, W, C):
        d.line((pad + x, top, pad + x, top + H), fill=(166, 150, 104))
    for y in range(0, H, C):
        d.line((pad, top + y, pad + W, top + y), fill=(166, 150, 104))
    shade = {0: (226, 222, 204), 1: (243, 240, 226), 2: (255, 253, 245)}
    for (a, b, y), (ax, kind) in bones:
        px = pad + (b - bmin) * C
        py = top + (amax - a) * C
        col = shade[y]
        if kind in ("tooth", "claw"):
            col = (255, 255, 250)
        d.rectangle((px, py, px + C - 1, py + C - 1), fill=col, outline=(150, 145, 128))
        if ax == 0:
            d.line((px + C // 2, py + 3, px + C // 2, py + C - 4), fill=(190, 185, 165))
        elif ax == 1:
            d.line((px + 3, py + C // 2, px + C - 4, py + C // 2), fill=(190, 185, 165))
        else:
            d.ellipse((px + 5, py + 5, px + C - 6, py + C - 6), outline=(190, 185, 165))
    try:
        f = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 26)
        fs = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 18)
    except OSError:
        f = fs = ImageFont.load_default()
    d.text((pad, 14), "Gojira Mod 0.1.5: sea-floor skeleton (top-down)", fill=(240, 240, 240), font=f)
    d.text((pad, 46), f"{len(bones)} bone blocks, {bmax - bmin + 1} blocks long x {amax - amin + 1} wide, up to {ymax + 1} layers high", fill=(220, 230, 240), font=fs)
    d.text((pad, 68), "tail tip  <---  facing direction  --->  snout          (up the page = the way it toppled)", fill=(210, 225, 235), font=fs)
    ly = top + H + pad
    for i, (lab, col) in enumerate([("on the sea floor (layer 0)", shade[0]), ("1 block up", shade[1]), ("2 blocks up", shade[2])]):
        x = pad + i * 320
        d.rectangle((x, ly, x + 22, ly + 22), fill=col, outline=(150, 145, 128))
        d.text((x + 32, ly), lab, fill=(230, 230, 230), font=fs)
    d.text((pad, ly + 40), "Bone blocks only replace water, air and seagrass; columns with no sea floor below are skipped.",
           fill=(210, 225, 235), font=fs)
    img = img.resize((img.width * 3 // 2, img.height * 3 // 2), Image.NEAREST)
    img.save(sys.argv[1])
    print("preview:", sys.argv[1], img.size)
