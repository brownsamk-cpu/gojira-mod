"""Showa Godzilla assets (0.2.0), all generated from data in this file (no copied art).
usage (from the repo root): python3 tools/showa.py      needs numpy, Pillow

Writes:
  src/client/java/com/gojiramod/client/ShowaGodzillaModelLayer.java  the model's parts and boxes (generated, do not edit)
  src/main/java/com/gojiramod/ShowaDimensions.java                  scale, mouth/feet positions, reach (generated)
  textures/entity/showa_godzilla.png       charcoal bumpy skin, smooth grey-tan belly, big friendly eyes, white plates
  textures/entity/showa_godzilla_glow.png  the plates + mouth glow layer (beam and jet flight)
  textures/item/showa_godzilla_spawn_egg.png (64x64 detailed egg)

June's hero kaiju, inspired by the 1960s/70s "Showa era" look, as an ORIGINAL blocky Minecraft design (not a copy of any
movie suit or asset), and on purpose very different from our scarier Gojira (tools/gojira.py):
  * a ROUND, friendly head: short rounded muzzle, no heavy brow, BIG round eyes (white with a dark pupil and a shine),
    little ear bumps, and a smiling lip line
  * a pear-shaped body with a big round belly (smooth grey-tan, soft ridges), short stubby arms with mitten hands
  * very thick, column-like legs with blunt toes
  * three rows of bright WHITE rounded leaf plates (4 boxes each: no jagged side points like Gojira's)
  * a long seven-part tail that drags on the ground
"""
import math, os
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = ROOT + "/src/main/resources/assets/gojiramod"
JAVA = ROOT + "/src/client/java/com/gojiramod/client/ShowaGodzillaModelLayer.java"
DIMS_JAVA = ROOT + "/src/main/java/com/gojiramod/ShowaDimensions.java"
TARGET_HEIGHT_BLOCKS = 164.0 / 3.0  # same as Gojira (June: "the same height")
TEX_W, TEX_H = 256, 256
TEX_DENSITY = 4

PARTS = []
def part(name, parent, pivot, rot, boxes, mirror=False):
    PARTS.append((name, parent, tuple(float(v) for v in pivot), tuple(float(v) for v in rot), boxes, mirror))
def mirror_x(boxes):
    return [(m, -(x + w), y, z, w, h, d) for (m, x, y, z, w, h, d) in boxes]
SIDED = set()
def sided(name, parent, pivot, rot, boxes):
    part("right_" + name, parent if parent is None else ("right_" + parent if parent in SIDED else parent), pivot, rot, boxes)
    lparent = parent if parent is None else ("left_" + parent if parent in SIDED else parent)
    part("left_" + name, lparent, (-pivot[0], pivot[1], pivot[2]), (rot[0], -rot[1], -rot[2]), mirror_x(boxes), True)
    SIDED.add(name)

# ---------------- 0.2.1 "super detailed" model ----------------
# Same friendly Showa-inspired shapes as 0.2.0 (round head, big eyes, round tan belly, thick legs, white rounded plates),
# with far more boxes: knobbly skin bumps and scale ridges, layered plates all the way down the tail, a detailed face
# (brow ridges, eyelids, nostril rims, ear bumps, teeth, tongue, chin), fingers with thumbs and two-part claws, four toes
# with toenails, a segmented tan belly, and knee, calf, thigh and shoulder bulges.
import random as _random
_brng = _random.Random(1971)
BUMP_SIZES = [(1.5, 1.5), (1.0, 1.0), (2.0, 1.5), (1.5, 2.0)]
def bumps(box, face, n, inset=0.25, depth=0.7, avoid=None):
    """n small knobbly "bump" cubes sticking out of one face of a box (x, y, z, w, h, d); face in +x -x -y +z -z"""
    _, x, y, z, w, h, d = box
    out = []
    for _ in range(n):
        a, b = BUMP_SIZES[_brng.randrange(len(BUMP_SIZES))]
        if face in ("+x", "-x"):
            py = y + inset + _brng.random() * max(0.1, h - a - 2 * inset); pz = z + inset + _brng.random() * max(0.1, d - b - 2 * inset)
            px = x + w - depth * 0.4 if face == "+x" else x - depth * 0.6
            out.append(("bump", round(px, 2), round(py, 2), round(pz, 2), depth, a, b))
        elif face == "-y":
            px = x + inset + _brng.random() * max(0.1, w - a - 2 * inset); pz = z + inset + _brng.random() * max(0.1, d - b - 2 * inset)
            out.append(("bump", round(px, 2), round(y - depth * 0.6, 2), round(pz, 2), a, depth, b))
        else:  # "+z" (back) or "-z" (front)
            px = x + inset + _brng.random() * max(0.1, w - a - 2 * inset); py = y + inset + _brng.random() * max(0.1, h - b - 2 * inset)
            pz = z + d - depth * 0.4 if face == "+z" else z - depth * 0.6
            out.append(("bump", round(px, 2), round(py, 2), round(pz, 2), a, b, depth))
    return out

# ---- thick column legs: thigh (with bulges) -> shin (knee, calf) -> wide foot with four toes and toenails ----
THIGH = ("skin", -7, -3, -6.5, 14, 13, 13)
sided("leg", None, (-8, 4, 2), (-0.12, 0, 0.0), [THIGH, ("skin", -6, 9, -5.5, 12, 2, 11),
    ("skin", -8, -1, -5, 1.5, 9, 10),            # outer thigh bulge
    ("skin", -5.5, -1, -7.5, 11, 8, 1.5),        # front thigh bulge
    ("skin", -5, 0, 6, 10, 7, 1.5),              # back of the thigh
    ("skin", -6, -4, -5.5, 12, 1.5, 11),         # hip joint roll
    ] + bumps(("", -8, -1, -5, 1.5, 9, 10), "-x", 6) + bumps(("", -5.5, -1, -7.5, 11, 8, 1.5), "-z", 5) + bumps(("", -5, 0, 6, 10, 7, 1.5), "+z", 3))
SHIN = ("skin", -6, 0, -6, 12, 9, 12)
sided("shin", "leg", (0, 10, 0), (0.2, 0, 0), [SHIN, ("skin", -6.5, 6, -6.5, 13, 2, 13),
    ("skin", -5, -1, -7.2, 10, 4, 1.5),          # knee bulge
    ("skin", -3.5, -1.6, -7.8, 7, 2.5, 1),       # kneecap
    ("skin", -5, 1, 5.8, 10, 5.5, 1.5),          # calf
    ("skin", -6.8, 1, -4, 1, 5, 8),              # outer shin ridge
    ] + bumps(SHIN, "-x", 4) + bumps(SHIN, "-z", 3))
TOE_X = (-6.75, -3.25, 0.25, 3.75)
sided("foot", "shin", (0, 8, 0), (-0.08, 0, 0), [("skin", -7, 0, -8, 14, 3, 14),
    ("skin", -6, -0.8, -6.5, 12, 1, 11),          # top of the foot
    ("skin", -5.5, 0.5, 6, 11, 2.5, 1.5),         # heel
    ] + [("skin", x, 0.5, -10.5, 3, 2.5, 2.5) for x in TOE_X]
      + [("skin", x + 0.25, 1, -11.5, 2.5, 2, 1) for x in TOE_X]
      + [("nail", x + 0.5, 1.5, -12.3, 2, 1.5, 0.8) for x in TOE_X])

# ---- pear-shaped body, round segmented tan belly, back hump for the plates ----
part("hips", None, (0, 4, 2), (0, 0, 0), [("skin", -11.5, -4, -8, 23, 8, 17), ("skin", -10.5, -2, 9, 21, 5, 1.5)])
LOWER = ("skin", -12, -11, -9, 24, 12, 18)
CHEST = ("skin", -10, -21, -8, 20, 11, 16)
BELLY_BANDS = [(-10.8, 14), (-8.8, 18), (-6.8, 20), (-4.8, 20), (-2.8, 19), (-0.8, 16)]       # (top y, width) lower belly
CHEST_BANDS = [(-19.8, 12), (-17.8, 15), (-15.8, 16), (-13.8, 16)]
part("body", "hips", (0, -1, 0), (0.08, 0, 0), [LOWER, CHEST,
    ("skin", -12, -20, -6, 2.5, 6, 11), ("skin", 9.5, -20, -6, 2.5, 6, 11),       # round shoulders
    ("skin", -13, -9, -7, 1.5, 8, 14), ("skin", 11.5, -9, -7, 1.5, 8, 14),        # belly sides
    ("skin", -9, -21, -9, 7.5, 3.5, 1.5), ("skin", 1.5, -21, -9, 7.5, 3.5, 1.5),  # chest bulges
    ("skin", -8, -21, 7.5, 16, 17, 1.5),                                          # back hump (the plates sit on it)
    ("skin", -12.5, -12, -8, 1, 2, 16), ("skin", 11.5, -12, -8, 1, 2, 16),        # waist rolls
    ] + [("belly", -w / 2, y, -10.5, w, 1.7, 1.5) for y, w in BELLY_BANDS]
      + [("belly", -w / 2, y, -9.5, w, 1.7, 1.5) for y, w in CHEST_BANDS]
      + [("belly", -5, -11.5, -11.2, 10, 1.2, 0.7)]                                # belly button bump
      + bumps(LOWER, "-x", 7) + bumps(LOWER, "+x", 7) + bumps(CHEST, "-x", 5) + bumps(CHEST, "+x", 5)
      + bumps(("", -8, -21, 7.5, 16, 17, 1.5), "+z", 6) + bumps(("", -12, -20, -6, 2.5, 6, 11), "-y", 2) + bumps(("", 9.5, -20, -6, 2.5, 6, 11), "-y", 2))
NECK = ("skin", -8, -6, -7, 16, 7, 14)
part("neck", "body", (0, -20, -1), (-0.1, 0, 0), [NECK,
    ("skin", -8.5, -3, -6, 17, 1.5, 12), ("skin", -8.3, -5.5, -5.5, 16.6, 1.2, 11),   # neck folds
    ("belly", -6, -5.6, -8.5, 12, 2.2, 1.5), ("belly", -5.5, -3.1, -8.5, 11, 2.2, 1.5)]  # throat segments
    + bumps(NECK, "-x", 3) + bumps(NECK, "+x", 3) + bumps(NECK, "-y", 2))
CRANIUM = ("skin", -7, -8, -8, 14, 9, 11)
part("head", "neck", (0, -5.5, -2.5), (0.05, 0, 0), [
    CRANIUM,
    ("skin", -6, -9.5, -7, 12, 1.5, 9),           # rounded crown
    ("skin", -7.5, -6.5, -7, 15, 6, 9),           # round cheeks
    ("skin", -7.9, -4, -6, 1, 3, 6), ("skin", 6.9, -4, -6, 1, 3, 6),             # jowls
    ("snout", -5, -5.5, -12.5, 10, 5, 5),         # short rounded muzzle
    ("snout", -4, -5, -13.5, 8, 4, 1),            # muzzle tip
    ("skin", -4, -6.5, -12, 8, 1, 4),             # muzzle top
    ("skin", -2.5, -7.2, -11, 5, 0.8, 3),         # muzzle ridge
    ("nostril", -2.5, -4.8, -13.7, 1, 1, 0.5), ("nostril", 1.5, -4.8, -13.7, 1, 1, 0.5),
    ("skin", -3, -5.4, -13.9, 2, 0.6, 0.6), ("skin", 1, -5.4, -13.9, 2, 0.6, 0.6),   # nostril rims
    ("eye", -6, -7.8, -8.6, 3.5, 3.5, 1), ("eye", 2.5, -7.8, -8.6, 3.5, 3.5, 1),     # BIG round friendly eyes
    ("lid", -6.1, -8.0, -9.1, 3.7, 0.8, 0.6), ("lid", 2.4, -8.0, -9.1, 3.7, 0.8, 0.6),   # upper eyelids
    ("lid", -6.1, -4.5, -9.0, 3.7, 0.5, 0.5), ("lid", 2.4, -4.5, -9.0, 3.7, 0.5, 0.5),   # lower lids
    ("skin", -6.6, -9.1, -9.3, 4.4, 1.2, 2.2), ("skin", 2.2, -9.1, -9.3, 4.4, 1.2, 2.2),  # brow ridges
    ("skin", -1.5, -8.8, -8.8, 3, 1, 1.5),        # brow middle
    ("skin", -7.5, -10, -3, 2, 2, 2), ("skin", 5.5, -10, -3, 2, 2, 2),               # ear bumps
    ("skin", -7.2, -10.8, -2.6, 1.4, 1, 1.4), ("skin", 5.8, -10.8, -2.6, 1.4, 1, 1.4),
    ("lip", -5, -1, -12.5, 10, 0.5, 5),           # smiling lip line
    ("lip", -5.3, -1.4, -8, 0.6, 0.8, 0.6), ("lip", 4.7, -1.4, -8, 0.6, 0.8, 0.6),   # smile corners
] + [("tooth", -4 + 2 * i, -1, -12.6, 1, 1, 1) for i in range(5)]
  + [("tooth", -4.6, -1, -11.4 + 1.5 * i, 0.8, 0.9, 0.8) for i in range(3)] + [("tooth", 3.8, -1, -11.4 + 1.5 * i, 0.8, 0.9, 0.8) for i in range(3)]
  + bumps(CRANIUM, "-y", 5) + bumps(CRANIUM, "-x", 3) + bumps(CRANIUM, "+x", 3))
part("jaw", "head", (0, -0.5, -3), (0, 0, 0), [("jaw", -4.5, 0, -9.5, 9, 3, 9), ("skin", -6, 0, -1, 12, 3, 4), ("belly", -4, 2.5, -9, 8, 1, 7),
    ("tongue", -2.5, -0.5, -8.5, 5, 0.8, 6), ("tongue", -1.8, -0.6, -9.2, 3.6, 0.7, 1),
    ("skin", -3.5, 2.8, -9.3, 7, 1, 5)]           # chin
    + [("tooth", -3.7 + 1.85 * i, -0.8, -9.4, 0.9, 0.9, 0.9) for i in range(5)]
    + [("tooth", -4.2, -0.8, -7.8 + 1.5 * i, 0.8, 0.8, 0.8) for i in range(3)] + [("tooth", 3.4, -0.8, -7.8 + 1.5 * i, 0.8, 0.8, 0.8) for i in range(3)])
# ---- short stubby arms: shoulder bulge, elbow, hand with three fingers, a thumb, and two-part claws ----
sided("arm", "body", (-12, -17, -4), (-0.5, 0, 0.35), [("skin", -3, -2, -3, 6, 8, 6), ("skin", -3.8, -2.5, -2.5, 1, 4.5, 5)]
      + bumps(("", -3, -2, -3, 6, 8, 6), "-x", 3))
sided("forearm", "arm", (0, 5.5, 0), (-0.85, 0, 0), [("skin", -2.5, 0, -2.5, 5, 6, 5), ("skin", -2, -0.6, 2.2, 4, 3, 1), ("skin", -3, 3, -2, 1, 3, 4)])
FINGER_X = (-2.8, -0.8, 1.2)
sided("hand", "forearm", (0, 5.5, 0), (0, 0, 0), [("skin", -3, 0, -2.5, 6, 3, 5)]
      + [("skin", x, 3, -2.3, 1.6, 1.8, 1.6) for x in FINGER_X]
      + [("nail", x, 4.8, -2.3, 1.6, 0.8, 1.6) for x in FINGER_X]
      + [("nail", x + 0.3, 5.6, -2.0, 1.0, 0.7, 1.0) for x in FINGER_X]
      + [("skin", 2.8, 0.8, -2.8, 1.5, 2.2, 1.5), ("nail", 3.0, 3.0, -2.6, 1.1, 0.8, 1.1)])   # thumb
# ---- long dragging tail, 7 parts: top ridge, side scale rows, a segmented tan underside ----
TAIL = [(16, 12, 11, -0.25), (13.5, 10.5, 11, -0.1), (11, 9, 11, -0.05), (9, 7.5, 11, 0.0), (7, 6, 10, 0.1), (5.5, 4.5, 10, 0.15), (4, 3.5, 9, 0.2)]
prev, z = "hips", 7
for i, (w, h, L, rx) in enumerate(TAIL):
    name = f"tail{i + 1}"
    seg = ("skin", -w / 2, -h / 2, -1, w, h, L + 1)
    boxes = [seg, ("skin", -w / 2 + 1.5, -h / 2 - 0.6, 0, w - 3, 0.8, L)]                       # top ridge
    boxes += [("belly", -w / 2 + 1, h / 2, zz, w - 2, 0.6, 1.6) for zz in np.arange(0.2, L - 1.0, 2.2)]  # tan underside segments
    boxes += [("skin", -w / 2 - 0.5, -h / 4, 0.5, 0.8, h / 2, L - 1), ("skin", w / 2 - 0.3, -h / 4, 0.5, 0.8, h / 2, L - 1)]  # side scale rows
    if i < 5:
        boxes += bumps(seg, "-x", 3 - i // 2) + bumps(seg, "+x", 3 - i // 2)
    part(name, prev, (0, -1 if i == 0 else 0.3, z), (rx, 0, 0), boxes)
    prev, z = name, L

# ---- layered rounded white plates: base, middle, upper, tip, plus a raised core layer and a back lobe ----
PLATE_SIZES = {"L": 1.55, "M": 1.25, "S": 0.95, "XS": 0.72, "XXS": 0.5}
R = lambda v: round(v * 2) / 2
def plate_boxes(size):
    s = PLATE_SIZES[size]
    t = lambda v: max(0.5, R(v))
    return [("plate", -1, -t(2.5 * s), -t(3.5 * s), 2, t(2.5 * s), 2 * t(3.5 * s)),
            ("plate", -0.9, -t(5 * s), -t(3 * s), 1.8, t(2.5 * s), 2 * t(3 * s)),
            ("plate", -0.8, -t(7 * s), -t(2 * s), 1.6, t(2 * s), 2 * t(2 * s)),
            ("plate", -0.7, -t(8.5 * s), -t(1 * s), 1.4, t(1.5 * s), max(0.5, 2 * t(1 * s))),
            ("platecore", -1.3, -t(5.5 * s), -t(1.5 * s), 2.6, t(4.5 * s), 2 * t(1.5 * s)),           # raised inner layer
            ("plate", -0.75, -t(4 * s), t(2.5 * s), 1.5, t(2 * s), t(1.5 * s))]                       # back lobe
PLATES = 0
def plate(parent, pos, size, rot):
    global PLATES
    PLATES += 1
    part(f"plate{PLATES}", parent, pos, rot, plate_boxes(size))
UP_BACK = -1.4
for y, size in ((-20, "L"), (-16, "L"), (-12, "L"), (-8, "M"), (-4, "S")):
    plate("body", (0, y, 8.5), size, (UP_BACK, 0, 0))
for y, size in ((-19, "S"), (-15, "M"), (-11, "S"), (-7, "XS")):
    plate("body", (-4.5, y, 8.2), size, (UP_BACK, 0, 0.3)); plate("body", (4.5, y, 8.2), size, (UP_BACK, 0, -0.3))
plate("neck", (0, -5, 6.5), "M", (UP_BACK, 0, 0)); plate("neck", (0, -1.5, 6.5), "S", (UP_BACK, 0, 0))
plate("neck", (-4, -3.5, 6.5), "XS", (UP_BACK, 0, 0.3)); plate("neck", (4, -3.5, 6.5), "XS", (UP_BACK, 0, -0.3))
plate("head", (0, -9, 3), "XXS", (UP_BACK + 0.3, 0, 0))
TAIL_CENTER = [(("M", 2), ("S", 5.5), ("S", 9)), (("S", 2), ("S", 5.5), ("S", 9)), (("S", 2), ("XS", 6)), (("XS", 2), ("XS", 6.5)),
               (("XS", 2), ("XXS", 6.5)), (("XXS", 2.5), ("XXS", 6.5)), (("XXS", 3),)]
for i, plist in enumerate(TAIL_CENTER):
    w, h, L, _ = TAIL[i]
    for size, zz in plist:
        plate(f"tail{i + 1}", (0, -h / 2 - 0.4, zz), size, (-0.3, 0, 0))
    if i < 3:
        side = "XS" if i < 2 else "XXS"
        for zz in ((3.5, 8) if i < 2 else (5,)):
            plate(f"tail{i + 1}", (-w / 2 + 2, -h / 2 + 0.3, zz), side, (-0.3, 0, 0.35))
            plate(f"tail{i + 1}", (w / 2 - 2, -h / 2 + 0.3, zz), side, (-0.3, 0, -0.35))


# ---------------- geometry ----------------
def rot(rx, ry, rz):
    cx_, sx = math.cos(rx), math.sin(rx); cy_, sy = math.cos(ry), math.sin(ry); cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx_, -sx], [0, sx, cx_]]); Ry = np.array([[cy_, 0, sy], [0, 1, 0], [-sy, 0, cy_]]); Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx
def transforms(parts):
    W = {}
    for name, parent, piv, r, boxes, _ in parts:
        Rm, T = rot(*r), np.array(piv, float)
        if parent:
            PR, PT = W[parent]
            W[name] = (PR @ Rm, PT + PR @ T)
        else:
            W[name] = (Rm, T)
    return W
def corners(parts, W):
    for name, _, _, _, boxes, _ in parts:
        R_, T = W[name]
        for (m, x, y, z, w, h, d) in boxes:
            for dx in (0, w):
                for dy in (0, h):
                    for dz in (0, d):
                        yield name, m, T + R_ @ np.array((x + dx, y + dy, z + dz), float)
WORLD = transforms(PARTS)
ground = max(p[1] for _, _, p in corners(PARTS, WORLD))
shift = 24.0 - ground
PARTS = [(n, par, (piv[0], piv[1] + shift, piv[2]) if par is None else piv, r, b, mi) for (n, par, piv, r, b, mi) in PARTS]
WORLD = transforms(PARTS)
pts = list(corners(PARTS, WORLD))
minY = min(p[1] for _, _, p in pts)
height_px = 24 - minY
scale = TARGET_HEIGHT_BLOCKS / (height_px / 16.0)
blocks = scale / 16.0
def to_model(partname, p):
    R_, T = WORLD[partname]
    return T + R_ @ np.array(p, float)
mouth = to_model("head", (0, -1, -13.5))
front = -min(p[2] for _, _, p in pts) * blocks
tail_tip = max(p[2] for _, _, p in pts) * blocks
half_w = max(abs(p[0]) for _, _, p in pts) * blocks
feet = [p for n, _, p in pts if "foot" in n]
feet_reach = max(math.hypot(p[0], p[2]) for p in feet) * blocks
cubes = sum(len(b) for (_, _, _, _, b, _) in PARTS)
body_half_w = max(max(abs(bx[1]), abs(bx[1] + bx[4])) for n_, _, _, _, bxs, _ in PARTS if n_ == "body" for bx in bxs) * blocks
if __name__ == "__main__":
    print(f"{len(PARTS)} parts, {cubes} cubes ({PLATES} plates); model height {height_px:.2f} px -> scale {scale:.4f}")
    print(f"MOUTH_UP = {(24 - mouth[1]) * blocks:.2f}, MOUTH_FORWARD = {-mouth[2] * blocks:.2f}, FEET_REACH = {feet_reach:.2f}")
    print(f"extents (blocks): front {front:.1f}, tail tip {tail_tip:.1f}, half width {half_w:.1f}")

# ---------------- UV packing ----------------
def key(m, w, h, d):
    return f"{m}:{w}:{h}:{d}"
sizes = {}
for _, _, _, _, boxes, _ in PARTS:
    for (m, x, y, z, w, h, d) in boxes:
        sizes[key(m, w, h, d)] = (m, w, h, d)
order = sorted(sizes, key=lambda k: -(sizes[k][2] + sizes[k][3]))
UV, cx, cy, shelf = {}, 0, 0, 0
for k in order:
    m, w, h, d = sizes[k]
    rw, rh = math.ceil(2 * (w + d)), math.ceil(d + h)
    if cx + rw > TEX_W:
        cx, cy, shelf = 0, cy + shelf, 0
    UV[k] = (cx, cy)
    cx += rw
    shelf = max(shelf, rh)
assert cy + shelf <= TEX_H, f"texture too small: needs {cy + shelf} rows"
D = TEX_DENSITY

def write_all():
    def f(v):
        return f"{float(v)}F"
    lines = []
    for name, parent, piv, r, boxes, mirror in PARTS:
        cb = "CubeListBuilder.create()"
        for (m, x, y, z, w, h, d) in boxes:
            u, v = UV[key(m, w, h, d)]
            cb += f"\n\t\t\t\t.texOffs({u}, {v})" + (".mirror()" if mirror else "") + f".addBox({f(x)}, {f(y)}, {f(z)}, {f(w)}, {f(h)}, {f(d)})"
        pose = f"PartPose.offsetAndRotation({f(piv[0])}, {f(piv[1])}, {f(piv[2])}, {f(r[0])}, {f(r[1])}, {f(r[2])})"
        lines.append(f"\t\tPartDefinition {name} = {'root' if parent is None else parent}.addOrReplaceChild(\"{name}\", {cb},\n\t\t\t\t{pose});")
    open(JAVA, "w").write(f"""package com.gojiramod.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by tools/showa.py: {len(PARTS)} parts, {cubes} cubes ({PLATES} rounded white back plates), UVs packed for a
 * {TEX_W}x{TEX_H} layout (the PNG is drawn {TEX_DENSITY}x bigger for detail). Edit the data there, not here.
 */
@SuppressWarnings("unused")
final class ShowaGodzillaModelLayer {{
	private ShowaGodzillaModelLayer() {{
	}}

	static LayerDefinition create() {{
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
{chr(10).join(lines)}
		return LayerDefinition.create(mesh, {TEX_W}, {TEX_H});
	}}
}}
""")
    open(DIMS_JAVA, "w").write(f"""package com.gojiramod;

/** GENERATED by tools/showa.py from the model data: Showa Godzilla's size and where his (visual) mouth and feet are. */
public final class ShowaDimensions {{
	/** Height he is drawn at, in blocks (164/3: the same as Gojira). */
	public static final float VISUAL_HEIGHT = {TARGET_HEIGHT_BLOCKS!r}F;
	/** Unscaled model height in model pixels. */
	public static final float MODEL_HEIGHT_PX = {height_px:.4f}F;
	/** How much the renderer scales the model up. */
	public static final float VISUAL_SCALE = {scale:.4f}F;
	/** The beam starts here: this many blocks above his feet and in front of his body (standing up). */
	public static final double MOUTH_UP = {(24 - mouth[1]) * blocks:.3f}, MOUTH_FORWARD = {-mouth[2] * blocks:.3f};
	/** The model's root pivot (what the jet-flight pose tips around), in blocks above his feet. */
	public static final double ROOT_PIVOT_UP = {24 * blocks:.3f};
	/** The farthest point of his feet/toes from his center, in blocks. */
	public static final double FEET_REACH = {feet_reach:.3f};
	/** How far the drawn model reaches from his center: snout, tail tip, half width (blocks). */
	public static final double FRONT_REACH = {front:.3f}, TAIL_REACH = {tail_tip:.3f}, HALF_WIDTH = {half_w:.3f};
	/** Half the width of his belly, in blocks: how high his body rests when he lies on his side (falling over). */
	public static final double BODY_HALF_WIDTH = {body_half_w:.3f};
	/** Number of model parts, cubes and back plates (for the tests). */
	public static final int PARTS = {len(PARTS)}, CUBES = {cubes}, DORSAL_PLATES = {PLATES};

	private ShowaDimensions() {{
	}}
}}
""")
    write_textures()
    write_egg()
    print("wrote ShowaGodzillaModelLayer.java, ShowaDimensions.java, showa_godzilla.png, showa_godzilla_glow.png, showa_godzilla_spawn_egg.png")

# ---------------- textures ----------------
def cellular(IW, IH, cell, seed):
    gg = np.random.default_rng(seed)
    gy, gx = IH // cell + 2, IW // cell + 2
    jx, jy = gg.random((gy, gx)), gg.random((gy, gx))
    yy, xx = np.mgrid[0:IH, 0:IW].astype(float)
    cxi, cyi = (xx // cell).astype(int), (yy // cell).astype(int)
    f1 = np.full((IH, IW), 9e9); f2 = np.full((IH, IW), 9e9)
    for oy in (-1, 0, 1):
        for ox in (-1, 0, 1):
            ny, nx = np.clip(cyi + oy, 0, gy - 1), np.clip(cxi + ox, 0, gx - 1)
            px, py = (nx + jx[ny, nx]) * cell, (ny + jy[ny, nx]) * cell
            dd = np.hypot(xx - px, yy - py)
            f2 = np.where(dd < f1, f1, np.minimum(f2, dd)); f1 = np.minimum(f1, dd)
    return f1 / cell, (f2 - f1) / cell

def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}
def px_rect(x0, y0, fw, fh):
    a, b = int(round(x0 * D)), int(round(y0 * D))
    return a, b, max(1, int(round((x0 + fw) * D)) - a), max(1, int(round((y0 + fh) * D)) - b)

def write_textures():
    IW, IH = TEX_W * D, TEX_H * D
    g = np.random.default_rng(1971)
    # rounder, knobblier bumps than Gojira's scales (the Showa suits looked lumpy, like rough bark)
    f1a, edgea = cellular(IW, IH, 5, 11)
    f1b, edgeb = cellular(IW, IH, 2, 12)
    f1c, edgec = cellular(IW, IH, 9, 13)
    bump = (1 - np.clip(f1a, 0, 1)) ** 1.5 * 0.6 + (1 - np.clip(f1b, 0, 1)) * 0.2 + (1 - np.clip(f1c, 0, 1)) ** 2 * 0.3
    gy_, gx_ = np.gradient(bump)
    lit = np.clip(0.5 - (gx_ * -0.8 + gy_ * -1.0) * 6.0, 0, 1)
    crevice = np.clip(1 - edgea / 0.15, 0, 1) * 0.45 + np.clip(1 - edgeb / 0.1, 0, 1) * 0.15 + np.clip(1 - edgec / 0.08, 0, 1) * 0.3
    skin_v = 50 + 40 * lit * bump + g.normal(0, 3, (IH, IW)) - 26 * crevice
    # sparse hand-placed-looking highlight flecks on the knob tops and darker pores
    flecks = (g.random((IH, IW)) < 0.012) & (bump > 0.55)
    pores = (g.random((IH, IW)) < 0.01) & (bump < 0.25)
    skin_v = np.where(flecks, skin_v + 28, skin_v); skin_v = np.where(pores, skin_v - 18, skin_v)
    img = np.zeros((IH, IW, 4)); glow = np.zeros((IH, IW, 4))
    for k, (m, w, h, d) in sizes.items():
        u, v = UV[k]
        for fname, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
            a, b, pw, ph = px_rect(fx, fy, fw, fh)
            ys = (np.arange(ph)[:, None] + 0.5) / ph * np.ones((1, pw))
            xs = np.ones((ph, 1)) * (np.arange(pw)[None, :] + 0.5) / pw
            sl = (slice(b, b + ph), slice(a, a + pw))
            side = fname not in ("top", "bottom")
            rim = True
            if m in ("skin", "snout", "jaw", "bump", "lid"):
                val = skin_v[sl].copy()
                if side and m != "bump" and fh >= 2.5:
                    # rows of overlapping scale ridges: a dark line with a bright lip just above it
                    yy_ = ys * fh + (a % 7) * 0.13
                    ph_ = (yy_ % 2.2) / 2.2
                    wav = 0.25 * np.sin(xs * fw * 2.3 + (b % 5))
                    ph_ = (ph_ + wav * 0.15) % 1
                    val += np.where(ph_ < 0.12, -16, 0) + np.where((ph_ > 0.85), 9, 0)
                if fname == "top":
                    val += 9
                elif fname == "bottom":
                    val -= 14
                else:
                    val *= 1.07 - 0.25 * ys ** 1.4          # light from above, ambient occlusion toward the bottom
                if m == "bump":
                    # a round knob: bright cap, dark rim
                    r_ = np.hypot(xs - 0.42, ys - 0.4)
                    val = val * 0.9 + np.where(r_ < 0.28, 30, np.where(r_ > 0.5, -14, 6))
                if m == "lid":
                    val = val + 10 - 25 * (ys > 0.7)
                col = np.stack([val * 0.97, val * 1.03, val * 1.0], -1)   # charcoal with a hint of green-grey
                if m == "snout" and fname == "bottom" or m == "jaw" and fname == "top":
                    col = np.stack([np.full_like(val, 120), np.full_like(val, 62), np.full_like(val, 70)], -1) * (0.85 + 0.15 * bump[sl][..., None])
                    glow[sl] = np.dstack([np.full((ph, pw), 225), np.full((ph, pw), 240), np.full((ph, pw), 255), np.full((ph, pw), 230)])
                # bevel: a soft highlight along the upper-left edge of each face
                e2 = max(1, D // 2)
                col[:e2] += 14; col[:, :e2] += 8
            elif m == "belly":
                # smooth tan belly segment: dark segment lines at the top and bottom, tiny pits, a soft sheen
                seg = (ys < 0.14) | (ys > 0.88) if side else np.zeros_like(ys, bool)
                pits = g.random((ph, pw)) < 0.04
                val = 134 + 14 * lit[sl] * bump[sl] + 18 * np.exp(-((xs - 0.4) ** 2 + (ys - 0.4) ** 2) / 0.08) + g.normal(0, 2.5, (ph, pw))
                val = val - 42 * seg - 18 * pits
                if fname == "top":
                    val += 6
                col = np.stack([val * 1.08, val * 0.98, val * 0.8], -1)
            elif m in ("plate", "platecore"):
                # bright white rounded plates: fine vertical striations, a cream base, a blue-grey rim; the core layer is pale grey
                base = 244 if m == "plate" else 214
                stri = 7 * np.sin(xs * fw * D * 1.1 + (a % 3)) * side
                val = base + g.normal(0, 3, (ph, pw)) - 34 * np.clip(ys - 0.65, 0, 1) * side + stri
                col = np.stack([val, val * 0.995, np.minimum(255, val * 1.03)], -1)
                col = col + (np.clip(ys - 0.75, 0, 1) * 60 * side)[..., None] * np.array([0.05, -0.02, -0.25])   # cream toward the base
                edge = (xs < 0.09) | (xs > 0.91) | (ys < 0.07)
                col = np.where(edge[..., None], np.array([168, 180, 200.0]), col)
                col = np.where(((ys > 0.07) & (ys < 0.15) & ~edge)[..., None], np.minimum(255, col + 12), col)    # shiny lip under the rim
                gl = np.clip(1.2 - 0.5 * ys, 0.6, 1.0) * (fname != "bottom") * (1.0 if m == "plate" else 0.75)
                glow[sl] = np.dstack([np.full((ph, pw), 215), np.full((ph, pw), 238), np.full((ph, pw), 255), 255 * gl])
                rim = False
            elif m == "tooth":
                val = 242 - 24 * ys + g.normal(0, 3, (ph, pw)); col = np.stack([val, val * 0.99, val * 0.93], -1)
            elif m == "nail":
                val = 220 - 60 * ys + g.normal(0, 4, (ph, pw)) + 10 * (xs < 0.3); col = np.stack([val, val * 0.97, val * 0.88], -1)
            elif m == "tongue":
                groove = np.abs(xs - 0.5) < 0.08
                val = 1.0 - 0.15 * groove - 0.12 * ys + g.normal(0, 0.02, (ph, pw))
                col = np.stack([215 * val, 112 * val, 122 * val], -1)
            elif m == "nostril":
                col = np.full((ph, pw, 3), 22.0) + 10 * (ys < 0.25)[..., None]
            elif m == "lip":
                col = np.full((ph, pw, 3), 32.0) + 8 * (ys < 0.3)[..., None]
            elif m == "eye":
                rim = False
                if fname == "front":
                    # big friendly eye: white, a big round dark-brown iris with a ring, a pupil, two shines
                    r_eye = np.hypot(xs - 0.5, ys - 0.5)
                    pupil = np.hypot(xs - 0.5, ys - 0.55)
                    col = np.where((r_eye < 0.64)[..., None], np.array([246, 246, 240.0]), np.array([40, 42, 40.0]))
                    col = np.where((ys < 0.22)[..., None] & (r_eye < 0.64)[..., None], np.array([214, 214, 206.0]), col)   # lid shadow
                    col = np.where((pupil < 0.29)[..., None], np.array([72, 46, 28.0]), col)
                    col = np.where((pupil < 0.24)[..., None], np.array([50, 32, 22.0]), col)
                    col = np.where((pupil < 0.15)[..., None], np.array([12, 10, 8.0]), col)
                    col = np.where((np.hypot(xs - 0.4, ys - 0.44) < 0.08)[..., None], np.array([255, 255, 255.0]), col)
                    col = np.where((np.hypot(xs - 0.6, ys - 0.66) < 0.04)[..., None], np.array([235, 235, 235.0]), col)
                else:
                    col = np.full((ph, pw, 3), 235.0)
            else:
                raise ValueError(m)
            img[sl] = np.dstack([np.clip(col, 0, 255), np.full((ph, pw), 255)])
            if rim:
                e = max(1, D // 3)
                img[b + ph - e:b + ph, a:a + pw, :3] *= 0.7; img[b:b + ph, a + pw - e:a + pw, :3] *= 0.75
                img[b:b + e, a:a + pw, :3] *= 0.85; img[b:b + ph, a:a + e, :3] *= 0.85
            elif m in ("plate", "platecore"):
                e = max(1, D // 4)
                img[b + ph - e:b + ph, a:a + pw, :3] *= 0.8
    os.makedirs(A + "/textures/entity", exist_ok=True)
    Image.fromarray(img.astype(np.uint8), "RGBA").save(A + "/textures/entity/showa_godzilla.png", optimize=True)
    Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8), "RGBA").save(A + "/textures/entity/showa_godzilla_glow.png", optimize=True)


# ---------------- spawn egg: a detailed 64x64 egg ----------------
# A rounder charcoal egg with knobbly bumps, a big smooth tan belly patch, bright white ROUNDED plates up the middle,
# and a glowing blue-white jet streak swooshing round the bottom (his rocket flight). Different from Gojira's cracked egg.
def write_egg():
    EGG = 64
    rng = np.random.default_rng(1971)
    yy, xx = np.mgrid[0:EGG, 0:EGG].astype(float) + 0.5
    cx_, cy_ = 32.0, 34.5
    ry = np.where(yy < cy_, 28.5, 27.0)
    rx = 24.0 * (1 - 0.08 * np.clip((cy_ - yy) / 28.5, 0, 1))
    ex, ey = (xx - cx_) / rx, (yy - cy_) / ry
    r2 = ex * ex + ey * ey
    inside = r2 <= 1.0
    nz = np.sqrt(np.clip(1 - r2, 0, 1))
    L = np.array([-0.55, -0.6, 0.58]); L /= np.linalg.norm(L)
    light = 0.45 + 0.72 * np.clip(ex * L[0] + ey * L[1] + nz * L[2], 0, 1)
    # knobbly bumps: jittered round nodules
    f1, edge = cellular(EGG, EGG, 5, 7)
    knob = (1 - np.clip(f1, 0, 1)) ** 1.3
    val = 70 + 38 * knob + rng.normal(0, 2.5, (EGG, EGG)) - 22 * np.clip(1 - edge / 0.18, 0, 1)
    rgb = np.stack([val * 0.96, val * 1.04, val * 1.0], -1)
    # belly patch (lower front), smooth tan with soft ridges
    belly = ((xx - 30) / 12.5) ** 2 + ((yy - 44) / 13.0) ** 2 < 1
    ridge = (yy % 4) < 1
    bv = 150 - 14 * ridge + rng.normal(0, 2, (EGG, EGG))
    rgb = np.where(belly[..., None], np.stack([bv * 1.05, bv, bv * 0.88], -1), rgb)
    rgb = rgb * light[..., None]
    rgb += (np.exp(-(((xx - 21) / 6.0) ** 2 + ((yy - 16) / 7.5) ** 2)) * 75)[..., None]
    rgb += (np.clip((r2 - 0.72) / 0.28, 0, 1) * np.clip(ex, 0, 1) * 50)[..., None] * np.array([0.6, 0.8, 1.0])
    # rounded white plates up the middle (and two smaller side rows)
    mask = np.zeros((EGG, EGG), bool); pc = np.zeros((EGG, EGG, 3))
    for (by, hw, h) in [(40, 3.0, 6.0), (31, 4.0, 8.0), (21, 4.2, 8.5), (12, 3.2, 6.5)]:
        for dxo, s in ((0.0, 1.0), (-9.0, 0.6), (9.0, 0.6)):
            cxs = cx_ + dxo * (1 - abs(by - cy_) / 45)
            t = (by + (2 if dxo else 0) - yy) / (h * s)
            half = hw * s * np.sqrt(np.clip(1 - t * t, 0, 1)) * (1.15 - 0.3 * t)   # rounded leaf
            m = (t >= -0.15) & (t <= 1) & (np.abs(xx - cxs) <= half) & inside
            side = (xx - cxs) / np.maximum(half, 0.5)
            v = 250 - 40 * np.clip(side, 0, 1) - 20 * np.clip(-t, 0, 1) * 3
            c = np.stack([v * 0.98, v * 0.99, np.minimum(255, v * 1.02)], -1) * (0.8 + 0.2 * light[..., None])
            pc = np.where(m[..., None] & ~mask[..., None], c, pc); mask |= m
    pad = np.pad(mask, 1)
    outline = (pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]) & ~mask & inside
    # plate detail: fine vertical striations, a cream base and a bright lip just inside the edge
    stri = np.where((xx.astype(int) % 2) == 0, -10, 4)
    pc = pc + stri[..., None] * mask[..., None]
    inner = mask & ~((pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:]))
    pc = np.where(inner[..., None], pc * 0.82, pc)
    rgb = np.where(mask[..., None], pc, rgb)
    rgb = np.where(outline[..., None], rgb * 0.45, rgb)
    # knob highlights: a bright pixel on the upper-left of each skin knob, a dark pixel below it
    kh = (knob > 0.8) & inside & ~mask & ~belly
    ks = np.roll(kh, 1, 0) & ~kh & inside & ~mask & ~belly
    rgb = np.where(kh[..., None], np.minimum(255, rgb * 1.35 + 10), rgb)
    rgb = np.where(ks[..., None], rgb * 0.7, rgb)
    # belly segment lines (dark) with a light edge, and tiny pits
    seg = belly & ((yy % 4) < 1)
    rgb = np.where(seg[..., None], rgb * 0.62, rgb)
    rgb = np.where((belly & (((yy - 1) % 4) < 1))[..., None], np.minimum(255, rgb * 1.12), rgb)
    pits = belly & (rng.random((EGG, EGG)) < 0.05)
    rgb = np.where(pits[..., None], rgb * 0.8, rgb)
    # a few twinkly sparkles
    for (sx, sy) in ((14, 22), (49, 30), (45, 15), (18, 47)):
        if inside[sy, sx] and not mask[sy, sx]:
            rgb[sy, sx] = (255, 255, 240)
            for ddx, ddy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                rgb[sy + ddy, sx + ddx] = np.minimum(255, rgb[sy + ddy, sx + ddx] * 0.4 + np.array([150, 160, 150]))
    # blue-white jet streak curving round the lower egg (a hint of his rocket flight)
    streak = np.zeros((EGG, EGG))
    for i in range(400):
        tt = i / 399
        px, py = 8 + 48 * tt, 50 - 10 * math.sin(tt * math.pi) + 6 * tt
        rr = 0.6 + 2.2 * tt
        streak = np.maximum(streak, np.clip(1 - np.hypot(xx - px, yy - py) / rr, 0, 1))
    streak *= inside & ~mask
    halo = streak.copy()
    for _ in range(3):
        halo = (halo + np.roll(halo, 1, 0) + np.roll(halo, -1, 0) + np.roll(halo, 1, 1) + np.roll(halo, -1, 1)) / 5 * 1.5
    halo = np.clip(halo, 0, 1) * inside
    rgb = rgb * (1 - 0.5 * halo[..., None]) + halo[..., None] * np.array([100, 180, 255]) * 0.6
    rgb = np.where((streak > 0.5)[..., None], np.array([236, 248, 255]), rgb)
    # two little friendly eyes peeking near the top (white with dark pupils)
    for ex0 in (25.5, 38.5):
        e = np.hypot(xx - ex0, yy - 9.5) < 2.6
        p_ = np.hypot(xx - ex0, yy - 10.0) < 1.4
        rgb = np.where(e[..., None], np.array([245, 245, 238]), rgb)
        rgb = np.where(p_[..., None], np.array([20, 16, 12]), rgb)
        rgb[int(8.5), int(ex0 - 1)] = (255, 255, 255)
    pad = np.pad(inside, 1)
    edge_ = inside & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    rgb = np.where(edge_[..., None], np.array([16, 20, 18]), rgb)
    rgb = np.where((inside & (ey > 0.8) & ~edge_)[..., None], rgb * 0.82, rgb)
    alpha = np.where(inside, 255, 0)
    os.makedirs(A + "/textures/item", exist_ok=True)
    Image.fromarray(np.dstack([np.clip(rgb, 0, 255), alpha]).astype(np.uint8), "RGBA").save(A + "/textures/item/showa_godzilla_spawn_egg.png")

if __name__ == "__main__":
    write_all()
