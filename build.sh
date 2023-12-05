#!/bin/bash

# Run gradlew to assemble the release
"$PWD/gradlew" assembleRelease --no-daemon

# Run genJar script with an argument
"$PWD/jar/genJar.sh" "$1"

cp "$PWD/jar/custom_spider.jar" "$PWD/jar/lefty.jar"
cp "$PWD/jar/custom_spider.jar" "/volume2/Filestation/config/tvbox/jar/lefty.jar"

# Pause (Linux equivalent)
read -p "Press Enter to continue..."

# Note: The behavior of 'pause' can be mimicked using 'read' on Linux.