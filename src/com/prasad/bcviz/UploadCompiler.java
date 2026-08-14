package com.prasad.bcviz;

import javax.tools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * Compiles a single uploaded .java file into the classes directory using
 * javax.tools.JavaCompiler - the same compiler API `javac` itself uses,
 * invoked in-process instead of shelling out to a separate `javac` process.
 *
 * Note: this requires a JDK at runtime (not just a JRE), since only the JDK
 * ships the compiler. The Docker runtime image must be eclipse-temurin:*-jdk,
 * not *-jre, for this to work when deployed.
 */
public class UploadCompiler {

    public static class CompileResult {
        public final boolean success;
        public final String message; // compiler diagnostics on failure, or empty on success
        CompileResult(boolean success, String message) { this.success = success; this.message = message; }
    }

    /** Writes `source` to <classesDir>/<className>.java and compiles it to .class in place. */
    public static CompileResult compile(String classesDir, String className, String source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new CompileResult(false,
                "No system Java compiler available. The server must run on a JDK, not a JRE.");
        }

        File srcFile = new File(classesDir, className + ".java");
        Files.write(srcFile.toPath(), source.getBytes(StandardCharsets.UTF_8));

        StringWriter diagnosticsOut = new StringWriter();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (StandardJavaFileManager fileManager =
                 compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {

            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(new File(classesDir)));
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(List.of(srcFile));

            List<String> options = Arrays.asList("--release", "21");

            JavaCompiler.CompilationTask task = compiler.getTask(
                    diagnosticsOut, fileManager, diagnostics, options, null, units);

            boolean ok = task.call();

            StringBuilder report = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                report.append(d.getKind()).append(": ")
                      .append(d.getMessage(null))
                      .append(" (line ").append(d.getLineNumber()).append(")\n");
            }
            report.append(diagnosticsOut);

            // clean up the .java source we wrote - we only want the .class artifact left behind
            srcFile.delete();

            return new CompileResult(ok, report.toString());
        }
    }
}
