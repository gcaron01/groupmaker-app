import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
from PIL import Image

SRC = r"C:/xampp/htdocs/groupmaker/images_site/marina-groupmaker_logo_blanc.png"
BG_COLOR = (32, 70, 110)

img = Image.open(SRC).convert("RGBA")
w, h = img.size

# Crop le symbole (x=240 a x=660, basé sur la distribution)
symbol = img.crop((240, 0, 660, h))

# Chercher la bounding box réelle (pixels avec alpha > 20)
sw, sh = symbol.size
min_x, min_y, max_x, max_y = sw, sh, 0, 0
for y in range(sh):
    for x in range(sw):
        a = symbol.getpixel((x, y))[3]
        if a > 20:
            if x < min_x: min_x = x
            if x > max_x: max_x = x
            if y < min_y: min_y = y
            if y > max_y: max_y = y

print(f"Bounding box reelle: ({min_x},{min_y}) -> ({max_x},{max_y})")
# Ajouter 5% de marge autour
margin_x = max(5, int((max_x - min_x) * 0.05))
margin_y = max(5, int((max_y - min_y) * 0.05))
cx1 = max(0, min_x - margin_x)
cy1 = max(0, min_y - margin_y)
cx2 = min(sw, max_x + margin_x)
cy2 = min(sh, max_y + margin_y)
symbol = symbol.crop((cx1, cy1, cx2, cy2))
sw, sh = symbol.size
print(f"Symbole taille finale: {sw}x{sh}")

# Convertir pixels : garder alpha, RGB -> blanc pur
pixels = [(255, 255, 255, img.getpixel((240+cx1+x, cy1+y))[3])
          for y in range(sh) for x in range(sw)]
# Plus simple: juste remplacer RGB
pixels2 = []
for x in range(sw):
    for y in range(sh):
        r,g,b,a = symbol.getpixel((x,y))
        pixels2.append((x, y, a))

new_img = Image.new("RGBA", (sw, sh), (0,0,0,0))
for x in range(sw):
    for y in range(sh):
        r,g,b,a = symbol.getpixel((x,y))
        new_img.putpixel((x,y), (255,255,255,a))
symbol = new_img

# Preview 512x512 avec fond bleu
SIZE = 512
icon = Image.new("RGBA", (SIZE, SIZE), BG_COLOR + (255,))
padding = 0.12
max_logo = int(SIZE * (1 - padding * 2))
ratio = min(max_logo/sw, max_logo/sh)
nw, nh = max(1,int(sw*ratio)), max(1,int(sh*ratio))
logo_r = symbol.resize((nw, nh), Image.LANCZOS)
x, y = (SIZE-nw)//2, (SIZE-nh)//2
icon.paste(logo_r, (x, y), logo_r)
icon.convert("RGB").save(r"C:/dev/groupmaker-app/icon_preview.png")
print(f"Logo dans icon: {nw}x{nh} a ({x},{y})")
print("Preview: C:/dev/groupmaker-app/icon_preview.png")
