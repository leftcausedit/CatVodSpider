#!/bin/bash

DIR="$(dirname "$0")"

rm -f "$DIR/custom_spider.jar"
rm -rf "$DIR/Smali_classes"

java -jar "$DIR/3rd/baksmali-2.5.2.jar" d "$DIR/../app/build/intermediates/dex/release/minifyReleaseWithR8/classes.dex" -o "$DIR/Smali_classes"

rm -rf "$DIR/spider.jar/smali/com/github/catvod/spider"
rm -rf "$DIR/spider.jar/smali/com/github/catvod/parser"
rm -rf "$DIR/spider.jar/smali/com/github/catvod/js"

mkdir -p "$DIR/spider.jar/smali/com/github/catvod/"

java -Dfile.encoding=utf-8 -jar "$DIR/3rd/oss.jar" "$DIR/Smali_classes"

mv "$DIR/Smali_classes/com/github/catvod/spider" "$DIR/spider.jar/smali/com/github/catvod/"
mv "$DIR/Smali_classes/com/github/catvod/parser" "$DIR/spider.jar/smali/com/github/catvod/"
mv "$DIR/Smali_classes/com/github/catvod/js" "$DIR/spider.jar/smali/com/github/catvod/"

java -jar "$DIR/3rd/apktool_2.4.1.jar" b "$DIR/spider.jar" -c

mv "$DIR/spider.jar/dist/dex.jar" "$DIR/custom_spider.jar"

md5sum "$DIR/custom_spider.jar" | awk '{print $1}' > "$DIR/custom_spider.jar.md5"

rm -rf "$DIR/spider.jar/build"
rm -rf "$DIR/spider.jar/smali"
rm -rf "$DIR/spider.jar/dist"
rm -rf "$DIR/Smali_classes"