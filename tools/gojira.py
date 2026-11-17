"""Gojira Mod assets, all generated from data in this file (no copied art or audio).
usage (from the repo root): python3 tools/gojira.py      needs numpy, Pillow, ffmpeg with libvorbis

Writes:
  src/client/java/com/gojiramod/client/GojiraModelLayer.java   the model's boxes (generated, do not edit by hand)
  textures/entity/gojira.png        dark grey scaly skin, pale dorsal spikes (box UVs packed by this script)
  textures/entity/gojira_glow.png   the spikes + mouth glow layer (shown while the white beam charges/fires)
  textures/item/gojira_spawn_egg.png (64x64 detailed egg), icon.png
  sounds/entity/gojira_roar.ogg     an original synthesized "skreeonk" style roar
and prints the model height and mouth position, which Gojira.java uses (VISUAL_SCALE, MOUTH_UP, MOUTH_FORWARD)."""
import math, os, random, subprocess, wave
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = ROOT + "/src/main/resources/assets/gojiramod"
JAVA = ROOT + "/src/client/java/com/gojiramod/client/GojiraModelLayer.java"
TARGET_HEIGHT_BLOCKS = 50.0  # June: "convert 50 meters into minecraft" -> 1 block = 1 m -> 50 blocks
rnd = random.Random(1954)

# ---------------- model: parts as data ----------------
# part: (name, parent, pivot(x,y,z), rot(x,y,z), [boxes], mirror)   box: (uvkey, x, y, z, w, h, d)
# Model space is Minecraft's: y down, ground at y = 24, the face looks toward -z.
SPIKE = {"s1": (2, 7, 7), "s2": (2, 5, 5), "s3": (2, 4, 4), "s4": (2, 3, 3), "t1": (2, 5, 5), "t2": (2, 4, 4), "t3": (2, 3, 3), "t4": (2, 2, 2)}
PARTS = [
    ("right_leg", None, (-6.5, 6, 3), (0, 0, 0), [("leg", -4, 0, -4.5, 8, 14, 9)], False),
    ("right_foot", "right_leg", (0, 14, 0), (0, 0, 0), [("foot", -5, 0, -8, 10, 4, 12)], False),
    ("left_leg", None, (6.5, 6, 3), (0, 0, 0), [("leg", -4, 0, -4.5, 8, 14, 9)], True),
    ("left_foot", "left_leg", (0, 14, 0), (0, 0, 0), [("foot", -5, 0, -8, 10, 4, 12)], True),
    ("hips", None, (0, 6, 3), (0, 0, 0), [], False),
    ("body", "hips", (0, 0, 0), (0.2, 0, 0), [("body", -9, -24, -8, 18, 28, 16),
        ("s1", -1, -23, 8, *SPIKE["s1"]), ("s1", -1, -16, 8, *SPIKE["s1"]), ("s2", -1, -9, 8, *SPIKE["s2"]), ("s3", -1, -3, 8, *SPIKE["s3"]),
        ("s2", -5, -21, 8, *SPIKE["s2"]), ("s3", -5, -14, 8, *SPIKE["s3"]), ("s3", -5, -8, 8, *SPIKE["s3"]), ("s4", -5, -2, 8, *SPIKE["s4"]),
        ("s2", 3, -21, 8, *SPIKE["s2"]), ("s3", 3, -14, 8, *SPIKE["s3"]), ("s3", 3, -8, 8, *SPIKE["s3"]), ("s4", 3, -2, 8, *SPIKE["s4"])], False),
    ("neck", "body", (0, -23, -3), (-0.25, 0, 0), [("neck", -6, -10, -6, 12, 10, 12),
        ("s2", -1, -10, 6, *SPIKE["s2"]), ("s3", -1, -5, 6, *SPIKE["s3"]), ("s4", -4.5, -8, 5.5, *SPIKE["s4"]), ("s4", 2.5, -8, 5.5, *SPIKE["s4"])], False),
    ("head", "neck", (0, -8, -3), (0.05, 0, 0), [("head", -6, -8, -11, 12, 8, 14), ("brow", -6.5, -8.5, -11.5, 13, 2, 3),
        ("snout", -4.5, -6, -17, 9, 5, 6), ("s4", -1, -10, 0, *SPIKE["s4"])], False),
    ("jaw", "head", (0, -1, 0), (0, 0, 0), [("jaw", -4.5, 0, -16, 9, 3, 14)], False),
    ("right_arm", "body", (-9, -17, -6), (-0.7, 0, 0.2), [("arm", -3, -1, -2, 4, 9, 4)], False),
    ("right_hand", "right_arm", (-1, 8, 0), (-0.4, 0, 0), [("hand", -2.5, 0, -3, 5, 3, 4)], False),
    ("left_arm", "body", (9, -17, -6), (-0.7, 0, -0.2), [("arm", -1, -1, -2, 4, 9, 4)], True),
    ("left_hand", "left_arm", (1, 8, 0), (-0.4, 0, 0), [("hand", -2.5, 0, -3, 5, 3, 4)], True),
    ("tail1", "hips", (0, 0, 9), (-0.25, 0, 0), [("tail1", -6, -5, -2, 12, 10, 14), ("t1", -1, -10, 1, *SPIKE["t1"]), ("t1", -1, -10, 7, *SPIKE["t1"])], False),
    ("tail2", "tail1", (0, 0.5, 12), (-0.1, 0, 0), [("tail2", -4.5, -4, -1, 9, 8, 14), ("t2", -1, -8, 1, *SPIKE["t2"]), ("t2", -1, -8, 7, *SPIKE["t2"])], False),
    ("tail3", "tail2", (0, 0.5, 12), (-0.05, 0, 0), [("tail3", -3, -3, -1, 6, 6, 14), ("t3", -1, -6, 1, *SPIKE["t3"]), ("t3", -1, -6, 7, *SPIKE["t3"])], False),
    ("tail4", "tail3", (0, 0.5, 12), (0.15, 0, 0), [("tail4", -2, -2, -1, 4, 4, 12), ("t4", -1, -4, 2, *SPIKE["t4"]), ("t4", -1, -4, 7, *SPIKE["t4"])], False),
]

# ---------------- UV packing (one region per uv key) ----------------
sizes = {}
for _, _, _, _, boxes, _ in PARTS:
    for (k, x, y, z, w, h, d) in boxes:
        sizes[k] = (w, h, d)
TEX_W, TEX_H = 256, 128
order = sorted(sizes, key=lambda k: -(sizes[k][1] + sizes[k][2]))
UV, cx, cy, shelf = {}, 0, 0, 0
for k in order:
    w, h, d = sizes[k]
    rw, rh = 2 * (w + d), d + h
    rw, rh = math.ceil(rw), math.ceil(rh)
    if cx + rw > TEX_W:
        cx, cy, shelf = 0, cy + shelf, 0
    UV[k] = (cx, cy)
    cx += rw
    shelf = max(shelf, rh)
assert cy + shelf <= TEX_H, "texture too small"

# ---------------- geometry: height and mouth position ----------------
def rot(rx, ry, rz):
    cx_, sx = math.cos(rx), math.sin(rx); cy_, sy = math.cos(ry), math.sin(ry); cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx_, -sx], [0, sx, cx_]]); Ry = np.array([[cy_, 0, sy], [0, 1, 0], [-sy, 0, cy_]]); Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx
WORLD = {}
for name, parent, piv, r, boxes, _ in PARTS:
    R, T = rot(*r), np.array(piv, float)
    if parent:
        PR, PT = WORLD[parent]
        WORLD[name] = (PR @ R, PT + PR @ T)
    else:
        WORLD[name] = (R, T)
def to_model(part, p):
    R, T = WORLD[part]
    return T + R @ np.array(p, float)
minY, minZ, maxZ, maxX = 1e9, 1e9, -1e9, 0
for name, _, _, _, boxes, _ in PARTS:
    for (k, x, y, z, w, h, d) in boxes:
        for dx in (0, w):
            for dy in (0, h):
                for dz in (0, d):
                    p = to_model(name, (x + dx, y + dy, z + dz))
                    minY, minZ, maxZ, maxX = min(minY, p[1]), min(minZ, p[2]), max(maxZ, p[2]), max(maxX, abs(p[0]))
height_px = 24 - minY
scale = TARGET_HEIGHT_BLOCKS / (height_px / 16.0)
mouth = to_model("head", (0, -1, -17))
blocks = scale / 16.0
print(f"model height {height_px:.2f} px -> VISUAL_SCALE = {scale:.3f}")
print(f"MOUTH_UP = {(24 - mouth[1]) * blocks:.2f}, MOUTH_FORWARD = {-mouth[2] * blocks:.2f}")
print(f"visual extents (blocks): front {-minZ * blocks:.1f}, tail tip {maxZ * blocks:.1f}, half width {maxX * blocks:.1f}")

# ---------------- generated Java ----------------
def f(v):
    return f"{float(v)}F"
lines = []
for name, parent, piv, r, boxes, mirror in PARTS:
    cubes = "CubeListBuilder.create()"
    for (k, x, y, z, w, h, d) in boxes:
        u, v = UV[k]
        cubes += f".texOffs({u}, {v})" + (".mirror()" if mirror else "") + f".addBox({f(x)}, {f(y)}, {f(z)}, {f(w)}, {f(h)}, {f(d)})"
    pose = f"PartPose.offsetAndRotation({f(piv[0])}, {f(piv[1])}, {f(piv[2])}, {f(r[0])}, {f(r[1])}, {f(r[2])})"
    par = "root" if parent is None else parent
    lines.append(f"\t\tPartDefinition {name} = {par}.addOrReplaceChild(\"{name}\", {cubes},\n\t\t\t\t{pose});")
java = f"""package com.gojiramod.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** GENERATED by tools/gojira.py (boxes + packed UVs for the {TEX_W}x{TEX_H} texture). Edit the data there, not here. */
@SuppressWarnings("unused")
final class GojiraModelLayer {{
	/** Unscaled model height in pixels; the renderer scales it so Gojira stands {TARGET_HEIGHT_BLOCKS:.0f} blocks tall. */
	static final float MODEL_HEIGHT_PX = {height_px:.3f}F;

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

# ---------------- textures ----------------
def faces(u, v, w, h, d):
    w, h, d = math.ceil(w), math.ceil(h), math.ceil(d)
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}

def skin(x, y):
    r = rnd.random()
    if r < 0.10:
        b = rnd.randint(36, 44)            # deep cracks between the scales
    elif r < 0.22:
        b = rnd.randint(78, 88)            # rough lighter scale tops
    else:
        b = rnd.randint(52, 66)
    # a scaly bump pattern: every 3x3 cell has a lighter upper-left pixel
    if (x % 3 == 0 and y % 3 == 0):
        b += 10
    return (b, b + 1, b + 4, 255)

def belly(x, y):
    b = rnd.randint(70, 82) - (8 if y % 4 == 0 else 0)  # horizontal belly plates
    return (b + 4, b + 2, b, 255)

def bone(x, y, w, h):
    t = y / max(1, h - 1)
    c = (int(232 - 25 * t), int(234 - 20 * t), int(228 - 10 * t))
    if x == 0 or x == w - 1 or y == h - 1:
        c = (c[0] - 40, c[1] - 38, c[2] - 30)
    if rnd.random() < 0.08:
        c = (c[0] - 18, c[1] - 18, c[2] - 15)
    return c + (255,)

img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
glow = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
for k, (w, h, d) in sizes.items():
    u, v = UV[k]
    for fname, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        for yy in range(fh):
            for xx in range(fw):
                if k[0] in "st" and k[1].isdigit():
                    img.putpixel((x0 + xx, y0 + yy), bone(xx, yy, fw, fh))
                    a = 255 if not (xx == 0 or xx == fw - 1) else 170
                    glow.putpixel((x0 + xx, y0 + yy), (235, 246, 255, a))
                elif fname == "front" and k in ("body", "neck", "tail1"):
                    img.putpixel((x0 + xx, y0 + yy), belly(xx, yy) if 2 <= xx < fw - 2 else skin(xx, yy))
                else:
                    img.putpixel((x0 + xx, y0 + yy), skin(xx, yy))

def face_px(k, fname, pts, col, layer=img):
    u, v = UV[k]
    x0, y0, fw, fh = faces(u, v, *sizes[k])[fname]
    for (x, y) in pts:
        layer.putpixel((x0 + x, y0 + y), col)

# head front (12x8): small fierce amber eyes under a heavy brow (eyes sit on the sides near the front)
for side in ("right", "left"):
    w = math.ceil(sizes["head"][2])  # side face width = depth 14; the front of the head is at the low-x end of 'right' and high-x end of 'left'
    ex = 2 if side == "right" else w - 3
    face_px("head", side, [(ex, 2), (ex + (1 if side == "right" else 1), 2)], (232, 170, 50, 255))
    face_px("head", side, [(ex, 3), (ex + 1, 3)], (30, 26, 24, 255))
# mouth: dark inside, a row of white teeth along the snout bottom and jaw top
sw, sd = 9, 6
face_px("snout", "bottom", [(x, y) for x in range(sw) for y in range(sd)], (70, 40, 42, 255))
face_px("snout", "bottom", [(x, y) for x in range(1, sw - 1) for y in range(1, sd)], (240, 246, 255, 255), glow)
face_px("snout", "front", [(x, 4) for x in range(0, sw, 2)], (236, 232, 220, 255))
jw, jd = 9, 14
face_px("jaw", "top", [(x, y) for x in range(jw) for y in range(jd)], (78, 44, 46, 255))
face_px("jaw", "top", [(x, y) for x in range(1, jw - 1) for y in range(0, 8)], (240, 246, 255, 255), glow)
face_px("jaw", "top", [(0, y) for y in range(0, jd, 2)] + [(jw - 1, y) for y in range(0, jd, 2)] + [(x, 0) for x in range(0, jw, 2)], (236, 232, 220, 255))
face_px("snout", "top", [(2, 1), (6, 1)], (34, 32, 34, 255))  # nostrils
# pale claws on the feet and hands
face_px("foot", "front", [(x, y) for x in (1, 2, 4, 5, 7, 8) for y in (2, 3)], (210, 206, 196, 255))
face_px("hand", "front", [(x, 2) for x in (0, 2, 4)], (210, 206, 196, 255))

for p in ("textures/entity",):
    os.makedirs(A + "/" + p, exist_ok=True)
img.save(A + "/textures/entity/gojira.png")
glow.save(A + "/textures/entity/gojira_glow.png")

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
