"""Draws Showa Godzilla preview images without OpenGL (a tiny software renderer over the generated model + texture).
usage: python3 tools/showa_preview.py [out.png]      (run tools/showa.py first)"""
import math, os, sys, importlib.util, io, contextlib
import numpy as np
from PIL import Image, ImageDraw, ImageFont
HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("showa", HERE + "/showa.py"); S = importlib.util.module_from_spec(spec)
with contextlib.redirect_stdout(io.StringIO()): spec.loader.exec_module(S)
TEX = S.A + "/textures/entity/"
tex = Image.open(TEX + "showa_godzilla.png").convert("RGBA"); glw = Image.open(TEX + "showa_godzilla_glow.png").convert("RGBA")
D = S.TEX_DENSITY

def render(yaw, pitch, pose=None, root=(0.0, 0.0), glow=0.0, size=900, bg=(150, 190, 235), extra=None, ground=True, body_tilt=0.0, lift=0.0):
    """yaw/pitch: camera; pose: {part: (dx, dy, dz) rotation deltas}; root: (xRot, y offset) of the whole model;
    extra(draw, S, V, world): draws effects (beam, trail) after the model"""
    pose = pose or {}
    parts = [(n, p, piv, tuple(r[i] + pose.get(n, (0, 0, 0))[i] for i in range(3)), b, m) for (n, p, piv, r, b, m) in S.PARTS]
    W = S.transforms(parts)
    G = S.rot(root[0], 0, 0); goff = np.array((0, root[1] - lift, 0.0))
    Tb = S.rot(body_tilt, 0, 0)
    def world(p):
        q = G @ p + goff
        return Tb @ (q - np.array((0, 24.0, 0))) + np.array((0, 24.0, 0))
    V = S.rot(pitch, 0, 0) @ S.rot(0, yaw, 0)
    T_img = tex.copy()
    if glow > 0:
        g = np.array(glw).astype(float); g[..., 3] *= glow; T_img.alpha_composite(Image.fromarray(g.astype(np.uint8)))
    faces = []
    for name, _, _, _, boxes, mirror in parts:
        R_, T = W[name]
        for (m, x, y, z, w, h, d) in boxes:
            u, v = S.UV[S.key(m, w, h, d)]
            P = lambda a, b, c: V @ world(T + R_ @ np.array((a, b, c), float))
            F = [((u + d, v, w, d), P(x, y, z), P(x + w, y, z), P(x, y, z + d)),
                 ((u + d + w, v, w, d), P(x, y + h, z), P(x + w, y + h, z), P(x, y + h, z + d)),
                 ((u, v + d, d, h), P(x, y, z + d), P(x, y, z), P(x, y + h, z + d)),
                 ((u + d, v + d, w, h), P(x, y, z), P(x + w, y, z), P(x, y + h, z)),
                 ((u + d + w, v + d, d, h), P(x + w, y, z), P(x + w, y, z + d), P(x + w, y + h, z)),
                 ((u + 2 * d + w, v + d, w, h), P(x + w, y, z + d), P(x, y, z + d), P(x + w, y + h, z + d))]
            for rect, o, ex, ey in F:
                nn = np.cross(ex - o, ey - o); ln = np.linalg.norm(nn)
                if ln < 1e-9: continue
                c = o + (ex - o) / 2 + (ey - o) / 2
                faces.append((c[2], rect, o, ex, ey, nn / ln))
    allp = [p for f_ in faces for p in (f_[2], f_[3], f_[4])]
    mnx, mxx = min(p[0] for p in allp), max(p[0] for p in allp); mny, mxy = min(p[1] for p in allp), max(p[1] for p in allp)
    mxy = max(mxy, 26)
    pad = 0.12 * max(mxx - mnx, mxy - mny)
    mnx -= pad; mxx += pad; mny -= pad; mxy += pad * 0.3
    sc = (size - 60) / max(mxx - mnx, mxy - mny)
    H = int((mxy - mny) * sc + 60)
    canvas = Image.new("RGBA", (size, H), bg + (255,))
    Sp = lambda p: np.array([30 + (p[0] - mnx) * sc, 30 + (p[1] - mny) * sc])
    dr = ImageDraw.Draw(canvas)
    if ground:
        gy = Sp(V @ np.array((0, 24.0, 0)))[1]
        if gy < H:
            canvas.paste((96, 140, 80, 255), (0, int(gy), size, H))
    for depth, (fx, fy, fw, fh), o, ex, ey, nn in sorted(faces, key=lambda f_: -f_[0]):
        shade = 0.55 + 0.45 * abs(nn[1] * -0.6 + nn[2] * -0.6 + nn[0] * 0.3)
        a, b = int(round(fx * D)), int(round(fy * D)); pw, ph = max(1, int(round((fx + fw) * D)) - a), max(1, int(round((fy + fh) * D)) - b)
        crop = T_img.crop((a, b, a + pw, b + ph))
        arr = np.array(crop).astype(float); arr[..., :3] *= min(1.0, shade); crop = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
        so, sx, sy = Sp(o), Sp(ex) - Sp(o), Sp(ey) - Sp(o)
        quad = [so, so + sx, so + sx + sy, so + sy]
        x0, y0 = np.floor(np.min(quad, 0)).astype(int); x1, y1 = np.ceil(np.max(quad, 0)).astype(int) + 1
        M = np.array([[sx[0], sy[0]], [sx[1], sy[1]]])
        if abs(np.linalg.det(M)) < 1e-6: continue
        Mi = np.linalg.inv(M)
        A_ = np.array([[Mi[0, 0] * pw, Mi[0, 1] * pw], [Mi[1, 0] * ph, Mi[1, 1] * ph]])
        off = -A_ @ (so - np.array([x0, y0]))
        warped = crop.transform((x1 - x0, y1 - y0), Image.AFFINE, (A_[0, 0], A_[0, 1], off[0], A_[1, 0], A_[1, 1], off[1]), Image.NEAREST)
        mask = Image.new("L", (x1 - x0, y1 - y0), 0)
        ImageDraw.Draw(mask).polygon([tuple(q - np.array([x0, y0])) for q in quad], fill=255)
        ma = np.array(mask) * (np.array(warped)[..., 3] > 0)
        canvas.paste(warped, (x0, y0), Image.fromarray(ma.astype(np.uint8)))
    if extra:
        W2 = W
        extra(dr, Sp, V, lambda pname, p: world(W2[pname][1] + W2[pname][0] @ np.array(p, float)), sc)
    return canvas.convert("RGB")

# ---- poses (mirror ShowaGodzillaModel.java) ----
def fly_pose():
    P = {"neck": (-0.55, 0, 0), "head": (-0.75, 0, 0), "jaw": (0.8, 0, 0),
         "right_leg": (0.35, 0, 0.05), "left_leg": (0.35, 0, -0.05), "right_shin": (0.5, 0, 0), "left_shin": (0.5, 0, 0),
         "right_arm": (1.0, 0, -0.1), "left_arm": (1.0, 0, 0.1), "right_forearm": (-0.9, 0, 0), "left_forearm": (-0.9, 0, 0),
         "tail1": (-1.0, 0, 0), **{f"tail{i}": (-0.05, 0, 0) for i in range(2, 8)}}
    return P
def kick_pose(k=1.0):
    return {"right_leg": (-0.3 * k, 0, 0.08 * k), "left_leg": (-0.3 * k, 0, -0.08 * k), "right_shin": (-0.2 * k, 0, 0), "left_shin": (-0.2 * k, 0, 0),
            "right_arm": (-0.9 * k, 0, 0.5 * k), "left_arm": (-0.9 * k, 0, -0.5 * k), "neck": (0.35 * k, 0, 0), "head": (0.3 * k, 0, 0),
            "tail1": (1.1 * k, 0, 0), "tail2": (0.15 * k, 0, 0), "jaw": (0.3 * k, 0, 0)}
def dance_pose(t):
    s = math.sin(t)
    return {"right_arm": (-2.4, 0, -0.9 + 0.3 * s), "left_arm": (-2.4, 0, 0.9 + 0.3 * s), "right_forearm": (-0.3, 0, 0), "left_forearm": (-0.3, 0, 0),
            "hips": (0, 0, 0.12 * s), "body": (0, 0, -0.08 * s), "head": (0, 0, 0.15 * s), "jaw": (0.35, 0, 0),
            "right_leg": (-0.5 * max(0, s), 0, 0), "right_shin": (0.8 * max(0, s), 0, 0), "left_leg": (-0.5 * max(0, -s), 0, 0), "left_shin": (0.8 * max(0, -s), 0, 0),
            **{f"tail{i + 1}": (0, 0.25 * s, 0) for i in range(7)}}

def beam_trail(dr, Sp, V, wp, sc):
    # the white jet beam blasting backward out of his mouth
    m = wp("head", (0, -1, -13.5)); m2 = wp("head", (0, -1, -40.0))
    d = (m2 - m) / np.linalg.norm(m2 - m)
    for i in range(60, 0, -1):
        p = V @ (m + d * i * 1.6); q = Sp(p); r = (3 + i * 0.25) * sc / 6
        a = int(255 * (1 - i / 62))
        dr.ellipse([q[0] - r, q[1] - r, q[0] + r, q[1] + r], fill=(200, 228, 255, a))
    for i in range(40, 0, -1):
        q = Sp(V @ (m + d * i * 1.4)); r = (1.2 + i * 0.08) * sc / 6
        dr.ellipse([q[0] - r, q[1] - r, q[0] + r, q[1] + r], fill=(255, 255, 255))

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "/workspace/showa_godzilla_0.2.1_preview.png"
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 34)
    fs = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 22)
    main = render(-math.pi * 0.22, -0.12, size=1000)
    fly = render(math.pi * 0.42, -0.2, fly_pose(), root=(1.45, 4.0), glow=1.0, size=1000, ground=False, bg=(120, 170, 230), extra=beam_trail)
    kick = render(math.pi * 0.5, -0.1, kick_pose(), body_tilt=-math.radians(75), lift=14.0, size=1000)
    dance = render(-math.pi * 0.15, -0.1, dance_pose(1.2), size=1000)
    tiles = [("Front three-quarter view", main), ("Jet flight: beam blasting backward", fly), ("Drop kick: both feet!", kick), ("Victory dance", dance)]
    TW, TH = 760, 760
    sheet = Image.new("RGB", (2 * (TW + 24) + 24, 96 + 2 * (TH + 50)), (30, 32, 38)); d = ImageDraw.Draw(sheet)
    d.text((24, 26), "Gojira Mod 0.2.1: Showa Godzilla, the hero (54.67 blocks tall)", fill=(240, 240, 240), font=font)
    for i, (lab, im) in enumerate(tiles):
        im.thumbnail((TW, TH), Image.LANCZOS)
        x = 24 + (i % 2) * (TW + 24); y = 96 + (i // 2) * (TH + 50)
        d.text((x, y), lab, fill=(220, 220, 220), font=fs)
        sheet.paste(im, (x + (TW - im.width) // 2, y + 32 + (TH - im.height) // 2))
    sheet.save(out); print(out, sheet.size)
