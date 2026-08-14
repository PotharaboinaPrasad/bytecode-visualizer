# Build stage: compile the Java source against the bundled ASM jars
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY lib/ lib/
COPY src/ src/
COPY samples/ samples/

RUN CP="lib/asm-9.7.jar:lib/asm-tree-9.7.jar:lib/asm-util-9.7.jar:lib/asm-analysis-9.7.jar" && \
    mkdir -p out && \
    javac -cp "$CP" -d out src/com/prasad/bcviz/*.java && \
    javac -d samples --release 21 samples/Sample.java

# Runtime stage: smaller image, only what's needed to run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/out out/
COPY --from=build /app/lib lib/
COPY --from=build /app/samples samples/
COPY web/ web/

EXPOSE 8080
CMD CP="lib/asm-9.7.jar:lib/asm-tree-9.7.jar:lib/asm-util-9.7.jar:lib/asm-analysis-9.7.jar" && \
    java -cp "out:$CP" com.prasad.bcviz.WebServer samples web ${PORT:-8080}
