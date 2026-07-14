function plotCubes(a_h, b_h,c_l,c_w,c_h,a1_l,a1_w,j1,j2,u1,u2,h1,h2)

    a_l = (10*a_h+a1_l);
    a_w = (5*a_h+a1_w);
    % a1_l = (a_l + c_l)/2;
    % a1_w = (a_w + c_w)/2;
    % 定义立方体的顶点坐标
    vertices_a = [
        % 整体框架部分
        0 0 0;  % 1
        0 0 a_h;  % 2
        a_l 0 a_h ;% 3
        a_l 0 0;% 4

        0 a_w 0;  % 5
        0 a_w a_h;  % 6
        a_l a_w a_h   % 7
        a_l a_w 0;  % 8

        % 中间挖空的部分
        (a_l/2 - a1_l/2) (a_w/2 - a1_w/2) a_h/2 % 9
        (a_l/2 - a1_l/2) (a_w/2 - a1_w/2) a_h   % 10
        (a_l/2 + a1_l/2) (a_w/2 - a1_w/2) a_h   % 11
        (a_l/2 + a1_l/2) (a_w/2 - a1_w/2) a_h/2 % 12

        (a_l/2 - a1_l/2) (a_w/2 + a1_w/2) a_h/2 % 13
        (a_l/2 - a1_l/2) (a_w/2 + a1_w/2) a_h   % 14
        (a_l/2 + a1_l/2) (a_w/2 + a1_w/2) a_h   % 15
        (a_l/2 + a1_l/2) (a_w/2 + a1_w/2) a_h/2 % 16


    ];

      vertices_b = [
        % 整体框架部分
        0 0 a_h;  % 1
        0 0 a_h+b_h;  % 2
        a_l 0 a_h+b_h ;% 3
        a_l 0 a_h;% 4

        0 a_w a_h;  % 5
        0 a_w a_h+b_h;  % 6
        a_l a_w a_h+b_h   % 7
        a_l a_w a_h;  % 8

    ];

       vertices_c = [
        % 整体框架部分
        (a_l/2 - c_l/2) (a_w/2 - c_w/2) a_h+b_h % 1
        (a_l/2 - c_l/2) (a_w/2 - c_w/2) a_h+b_h+c_h   % 2
        (a_l/2 + c_l/2) (a_w/2 - c_w/2) a_h+b_h+c_h   % 3
        (a_l/2 + c_l/2) (a_w/2 - c_w/2) a_h+b_h % 4

        (a_l/2 - c_l/2) (a_w/2 + c_w/2) a_h+b_h % 5
        (a_l/2 - c_l/2) (a_w/2 + c_w/2) a_h+b_h+c_h   % 6
        (a_l/2 + c_l/2) (a_w/2 + c_w/2) a_h+b_h+c_h   % 7
        (a_l/2 + c_l/2) (a_w/2 + c_w/2) a_h+b_h % 8

    ];

     



    % 定义每个面的顶点索引
    faces_a1 = [
        1 2 3 4;  % 后面
        5 6 7 8;  % 前面
        1 5 8 4;  % 底面
        1 2 6 5;  % 左面
        4 3 7 8;  % 右面

        9 10 11 12; % 后面
        13 14 15 16;% 前面
        9 13 16 12; % 底面
        9 10 14 13; % 左面
        11 12 16 15;% 右面
    ];

    faces_a2 = [
         %上表面
        2 10 14 6;
        6 14 15 7;
        7 15 11 3;
        3 11 10 2;];


      % 定义每个面的顶点索引
    faces_b = [
        1 2 3 4;  % 后面
        5 6 7 8;  % 前面
        1 5 8 4;  % 底面
        1 2 6 5;  % 左面
        4 3 7 8;  % 右面
        ];



          % 定义每个面的顶点索引
    faces_c = [
        1 2 3 4;  % 后面
        5 6 7 8;  % 前面
        1 5 8 4;  % 底面
        1 2 6 5;  % 左面
        4 3 7 8;  % 右面
        ];


          % 定义两个点的坐标
    P1 = [(a_l/2 - a1_l/2),(a_w/2 - a1_w/2),a_h];
    P2 = [(a_l/2 + a1_l/2),(a_w/2 - a1_w/2),a_h];
    P3 = [(a_l/2 - a1_l/2),(a_w/2 + a1_w/2),a_h];
    P4 = [(a_l/2 + a1_l/2),(a_w/2 + a1_w/2),a_h];

    Q1 = [(a_l/2 - c_l/2),(a_w/2 - c_w/2),a_h+b_h];
    Q2 = [(a_l/2 + c_l/2),(a_w/2 - c_w/2),a_h+b_h];
    Q3 = [(a_l/2 - c_l/2),(a_w/2 + c_w/2),a_h+b_h];
    Q4 = [(a_l/2 + c_l/2),(a_w/2 + c_w/2),a_h+b_h];

   

     % 创建图形
    figure;
    hold on;
    
     % ------------绘制a----------------
     % 绘制每个面
     for i = 1:size(faces_a1, 1)
         % 获取当前面的顶点索引
         face_vertices = vertices_a(faces_a1(i, :), :);
     
         % 绘制当前面
         patch('Faces', [1 2 3 4], 'Vertices', face_vertices, 'FaceColor', 'black', 'FaceAlpha', 0.5, 'EdgeColor', 'k', 'LineWidth', 2);
     end
     
      % 绘制上表面-无边框线
     for i = 1:size(faces_a2, 1)
         % 获取当前面的顶点索引
         face_vertices = vertices_a(faces_a2(i, :), :);
     
         % 绘制当前面
         patch('Faces', [1 2 3 4], 'Vertices', face_vertices, 'FaceColor', 'black','EdgeColor', 'none');
     end


    % ------------绘制b----------------

      % 绘制上表面-无边框线
    for i = 1:size(faces_b, 1)
        % 获取当前面的顶点索引
        face_vertices = vertices_b(faces_b(i, :), :);
        
        % 绘制当前面
        patch('Faces', [1 2 3 4], 'Vertices', face_vertices,  'EdgeColor', 'k', 'LineStyle', '--', 'FaceAlpha', 0.1);
    end




      % 绘制上表面-无边框线
    for i = 1:size(faces_c, 1)
        % 获取当前面的顶点索引
        face_vertices = vertices_c(faces_c(i, :), :);
        
        % 绘制当前面
        patch('Faces', [1 2 3 4], 'Vertices', face_vertices,'FaceColor',...
        'red', 'EdgeColor', 'k', 'LineStyle', '--', 'LineWidth', 1);
 
    end

    % 在图形中绘制一条连接这两个点的线
    plot3([P1(1), Q1(1)], [P1(2), Q1(2)], [P1(3), Q1(3)], 'm-', 'LineWidth', 1);
    plot3([P2(1), Q2(1)], [P2(2), Q2(2)], [P2(3), Q2(3)], 'm-', 'LineWidth', 1);
    plot3([P3(1), Q3(1)], [P3(2), Q3(2)], [P3(3), Q3(3)], 'm-', 'LineWidth', 1);
    plot3([P4(1), Q4(1)], [P4(2), Q4(2)], [P4(3), Q4(3)], 'm-', 'LineWidth', 1);


    
         % 定义三点
    T1 = [(a_l/2 - a1_l/2), a_w/2, a_h];  % 点1
    T2 = [a_l/2, a_w/2, a_h+b_h+c_h+a_h];  % 点3
    T3 = [(a_l/2 + a1_l/2), a_w/2, a_h];  % 点2
    % X, Y, Z 坐标
    X = [T1(1), T2(1), T3(1)];
    Y = [T1(2), T2(2), T3(2)];
    Z = [T1(3), T2(3), T3(3)];

    % 创建插值的点
    t = linspace(1, 3, 100);  % 插值的区间 [1, 2, 3] 上产生100个点
    
    % 使用样条插值进行插值
    X_interp = spline([1, 2, 3], X, t);
    Y_interp = spline([1, 2, 3], Y, t);
    Z_interp = spline([1, 2, 3], Z, t);
    
    % 绘制插值曲线
    % plot3(X_interp, Y_interp, Z_interp, 'b-', 'LineWidth', 2);  % 紫色线
    

    str = "(" + u1 + "," + j1 + ")";
    % 计算1/3处的位置的索引
    index_1_3 = round(length(X_interp) / 6);  % 1/3处的索引，使用 round 以确保为整数
    
    % 获取1/3处的坐标
    X_1_3 = X_interp(index_1_3);
    Y_1_3 = Y_interp(index_1_3);
    Z_1_3 = Z_interp(index_1_3);
    
    % 在1/3处添加标注
    % text(X_1_3, Y_1_3, Z_1_3, str, 'Color', 'blue', 'FontSize', 12);



    % 定义三点
    T1 = [a_l/2, (a_w/2 - a1_w/2), a_h];  % 点1
    T2 = [a_l/2, a_w/2, a_h+b_h+c_h+a_h];  % 点3
    T3 = [a_l/2 , (a_w/2 + a1_w/2), a_h];  % 点2
    % X, Y, Z 坐标
    X = [T1(1), T2(1), T3(1)];
    Y = [T1(2), T2(2), T3(2)];
    Z = [T1(3), T2(3), T3(3)];

    % 创建插值的点
    t = linspace(1, 3, 100);  % 插值的区间 [1, 2, 3] 上产生100个点
    
    % 使用样条插值进行插值
    X_interp = spline([1, 2, 3], X, t);
    Y_interp = spline([1, 2, 3], Y, t);
    Z_interp = spline([1, 2, 3], Z, t);
    
    % 绘制插值曲线
    % plot3(X_interp, Y_interp, Z_interp, 'r-', 'LineWidth', 2);  % 紫色线
    
   
  
     str = "(" + u2 + "," + j2 + ")";
    % 计算1/3处的位置的索引
    index_1_3 = round(length(X_interp) / 6);  % 1/3处的索引，使用 round 以确保为整数
    
    % 获取1/3处的坐标
    X_1_3 = X_interp(index_1_3);
    Y_1_3 = Y_interp(index_1_3);
    Z_1_3 = Z_interp(index_1_3);
    
    % 在1/3处添加标注
    % text(X_1_3, Y_1_3, Z_1_3, str, 'Color', 'red', 'FontSize', 12);




    S1 = [(a_l/2 - a1_l/2),-1,0];
    S2 = [(a_l/2 + a1_l/2),-1,0];

    S3 = [(a_l/2),-1,a_h];
    S4 = [(a_l/2),-1,a_h+b_h];

    % 绘制直线S1和S2
    plot3([S1(1), S2(1)], [S1(2), S2(2)], [S1(3), S2(3)], 'r-', 'LineWidth', 4);
    
    % 绘制直线S3和S4
    plot3([S3(1), S4(1)], [S3(2), S4(2)], [S3(3), S4(3)], 'r-', 'LineWidth', 4);


    str = "工作面推进:"+h2+"m";
    text(S1(1), S1(2), S1(3), str, 'Color', 'red', 'FontSize', 12);
    str = "层高:"+ h1+ "m";
    text(S4(1), S4(2), S4(3), str, 'Color', 'red', 'FontSize', 12);

    % ============== 正立圆顶圆锥体（无顶部圆盘） ==============
    % 计算圆锥底面中心坐标（立方体C顶面中心）
    cone_base_x = a_l/2;                   % X中心
    cone_base_y = a_w/2;                   % Y中心
    cone_base_z = a_h + b_h + c_h;         % 圆锥底面Z坐标
    
    % 圆锥参数
    cone_radius = sqrt((c_l/2)^2 + (c_w/2)^2); % 覆盖C顶面对角线
    cone_height = 2*c_h;                      % 圆锥高度
    
    % 生成正立圆锥网格
    n_theta = 50;                            % 圆周分段数
    n_z = 30;                                % 高度分段数
    [X_cone, Y_cone, Z_cone] = cylinder(...
        cone_radius * (1 - linspace(0,1,n_z)'.^2),... % 渐变半径
        n_theta);
    
    % 调整坐标位置
    Z_cone = Z_cone * cone_height + cone_base_z; % 正立高度
    X_cone = X_cone + cone_base_x;               % X平移
    Y_cone = Y_cone + cone_base_y;               % Y平移

    
    % 绘制透明圆锥
    % surf(X_cone, Y_cone, Z_cone,...
    %     'FaceColor', [0.2 0.6 0.8],...
    %     'FaceAlpha', 0.3,...
    %     'EdgeColor', 'none');

    % 计算顶点坐标（圆锥最高点）
    apex_pos = [cone_base_x, cone_base_y, cone_base_z + cone_height];
    
    % 添加文字标注（偏移0.5个单位防止重叠）
    text(apex_pos(1), apex_pos(2), apex_pos(3)+0.5,...
        '覆岩破断停止能量积聚',...
        'Color', 'k',...                % 黑色文字
        'FontSize', 12,...             % 字号12
        'HorizontalAlignment', 'center',...  % 水平居中
        'VerticalAlignment', 'bottom',...    % 垂直底部对齐
        'FontWeight', 'bold');          % 加粗字体

    hold off;

  

    % 设置视图
    view(3);
    axis equal;
    grid on;

    % 隐藏坐标轴的数字
    set(gca, 'XTick', [], 'YTick', [], 'ZTick', []);

    xlabel('X');
    ylabel('Y');
    zlabel('Z');
    title('煤层图形');
end

