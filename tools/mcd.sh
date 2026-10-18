#!/bin/bash
# Describe a class from the named 1.21.11 Minecraft jar.
#   ./mcd.sh net.minecraft.client.render.RenderLayer
#   ./mcd.sh net.minecraft.client.render.RenderLayer MultiPhase
#   ./mcd.sh net.minecraft.client.render.RenderLayer -m of
JDK="$HOME/Library/Application Support/minecraft/runtime/java-runtime-delta/osx/java-runtime-delta/jre.bundle/Contents/Home"
JAR="$HOME/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2/minecraft-merged-1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.6-v2.jar"

CLS="$1"
if [ -n "$2" ] && [ "$2" != "-m" ]; then
  CLS="$1\$$2"
  shift
fi
shift
"$JDK/bin/javap" -p -classpath "$JAR" "$CLS" "$@" 2>&1
