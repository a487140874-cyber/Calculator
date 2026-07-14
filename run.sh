#!/bin/bash

# 设置Java环境
export JAVA_HOME=/Users/gcf/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH

# 设置JavaFX模块路径
JAVAFX_PATH="$(pwd)/javafx-sdk-21.0.5/lib"

# 运行JavaFX应用程序
# 使用独立的JavaFX SDK来解决运行时问题
java --module-path "$JAVAFX_PATH" \
     --add-modules javafx.controls,javafx.fxml,javafx.graphics \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     --add-opens java.desktop/java.awt=ALL-UNNAMED \
     --add-opens java.desktop/java.awt.font=ALL-UNNAMED \
     -Djava.awt.headless=false \
     -jar target/calculator-executable.jar

echo "如果仍然出现问题，请检查JavaFX SDK路径是否正确"