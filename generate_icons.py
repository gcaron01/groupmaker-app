import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
from PIL import Image, ImageDraw
import os

SRC = r"C:/xampp/htdocs/groupmaker/images_site/marina-groupmaker_logo_blanc.png"
BG_COLOR = (32, 70, 110)  # #20466e

BASE_DIR = r"C:/dev/groupmaker-app/android/app/src/main/res"

SIZES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

FOREGROUND_SIZES = {
    "mipmap-mdpi":    108,
    "mipmap-hdpi":    162,
    "mipmap-xhdpi":   216,
    "mipmap-xxhdpi":  324,
    "mipmap-xxxhdpi": 432,
}

def load_symbol(path):
    img = Image.open(path).convert("RGBA")
    w, h = img.size

    # Crop sur le symbole seul (x=240 a x=660, distribution analysee)
    symbol = img.crop((240, 0, 660, h))
    sw, sh = symbol.size

    # Bounding box reelle (alpha > 20)
    min_x, min_y, max_x, max_y = sw, sh, 0, 0
    for y in range(sh):
        for x in range(sw):
            if symbol.getpixel((x, y))[3] > 20:
                if x < min_x: min_x = x
                if x > max_x: max_x = x
                if y < min_y: min_y = y
                if y > max_y: max_y = y

    # Marge 5%
    mx = max(5, int((max_x - min_x) * 0.05))
    my = max(5, int((max_y - min_y) * 0.05))
    symbol = symbol.crop((max(0, min_x-mx), max(0, min_y-my),
                          min(sw, max_x+mx), min(sh, max_y+my)))
    sw, sh = symbol.size

    # RGB -> blanc pur, alpha conserve
    result = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    for x in range(sw):
        for y in range(sh):
            r, g, b, a = symbol.getpixel((x, y))
            result.putpixel((x, y), (255, 255, 255, a))
    return result

def make_square(logo, size, padding=0.12):
    icon = Image.new("RGBA", (size, size), BG_COLOR + (255,))
    max_logo = int(size * (1 - padding * 2))
    lw, lh = logo.size
    ratio = min(max_logo / lw, max_logo / lh)
    nw, nh = max(1, int(lw * ratio)), max(1, int(lh * ratio))
    logo_r = logo.resize((nw, nh), Image.LANCZOS)
    icon.paste(logo_r, ((size - nw) // 2, (size - nh) // 2), logo_r)
    return icon

def make_round(logo, size, padding=0.12):
    icon = make_square(logo, size, padding)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size, size), fill=255)
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    result.paste(icon, (0, 0), mask)
    return result

def make_foreground(logo, size, safe=0.60):
    fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    max_logo = int(size * safe)
    lw, lh = logo.size
    ratio = min(max_logo / lw, max_logo / lh)
    nw, nh = max(1, int(lw * ratio)), max(1, int(lh * ratio))
    logo_r = logo.resize((nw, nh), Image.LANCZOS)
    fg.paste(logo_r, ((size - nw) // 2, (size - nh) // 2), logo_r)
    return fg

print("Chargement du symbole...")
logo = load_symbol(SRC)
print(f"Symbole: {logo.size[0]}x{logo.size[1]} px")

print("\nGeneration des icones...")
for folder, size in SIZES.items():
    dest = os.path.join(BASE_DIR, folder)
    make_square(logo, size).convert("RGB").save(os.path.join(dest, "ic_launcher.png"))
    make_round(logo, size).save(os.path.join(dest, "ic_launcher_round.png"))
    print(f"  OK {folder} ({size}x{size})")

for folder, size in FOREGROUND_SIZES.items():
    dest = os.path.join(BASE_DIR, folder)
    make_foreground(logo, size).save(os.path.join(dest, "ic_launcher_foreground.png"))
    print(f"  OK {folder} foreground ({size}x{size})")

print("\nToutes les icones generees !")
print("Prochaine etape : git add + commit + push")
