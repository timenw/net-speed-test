#!/usr/bin/env python3
"""Generate launcher icons for NetSpeed Test app."""
import os

def create_icon(size, path):
    try:
        from PIL import Image, ImageDraw, ImageFont
        img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)

        padding = max(2, size // 20)
        corner = max(4, size // 5)
        box = [padding, padding, size - padding, size - padding]

        # Gradient background
        for y in range(size):
            for x in range(size):
                dx = max(box[0] - x, x - box[2], 0)
                dy = max(box[1] - y, y - box[3], 0)
                if dx*dx + dy*dy > corner*corner:
                    continue
                ratio = (x + y) / (size * 2)
                if ratio < 0.5:
                    s = ratio * 2
                    r = int(10 + (0-10)*s)
                    g = int(30 + (140-30)*s)
                    b = int(80 + (180-80)*s)
                else:
                    s = (ratio - 0.5) * 2
                    r = int(0 + (60-0)*s)
                    g = int(140 + (200-140)*s)
                    b = int(180 + (240-180)*s)
                img.putpixel((x, y), (r, g, b, 255))

        # Speedometer-like symbol
        cx, cy = size // 2, size // 2
        import math
        # Arc
        arc_r = int(size * 0.3)
        for angle in range(-135, 136, 2):
            rad = math.radians(angle)
            for w in range(max(1, size // 40)):
                px = int(cx + (arc_r + w) * math.cos(rad))
                py = int(cy - (arc_r + w) * math.sin(rad))
                if 0 <= px < size and 0 <= py < size:
                    img.putpixel((px, py), (255, 255, 255, 200))

        # Arrow needle
        needle_len = int(size * 0.28)
        needle_angle = math.radians(45)  # pointing upper-right
        for i in range(needle_len):
            nx = int(cx + i * math.cos(needle_angle))
            ny = int(cy - i * math.sin(needle_angle))
            if 0 <= nx < size and 0 <= ny < size:
                img.putpixel((nx, ny), (255, 255, 255, 255))
                if nx+1 < size: img.putpixel((nx+1, ny), (255, 255, 255, 200))

        # Center dot
        dot_r = max(2, size // 20)
        for y in range(size):
            for x in range(size):
                dx, dy = x - cx, y - cy
                if dx*dx + dy*dy <= dot_r*dot_r:
                    img.putpixel((x, y), (255, 255, 255, 255))

        # Convert to RGB
        rgb = Image.new('RGB', (size, size), (0, 0, 0))
        rgb.paste(img, mask=img.split()[3])

        os.makedirs(os.path.dirname(path), exist_ok=True)
        rgb.save(path)
        print(f'{path} ({size}x{size})')
    except ImportError:
        print(f'Pillow not available, skipping {path}')

sizes = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
for density, size in sizes.items():
    base = f'app/src/main/res/mipmap-{density}'
    create_icon(size, f'{base}/ic_launcher.png')
    create_icon(size, f'{base}/ic_launcher_round.png')
print('Done!')
