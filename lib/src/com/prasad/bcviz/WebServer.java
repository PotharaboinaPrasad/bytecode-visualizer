package com.prasad.bcviz;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves a single-page frontend + a JSON API backed by Analyzer.
 * Uses the JDK's built-in com.sun.net.httpserver so this project needs
 * zero extra web-framework dependencies.
 *
 * Routes:
 *   GET /                 -> static frontend (index.html)
 *   GET /app.js           -> static frontend script
 *   GET /api/classes      -> list of .class files available in the classes dir
 *   GET /api/analyze?file=Sample.class -> full JSON analysis for that class
 */
public class WebServer {

    private final String classesDir;
    private final String webRoot;

    public WebServer(String classesDir, String webRoot) {
        this.classesDir = classesDir;
        this.webRoot = webRoot;
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/classes", this::handleListClasses);
        server.createContext("/api/analyze", this::handleAnalyze);
        server.createContext("/api/upload", this::handleUpload);
        server.createContext("/", this::handleStatic);
        server.setExecutor(null);
        server.start();
        System.out.println("Bytecode Visualizer running at http://localhost:" + port);
        System.out.println("Serving .class files from: " + new File(classesDir).getAbsolutePath());
    }

    private void handleListClasses(HttpExchange ex) throws IOException {
        StringBuilder json = new StringBuilder("[");
        File dir = new File(classesDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".class"));
        if (files != null) {
            boolean first = true;
            for (File f : files) {
                if (!first) json.append(",");
                json.append(Json.str(f.getName()));
                first = false;
            }
        }
        json.append("]");
        sendJson(ex, 200, json.toString());
    }

    private void handleAnalyze(HttpExchange ex) throws IOException {
        Map<String, String> params = parseQuery(ex.getRequestURI().getRawQuery());
        String file = params.get("file");
        if (file == null || file.contains("..") || file.contains("/")) {
            sendJson(ex, 400, "{\"error\":\"invalid file parameter\"}");
            return;
        }
        File target = new File(classesDir, file);
        if (!target.exists()) {
            sendJson(ex, 404, "{\"error\":\"class file not found\"}");
            return;
        }
        try {
            String json = Analyzer.analyzeToJson(target.getPath());
            sendJson(ex, 200, json);
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":" + Json.str(String.valueOf(e.getMessage())) + "}");
        }
    }

    private void handleUpload(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"error\":\"use POST\"}");
            return;
        }

        // Read the raw request body (frontend sends the file's plain text content).
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream is = ex.getRequestBody()) {
            is.transferTo(buffer);
        }
        String source = buffer.toString(StandardCharsets.UTF_8);

        final int MAX_SOURCE_BYTES = 200_000; // generous for a single class, prevents abuse
        if (source.isBlank()) {
            sendJson(ex, 400, "{\"error\":\"empty file\"}");
            return;
        }
        if (buffer.size() > MAX_SOURCE_BYTES) {
            sendJson(ex, 400, "{\"error\":\"file too large (max 200KB)\"}");
            return;
        }

        // Derive the public class name from the source itself - javac requires
        // the filename to match the public class name exactly, so we don't trust
        // whatever filename the browser sent.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:public\\s+)?class\\s+([A-Za-z_$][A-Za-z0-9_$]*)")
                .matcher(source);
        if (!m.find()) {
            sendJson(ex, 400, "{\"error\":\"couldn't find a class declaration in this file\"}");
            return;
        }
        String className = m.group(1);

        try {
            UploadCompiler.CompileResult result = UploadCompiler.compile(classesDir, className, source);
            if (!result.success) {
                Map<String, String> err = new HashMap<>();
                sendJson(ex, 422, "{\"error\":" + Json.str("Compilation failed:\n" + result.message) + "}");
                return;
            }
            sendJson(ex, 200, "{\"success\":true,\"className\":" + Json.str(className + ".class") + "}");
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":" + Json.str(String.valueOf(e.getMessage())) + "}");
        }
    }

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        File file = new File(webRoot, path.substring(1));
        if (!file.exists() || file.isDirectory()) {
            sendText(ex, 404, "Not found");
            return;
        }
        byte[] content = Files.readAllBytes(file.toPath());
        String contentType = path.endsWith(".js") ? "application/javascript"
                : path.endsWith(".css") ? "text/css"
                : "text/html";
        ex.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        ex.sendResponseHeaders(200, content.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(content); }
    }

    private void sendJson(HttpExchange ex, int status, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void sendText(HttpExchange ex, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null) return map;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            map.put(k, v);
        }
        return map;
    }

    public static void main(String[] args) throws IOException {
        String classesDir = args.length > 0 ? args[0] : "samples";
        String webRoot = args.length > 1 ? args[1] : "web";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 8080;
        new WebServer(classesDir, webRoot).start(port);
    }
}
