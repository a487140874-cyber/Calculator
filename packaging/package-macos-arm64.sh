#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUTPUT_DIR="$PROJECT_DIR/dist/macos-arm64"
INPUT_DIR="$PROJECT_DIR/target/jpackage-input"

if [ "$(uname -s)" != "Darwin" ] || [ "$(uname -m)" != "arm64" ]; then
  echo "This script must run on an Apple Silicon Mac."
  exit 1
fi

# JDK 解析顺序：环境变量 > /usr/libexec/java_home 自动探测。
# 打包用 JDK 需 >= 21（jpackage）；构建用 JDK 固定 21（pom 的 source/target=21）。
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 23 2>/dev/null \
           || /usr/libexec/java_home -v 21 2>/dev/null \
           || /usr/libexec/java_home 2>/dev/null || true)
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/jpackage" ]; then
  echo "未找到可用于打包的 JDK（需要含 jpackage 的 JDK 21+）。"
  echo "请先安装 JDK，或显式指定：JAVA_HOME=/path/to/jdk sh packaging/package-macos-arm64.sh"
  echo "已安装的 JDK："
  /usr/libexec/java_home -V 2>&1 || true
  exit 1
fi
BUILD_JAVA_HOME=${BUILD_JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || echo "$JAVA_HOME")}

echo "打包 JDK: $JAVA_HOME"
echo "构建 JDK: $BUILD_JAVA_HOME"

rm -rf "$INPUT_DIR" "$OUTPUT_DIR"
mkdir -p "$INPUT_DIR" "$OUTPUT_DIR"

# 用项目自带的 maven wrapper，避免依赖系统安装的 mvn；
# 不加 -o，允许首次构建联网拉取依赖。
JAVA_HOME="$BUILD_JAVA_HOME" "$PROJECT_DIR/mvnw" -q package -DskipTests
cp "$PROJECT_DIR/target/calculator-executable.jar" "$INPUT_DIR/Calculator.jar"

"$JAVA_HOME/bin/jpackage" \
  --type dmg \
  --name "超长工作面采动覆岩承载结构能量积聚预测系统" \
  --app-version 1.0.0 \
  --vendor "超长工作面采动覆岩承载结构能量积聚预测系统" \
  --description "超长工作面采动覆岩承载结构能量积聚预测系统：关键层判别、垮落与破断分析、能量积聚计算与三维可视化" \
  --input "$INPUT_DIR" \
  --main-jar Calculator.jar \
  --main-class ui.Launcher \
  --dest "$OUTPUT_DIR" \
  --mac-package-identifier com.example.calculator \
  # 注：--mac-package-name 对应 CFBundleName（菜单栏显示名），Apple 建议 <=15 字符。
  #     此处保持全名以与其它位置一致；macOS 菜单栏可能截断，如需短名自行替换即可。
  --mac-package-name "超长工作面采动覆岩承载结构能量积聚预测系统" \
  --mac-app-category public.app-category.utilities \
  --java-options -Dfile.encoding=UTF-8

echo "Created macOS installer in: $OUTPUT_DIR"
