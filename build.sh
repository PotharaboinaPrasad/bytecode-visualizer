#!/bin/bash
# Compiles the project against the local ASM jars in lib/.
set -e
CP="lib/asm-9.7.jar:lib/asm-tree-9.7.jar:lib/asm-util-9.7.jar:lib/asm-analysis-9.7.jar"
mkdir -p out
javac -cp "$CP" -d out src/com/prasad/bcviz/*.java
echo "Build OK -> out/"
echo "Run with: ./run.sh"
