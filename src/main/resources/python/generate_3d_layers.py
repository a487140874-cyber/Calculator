#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
3D岩层可视化脚本

视觉风格对齐产品原型（深色工程视图）：
  · 岩层按真实厚度比例自下而上堆叠，按岩性填充实色
  · 深色背景 + 地面网格 + 三向坐标箭头
  · 每一层在右侧引出统一标注：层号 / 岩性 / 厚度（能量层追加能量值与层高）

业务语义：
  · 所有导入岩层下方固定绘制一个空心煤层
  · power > 0 的岩层中，只有最上面的那一层整层标红；其余能量层保持岩性配色，
    仅在标注里保留能量值
"""

import json
import sys
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D
from mpl_toolkits.mplot3d.art3d import Poly3DCollection, Line3DCollection
from matplotlib.patches import Patch

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['Arial Unicode MS', 'PingFang SC', 'Heiti SC', 'SimHei', 'Microsoft YaHei', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

# ---- 深色调色板，取自应用 app.css 的 .theme-dark ----
BG_FIGURE = '#0F1720'
BG_SCENE = '#0B1118'
GRID_LINE = '#213344'
GRID_LINE_MAJOR = '#34506A'
TEXT_MAIN = '#E5EDF5'
TEXT_SOFT = '#A8B5C2'
TEXT_MUTED = '#718093'
PANEL_BG = '#151F2B'
BORDER = '#2C3B4D'
ACCENT = '#4DA3FF'
ACCENT_VIOLET = '#9B8CFF'
ACCENT_GREEN = '#51CF66'
ACCENT_RED = '#FF6B6B'          # 能量聚集层整层标红
COAL_FACE = '#26303B'
COAL_EDGE = '#6B7F94'

# ---- 岩性配色，与应用二维剖面 MainController.LITHOLOGY_COLORS 保持一致 ----
LITHOLOGY_COLORS = {
    '泥岩': '#7D7468',
    '砂质泥岩': '#A38C6C',
    '细砂岩': '#E0CE96',
    '中砂岩': '#D2A03F',
    '粗砂岩': '#96562B',
    '石灰岩': '#74A6B8',
    '砂岩': '#D9A06B',
    '页岩': '#4A6A85',
    '煤层': '#2E2E33',
    '煤': '#2E2E33',
}
DEFAULT_LITHOLOGY_COLOR = '#C2CBD4'


def lithology_color(name):
    """按岩层名称取岩性配色，未登记的岩性回落到中性灰蓝。"""
    return LITHOLOGY_COLORS.get((name or '').strip(), DEFAULT_LITHOLOGY_COLOR)


def shade(hex_color, factor):
    """按系数调亮(>1)或调暗(<1)一个十六进制颜色，用于生成描边色。"""
    hex_color = hex_color.lstrip('#')
    rgb = [int(hex_color[i:i + 2], 16) for i in (0, 2, 4)]
    return '#%02x%02x%02x' % tuple(max(0, min(255, int(c * factor))) for c in rgb)


def create_cube_vertices(x_min, x_max, y_min, y_max, z_min, z_max):
    """创建立方体的顶点"""
    return [
        [x_min, y_min, z_min], [x_max, y_min, z_min], [x_max, y_max, z_min], [x_min, y_max, z_min],
        [x_min, y_min, z_max], [x_max, y_min, z_max], [x_max, y_max, z_max], [x_min, y_max, z_max]
    ]


def create_cube_faces(vertices):
    """创建立方体的六个面，顺序为 底、顶、前、后、右、左"""
    return [
        [vertices[0], vertices[1], vertices[2], vertices[3]],
        [vertices[4], vertices[5], vertices[6], vertices[7]],
        [vertices[0], vertices[1], vertices[5], vertices[4]],
        [vertices[2], vertices[3], vertices[7], vertices[6]],
        [vertices[1], vertices[2], vertices[6], vertices[5]],
        [vertices[0], vertices[3], vertices[7], vertices[4]]
    ]


def create_hollow_cube_faces(outer_vertices, inner_vertices):
    """创建空心立方体的面（外部立方体挖掉内部立方体）"""
    faces = []

    # 外壁（除顶面）
    faces.extend([
        [outer_vertices[0], outer_vertices[1], outer_vertices[2], outer_vertices[3]],
        [outer_vertices[0], outer_vertices[1], outer_vertices[5], outer_vertices[4]],
        [outer_vertices[2], outer_vertices[3], outer_vertices[7], outer_vertices[6]],
        [outer_vertices[1], outer_vertices[2], outer_vertices[6], outer_vertices[5]],
        [outer_vertices[0], outer_vertices[3], outer_vertices[7], outer_vertices[4]]
    ])

    # 内腔
    faces.extend([
        [inner_vertices[4], inner_vertices[5], inner_vertices[6], inner_vertices[7]],
        [inner_vertices[0], inner_vertices[1], inner_vertices[5], inner_vertices[4]],
        [inner_vertices[2], inner_vertices[3], inner_vertices[7], inner_vertices[6]],
        [inner_vertices[1], inner_vertices[2], inner_vertices[6], inner_vertices[5]],
        [inner_vertices[0], inner_vertices[3], inner_vertices[7], inner_vertices[4]]
    ])

    # 顶部一圈边框，连接外壁与内腔
    for outer_a, inner_a, inner_b, outer_b in (
        (4, 4, 5, 5), (5, 5, 1, 1), (1, 1, 0, 0), (0, 0, 4, 4),
        (7, 7, 6, 6), (6, 6, 2, 2), (2, 2, 3, 3), (3, 3, 7, 7),
        (4, 4, 7, 7), (7, 7, 3, 3), (3, 3, 0, 0), (0, 0, 4, 4),
        (5, 5, 6, 6), (6, 6, 2, 2), (2, 2, 1, 1), (1, 1, 5, 5),
    ):
        faces.append([outer_vertices[outer_a], inner_vertices[inner_a],
                      inner_vertices[inner_b], outer_vertices[outer_b]])

    return faces


def draw_floor_grid(ax, x_span, y_span, divisions=14):
    """在 z=0 平面绘制地面网格，对应原型中的 GridHelper。"""
    x_min, x_max = x_span
    y_min, y_max = y_span
    segments = []
    for value in np.linspace(x_min, x_max, divisions + 1):
        segments.append([(value, y_min, 0), (value, y_max, 0)])
    for value in np.linspace(y_min, y_max, divisions + 1):
        segments.append([(x_min, value, 0), (x_max, value, 0)])
    ax.add_collection3d(Line3DCollection(segments, colors=GRID_LINE, linewidths=0.6,
                                         alpha=0.55, zorder=0))

    border = [
        [(x_min, y_min, 0), (x_max, y_min, 0)], [(x_max, y_min, 0), (x_max, y_max, 0)],
        [(x_max, y_max, 0), (x_min, y_max, 0)], [(x_min, y_max, 0), (x_min, y_min, 0)],
    ]
    ax.add_collection3d(Line3DCollection(border, colors=GRID_LINE_MAJOR, linewidths=1.1,
                                         alpha=0.9, zorder=0))


def draw_axis_arrows(ax, ax_value, by_value, z_top, offset_x, offset_y):
    """绘制三向坐标箭头与轴名。原点偏出岩层投影范围，否则箭头会被岩层整体遮住。"""
    ox, oy = -offset_x, -offset_y
    specs = [
        ((ax_value * 1.12, 0, 0), ACCENT, 'X  剖面长度 ax', f'{ax_value:g} m',
         (ax_value * 0.62, -offset_y * 0.75, 0)),
        ((0, by_value * 1.12, 0), ACCENT_VIOLET, 'Z  剖面宽度 by', f'{by_value:g} m',
         (-offset_x * 1.35, by_value * 0.45, 0)),
        ((0, 0, z_top * 1.04), ACCENT_GREEN, 'Y  累计高度', f'{z_top:.1f} m',
         (-offset_x * 0.55, -offset_y * 0.55, z_top * 1.12)),
    ]
    for (vector, color, label, value, anchor) in specs:
        ax.quiver(ox, oy, 0, vector[0], vector[1], vector[2],
                  color=color, linewidth=1.8, alpha=0.95, arrow_length_ratio=0.06, zorder=8)
        ax.text(ox + anchor[0], oy + anchor[1], anchor[2],
                f'{label}\n{value}', color=color, fontsize=9,
                ha='center', va='center', alpha=0.95, zorder=9)


def stagger_label_heights(entries, min_gap, z_low, z_high):
    """摊开标注高度：先自下而上推开，再自上而下回压，最后整体夹在可视区间内。"""
    placed = list(entries)
    for i in range(1, len(placed)):
        placed[i] = max(placed[i], placed[i - 1] + min_gap)
    overflow = placed[-1] - z_high
    if overflow > 0:
        placed[-1] = z_high
        for i in range(len(placed) - 2, -1, -1):
            placed[i] = min(placed[i], placed[i + 1] - min_gap)
    if placed[0] < z_low:
        shift = z_low - placed[0]
        placed = [value + shift for value in placed]
    return placed


def generate_3d_visualization(data, output_path):
    """生成3D岩层可视化图像"""
    try:
        layers = data['layers']
        ax_value = float(data['ax'])
        by_value = float(data['by'])

        if not layers:
            raise ValueError('岩层数据不能为空')

        total_thickness = sum(float(layer['h']) for layer in layers)
        avg_thickness = total_thickness / len(layers)

        fig = plt.figure(figsize=(15, 9.6), facecolor=BG_FIGURE)
        ax = fig.add_subplot(111, projection='3d', facecolor=BG_SCENE, computed_zorder=False)

        # 产品要求：在所有导入岩层下方固定绘制一个空心煤层
        coal_height = avg_thickness
        coal_outer_vertices = create_cube_vertices(0, ax_value, 0, by_value, 0, coal_height)
        inner_margin = min(ax_value, by_value) * 0.1
        coal_inner_vertices = create_cube_vertices(
            inner_margin, ax_value - inner_margin,
            inner_margin, by_value - inner_margin,
            coal_height / 2, coal_height
        )

        z_top = coal_height + total_thickness

        # ---- 场景底衬：地面网格与坐标箭头 ----
        pad_x = ax_value * 0.20
        pad_y = by_value * 0.20
        x_lo, x_hi = -pad_x * 1.6, ax_value * 1.62
        y_lo, y_hi = -pad_y * 1.6, by_value * 1.46
        z_lo, z_hi = 0.0, z_top * 1.14
        draw_floor_grid(ax, (-pad_x, ax_value + pad_x), (-pad_y, by_value + pad_y))
        draw_axis_arrows(ax, ax_value, by_value, z_top, pad_x * 1.5, pad_y * 1.5)

        # ---- 固定空心煤层 ----
        coal_faces = create_hollow_cube_faces(coal_outer_vertices, coal_inner_vertices)
        coal_collection = Poly3DCollection(coal_faces, alpha=0.95, facecolor=COAL_FACE,
                                           edgecolor=COAL_EDGE, linewidths=1.0, zorder=1)
        coal_collection.set_zsort('max')
        ax.add_collection3d(coal_collection)

        # ---- 岩层实体：按真实厚度比例自下而上堆叠，最上部能量层整层标红 ----
        # layers 自下而上排列，因此最后一个 power > 0 的即最上面的能量聚集层
        marked_index = None
        for index, layer in enumerate(layers):
            power = layer.get('power')
            if power is not None and power > 0:
                marked_index = index

        label_entries = []
        current_z = coal_height
        for index, layer in enumerate(layers):
            thickness = float(layer['h'])
            name = layer.get('name', f'Layer {index + 1}')
            num = layer.get('num', index + 1)
            power = layer.get('power')
            has_energy = power is not None and power > 0
            is_marked = index == marked_index

            face = ACCENT_RED if is_marked else lithology_color(name)
            vertices = create_cube_vertices(0, ax_value, 0, by_value, current_z, current_z + thickness)
            all_faces = create_cube_faces(vertices)
            if is_marked:
                # 标红层要读成一块实心板：只画侧面会从正面看穿到背面，变成一个空心框
                faces = list(all_faces)
            else:
                faces = list(all_faces[2:])        # 相邻层的顶/底面完全重合，只画侧面避免糊成一片
                if index == 0:
                    faces.append(all_faces[0])     # 最底层补底面
                if index == len(layers) - 1:
                    faces.append(all_faces[1])     # 最顶层补顶面

            body = Poly3DCollection(
                faces,
                alpha=0.92 if is_marked else 0.75,
                facecolor=face,
                edgecolor=shade(face, 1.25) if is_marked else shade(face, 1.15),
                linewidths=1.4 if is_marked else 0.6,
                # 标红层抬到普通岩层之上，避免被前方岩层的半透明面压暗
                zorder=4 if is_marked else 2 + index * 0.01)
            body.set_zsort('max')
            ax.add_collection3d(body)

            label_entries.append({
                'z_mid': current_z + thickness / 2,
                'num': num,
                'name': name,
                'thickness': thickness,
                'power': power if has_energy else None,
                'marked': is_marked,
                'layer_height': current_z - coal_height,
            })

            current_z += thickness

        energy_count = sum(1 for entry in label_entries if entry['power'] is not None)

        # ---- 右侧统一标注：层号 / 岩性 / 厚度（能量层追加能量值与层高）----
        min_gap = z_top * 0.053
        placed = stagger_label_heights([entry['z_mid'] for entry in label_entries],
                                       min_gap, z_top * 0.02, z_hi * 0.99)
        label_x = ax_value * 1.30
        label_y = by_value * 1.24
        for entry, z_label in zip(label_entries, placed):
            text = f"{entry['num']}  {entry['name']}   {entry['thickness']:g} m"
            if entry['power'] is not None:
                text += f"   ·   {entry['power']:.2f} MJ   层高 {entry['layer_height']:.1f} m"
            marked = entry['marked']

            ax.plot([ax_value, label_x], [by_value, label_y], [entry['z_mid'], z_label],
                    color=ACCENT_RED if marked else BORDER,
                    linewidth=0.9 if marked else 0.8,
                    alpha=0.8 if marked else 0.5, zorder=9)
            ax.text(label_x, label_y, z_label, text,
                    color=TEXT_MAIN if marked else TEXT_SOFT,
                    fontsize=8, ha='left', va='center', zorder=10,
                    bbox=dict(boxstyle='round,pad=0.34', facecolor=PANEL_BG,
                              edgecolor=ACCENT_RED if marked else BORDER,
                              linewidth=1.1 if marked else 0.7, alpha=0.95))

        # ---- 视图与坐标系 ----
        # 比例按坐标区间给，而不是按数据范围，这样留白不会把真实厚度比例压歪
        ax.set_box_aspect((x_hi - x_lo, y_hi - y_lo, z_hi - z_lo), zoom=1.22)
        ax.view_init(elev=15, azim=-60)
        ax.set_axis_off()
        ax.set_xlim(x_lo, x_hi)
        ax.set_ylim(y_lo, y_hi)
        ax.set_zlim(z_lo, z_hi)

        # ---- 图面文字：标题、比例尺、图例 ----
        fig.text(0.022, 0.955, '三维岩层结构图', color=TEXT_MAIN, fontsize=17, weight='bold')
        subtitle = '岩层按真实厚度比例自下而上堆叠；底部为固定空心煤层。'
        if marked_index is not None:
            subtitle = subtitle[:-1] + '，红色为最上部能量聚集层。'
        fig.text(0.022, 0.922, subtitle, color=TEXT_MUTED, fontsize=10)

        scale_text = (f"岩层数：{len(layers)} 层\n"
                      f"总厚度：{total_thickness:g} m（不含固定煤层 {coal_height:.1f} m）\n"
                      f"剖面长度 ax：{ax_value:g} m　剖面宽度 by：{by_value:g} m\n"
                      f"能量聚集层：{energy_count} 层"
                      + (f"（标红：第 {label_entries[marked_index]['num']} 层）"
                         if marked_index is not None else ""))
        fig.text(0.022, 0.045, scale_text, color=TEXT_SOFT, fontsize=9.5, va='bottom',
                 bbox=dict(boxstyle='round,pad=0.6', facecolor=PANEL_BG,
                           edgecolor=BORDER, linewidth=1.0, alpha=0.95))

        handles = [
            Patch(facecolor=DEFAULT_LITHOLOGY_COLOR, edgecolor=shade(DEFAULT_LITHOLOGY_COLOR, 1.4),
                  alpha=0.75, label='岩层（按岩性配色）'),
            Patch(facecolor=COAL_FACE, edgecolor=COAL_EDGE, linewidth=1.2, label='固定空心煤层'),
        ]
        if marked_index is not None:
            handles.append(Patch(facecolor=ACCENT_RED, edgecolor=shade(ACCENT_RED, 1.3),
                                 alpha=0.92, label='最上部能量聚集层（整层标红）'))
        legend = ax.legend(handles=handles, loc='upper right', bbox_to_anchor=(1.0, 0.98),
                           fontsize=9.5, facecolor=PANEL_BG, edgecolor=BORDER, framealpha=0.95)
        for text in legend.get_texts():
            text.set_color(TEXT_SOFT)

        plt.savefig(output_path, dpi=150, facecolor=fig.get_facecolor(), pad_inches=0.25)
        plt.close(fig)

        return True, "3D图像生成成功"

    except Exception as e:
        return False, f"生成3D图像时出错: {str(e)}"


def main():
    """主函数"""
    if len(sys.argv) != 3:
        print("Usage: python generate_3d_layers.py <input_json_file> <output_image_file>")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2]

    try:
        with open(input_file, 'r', encoding='utf-8') as f:
            data = json.load(f)

        success, message = generate_3d_visualization(data, output_file)

        if success:
            print(f"SUCCESS: {message}")
            print(f"OUTPUT: {output_file}")
        else:
            print(f"ERROR: {message}")
            sys.exit(1)

    except FileNotFoundError:
        print(f"ERROR: 输入文件不存在: {input_file}")
        sys.exit(1)
    except json.JSONDecodeError:
        print(f"ERROR: JSON文件格式错误: {input_file}")
        sys.exit(1)
    except Exception as e:
        print(f"ERROR: {str(e)}")
        sys.exit(1)


if __name__ == "__main__":
    main()
