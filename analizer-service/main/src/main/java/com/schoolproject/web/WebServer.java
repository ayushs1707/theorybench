package com.schoolproject.web;

import java.util.HashMap;
import java.util.List;

import com.schoolproject.analyzer.MidiDifficultyAnalyzer;
import com.schoolproject.db.MidiDBOperations;

import io.javalin.Javalin;

public class WebServer {

    private final MidiDBOperations dbOps;
    private final MidiDifficultyAnalyzer analyzer;

    public WebServer(MidiDBOperations dbOps) {
        this.dbOps = dbOps;
        this.analyzer = new MidiDifficultyAnalyzer();
    }

    public void start() {

        Javalin app = Javalin.create(config -> {
            config.http.defaultContentType = "application/json";
            config.plugins.enableCors(cors -> cors.add(it -> it.anyHost()));
        });

        // =====================================================================
        // HEALTH CHECK
        // =====================================================================
        app.get("/health", ctx -> ctx.result("{\"status\":\"ok\"}"));


        // =====================================================================
        // LIST STORED DB FILES (HTML PAGE)
        // =====================================================================
        app.get("/files", ctx -> {

            List<String> files = dbOps.listAll();
            StringBuilder links = new StringBuilder();

            for (String f : files) {
                links.append("<li>")
                        .append("<a href=\"/analyze/").append(f).append("\">Raw JSON</a> | ")
                        .append("<a href=\"/analyze-html/").append(f).append("\">HTML View</a> — ")
                        .append(f)
                        .append("</li>");
            }

            String html = """
                    <html>
                    <head>
                        <title>MIDI Files</title>
                        <style>
                            body { background:#111; color:#eee; font-family:Arial; padding:30px; }
                            h1 { color:#88b4ff; }
                            ul { list-style:none; padding-left:0; }
                            li { margin:8px 0; font-size:18px; }
                            a { color:#66aaff; text-decoration:none; }
                            a:hover { text-decoration:underline; }
                            .box { background:#1a1a1a; padding:20px; border-radius:8px; border:1px solid #333; }
                        </style>
                    </head>
                    <body>

                        <h1>Stored MIDI Files</h1>

                        <div class="box">
                            <ul>
                                %s
                            </ul>
                        </div>

                        <br>
                        <a href="/">← Back to Home</a>

                    </body>
                    </html>
                    """;

            ctx.html(String.format(html, links.toString()));
        });

        // =====================================================================
        // RAW JSON ANALYSIS FOR SAVED DB FILE
        // =====================================================================
        app.get("/analyze/{filename}", ctx -> {

            String name = ctx.pathParam("filename");
            byte[] data = dbOps.load(name);

            if (data == null) {
                ctx.status(404).json("{\"error\":\"File not found\"}");
                return;
            }

            var result = analyzer.analyzeBytes(data);
            ctx.json(result);
        });


        // =====================================================================
        // HTML ANALYSIS PAGE — PRETTY VISUAL SUMMARY
        // =====================================================================
        app.get("/analyze-html/{filename}", ctx -> {

            String name = ctx.pathParam("filename");
            byte[] data = dbOps.load(name);

            if (data == null) {
                ctx.html("<h2 style='color:red'>File not found: " + name + "</h2>");
                return;
            }

            var result = analyzer.analyzeBytes(data);

            StringBuilder timeline = new StringBuilder();
            for (String entry : result.chordTimeline) {
                timeline.append("<li>").append(entry).append("</li>");
            }

            String html = """
                    <html>
                    <head>
                        <title>Analysis for %s</title>
                        <style>
                            body { font-family:Arial; background:#111; color:#eee; padding:30px; }
                            h1 { color:#88b4ff; }
                            .box { background:#1a1a1a; padding:20px; border-radius:8px; border:1px solid #333; margin-bottom:25px; }
                            table { width:100%%; border-collapse:collapse; }
                            td, th { border:1px solid #333; padding:10px; }
                            th { background:#222; }
                            ul { line-height:1.7; }
                            a { color:#66aaff; }
                        </style>
                    </head>

                    <body>

                        <h1>Analysis for %s</h1>

                        <div class="box">
                            <h2>Summary</h2>
                            <table>
                                <tr><th>Max Polyphony</th><td>%d</td></tr>
                                <tr><th>Note Count</th><td>%d</td></tr>
                                <tr><th>Chord Difficulty</th><td>%d</td></tr>
                                <tr><th>Rhythm Difficulty</th><td>%d</td></tr>
                                <tr><th>Total Difficulty</th><td>%d</td></tr>
                            </table>
                        </div>

                        <div class="box">
                            <h2>Chord Timeline</h2>
                            <ul>%s</ul>
                        </div>

                        <a href="/files">← Back to Files</a>

                    </body>
                    </html>
                    """;

            ctx.html(String.format(
                    html,
                    name,    // title
                    name,    // heading
                    result.maxPolyphony,
                    result.noteCount,
                    result.chordDifficulty,
                    result.rhythmDifficulty,
                    result.totalDifficulty,
                    timeline.toString()
            ));
        });



        // =====================================================================
        // BULK UPLOAD (MULTIPLE FILES)
        // =====================================================================
        app.post("/analyze-bulk", ctx -> {

            var uploadedFiles = ctx.uploadedFiles("files");

            if (uploadedFiles.isEmpty()) {
                ctx.status(400).json("{\"error\":\"No files uploaded\"}");
                return;
            }

            HashMap<String, Object> results = new HashMap<>();

            for (var uf : uploadedFiles) {
                try {
                    byte[] data = uf.content().readAllBytes();
                    var analysis = analyzer.analyzeBytes(data);
                    results.put(uf.filename(), analysis);

                } catch (Exception e) {
                    results.put(uf.filename(), new HashMap<>() {{
                        put("error", e.getMessage());
                    }});
                }
            }

            ctx.json(results);
        });



        // =====================================================================
        // HOME PAGE
        // =====================================================================
        app.get("/", ctx -> {
            ctx.html("""
                    <html>
                    <head>
                        <title>Analyzer Service</title>
                        <style>
                            body { background:#111; color:#eee; font-family:Arial; padding:30px; }
                            h1 { color:#88b4ff; }
                            .box { background:#1a1a1a; padding:20px; border:1px solid #333; border-radius:8px; margin-bottom:25px; }
                            a { color:#66aaff; }
                            button { padding:10px 20px; background:#333; border:1px solid #444; color:white; cursor:pointer; }
                            button:hover { background:#444; }
                        </style>
                    </head>

                    <body>

                        <h1>Analyzer Service</h1>

                        <div class="box">
                            <h3>Available Endpoints</h3>
                            <ul>
                                <li><a href="/health">/health</a></li>
                                <li><a href="/files">/files</a></li>
                            </ul>
                        </div>

                        <div class="box">
                            <h3>Upload 1 or More MIDI Files</h3>

                            <form action="/analyze-bulk" method="post" enctype="multipart/form-data">
                                <input type="file" name="files" accept=".mid" multiple required />
                                <br><br>
                                <button type="submit">Upload & Analyze</button>
                            </form>
                        </div>

                    </body>
                    </html>
                    """);
        });

        // Start server
        app.start(3000);
        System.out.println("Analyzer-service running on http://localhost:3000");
    }
}
