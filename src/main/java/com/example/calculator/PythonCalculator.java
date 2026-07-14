package com.example.calculator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class PythonCalculator {

    public static double callPythonScript(String functionName, double... params) throws Exception {
        // 1. 配置Python解释器和脚本的路径
        // 注意：在生产环境中，这些路径最好从配置文件读取
        String pythonExecutable = "python"; // 或者 "python3"，或者Python.exe的完整路径
        String scriptPath = "/Users/gcf/Project/MyProject/hxh/Calculator/src/main/java/Py/main_calculator.py"; // <--- !! 修改为您的实际路径

        // 2. 构建命令行参数列表
        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add(scriptPath);
        command.add(functionName); // "computeU1" or "computeU2"

        // 添加所有数值参数
        for (double param : params) {
            command.add(String.valueOf(param));
        }

        // 3. 使用ProcessBuilder执行命令
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        // 4. 读取Python脚本的标准输出 (计算结果)
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String resultLine = reader.readLine();
        if (resultLine == null) {
            // 如果没有输出，尝试读取错误信息
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String errorLine;
            StringBuilder errorMessage = new StringBuilder("Python script provided no output. Error:\n");
            while ((errorLine = errorReader.readLine()) != null) {
                errorMessage.append(errorLine).append("\n");
            }
            throw new RuntimeException(errorMessage.toString());
        }


        // 5. 等待进程结束并进行错误处理
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            // 如果进程非正常退出，抛出异常
            throw new RuntimeException("Python script exited with error code: " + exitCode);
        }

        // 6. 解析结果并返回
        return Double.parseDouble(resultLine);
    }

    public static void main(String[] args) {
        // --- 示例：调用 computeU1 ---
        try {
            System.out.println("Calling computeU1 in Python...");
            // 这里使用一组示例参数，请替换为您的实际参数
            // computeU1(k, l, ht, dta, e, yh, hh, M1, M2, ax)
            double u1Result = callPythonScript("computeU1", 1.0, 2.0, 0.1, 0.785, 2e11, 0.05, 0.02, 100, 50, 1e5);
            System.out.println("Result from computeU1: " + u1Result);

        } catch (Exception e) {
            System.err.println("Error calling computeU1:");
            e.printStackTrace();
        }

        System.out.println("\n-----------------------------------\n");

        // --- 示例：调用 computeU2 ---
        try {
            System.out.println("Calling computeU2 in Python...");
            // computeU2(k,l, ht, dta,e,yh,ax,hh,M1, M2)
            double u2Result = callPythonScript("computeU2", 1.0, 2.0, 0.1, 0.785, 2e11, 0.05, 1e5, 0.02, 100, 50);
            System.out.println("Result from computeU2: " + u2Result);

        } catch (Exception e) {
            System.err.println("Error calling computeU2:");
            e.printStackTrace();
        }
    }
}