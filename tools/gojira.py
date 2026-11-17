"""Gojira Mod assets, all generated from data in this file (no copied art or audio).
usage (from the repo root): python3 tools/gojira.py      needs numpy, Pillow, ffmpeg with libvorbis

Writes:
  src/client/java/com/gojiramod/client/GojiraModelLayer.java   the model's parts and boxes (generated, do not edit by hand)
  src/main/java/com/gojiramod/GojiraDimensions.java           scale, mouth/feet positions, reach (generated)
  textures/entity/gojira.png        hi-res bumpy charcoal skin, belly ridges, pale jagged dorsal plates, teeth, claws
  textures/entity/gojira_glow.png   the dorsal plates + mouth glow layer (shown while the white beam charges/fires)
  textures/item/gojira_spawn_egg.png (64x64 detailed egg), icon.png
  sounds/entity/gojira_roar.ogg     an original synthesized "skreeonk" style roar

0.1.3 "super detailed" model: an original, blocky take on a classic upright kaiju: thick thighs, shins and clawed toes,
short strong arms with fingers and claws, a big rounded head with a heavy brow, cheeks, eyes, nostrils, an opening jaw
with two rows of teeth, a thick neck and chest, a long six-part tapering tail, and three rows of big jagged
maple-leaf-shaped dorsal plates (each plate is built from six boxes). The texture is drawn at TEX_DENSITY pixels per
model unit (a hi-res texture on the same UV layout), with bumpy cellular scales, crevices and fake ambient occlusion."""
import math, os, random, subprocess, wave
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = ROOT + "/src/main/resources/assets/gojiramod"
JAVA = ROOT + "/src/client/java/com/gojiramod/client/GojiraModelLayer.java"
DIMS_JAVA = ROOT + "/src/main/java/com/gojiramod/GojiraDimensions.java"
TARGET_HEIGHT_BLOCKS = 164.0 / 3.0  # 0.1.2: the kids want 54.6667 blocks (164/3), up from 50; 1 block = 1 m
TEX_W, TEX_H = 256, 128  # texture size in model units (what the model's UVs use)
TEX_DENSITY = 4          # pixels per model unit in the PNG (so the PNG is 1024x512: 4x the detail)
rnd = random.Random(1954)

# ---------------- model: parts as data ----------------
# part: (name, parent, pivot(x,y,z), rot(x,y,z), [boxes], mirror)   box: (material, x, y, z, w, h, d)
# Model space is Minecraft's: y down, the face looks toward -z. The whole model is moved so its feet touch y = 24.
PARTS = []

def part(name, parent, pivot, rot, boxes, mirror=False):
    PARTS.append((name, parent, tuple(float(v) for v in pivot), tuple(float(v) for v in rot), boxes, mirror))

def mirror_x(boxes):
    return [(m, -(x + w), y, z, w, h, d) for (m, x, y, z, w, h, d) in boxes]

def sided(name, parent, pivot, rot, boxes):
    """A right-side part plus its mirrored left-side twin (right is -x in model space)."""
    part("right_" + name, parent if parent is None else ("right_" + parent if parent in SIDED else parent), pivot, rot, boxes)
    lp = (-pivot[0], pivot[1], pivot[2])
    lparent = parent if parent is None else ("left_" + parent if parent in SIDED else parent)
    part("left_" + name, lparent, lp, (rot[0], -rot[1], -rot[2]), mirror_x(boxes), True)
    SIDED.add(name)

SIDED = set()
R = lambda v: round(v * 2) / 2  # half-unit sizes keep the UV layout tidy

# legs: thigh -> shin -> foot with three toes and claws
sided("leg", None, (-6.5, 4, 3), (-0.22, 0, 0.0), [("skin", -5, -2, -5.5, 10, 11, 11), ("skin", -4, 8, -4.5, 8, 2, 9)])
sided("shin", "leg", (0, 9.5, -0.5), (0.42, 0, 0), [("skin", -4, 0, -4, 8, 9, 8), ("skin", -4.5, 1, 1.5, 9, 5, 3)])
sided("foot", "shin", (0, 8.5, 0), (-0.2, 0, 0), [("skin", -5, 0, -7, 10, 3, 11),
    ("skin", -5, 0.5, -10, 3, 2.5, 3), ("skin", -1.5, 0.5, -10.5, 3, 2.5, 3.5), ("skin", 2, 0.5, -10, 3, 2.5, 3),
    ("claw", -4.5, 1.5, -11.5, 2, 1.5, 1.5), ("claw", -1, 1.5, -12, 2, 1.5, 1.5), ("claw", 2.5, 1.5, -11.5, 2, 1.5, 1.5),
    ("skin", -4, 0.5, 4, 8, 2.5, 2)])
# hips, body, chest
part("hips", None, (0, 4, 3), (0, 0, 0), [("skin", -8, -3, -6, 16, 6, 13)])
part("body", "hips", (0, 0, 0), (0.18, 0, 0), [
    ("skin", -9, -22, -8, 18, 24, 16),            # torso
    ("belly", -7.5, -20, -9.5, 15, 13, 2),        # chest plates
    ("belly", -6.5, -7, -9, 13, 8, 1.5),          # belly
    ("skin", -10.5, -21, -6, 3, 7, 11), ("skin", 7.5, -21, -6, 3, 7, 11),   # shoulders
    ("skin", -9.5, -12, -7, 2, 10, 13), ("skin", 7.5, -12, -7, 2, 10, 13),  # flanks
])
part("neck", "body", (0, -21, -2), (-0.22, 0, 0), [("skin", -7, -9, -7, 14, 10, 13), ("belly", -5.5, -8, -8.5, 11, 8, 2)])
part("head", "neck", (0, -8, -3), (0.1, 0, 0), [
    ("skin", -6.5, -7, -9, 13, 8, 13),            # skull (big and rounded)
    ("skin", -5.5, -8.5, -7, 11, 2, 9),           # crown
    ("skin", -7, -7.5, -10.5, 14, 2.5, 4),        # heavy brow
    ("skin", -7.5, -4.5, -8, 15, 4, 7),           # cheeks
    ("snout", -5, -6, -16, 10, 5, 7),             # snout
    ("skin", -3, -7, -15, 6, 1, 6),               # snout ridge
    ("nostril", -3.5, -6.5, -16.5, 1.5, 1, 1), ("nostril", 2, -6.5, -16.5, 1.5, 1, 1),
    ("eye", -7.2, -5.6, -10, 1, 1.5, 2), ("eye", 6.2, -5.6, -10, 1, 1.5, 2),
] + [("tooth", -4.6, -1, -15.5 + 1.6 * i, 1, 1.5, 1) for i in range(5)] + [("tooth", 3.6, -1, -15.5 + 1.6 * i, 1, 1.5, 1) for i in range(5)]
  + [("tooth", -2.5 + 1.7 * i, -1, -15.8, 1, 1.5, 1) for i in range(3)])
part("jaw", "head", (0, -1, -2), (0, 0, 0), [("jaw", -4.5, 0, -14, 9, 3, 13), ("skin", -4, 2, -14.5, 8, 2, 4), ("skin", -5.5, 0, -4, 11, 3.5, 5)]
  + [("tooth", -4.2, -1.2, -13 + 1.7 * i, 1, 1.2, 1) for i in range(5)] + [("tooth", 3.2, -1.2, -13 + 1.7 * i, 1, 1.2, 1) for i in range(5)])
# short strong arms with four fingers and claws
sided("arm", "body", (-10, -16, -6), (-0.6, 0, 0.28), [("skin", -2.5, -1.5, -2.5, 5, 8, 5)])
sided("forearm", "arm", (0, 6, 0), (-0.75, 0, 0), [("skin", -2, 0, -2, 4, 6, 4)])
sided("hand", "forearm", (0, 5.5, 0), (0, 0, 0), [("skin", -2.5, 0, -2, 5, 2.5, 3.5)]
      + [("skin", -2.4 + 1.25 * i, 2.5, -1.8, 1, 2.5, 1) for i in range(4)]
      + [("claw", -2.4 + 1.25 * i, 5, -1.8, 1, 1.2, 1) for i in range(4)])
# long, thick, tapering tail
TAIL = [(13, 11, 12, -0.25), (11, 9, 11, -0.1), (9, 7.5, 11, -0.06), (7.5, 6, 10, -0.03), (5.5, 4.5, 9, 0.06), (3.5, 3, 8, 0.12)]
prev, z = "hips", 9
for i, (w, h, L, rx) in enumerate(TAIL):
    name = f"tail{i + 1}"
    part(name, prev, (0, -1 if i == 0 else 0.4, z), (rx, 0, 0), [("skin", -w / 2, -h / 2, -1, w, h, L + 1), ("belly", -w / 2 + 1, h / 2, 0, w - 2, 0.5, L)])
    prev, z = name, L

# jagged maple-leaf dorsal plates: a stepped, pointed plate (base, middle, upper, tip) with two jagged side points, six
# boxes each; local "up" is -y and the plate runs along z
PLATE_SIZES = {"L": 1.15, "M": 0.9, "S": 0.7, "XS": 0.52, "XXS": 0.38}
def plate_boxes(size):
    s = PLATE_SIZES[size]
    t = lambda v: max(0.5, R(v))
    return [("plate", -1, -t(3 * s), -t(3 * s), 2, t(3 * s), 2 * t(3 * s)),                  # base
            ("plate", -0.9, -t(6 * s), -t(2.25 * s), 1.8, t(3 * s), 2 * t(2.25 * s)),        # middle
            ("plate", -0.8, -t(8.5 * s), -t(1.5 * s), 1.6, t(2.5 * s), 2 * t(1.5 * s)),      # upper
            ("plate", -0.7, -t(10.5 * s), -t(0.75 * s), 1.4, t(2 * s), max(0.5, 2 * t(0.75 * s))),  # tip
            ("plate", -0.75, -t(5.5 * s), -t(4.25 * s), 1.5, t(1.5 * s), t(1.75 * s)),       # front point
            ("plate", -0.75, -t(4.5 * s), t(2.5 * s), 1.5, t(1.5 * s), t(1.75 * s))]         # back point
PLATES = 0
def plate(parent, pos, size, rot):
    global PLATES
    PLATES += 1
    part(f"plate{PLATES}", parent, pos, rot, plate_boxes(size))
UP_BACK = -1.35  # turns a plate's "up" to point backwards (and a bit up) off an upright body
for y, size in ((-20, "L"), (-14, "L"), (-8.5, "M"), (-3, "S")):
    plate("body", (0, y, 8), size, (UP_BACK, 0, 0))
for y, size in ((-18, "S"), (-12, "S"), (-6.5, "XS"), (-1.5, "XS")):
    plate("body", (-4.5, y, 7.5), size, (UP_BACK, 0, 0.32)); plate("body", (4.5, y, 7.5), size, (UP_BACK, 0, -0.32))
for y, size in ((-7.5, "M"), (-2.5, "S")):
    plate("neck", (0, y, 6), size, (UP_BACK, 0, 0))
plate("neck", (-4, -5.5, 5.5), "XS", (UP_BACK, 0, 0.32)); plate("neck", (4, -5.5, 5.5), "XS", (UP_BACK, 0, -0.32))
plate("head", (0, -8, 2.5), "XXS", (UP_BACK + 0.3, 0, 0))
TAIL_PLATES = [(("M", 2), ("S", 8)), (("S", 2), ("S", 7.5)), (("S", 2), ("XS", 7.5)), (("XS", 2), ("XS", 6.5)), (("XXS", 3),), (("XXS", 3),)]
TAIL_SIDES = [("XS", (3, 8.5)), ("XXS", (3, 8)), ("XXS", (4,)), (None, ()), (None, ()), (None, ())]
for i, plates in enumerate(TAIL_PLATES):
    w, h, L, _ = TAIL[i]
    for size, zz in plates:
        plate(f"tail{i + 1}", (0, -h / 2, zz), size, (-0.35, 0, 0))
    size, zs = TAIL_SIDES[i]
    for zz in zs:
        plate(f"tail{i + 1}", (-w / 2 + 1.5, -h / 2 + 0.5, zz), size, (-0.35, 0, 0.35))
        plate(f"tail{i + 1}", (w / 2 - 1.5, -h / 2 + 0.5, zz), size, (-0.35, 0, -0.35))

# ---------------- geometry: transforms, auto-grounding, height and mouth position ----------------
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
shift = 24.0 - ground  # put the lowest point of the feet exactly on the ground
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
mouth = to_model("head", (0, -1, -16.5))
front = -min(p[2] for _, _, p in pts) * blocks
tail_tip = max(p[2] for _, _, p in pts) * blocks
half_w = max(abs(p[0]) for _, _, p in pts) * blocks
feet = [p for n, _, p in pts if "foot" in n]
feet_reach = max(math.hypot(p[0], p[2]) for p in feet) * blocks
reach = max(front, tail_tip, half_w)
cubes = sum(len(b) for (_, _, _, _, b, _) in PARTS)
print(f"{len(PARTS)} parts, {cubes} cubes ({PLATES} dorsal plates)")
print(f"model height {height_px:.2f} px -> VISUAL_SCALE = {scale:.4f}")
print(f"MOUTH_UP = {(24 - mouth[1]) * blocks:.2f}, MOUTH_FORWARD = {-mouth[2] * blocks:.2f}, FEET_REACH = {feet_reach:.2f}")
print(f"visual extents (blocks): front {front:.1f}, tail tip {tail_tip:.1f}, half width {half_w:.1f}")

# ---------------- UV packing (one region per material+size) ----------------
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
print(f"UV layout uses {cy + shelf} of {TEX_H} rows; PNG is {TEX_W * TEX_DENSITY}x{TEX_H * TEX_DENSITY}")

# ---------------- generated Java ----------------
def f(v):
    return f"{float(v)}F"
lines = []
for name, parent, piv, r, boxes, mirror in PARTS:
    cb = "CubeListBuilder.create()"
    for (m, x, y, z, w, h, d) in boxes:
        u, v = UV[key(m, w, h, d)]
        cb += f"\n\t\t\t\t.texOffs({u}, {v})" + (".mirror()" if mirror else "") + f".addBox({f(x)}, {f(y)}, {f(z)}, {f(w)}, {f(h)}, {f(d)})"
    pose = f"PartPose.offsetAndRotation({f(piv[0])}, {f(piv[1])}, {f(piv[2])}, {f(r[0])}, {f(r[1])}, {f(r[2])})"
    par = "root" if parent is None else parent
    lines.append(f"\t\tPartDefinition {name} = {par}.addOrReplaceChild(\"{name}\", {cb},\n\t\t\t\t{pose});")
java = f"""package com.gojiramod.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by tools/gojira.py: {len(PARTS)} parts, {cubes} cubes ({PLATES} jagged dorsal plates), UVs packed for a
 * {TEX_W}x{TEX_H} layout (the PNG is drawn {TEX_DENSITY}x bigger for detail). Edit the data there, not here.
 */
@SuppressWarnings("unused")
final class GojiraModelLayer {{
	private GojiraModelLayer() {{
	}}

	static LayerDefinition create() {{
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
{chr(10).join(lines)}
		return LayerDefinition.create(mesh, {TEX_W}, {TEX_H});
	}}
}}
"""
os.makedirs(os.path.dirname(JAVA), exist_ok=True)
open(JAVA, "w").write(java)
body_half_w = max(max(abs(bx[1]), abs(bx[1] + bx[4])) for n_, _, _, _, bxs, _ in PARTS if n_ == "body" for bx in bxs) * blocks
open(DIMS_JAVA, "w").write(f"""package com.gojiramod;

/** GENERATED by tools/gojira.py from the model data: Gojira's size and where its (visual) mouth and feet are. */
public final class GojiraDimensions {{
	/** Height Gojira is drawn at, in blocks (164/3, the kids' number). */
	public static final float VISUAL_HEIGHT = {TARGET_HEIGHT_BLOCKS!r}F;
	/** Unscaled model height in model pixels. */
	public static final float MODEL_HEIGHT_PX = {height_px:.4f}F;
	/** How much the renderer scales the model up. */
	public static final float VISUAL_SCALE = {scale:.4f}F;
	/** The beam starts here: this many blocks above its feet and in front of its body. */
	public static final double MOUTH_UP = {(24 - mouth[1]) * blocks:.3f}, MOUTH_FORWARD = {-mouth[2] * blocks:.3f};
	/** The farthest point of its feet/toes from its center, in blocks. */
	public static final double FEET_REACH = {feet_reach:.3f};
	/** How far the drawn model reaches from its center: snout, tail tip, half width (blocks). */
	public static final double FRONT_REACH = {front:.3f}, TAIL_REACH = {tail_tip:.3f}, HALF_WIDTH = {half_w:.3f};
	/** Half the width of its chest, in blocks: how high its body rests when it lies on its side (death animation). */
	public static final double BODY_HALF_WIDTH = {body_half_w:.3f};
	/** Number of model parts and cubes (for the tests). */
	public static final int PARTS = {len(PARTS)}, CUBES = {cubes}, DORSAL_PLATES = {PLATES};

	private GojiraDimensions() {{
	}}
}}
""")

# ---------------- textures (hi-res) ----------------
D = TEX_DENSITY
IW, IH = TEX_W * D, TEX_H * D
g = np.random.default_rng(1954)
# cellular "bumpy scales": distance to jittered seed points -> rounded nodules with dark crevices between them
def cellular(cell, seed):
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
f1a, edgea = cellular(7, 1)      # big scales (about 1.75 model units)
f1b, edgeb = cellular(3, 2)      # small pebbly bumps
bump = (1 - np.clip(f1a, 0, 1)) * 0.7 + (1 - np.clip(f1b, 0, 1)) * 0.3
gy_, gx_ = np.gradient(bump)
lit = np.clip(0.5 - (gx_ * -0.8 + gy_ * -1.0) * 6.0, 0, 1)  # light from the upper left
crevice = np.clip(1 - edgea / 0.12, 0, 1) * 0.55 + np.clip(1 - edgeb / 0.1, 0, 1) * 0.2
skin_v = 46 + 34 * lit * bump + g.normal(0, 3, (IH, IW)) - 30 * crevice
img = np.zeros((IH, IW, 4)); glow = np.zeros((IH, IW, 4))

def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}
def px_rect(x0, y0, fw, fh):
    a, b = int(round(x0 * D)), int(round(y0 * D))
    return a, b, max(1, int(round((x0 + fw) * D)) - a), max(1, int(round((y0 + fh) * D)) - b)

BELLY_FRONT = {"belly"}
for k, (m, w, h, d) in sizes.items():
    u, v = UV[k]
    for fname, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
        a, b, pw, ph = px_rect(fx, fy, fw, fh)
        ys = (np.arange(ph)[:, None] + 0.5) / ph * np.ones((1, pw))      # 0 top .. 1 bottom of the face
        xs = np.ones((ph, 1)) * (np.arange(pw)[None, :] + 0.5) / pw
        sl = (slice(b, b + ph), slice(a, a + pw))
        if m in ("skin", "snout", "jaw"):
            val = skin_v[sl].copy()
            if fname == "top":
                val += 8
            elif fname == "bottom":
                val -= 14
            else:
                val *= 1.05 - 0.28 * ys ** 1.5                              # fake ambient occlusion toward the bottom
            col = np.stack([val, val * 1.01, val * 1.06], -1)
            if m == "snout" and fname == "bottom" or m == "jaw" and fname == "top":
                mouth_c = np.stack([np.full_like(val, 70), np.full_like(val, 38), np.full_like(val, 42)], -1) * (0.8 + 0.2 * bump[sl][..., None])
                col = mouth_c
                glow[sl] = np.dstack([np.full((ph, pw), 230), np.full((ph, pw), 244), np.full((ph, pw), 255), np.full((ph, pw), 230)])
        elif m == "belly":
            ridge = ((ys * fh * 1.0) % 2.0) < 0.35                           # horizontal belly ridges every 2 units
            val = 64 + 18 * lit[sl] * bump[sl] - 22 * ridge + g.normal(0, 2.5, (ph, pw))
            col = np.stack([val * 1.04, val * 1.01, val * 0.97], -1)
        elif m == "plate":
            # pale bone plates: vertical striations, darker toward the base, greyer edges
            stri = np.sin((xs * fw + 0.3 * np.sin(ys * 9)) * 7.0) * 6
            val = 226 + stri - 70 * np.clip(ys - 0.65, 0, 1) ** 1.2 * (fname not in ("top", "bottom")) + g.normal(0, 4, (ph, pw)) - 12 * crevice[sl]
            val -= 46 * ((xs < 0.07) | (xs > 0.93) | (ys < 0.05))
            col = np.stack([val * 0.99, val, val * 1.02], -1)
            gl = np.clip(1.15 - 0.6 * ys, 0.55, 1.0) * (fname not in ("bottom",))
            glow[sl] = np.dstack([np.full((ph, pw), 232), np.full((ph, pw), 245), np.full((ph, pw), 255), 255 * gl])
        elif m == "tooth":
            val = 232 - 30 * ys + g.normal(0, 3, (ph, pw)); col = np.stack([val, val * 0.98, val * 0.9], -1)
        elif m == "claw":
            val = 205 - 70 * ys + g.normal(0, 4, (ph, pw)); col = np.stack([val, val * 0.97, val * 0.9], -1)
        elif m == "nostril":
            col = np.full((ph, pw, 3), 20.0)
        elif m == "eye":
            r2 = ((xs - 0.5) * 2) ** 2 + ((ys - 0.5) * 2) ** 2
            col = np.where((np.abs(xs - 0.5) < 0.16)[..., None], np.array([18, 14, 10.0]), np.array([236, 176, 52.0]) * (1 - 0.35 * r2[..., None]))
        img[sl] = np.dstack([np.clip(col, 0, 255), np.full((ph, pw), 255)])
        # thin darker outline on every face so the blocky shapes read clearly
        e = max(1, D // 3)
        img[b:b + e, a:a + pw, :3] *= 0.7; img[b + ph - e:b + ph, a:a + pw, :3] *= 0.7
        img[b:b + ph, a:a + e, :3] *= 0.7; img[b:b + ph, a + pw - e:a + pw, :3] *= 0.7
os.makedirs(A + "/textures/entity", exist_ok=True)
Image.fromarray(img.astype(np.uint8), "RGBA").save(A + "/textures/entity/gojira.png", optimize=True)
Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8), "RGBA").save(A + "/textures/entity/gojira_glow.png", optimize=True)

# ---------------- spawn egg: a "super detailed" 64x64 hand-painted-style egg (June and Leon's request) ----------------
# Dark grey scaly egg with real scale rows and 3D shading, pale dorsal spikes running up the middle, glowing white-blue
# cracks with a soft halo, a highlight on the upper left, a cool rim light on the right and a dark outline.
# 64x64 is still a clean power of two, so it works in the item atlas and with mipmaps; item/generated renders it as-is
# (no vanilla tinted spawn-egg template).
EGG = 64
erng = np.random.default_rng(1954)
yy, xx = np.mgrid[0:EGG, 0:EGG].astype(float) + 0.5
cx_, cy_ = 32.0, 34.0
ry = np.where(yy < cy_, 29.5, 27.0)                  # pointier top, rounder bottom
rx = 22.5 * (1 - 0.10 * np.clip((cy_ - yy) / 29.5, 0, 1))
ex, ey = (xx - cx_) / rx, (yy - cy_) / ry
r2 = ex * ex + ey * ey
inside = r2 <= 1.0
nz = np.sqrt(np.clip(1 - r2, 0, 1))                  # a sphere-ish normal for lighting
L = np.array([-0.55, -0.6, 0.58]); L /= np.linalg.norm(L)
diffuse = np.clip(ex * L[0] + ey * L[1] + nz * L[2], 0, 1)
light = 0.42 + 0.75 * diffuse
# scales: offset rows of rounded scales, each brighter at its top and dark along its lower rim
row_h, col_w = 5.0, 6.0
row = np.floor(yy / row_h)
off = (row % 2) * col_w / 2
col = np.floor((xx + off) / col_w)
sx = ((xx + off) % col_w) / col_w - 0.5              # -0.5..0.5 across a scale
sy = (yy % row_h) / row_h                            # 0 top .. 1 bottom of the scale row
scale_edge = (sy + 1.6 * sx * sx) > 0.92             # curved lower rim of each scale
scale_shade = 1.12 - 0.30 * sy - 0.15 * np.abs(sx)
jitter = erng.normal(0, 1, (int(EGG / row_h) + 2, int(EGG / col_w) + 3))
scale_tone = jitter[row.astype(int) % jitter.shape[0], col.astype(int) % jitter.shape[1]] * 5
base = 84 + scale_tone + erng.normal(0, 2.5, (EGG, EGG))
val = base * scale_shade
val = np.where(scale_edge, val * 0.5, val)
val = np.where((sy < 0.2) & ~scale_edge & (np.abs(sx) < 0.3), val * 1.18, val)  # glint on each scale's top
rgb = np.stack([val * 0.97, val * 1.0, val * 1.07], -1) * light[..., None]
# specular highlight (upper left) and cool rim light (right edge)
spec = np.exp(-(((xx - 21) / 6.5) ** 2 + ((yy - 17) / 8.0) ** 2)) * 70
rim = np.clip((r2 - 0.72) / 0.28, 0, 1) * np.clip(ex, 0, 1) * 55
rgb += spec[..., None] + rim[..., None] * np.array([0.55, 0.75, 1.0])
# dorsal spikes: pale bone plates running up the middle of the egg, bigger in the middle, each with shading + outline
spike_mask = np.zeros((EGG, EGG), bool)
spike_col = np.zeros((EGG, EGG, 3))
spikes = [(54, 2.5, 5.0), (45, 3.5, 7.0), (35, 4.0, 8.0), (25, 3.8, 8.0), (15, 3.0, 6.5)]  # (base y, half width, height)
for (by, hw, h) in spikes:
    for k, dxo in ((0, 0.0), (1, -8.0), (2, 8.0)):     # center row plus two smaller side rows
        s = 1.0 if k == 0 else 0.6
        cxs = cx_ + dxo * (1 - abs(by - cy_) / 40)
        bys, hws, hs = by + (2 if k else 0), hw * s, h * s
        t = (bys - yy) / hs                             # 0 at base .. 1 at tip
        half = hws * np.clip(1 - t, 0, 1) ** 0.85 * (1 + 0.18 * np.sin(t * 9))  # a slightly jagged plate edge
        m = (t >= 0) & (t <= 1) & (np.abs(xx - cxs) <= half) & inside
        # pale bone: lit on the left, cooler shadow on the right, darker toward the base
        side = (xx - cxs) / np.maximum(half, 0.5)
        v = 236 - 34 * np.clip(side, 0, 1) + 10 * np.clip(-side, 0, 1) - 28 * (1 - t) ** 3
        c = np.stack([v * 0.98, v * 0.99, v * 1.02], -1) * (0.75 + 0.25 * light[..., None])
        spike_col = np.where(m[..., None] & ~spike_mask[..., None], c, spike_col)
        spike_mask |= m
# spike outlines (dark pixels just outside each plate) for a crisp, hand-drawn look
pad = np.pad(spike_mask, 1)
neigh = pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]
spike_outline = neigh & ~spike_mask & inside
rgb = np.where(spike_mask[..., None], spike_col, rgb)
rgb = np.where(spike_outline[..., None], rgb * 0.45, rgb)
# glowing white-blue cracks: jagged lines branching across the shell, with a soft blue halo
crack = np.zeros((EGG, EGG))
def crack_line(points):
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        n = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for i in range(n + 1):
            px, py = x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n
            crack[int(py), int(px)] = 1.0
crack_line([(14, 30), (18, 33), (17, 37), (22, 41), (21, 46), (25, 50)])
crack_line([(18, 33), (13, 38), (11, 43)])
crack_line([(48, 24), (45, 28), (47, 32), (43, 37), (45, 42)])
crack_line([(47, 32), (51, 35)])
crack_line([(38, 54), (42, 57), (46, 55)])
crack *= inside & ~spike_mask
halo = crack.copy()
for _ in range(3):                                   # cheap blur for the glow halo
    halo = (halo + np.roll(halo, 1, 0) + np.roll(halo, -1, 0) + np.roll(halo, 1, 1) + np.roll(halo, -1, 1)) / 5 * 1.6
halo = np.clip(halo, 0, 1) * inside
rgb = rgb * (1 - 0.6 * halo[..., None]) + halo[..., None] * np.array([90, 170, 255]) * 0.6
rgb = np.where(crack[..., None] > 0, np.array([235, 248, 255]), rgb)
# glowing spike tips (a hint of the beam)
for (by, hw, h) in spikes[1:5]:
    ty = int(by - h + 1)
    rgb[ty:ty + 2, 31:33] = np.array([200, 232, 255])
# dark outline around the whole egg + a soft ground shadow inside the bottom edge
pad = np.pad(inside, 1)
edge = inside & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
rgb = np.where(edge[..., None], np.array([18, 19, 24]), rgb)
rgb = np.where((inside & (ey > 0.78) & ~edge)[..., None], rgb * 0.8, rgb)
alpha = np.where(inside, 255, 0)
egg = Image.fromarray(np.dstack([np.clip(rgb, 0, 255), alpha]).astype(np.uint8), "RGBA")
os.makedirs(A + "/textures/item", exist_ok=True)
egg.save(A + "/textures/item/gojira_spawn_egg.png")
egg.resize((128, 128), Image.NEAREST).save(A + "/icon.png")

# ---------------- sound: an original "skreeonk" roar ----------------
SR = 44100
g = np.random.default_rng(54)
def seg(dur):
    return np.arange(int(SR * dur)) / SR
# part 1 "SKREE": a harsh, scraping, rising screech (rich sawtooth + gritty roughness + scrape noise)
t1 = seg(1.25)
f1 = 520 + 420 * (1 - np.exp(-t1 * 3.0)) + 18 * np.sin(2 * np.pi * 7 * t1) + 25 * g.normal(0, 1, t1.size).cumsum() / np.sqrt(np.arange(1, t1.size + 1))
# part 2 "ONK": the pitch drops away into a big, honking, nasal bellow that fades
t2 = seg(1.55)
f2 = 230 + 720 * np.exp(-t2 * 6.0) + 6 * np.sin(2 * np.pi * 5 * t2)
f = np.concatenate([f1, f2])
t = np.arange(f.size) / SR
ph = 2 * np.pi * np.cumsum(f) / SR
saw = sum(((-1) ** (k + 1)) * np.sin(k * ph) / k for k in range(1, 26))
rough = 1 + 0.55 * np.sin(2 * np.pi * (48 + 20 * np.sin(2 * np.pi * 0.7 * t)) * t)  # leathery rasp
noise = g.normal(0, 1, f.size)
scrape = np.convolve(noise, np.ones(6) / 6, mode="same") * (1 + np.sin(ph * 2))  # noise that follows the pitch
n1, n2 = t1.size, t2.size
env = np.concatenate([np.minimum(1, t1 / 0.08) * (0.85 + 0.15 * t1 / t1[-1]), np.exp(-t2 * 1.4) * (1 - np.exp(-t2 * 40)) * 0.95 + 0.05 * (t2 < 0.05)])
nasal = np.concatenate([np.full(n1, 0.0), np.ones(n2)])
sig = env * (saw * rough * 0.7 + scrape * (0.35 - 0.2 * nasal)) + env * nasal * 0.5 * np.sin(ph) * np.sin(3 * ph)
sig = np.tanh(sig * 2.2)  # grit
# a little honk formant band for the "onk"
from numpy.fft import rfft, irfft, rfftfreq
spec = rfft(sig); fr = rfftfreq(sig.size, 1 / SR)
spec *= 0.35 + np.exp(-((fr - 750) / 380) ** 2) + 0.7 * np.exp(-((fr - 1500) / 500) ** 2) + 0.25 * (fr < 300)
spec *= np.where(fr > 7000, np.exp(-(fr - 7000) / 2500), 1.0)
sig = irfft(spec, n=sig.size)
edge = int(SR * 0.25); sig[-edge:] *= np.linspace(1, 0, edge) ** 2; sig[:200] *= np.linspace(0, 1, 200)
# huge-creature space: a long, dark reverb tail (convolution with decaying filtered noise)
tail_t = seg(2.2)
ir = g.normal(0, 1, tail_t.size) * np.exp(-tail_t * 2.4)
ir = np.convolve(ir, np.ones(30) / 30, mode="same"); ir[0] = 6.0
wet = np.convolve(np.concatenate([sig, np.zeros(tail_t.size)]), ir)[: sig.size + tail_t.size]
out = 0.65 * np.concatenate([sig, np.zeros(tail_t.size)]) / np.abs(sig).max() + 0.35 * wet / np.abs(wet).max()
out = out / np.abs(out).max() * 0.85
fade = int(SR * 0.4); out[-fade:] *= np.linspace(1, 0, fade)
pcm = (out * 32767).astype(np.int16)
wav = "/tmp/gojira_roar.wav"
with wave.open(wav, "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
os.makedirs(A + "/sounds/entity", exist_ok=True)
subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", A + "/sounds/entity/gojira_roar.ogg"], check=True)
print(f"wrote GojiraModelLayer.java, gojira.png, gojira_glow.png, gojira_spawn_egg.png, icon.png, gojira_roar.ogg ({out.size / SR:.1f} s)")
