#!/usr/bin/env python3
"""
创建老头图标的 PNG 文件
使用 PIL 库生成简单的像素风格老头图标
"""

from PIL import Image, ImageDraw
import os

# 图标尺寸
sizes = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

# 颜色定义
COLOR_BG = '#F5F0E8'      # 背景色
COLOR_SKIN = '#F5D0A9'    # 皮肤色
COLOR_HAIR = '#4A4A4A'    # 头发/帽子
COLOR_GLASS = '#2C2C2C'   # 眼镜框
COLOR_GLASS_SHINE = '#FFFFFF'  # 眼镜反光
COLOR_NOSE = '#E8C09A'    # 鼻子
COLOR_MOUTH = '#8B6914'   # 嘴巴
COLOR_WRINKLE = '#DDD0B8' # 皱纹

def create_old_man_icon(size):
    """创建老头图标"""
    img = Image.new('RGBA', (size, size), COLOR_BG)
    draw = ImageDraw.Draw(img)

    # 计算像素大小（基于 16x16 网格）
    pixel_size = size // 16

    # 中心偏移
    offset_x = (size - (16 * pixel_size)) // 2
    offset_y = (size - (16 * pixel_size)) // 2

    def draw_pixel(x, y, color):
        draw.rectangle(
            [offset_x + x * pixel_size, offset_y + y * pixel_size,
             offset_x + (x + 1) * pixel_size, offset_y + (y + 1) * pixel_size],
            fill=color
        )

    # 脸部轮廓 - 浅米色 (3,2) 10x10
    for y in range(2, 12):
        for x in range(3, 13):
            draw_pixel(x, y, COLOR_SKIN)

    # 头发/帽子顶部 - 深灰色 (3,0) 10x2
    for y in range(0, 2):
        for x in range(3, 13):
            draw_pixel(x, y, COLOR_HAIR)

    # 左侧头发 (2,2) 2x6
    for y in range(2, 8):
        for x in range(2, 4):
            draw_pixel(x, y, COLOR_HAIR)

    # 右侧头发 (12,2) 2x6
    for y in range(2, 8):
        for x in range(12, 14):
            draw_pixel(x, y, COLOR_HAIR)

    # 眼镜 - 左片 (3,5) 4x3
    for y in range(5, 8):
        for x in range(3, 7):
            draw_pixel(x, y, COLOR_GLASS)

    # 眼镜 - 右片 (9,5) 4x3
    for y in range(5, 8):
        for x in range(9, 13):
            draw_pixel(x, y, COLOR_GLASS)

    # 眼镜桥 (7,6) 2x1
    for x in range(7, 9):
        draw_pixel(x, 6, COLOR_GLASS)

    # 左镜片反光 (4,5)
    draw_pixel(4, 5, COLOR_GLASS_SHINE)

    # 右镜片反光 (10,5)
    draw_pixel(10, 5, COLOR_GLASS_SHINE)

    # 鼻子 - 小圆点 (7,8) 2x1
    for x in range(7, 9):
        draw_pixel(x, 8, COLOR_NOSE)

    # 嘴巴 - 微笑 (5,10) 6x1
    for x in range(5, 11):
        draw_pixel(x, 10, COLOR_MOUTH)

    # 嘴巴下部 (6,11) 4x1
    for x in range(6, 10):
        draw_pixel(x, 11, COLOR_MOUTH)

    # 左耳朵 (1,6) 1x2
    for y in range(6, 8):
        draw_pixel(1, y, COLOR_SKIN)

    # 右耳朵 (14,6) 1x2
    for y in range(6, 8):
        draw_pixel(14, y, COLOR_SKIN)

    # 胡子/皱纹纹理
    draw_pixel(6, 9, COLOR_WRINKLE)
    draw_pixel(9, 9, COLOR_WRINKLE)

    return img

def main():
    base_dir = 'app/src/main/res'

    for folder, size in sizes.items():
        folder_path = os.path.join(base_dir, folder)

        # 创建普通图标
        img = create_old_man_icon(size)
        img.save(os.path.join(folder_path, 'ic_launcher.png'))
        print(f'Created {folder}/ic_launcher.png ({size}x{size})')

        # 创建圆形图标
        mask = Image.new('L', (size, size), 0)
        mask_draw = ImageDraw.Draw(mask)
        mask_draw.ellipse([(0, 0), (size, size)], fill=255)

        round_img = Image.new('RGBA', (size, size), COLOR_BG)
        round_img.paste(img, (0, 0), mask)
        round_img.save(os.path.join(folder_path, 'ic_launcher_round.png'))
        print(f'Created {folder}/ic_launcher_round.png ({size}x{size})')

    print('\n图标创建完成！')

if __name__ == '__main__':
    main()
