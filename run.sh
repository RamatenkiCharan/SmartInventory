#!/usr/bin/env bash
# Compile and run (needs JDK 17+).  Usage: ./run.sh [--reset] [--console]
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -cp lib/sqlite-jdbc.jar -d out $(find src -name "*.java")
java -cp "out:lib/sqlite-jdbc.jar" com.inventory.Main "$@"
