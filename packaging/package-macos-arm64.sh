#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_HOME=${JAVA_HOME:-/Users/gcf/Library/Java/JavaVirtualMachines/openjdk-23.0.1/Contents/Home}
BUILD_JAVA_HOME=${BUILD_JAVA_HOME:-/Users/gcf/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home}
OUTPUT_DIR="$PROJECT_DIR/dist/macos-arm64"
INPUT_DIR="$PROJECT_DIR/target/jpackage-input"

if [ "$(uname -s)" != "Darwin" ] || [ "$(uname -m)" != "arm64" ]; then
  echo "This script must run on an Apple Silicon Mac."
  exit 1
fi

rm -rf "$INPUT_DIR" "$OUTPUT_DIR"
mkdir -p "$INPUT_DIR" "$OUTPUT_DIR"

JAVA_HOME="$BUILD_JAVA_HOME" mvn -o -q package -DskipTests
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
  --mac-package-name "超长工作面采动覆岩承载结构能量积聚预测系统" \
  --mac-app-category public.app-category.utilities \
  --java-options -Dfile.encoding=UTF-8

echo "Created macOS installer in: $OUTPUT_DIR"
