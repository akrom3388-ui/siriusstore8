#!/usr/bin/env python3
"""SIRIUS STORE logotipi (QULUPNAY, fonsiz) — launcher ikonkalari. Natija assets/res/ ichida (zipga tayyor holda kiritilgan)."""
import math, os
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "assets", "res")
S = 1024
SS = 2          # supersampling

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))

def strawberry(size):
    """Shaffof fonda qulupnay (hech qanday fon yo'q). size — kvadrat tomoni."""
    W = size * SS
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    cx = W / 2
    top, bot = W * 0.27, W * 0.93
    half = W * 0.37

    def halfw(u):
        shoulder = 0.62 + 0.38 * min(1.0, u * 5.0)
        return half * shoulder * (1 - u ** 2.0) ** 0.62

    # ---- tana (gradient) ----
    mask = Image.new("L", (W, W), 0)
    md = ImageDraw.Draw(mask)
    pts = []
    N = 220
    for i in range(N + 1):
        u = i / N
        pts.append((cx - halfw(u), top + (bot - top) * u))
    for i in range(N, -1, -1):
        u = i / N
        pts.append((cx + halfw(u), top + (bot - top) * u))
    md.polygon(pts, fill=255)
    md.ellipse((cx - half * 0.62, top - W * 0.035, cx + half * 0.62, top + W * 0.085), fill=255)   # yumshoq yelka
    mask = mask.filter(ImageFilter.GaussianBlur(W * 0.004))
    body = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    px = body.load()
    c_hi, c_mid, c_lo = (255, 92, 96), (232, 35, 58), (168, 14, 40)
    for y in range(int(top - W * 0.04), int(bot) + 2):
        for x in range(int(cx - half - 4), int(cx + half + 5)):
            dx = (x - (cx - half * 0.35)) / (half * 1.6)
            dy = (y - (top + (bot - top) * 0.28)) / ((bot - top) * 0.9)
            t = min(1.0, math.sqrt(dx * dx + dy * dy))
            col = lerp(c_hi, c_mid, t / 0.55) if t < 0.55 else lerp(c_mid, c_lo, (t - 0.55) / 0.45)
            px[x, y] = col + (255,)
    body.putalpha(mask)
    img = Image.alpha_composite(img, body)

    # ---- urug'lar ----
    d = ImageDraw.Draw(img)
    rows = [(0.17, 3), (0.28, 4), (0.40, 4), (0.52, 3), (0.64, 3), (0.76, 2), (0.87, 1)]
    for ri, (u, n) in enumerate(rows):
        y = top + (bot - top) * u
        w = halfw(u) * 0.74
        for k in range(n):
            if n == 1:
                x = cx
            else:
                f = (k + (0.5 if ri % 2 else 0.0)) / (n - (0 if ri % 2 == 0 else 0))
                x = cx - w + 2 * w * (k / (n - 1))
                if ri % 2:
                    x += w * 0.06
            sw, sh = W * 0.020, W * 0.032
            d.ellipse((x - sw, y - sh, x + sw, y + sh), fill=(255, 226, 122, 255), outline=(176, 92, 20, 255), width=max(1, int(W * 0.0035)))
    # ---- yaltiroq dog' ----
    gl = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    ImageDraw.Draw(gl).ellipse((cx - half * 0.72, top + (bot - top) * 0.10, cx - half * 0.42, top + (bot - top) * 0.34), fill=(255, 255, 255, 70))
    gl = gl.filter(ImageFilter.GaussianBlur(W * 0.012))
    img = Image.alpha_composite(img, gl)

    # ---- barglar (kosacha) ----
    d = ImageDraw.Draw(img)
    gx, gy = cx, top + W * 0.012
    leaf_dark, leaf_mid = (24, 128, 56, 255), (52, 190, 88, 255)
    for ang, ln, wd in [(-150, 0.20, 0.050), (150 - 300 + 300, 0, 0)]:
        pass
    def leaf(angle_deg, length, width, color):
        a = math.radians(angle_deg)
        ux, uy = math.cos(a), math.sin(a)
        nx, ny = -uy, ux
        L = W * length; Wd = W * width
        p = [(gx, gy), (gx + ux * L * 0.45 + nx * Wd, gy + uy * L * 0.45 + ny * Wd), (gx + ux * L, gy + uy * L),
             (gx + ux * L * 0.45 - nx * Wd, gy + uy * L * 0.45 - ny * Wd)]
        d.polygon(p, fill=color)
    for a in (200, 340, 232, 308):
        leaf(a, 0.205, 0.040, leaf_dark)
    for a in (216, 324, 252, 288):
        leaf(a, 0.19, 0.036, leaf_mid)
    d.ellipse((gx - W * 0.055, gy - W * 0.03, gx + W * 0.055, gy + W * 0.03), fill=leaf_mid)
    # poya
    d.polygon([(gx - W * 0.014, gy - W * 0.01), (gx + W * 0.014, gy - W * 0.01), (gx + W * 0.03, gy - W * 0.115), (gx + W * 0.0, gy - W * 0.112)], fill=(46, 150, 72, 255))
    return img.resize((size, size), Image.LANCZOS)

def with_scale(art, size, scale):
    """Qulupnayni size x size shaffof kanvasda scale (0..1) o'lchamda markazga qo'yadi."""
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    a = art.resize((int(size * scale), int(size * scale)), Image.LANCZOS)
    # qulupnay markazi balandligi bo'yicha biroz ko'tarish (barg tepada)
    out.alpha_composite(a, ((size - a.width) // 2, (size - a.height) // 2))
    return out

def mono(art):
    r = Image.new("RGBA", art.size, (255, 255, 255, 0))
    r.putalpha(art.getchannel("A"))
    px = r.load()
    for y in range(r.height):
        for x in range(r.width):
            a = px[x, y][3]
            px[x, y] = (255, 255, 255, a)
    return r

def save(img, path, px):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.resize((px, px), Image.LANCZOS).save(path, "PNG", optimize=True)

def main():
    dens = {"mdpi": (48, 108), "hdpi": (72, 162), "xhdpi": (96, 216), "xxhdpi": (144, 324), "xxxhdpi": (192, 432)}
    art = strawberry(S)
    legacy = with_scale(art, S, 0.96)      # eski Androidlar: fonsiz PNG
    fg = with_scale(art, S, 0.64)          # adaptive: xavfsiz zona ichida
    mo = mono(fg)
    for d, (ic, fgs) in dens.items():
        base = os.path.join(OUT, "mipmap-" + d)
        save(legacy, os.path.join(base, "ic_launcher.png"), ic)
        save(legacy, os.path.join(base, "ic_launcher_round.png"), ic)
        save(fg, os.path.join(base, "sirius_fg.png"), fgs)
        save(mo, os.path.join(base, "sirius_mono.png"), fgs)
    os.makedirs(os.path.join(OUT, "mipmap-anydpi-v26"), exist_ok=True)
    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@color/sirius_bg"/>\n'
                '    <foreground android:drawable="@mipmap/sirius_fg"/>\n'
                '    <monochrome android:drawable="@mipmap/sirius_mono"/>\n'
                '</adaptive-icon>\n')
    for n in ("ic_launcher.xml", "ic_launcher_round.xml"):
        open(os.path.join(OUT, "mipmap-anydpi-v26", n), "w").write(adaptive)
    os.makedirs(os.path.join(OUT, "values"), exist_ok=True)
    open(os.path.join(OUT, "values", "sirius_colors.xml"), "w").write(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="sirius_bg">#00000000</color>\n'
        '    <color name="sirius_splash_bg">#0A0B0E</color>\n</resources>\n')
    os.makedirs(os.path.join(OUT, "drawable"), exist_ok=True)
    open(os.path.join(OUT, "drawable", "ic_stat_sirius.xml"), "w").write(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp"'
        ' android:viewportWidth="100" android:viewportHeight="100">\n'
        '    <path android:fillColor="#FFFFFFFF" android:pathData="M50,93 C30,84 12,62 15,42 C17,29 32,25 50,31 C68,25 83,29 85,42 C88,62 70,84 50,93 Z M50,30 L38,14 L46,25 L50,8 L54,25 L62,14 Z"/>\n'
        '</vector>\n')
    open(os.path.join(OUT, "drawable", "splash.xml"), "w").write(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <item><shape android:shape="rectangle"><solid android:color="@color/sirius_splash_bg"/></shape></item>\n'
        '    <item><bitmap android:gravity="center" android:src="@mipmap/sirius_fg"/></item>\n'
        '</layer-list>\n')
    os.makedirs(os.path.join(OUT, "xml"), exist_ok=True)
    open(os.path.join(OUT, "xml", "sirius_paths.xml"), "w").write(
        '<?xml version="1.0" encoding="utf-8"?>\n<paths xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <cache-path name="sirius_cache" path="." />\n</paths>\n')
    # tekshirish uchun katta rasm (fonsiz)
    legacy.resize((512, 512), Image.LANCZOS).save(os.path.join(os.path.dirname(OUT), "logo-512.png"))

main()
