#!/bin/bash
# Starts the web server. Usage: ./run.sh [classesDir] [port]
CP="lib/asm-9.7.jar:lib/asm-tree-9.7.jar:lib/asm-util-9.7.jar:lib/asm-analysis-9.7.jar"
CLASSES_DIR="${1:-samples}"
PORT="${2:-8080}"
java -cp "out:$CP" com.prasad.bcviz.WebServer "$CLASSES_DIR" web "$PORT"
