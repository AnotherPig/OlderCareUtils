"""
Generate pixelated old man app icon for 老头乐 (Older Care Utility)
- White hair, glasses, smiling
- Pixel art style on 32x32 grid for more detail
"""
from PIL import Image, ImageDraw
import os
import math

# Colors
SKIN = (255, 213, 175)
SKIN_DARK = (240, 195, 160)
HAIR_W = (230, 230, 240)
HAIR_WD = (200, 200, 215)
HAIR_WL = (245, 245, 250)
GLASS_F = (50, 50, 70)
GLASS_L = (190, 220, 245, 120)
EYE_P = (35, 35, 50)
MOUTH_L = (210, 105, 90)
MOUTH_D = (175, 70, 60)
CHEEK = (255, 185, 170)
BROW = (185, 185, 195)
SHIRT = (90, 145, 200)
SHIRT_D = (70, 120, 175)
COLLAR = (240, 240, 245)
NOSE = (240, 190, 155)

# Background warm cream
BG_COLOR = (255, 248, 235)

def draw_pixel(draw, x, y, ps, color):
    """Draw a single pixel block."""
    if color is None:
        return
    draw.rectangle([x, y, x + ps - 1, y + ps - 1], fill=color)

def create_icon(size):
    """Create a 32x32 pixel art icon of a smiling old man."""
    grid_size = 32
    ps = size / grid_size  # pixel size

    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    def px(col, row, color):
        x = int(col * ps)
        y = int(row * ps)
        s = int(ps)
        if color:
            draw.rectangle([x, y, x + s - 1, y + s - 1], fill=color)

    # ===== Background circle =====
    cx, cy = size // 2, size // 2
    r = int(size * 0.46)
    for y2 in range(size):
        for x2 in range(size):
            if (x2 - cx)**2 + (y2 - cy)**2 <= r * r:
                img.putpixel((x2, y2), BG_COLOR + (255,))

    # ===== White hair (top of head) =====
    # Row 4-5: top of hair, wide
    hair_rows = {
        3: [(10, 21)],
        4: [(8, 23)],
        5: [(7, 24)],
        6: [(6, 9), (10, 20), (23, 25)],
        7: [(6, 8), (10, 20), (24, 25)],
    }
    for row, ranges in hair_rows.items():
        for start, end in ranges:
            for c in range(start, end + 1):
                color = HAIR_WD if (c + row) % 5 == 0 else HAIR_W
                px(c, row, color)

    # ===== Face (skin area) =====
    # Forehead
    for row in range(8, 12):
        for c in range(7, 24):
            color = SKIN if (c + row) % 7 != 0 else SKIN_DARK
            px(c, row, color)

    # Eyebrows
    for c in range(8, 14):
        px(c, 10, BROW)
    for c in range(17, 23):
        px(c, 10, BROW)

    # Glasses - left lens
    for row in range(11, 15):
        for c in range(7, 15):
            px(c, row, GLASS_F)
    for row in range(12, 14):
        for c in range(8, 14):
            px(c, row, GLASS_L)

    # Glasses - right lens
    for row in range(11, 15):
        for c in range(17, 25):
            px(c, row, GLASS_F)
    for row in range(12, 14):
        for c in range(18, 24):
            px(c, row, GLASS_L)

    # Glasses bridge
    for c in range(14, 18):
        px(c, 12, GLASS_F)
        px(c, 13, GLASS_F)

    # Eyes (pupils)
    px(10, 12, EYE_P)
    px(11, 12, EYE_P)
    px(20, 12, EYE_P)
    px(21, 12, EYE_P)

    # Eye whites / highlights
    px(10, 13, (255, 255, 255, 200))
    px(20, 13, (255, 255, 255, 200))

    # Cheek area and nose area
    for row in range(15, 18):
        for c in range(7, 24):
            px(c, row, SKIN)

    # Nose
    px(15, 15, NOSE)
    px(16, 15, NOSE)
    px(15, 16, NOSE)
    px(16, 16, NOSE)

    # Rosy cheeks
    for c in range(8, 11):
        px(c, 16, CHEEK)
    for c in range(21, 24):
        px(c, 16, CHEEK)

    # ===== SMILE - curved upward mouth =====
    # Smile line (wide grin)
    for c in range(11, 20):
        px(c, 18, MOUTH_L)
    # Smile corners going up
    px(10, 18, MOUTH_L)
    px(10, 17, MOUTH_L)  # left corner up
    px(20, 18, MOUTH_L)
    px(20, 17, MOUTH_L)  # right corner up
    # Open smile interior
    for c in range(12, 19):
        px(c, 18, MOUTH_D)
    # Teeth hint
    for c in range(13, 18):
        px(c, 17, (255, 255, 255))

    # Chin
    for c in range(10, 21):
        px(c, 19, SKIN)
    for c in range(12, 19):
        px(c, 20, SKIN)

    # Side hair (around ears)
    for row in range(8, 17):
        px(6, row, HAIR_W)
        px(24, row, HAIR_W)
        if row % 3 == 0:
            px(5, row, HAIR_WD)
            px(25, row, HAIR_WD)

    # ===== Shirt / collar =====
    for c in range(8, 23):
        px(c, 21, COLLAR)
    # Collar V
    px(13, 22, COLLAR)
    px(14, 22, COLLAR)
    px(17, 22, COLLAR)
    px(18, 22, COLLAR)

    for c in range(7, 24):
        px(c, 22, SHIRT)
    px(15, 22, SHIRT_D)
    px(16, 22, SHIRT_D)

    for row in range(23, 26):
        for c in range(6, 25):
            color = SHIRT if (c + row) % 4 != 0 else SHIRT_D
            px(c, row, color)

    return img


def create_adaptive_bg(size):
    """Create adaptive icon background layer - warm cream circle."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    cx, cy = size // 2, size // 2
    r = int(size * 0.5)

    for y in range(size):
        for x in range(size):
            if (x - cx)**2 + (y - cy)**2 <= r * r:
                img.putpixel((x, y), BG_COLOR + (255,))

    return img


def create_adaptive_fg(size):
    """Create adaptive icon foreground layer - just the character."""
    return create_icon(size)


def create_legacy_icon(size):
    """Create legacy icon with background baked in."""
    return create_icon(size)


# Android density sizes
densities = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

base_dir = os.path.dirname(os.path.abspath(__file__))
res_dir = os.path.join(base_dir, 'app', 'src', 'main', 'res')

# Generate legacy icons
for density, size in densities.items():
    out_dir = os.path.join(res_dir, density)
    os.makedirs(out_dir, exist_ok=True)

    icon = create_legacy_icon(size)
    icon.save(os.path.join(out_dir, 'ic_launcher.png'))
    print(f"Generated {density}/ic_launcher.png ({size}x{size})")

    icon.save(os.path.join(out_dir, 'ic_launcher_round.png'))
    print(f"Generated {density}/ic_launcher_round.png ({size}x{size})")

# Generate adaptive icon layers (1.5x for safe zone)
for layer_name, creator in [
    ('ic_launcher_foreground', create_adaptive_fg),
    ('ic_launcher_background', create_adaptive_bg),
]:
    for density, base_size in densities.items():
        size = int(base_size * 1.5)
        out_dir = os.path.join(res_dir, density)
        img = creator(size)
        img.save(os.path.join(out_dir, f'{layer_name}.png'))
        print(f"Generated {density}/{layer_name}.png ({size}x{size})")

# Also create a preview at 512x512
preview = create_icon(512)
preview.save(os.path.join(base_dir, 'icon_preview.png'))
print("\nGenerated icon_preview.png (512x512)")

print("\nAll icons generated successfully!")
