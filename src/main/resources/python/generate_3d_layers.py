#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
3D岩层可视化脚本
根据岩层数据生成3D图像，包含煤层和各个岩层
"""

import json
import sys
import numpy as np
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
import matplotlib.patches as patches
from matplotlib.colors import to_rgba
import os

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['SimHei', 'Arial Unicode MS', 'DejaVu Sans']  # 支持中文显示
plt.rcParams['axes.unicode_minus'] = False  # 正常显示负号

def create_cube_vertices(x_min, x_max, y_min, y_max, z_min, z_max):
    """创建立方体的顶点"""
    vertices = [
        [x_min, y_min, z_min], [x_max, y_min, z_min], [x_max, y_max, z_min], [x_min, y_max, z_min],  # 底面
        [x_min, y_min, z_max], [x_max, y_min, z_max], [x_max, y_max, z_max], [x_min, y_max, z_max]   # 顶面
    ]
    return vertices

def create_cube_edges(vertices):
    """创建立方体的边线（用于线框显示）"""
    edges = [
        # 底面的4条边
        [vertices[0], vertices[1]], [vertices[1], vertices[2]], 
        [vertices[2], vertices[3]], [vertices[3], vertices[0]],
        # 顶面的4条边
        [vertices[4], vertices[5]], [vertices[5], vertices[6]], 
        [vertices[6], vertices[7]], [vertices[7], vertices[4]],
        # 连接底面和顶面的4条边
        [vertices[0], vertices[4]], [vertices[1], vertices[5]], 
        [vertices[2], vertices[6]], [vertices[3], vertices[7]]
    ]
    return edges
def create_cube_faces(vertices):
    """创建立方体的面"""
    faces = [
        [vertices[0], vertices[1], vertices[2], vertices[3]],  # 底面
        [vertices[4], vertices[5], vertices[6], vertices[7]],  # 顶面
        [vertices[0], vertices[1], vertices[5], vertices[4]],  # 前面
        [vertices[2], vertices[3], vertices[7], vertices[6]],  # 后面
        [vertices[1], vertices[2], vertices[6], vertices[5]],  # 右面
        [vertices[0], vertices[3], vertices[7], vertices[4]]   # 左面
    ]
    return faces

def create_hollow_cube_faces(outer_vertices, inner_vertices):
    """创建空心立方体的面（外部立方体挖掉内部立方体）"""
    faces = []
    
    # 外部立方体的面（除了顶面）
    faces.extend([
        [outer_vertices[0], outer_vertices[1], outer_vertices[2], outer_vertices[3]],  # 底面
        [outer_vertices[0], outer_vertices[1], outer_vertices[5], outer_vertices[4]],  # 前面
        [outer_vertices[2], outer_vertices[3], outer_vertices[7], outer_vertices[6]],  # 后面
        [outer_vertices[1], outer_vertices[2], outer_vertices[6], outer_vertices[5]],  # 右面
        [outer_vertices[0], outer_vertices[3], outer_vertices[7], outer_vertices[4]]   # 左面
    ])
    
    # 内部空洞的面
    faces.extend([
        [inner_vertices[4], inner_vertices[5], inner_vertices[6], inner_vertices[7]],  # 内部顶面
        [inner_vertices[0], inner_vertices[1], inner_vertices[5], inner_vertices[4]],  # 内部前面
        [inner_vertices[2], inner_vertices[3], inner_vertices[7], inner_vertices[6]],  # 内部后面
        [inner_vertices[1], inner_vertices[2], inner_vertices[6], inner_vertices[5]],  # 内部右面
        [inner_vertices[0], inner_vertices[3], inner_vertices[7], inner_vertices[4]]   # 内部左面
    ])
    
    # 添加连接面 - 连接外部立方体和内部空洞的边缘
    # 前连接面（外部前面上边缘到内部前面上边缘）
    faces.extend([
        [outer_vertices[4], inner_vertices[4], inner_vertices[5], outer_vertices[5]],  # 前上连接
        [outer_vertices[5], inner_vertices[5], inner_vertices[1], outer_vertices[1]],  # 前右连接  
        [outer_vertices[1], inner_vertices[1], inner_vertices[0], outer_vertices[0]],  # 前下连接
        [outer_vertices[0], inner_vertices[0], inner_vertices[4], outer_vertices[4]]   # 前左连接
    ])
    
    # 后连接面
    faces.extend([
        [outer_vertices[7], inner_vertices[7], inner_vertices[6], outer_vertices[6]],  # 后上连接
        [outer_vertices[6], inner_vertices[6], inner_vertices[2], outer_vertices[2]],  # 后右连接
        [outer_vertices[2], inner_vertices[2], inner_vertices[3], outer_vertices[3]],  # 后下连接  
        [outer_vertices[3], inner_vertices[3], inner_vertices[7], outer_vertices[7]]   # 后左连接
    ])
    
    # 左连接面
    faces.extend([
        [outer_vertices[4], inner_vertices[4], inner_vertices[7], outer_vertices[7]],  # 左上连接
        [outer_vertices[7], inner_vertices[7], inner_vertices[3], outer_vertices[3]],  # 左后连接
        [outer_vertices[3], inner_vertices[3], inner_vertices[0], outer_vertices[0]],  # 左下连接
        [outer_vertices[0], inner_vertices[0], inner_vertices[4], outer_vertices[4]]   # 左前连接
    ])
    
    # 右连接面  
    faces.extend([
        [outer_vertices[5], inner_vertices[5], inner_vertices[6], outer_vertices[6]],  # 右上连接
        [outer_vertices[6], inner_vertices[6], inner_vertices[2], outer_vertices[2]],  # 右后连接
        [outer_vertices[2], inner_vertices[2], inner_vertices[1], outer_vertices[1]],  # 右下连接
        [outer_vertices[1], inner_vertices[1], inner_vertices[5], outer_vertices[5]]   # 右前连接
    ])
    
    return faces

def generate_3d_visualization(data, output_path):
    """生成3D岩层可视化图像"""
    try:
        # 解析数据
        layers = data['layers']
        ax_value = data['ax']
        by_value = data['by']
        
        # ========== 特殊处理模式判断 ==========
        # 当ax >= 280时启用特殊显示逻辑
        # 特殊处理包括：
        # 1. 第15层绿色能量立方体改为红色
        # 2. 第18层不显示绿色能量立方体
        # 3. 汇聚线终止于第15层下平面而不是最后一个有能量的层
        # 注意：删除此段代码可恢复原始逻辑
        is_special_mode = ax_value >= 280
        special_layer_15_bottom_z = None  # 用于存储第15层的下平面Z坐标
        # ========== 特殊处理模式判断结束 ==========
        
        # 计算平均层厚
        total_thickness = sum(float(layer['h']) for layer in layers)
        avg_thickness = total_thickness / len(layers)
        
        # 设置图形 - 扩大图像尺寸以确保3D图和标注完整显示在图片内
        fig = plt.figure(figsize=(26, 20))
        ax = fig.add_subplot(111, projection='3d')
        
        # 设置坐标系范围 - 优化buffer值以充满坐标系
        max_dim = max(ax_value, by_value, total_thickness)
        buffer = max_dim * 0.1  # 减小buffer值，使图像更充满坐标系
        
        # 创建煤层（最下方的空心立方体）- 调整坐标使零点对齐
        coal_height = avg_thickness
        coal_outer_vertices = create_cube_vertices(
            0, ax_value, 
            0, by_value, 
            0, coal_height
        )
        
        # 煤层内部空洞 - 稍微缩小内部空洞
        inner_margin = min(ax_value, by_value) * 0.1  # 内部边距
        coal_inner_vertices = create_cube_vertices(
            inner_margin, ax_value - inner_margin, 
            inner_margin, by_value - inner_margin, 
            coal_height/2, coal_height
        )
        
        # 创建煤层面
        coal_faces = create_hollow_cube_faces(coal_outer_vertices, coal_inner_vertices)
        coal_collection = Poly3DCollection(coal_faces, alpha=0.6, facecolor='black', edgecolor='darkgray')
        ax.add_collection3d(coal_collection)
        
        # 添加从煤层挖空部分顶点汇聚到上方中心点的四条线
        # 煤层内部空洞的上表面四个顶点 - 使用新的内部边距
        inner_top_vertices = [
            [inner_margin, inner_margin, coal_height],           # 左前角
            [ax_value - inner_margin, inner_margin, coal_height],    # 右前角
            [ax_value - inner_margin, by_value - inner_margin, coal_height],  # 右后角
            [inner_margin, by_value - inner_margin, coal_height]     # 左后角
        ]
        
        # 计算上平面中心点
        center_x = ax_value / 2
        center_y = by_value / 2
        
        # 计算汇聚点高度（与上平面夹角70度）
        # 使用最远角点到中心的距离来计算
        max_distance = np.sqrt((ax_value/2)**2 + (by_value/2)**2)
        convergence_height = coal_height + max_distance * np.tan(np.radians(70))
        convergence_point = [center_x, center_y, convergence_height]
        
        # 创建岩层
        current_z = coal_height
        
        # 计算最后一层的上表面高度
        total_height = coal_height + sum(float(layer['h']) for layer in layers)
        
        # 找到最后一个带有能量的层，用于确定汇聚线的消失点
        last_energy_layer_bottom = coal_height  # 默认为煤层顶部
        last_energy_layer_top = coal_height  # 默认为煤层顶部
        temp_z = coal_height  # 使用临时变量计算高度
        for i, layer in enumerate(layers):
            layer_thickness = float(layer['h'])
            layer_energy = layer.get('power')
            if layer_energy is not None and layer_energy > 0:
                # 更新最后一个有能量层的底部和顶部高度
                last_energy_layer_bottom = temp_z
                last_energy_layer_top = temp_z + layer_thickness
            temp_z += layer_thickness
        
        # 重新计算汇聚点，使其位于最后一个有能量层的上方
        convergence_height = last_energy_layer_top + max_distance * np.tan(np.radians(70))
        convergence_point = [center_x, center_y, convergence_height]
        
        for i, layer in enumerate(layers):
            layer_thickness = float(layer['h'])
            layer_name = layer.get('name', f'Layer {i+1}')
            layer_energy = layer.get('power')  # 获取岩层能量
            layer_num = layer.get('num', i+1)  # 获取岩层编号
            
            # ========== 特殊处理：记录第15层的下平面Z坐标 ==========
            if is_special_mode and layer_num == 15:
                special_layer_15_bottom_z = current_z
            # ========== 特殊处理结束 ==========
            
            # 计算当前层的层高（当前层下所有岩层的层厚之和，不包括煤层）
            layer_height = current_z - coal_height
            
            # 创建岩层立方体 - 调整坐标使零点对齐
            layer_vertices = create_cube_vertices(
                0, ax_value, 
                0, by_value, 
                current_z, current_z + layer_thickness
            )
            
            # 创建岩层立方体的边线（线框模式）
            layer_edges = create_cube_edges(layer_vertices)
            
            # 绘制岩层的边线，只显示外框，内部透明
            for edge in layer_edges:
                xs, ys, zs = zip(*edge)
                ax.plot(xs, ys, zs, color='lightgray', linewidth=0.5, alpha=0.6)
            
            # ========== 特殊处理：能量立方体显示逻辑 ==========
            # 原始逻辑：如果岩层有能量，计算汇聚线与该岩层下平面的交汇点并绘制浅绿色平面
            # 特殊处理：当ax >= 280时，第15层改为红色，第18层不显示
            should_draw_energy_cube = False
            energy_cube_color = 'lightgreen'
            energy_edge_color = 'darkgreen'
            
            if layer_energy is not None and layer_energy > 0:
                if is_special_mode:
                    if layer_num == 15:
                        # 第15层：绿色改为红色
                        should_draw_energy_cube = True
                        energy_cube_color = 'lightcoral'
                        energy_edge_color = 'darkred'
                    elif layer_num == 18:
                        # 第18层：不显示绿色立方体
                        should_draw_energy_cube = False
                    else:
                        # 其他有能量的层：正常显示绿色
                        should_draw_energy_cube = True
                else:
                    # 正常模式：所有有能量的层都显示绿色
                    should_draw_energy_cube = True
            
            if should_draw_energy_cube:
                # ========== 特殊处理结束 ==========
                layer_bottom_z = current_z
                
                # 计算四条汇聚线与该岩层下平面的交汇点
                intersection_points = []
                for vertex in inner_top_vertices:
                    # 计算线的方向向量
                    direction = np.array([convergence_point[0] - vertex[0], 
                                        convergence_point[1] - vertex[1], 
                                        convergence_point[2] - vertex[2]])
                    
                    # 计算参数t，使得线上的点z坐标等于layer_bottom_z
                    if direction[2] != 0:  # 避免除零
                        t = (layer_bottom_z - vertex[2]) / direction[2]
                        if 0 <= t <= 1:  # 交点在线段范围内
                            intersection_x = vertex[0] + t * direction[0]
                            intersection_y = vertex[1] + t * direction[1]
                            intersection_points.append([intersection_x, intersection_y, layer_bottom_z])
                
                # 如果有四个交汇点，绘制浅绿色立方体
                if len(intersection_points) == 4:
                    # 计算立方体的高度（使用当前层的厚度h）
                    cube_height = layer_thickness
                    
                    # 创建立方体的顶面顶点（在交汇点基础上向上延伸h的高度）
                    top_intersection_points = []
                    for point in intersection_points:
                        top_point = [point[0], point[1], point[2] + cube_height]
                        top_intersection_points.append(top_point)
                    
                    # 创建立方体的所有面
                    cube_faces = []
                    
                    # 底面
                    cube_faces.append([intersection_points[0], intersection_points[1], 
                                     intersection_points[2], intersection_points[3]])
                    
                    # 顶面
                    cube_faces.append([top_intersection_points[0], top_intersection_points[1], 
                                     top_intersection_points[2], top_intersection_points[3]])
                    
                    # 四个侧面
                    for i in range(4):
                        next_i = (i + 1) % 4
                        side_face = [intersection_points[i], intersection_points[next_i],
                                   top_intersection_points[next_i], top_intersection_points[i]]
                        cube_faces.append(side_face)
                    
                    # ========== 特殊处理：使用动态颜色绘制立方体 ==========
                    # 原始代码：绘制浅绿色立方体 - 增强3D视觉效果
                    # 特殊处理：根据前面设置的颜色变量来绘制
                    cube_collection = Poly3DCollection(cube_faces, alpha=0.7, 
                                                     facecolors=energy_cube_color, 
                                                     edgecolors=energy_edge_color, linewidths=2)
                    ax.add_collection3d(cube_collection)
                    # ========== 特殊处理结束 ==========
                    
                    # 在汇聚线附近添加集中的标注
                    # 计算绿色层的中心位置
                    center_x_green = sum([p[0] for p in intersection_points]) / 4
                    center_y_green = sum([p[1] for p in intersection_points]) / 4
                    center_z_green = current_z + layer_thickness / 2
                    
                    # 计算当前层数（从1开始计数有能量的层）
                    energy_layer_count = sum(1 for layer in layers[:i+1] if layer.get('power') is not None and layer.get('power') > 0)
                    
                    # 选择最靠近中心的汇聚线作为标注基准线
                    # 计算每条汇聚线到中心的距离，选择最近的一条
                    distances = []
                    for vertex in inner_top_vertices:
                        dist = np.sqrt((vertex[0] - center_x)**2 + (vertex[1] - center_y)**2)
                        distances.append(dist)
                    
                    # 选择距离中心最近的汇聚线
                    closest_line_index = distances.index(min(distances))
                    base_vertex = inner_top_vertices[closest_line_index]
                    
                    # 计算汇聚线与当前层中心高度的交点
                    direction = np.array([convergence_point[0] - base_vertex[0], 
                                        convergence_point[1] - base_vertex[1], 
                                        convergence_point[2] - base_vertex[2]])
                    
                    if direction[2] != 0:
                        t = (center_z_green - base_vertex[2]) / direction[2]
                        if 0 <= t <= 1:
                            line_x = base_vertex[0] + t * direction[0]
                            line_y = base_vertex[1] + t * direction[1]
                        else:
                            line_x, line_y = center_x_green, center_y_green
                    else:
                        line_x, line_y = center_x_green, center_y_green
                    
                    # 计算标注偏移距离，确保不重叠且在坐标系内
                    offset_base = max(ax_value, by_value) * 0.15  # 基础偏移距离
                    
                    # 将所有标注放在汇聚线的一侧，垂直排列
                    # 计算垂直于汇聚线的方向向量
                    if abs(direction[0]) > abs(direction[1]):
                        # 汇聚线主要沿X方向，标注沿Y方向偏移
                        offset_x = 0
                        offset_y = offset_base if line_y < by_value/2 else -offset_base
                    else:
                        # 汇聚线主要沿Y方向，标注沿X方向偏移
                        offset_x = offset_base if line_x < ax_value/2 else -offset_base
                        offset_y = 0
                    
                    # 将标注放置到坐标系外，但确保在坐标轴范围内
                    margin_distance = max(ax_value, by_value) * 0.2  # 减小距离，确保在坐标轴范围内
                    
                    if abs(direction[0]) > abs(direction[1]):
                        # 汇聚线主要沿X方向，标注沿Y方向完全外置
                        annotation_x = line_x
                        if line_y < by_value/2:
                            # 放置在上方，但确保在坐标轴范围内
                            annotation_y = min(by_value + margin_distance, by_value * 1.6)  # 限制在坐标轴范围内
                        else:
                            # 放置在下方，但确保在坐标轴范围内
                            annotation_y = max(-margin_distance, -by_value * 0.6)  # 限制在坐标轴范围内
                    else:
                        # 汇聚线主要沿Y方向，标注沿X方向完全外置
                        annotation_y = line_y
                        if line_x < ax_value/2:
                            # 放置在右侧，但确保在坐标轴范围内
                            annotation_x = min(ax_value + margin_distance, ax_value * 1.6)  # 限制在坐标轴范围内
                        else:
                            # 放置在左侧，但确保在坐标轴范围内
                            annotation_x = max(-margin_distance, -ax_value * 0.6)  # 限制在坐标轴范围内
                    
                    # 获取层号（从num字段）
                    layer_num = layer.get('num', 0)
                    
                    # 构建合并的单行信息字符串
                    info_parts = []
                    info_parts.append(f'第{layer_num}层')
                    info_parts.append(f'能量:{layer_energy:.2f}MJ')
                    info_parts.append(f'厚度:{layer_thickness:.1f}m')
                    info_parts.append(f'层高:{layer_height:.1f}m')
                    
                    # 将所有信息组合成一行
                    combined_text = ' | '.join(info_parts)
                    
                    # 合并的单行标注
                    ax.text(annotation_x, annotation_y, center_z_green, 
                           combined_text, fontsize=10, ha='center', va='center',
                           bbox=dict(boxstyle="round,pad=0.3", facecolor="lightblue", 
                                   alpha=0.9, edgecolor='blue', linewidth=1),
                           weight='normal')
                    
                    # 从汇聚线交点到标注组的连接线 - 改为虚线且更细
                    ax.plot([line_x, annotation_x], [line_y, annotation_y], 
                           [center_z_green, center_z_green], 
                           color='red', linewidth=1.5, alpha=0.8, linestyle='--')
                    
                    # 添加箭头指示方向（从交点指向标注）
                    from mpl_toolkits.mplot3d.art3d import Line3DCollection
                    # 计算箭头方向
                    arrow_length = 0.1 * max(ax_value, by_value)
                    dx = annotation_x - line_x
                    dy = annotation_y - line_y
                    length = np.sqrt(dx**2 + dy**2)
                    if length > 0:
                        dx_norm = dx / length * arrow_length
                        dy_norm = dy / length * arrow_length
                        # 在标注端绘制箭头
                        ax.plot([annotation_x - dx_norm, annotation_x], 
                               [annotation_y - dy_norm, annotation_y], 
                               [center_z_green, center_z_green], 
                               color='darkred', linewidth=3, alpha=0.9)
                    
                    # 在汇聚线交点处添加标记点
                    ax.scatter([line_x], [line_y], [center_z_green], 
                              color='red', s=60, alpha=0.9, marker='o', 
                              edgecolors='darkred', linewidth=2)
            
            current_z += layer_thickness
        
        # ========== 特殊处理：汇聚线终点计算 ==========
        # 绘制四条汇聚线（限制在最后一个有能量层的下表面以内）
        # 特殊处理：当ax >= 280时，汇聚线终止于第15层下平面
        # 增强汇聚线的可见性和层次关系
        for i, vertex in enumerate(inner_top_vertices):
            # 计算线的终点
            if is_special_mode and special_layer_15_bottom_z is not None:
                # 特殊模式：限制在第15层下表面
                target_z = special_layer_15_bottom_z
            else:
                # 正常模式：限制在最后一个有能量层的下表面
                target_z = last_energy_layer_bottom
            
            if convergence_point[2] > target_z:
                # 计算与目标层下表面的交点
                direction = np.array([convergence_point[0] - vertex[0], 
                                    convergence_point[1] - vertex[1], 
                                    convergence_point[2] - vertex[2]])
                if direction[2] != 0:
                    t = (target_z - vertex[2]) / direction[2]
                    end_x = vertex[0] + t * direction[0]
                    end_y = vertex[1] + t * direction[1]
                    end_z = target_z
                else:
                    end_x, end_y, end_z = convergence_point
            else:
                end_x, end_y, end_z = convergence_point
            # ========== 特殊处理结束 ==========
            
            # 使用不同颜色和样式来区分四条汇聚线
            colors = ['red', 'darkred', 'crimson', 'maroon']
            line_styles = ['-', '-', '-', '-']
            
            # 绘制主汇聚线（更粗更明显）
            ax.plot([vertex[0], end_x], 
                   [vertex[1], end_y], 
                   [vertex[2], end_z], 
                   color=colors[i % len(colors)], linewidth=3, alpha=0.9, 
                   linestyle=line_styles[i % len(line_styles)], 
                   label=f'汇聚线{i+1}' if i == 0 else "")
            
            # 添加汇聚线的阴影效果（稍微偏移的细线）
            shadow_offset = 0.1
            ax.plot([vertex[0] + shadow_offset, end_x + shadow_offset], 
                   [vertex[1] + shadow_offset, end_y + shadow_offset], 
                   [vertex[2], end_z], 
                   color='black', linewidth=1, alpha=0.3, linestyle='-')
            
            # 在汇聚线的起点添加小球标记
            ax.scatter([vertex[0]], [vertex[1]], [vertex[2]], 
                      color=colors[i % len(colors)], s=50, alpha=0.8, 
                      edgecolors='black', linewidth=1)
        
        # 在汇聚点添加特殊标记
        if convergence_point[2] <= last_energy_layer_bottom:
            ax.scatter([convergence_point[0]], [convergence_point[1]], [convergence_point[2]], 
                      color='gold', s=100, alpha=0.9, marker='*', 
                      edgecolors='black', linewidth=2, label='汇聚点')
        
        # 设置坐标轴
        ax.set_xlabel('X轴 (ax方向)', fontsize=14)
        ax.set_ylabel('Y轴 (by方向)', fontsize=14)
        ax.set_zlabel('Z轴 (高度)', fontsize=14)
        
        # 设置坐标轴范围 - 进一步增大边距确保标注完全可见
        margin_x = ax_value * 0.8  # X轴扩展80%
        margin_y = by_value * 0.8  # Y轴扩展80%
        ax.set_xlim(-margin_x, ax_value + margin_x)
        ax.set_ylim(-margin_y, by_value + margin_y)
        ax.set_zlim(0, current_z)
        
        # 设置标题 - 更新描述
        ax.set_title('3D地质分层图 - 煤层(黑色)、岩层(灰框)、能量层(绿色)\n标注已外置到坐标系外', 
                    fontsize=16, pad=20, weight='bold')
        
        # 优化视角设置 - 更好的3D观察角度
        ax.view_init(elev=25, azim=35)
        
        # 增强网格效果
        ax.grid(True, alpha=0.4, linewidth=0.8)
        
        # 设置背景颜色
        ax.xaxis.pane.fill = False
        ax.yaxis.pane.fill = False
        ax.zaxis.pane.fill = False
        
        # 设置坐标轴线条样式
        ax.xaxis.pane.set_edgecolor('gray')
        ax.yaxis.pane.set_edgecolor('gray')
        ax.zaxis.pane.set_edgecolor('gray')
        ax.xaxis.pane.set_alpha(0.1)
        ax.yaxis.pane.set_alpha(0.1)
        ax.zaxis.pane.set_alpha(0.1)
        
        # 添加图例
        ax.legend(loc='upper left', bbox_to_anchor=(0.02, 0.98), fontsize=12)
        
        # 保存图像 - 使用更宽松的布局设置确保完整显示，增大图片尺寸
        plt.tight_layout(pad=4.0)  # 增加内边距以容纳标注
        plt.savefig(output_path, dpi=300, bbox_inches='tight', pad_inches=1.5)  # 增加外边距确保标注完整
        plt.close()
        
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
        # 读取输入数据
        with open(input_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        # 生成3D图像
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