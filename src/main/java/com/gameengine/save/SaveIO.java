package com.gameengine.save;

import com.gameengine.recording.RecordingJson;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 负责保存/读取 SaveState 到磁盘。
 */
public final class SaveIO {
    private SaveIO() {}

    public static void write(SaveState state, String path) throws IOException {
        Path p = Paths.get(path);
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
        try (BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
            bw.write(toJson(state));
        }
    }

    public static SaveState read(String path) throws IOException {
        String json = Files.readString(Paths.get(path), StandardCharsets.UTF_8);
        return fromJson(json);
    }

    public static List<File> listSaves() {
        File dir = new File("saves");
        if (!dir.exists() || !dir.isDirectory()) return new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.startsWith("save") && name.endsWith(".json"));
        if (files == null) return new ArrayList<>();
        List<File> list = new ArrayList<>();
        for (File f : files) {
            list.add(f);
        }
        list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return list;
    }

    public static String nextSavePath() {
        File dir = new File("saves");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        int idx = 1;
        while (true) {
            File candidate = new File(dir, "save" + idx + ".json");
            if (!candidate.exists()) {
                return candidate.getPath();
            }
            idx++;
        }
    }

    private static String toJson(SaveState state) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"version\":").append(state.version).append(',');
        sb.append("\"score\":").append(state.score).append(',');
        sb.append("\"lives\":").append(state.lives).append(',');
        sb.append("\"spawn\":").append(state.spawnTimer).append(',');
        sb.append("\"shot\":").append(state.timeSinceLastShot).append(',');
        sb.append("\"seed\":").append(state.randomSeed).append(',');
        sb.append("\"entities\":[");
        for (int i = 0; i < state.entities.size(); i++) {
            SaveState.EntityState e = state.entities.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            appendQuoted(sb, "type", e.type); sb.append(',');
            appendQuoted(sb, "name", e.name); sb.append(',');
            sb.append("\"x\":").append(e.x).append(',');
            sb.append("\"y\":").append(e.y).append(',');
            sb.append("\"vx\":").append(e.vx).append(',');
            sb.append("\"vy\":").append(e.vy).append(',');
            sb.append("\"w\":").append(e.width).append(',');
            sb.append("\"h\":").append(e.height).append(',');
            sb.append("\"color\":[").append(e.colorR).append(',').append(e.colorG).append(',').append(e.colorB).append(',').append(e.colorA).append("],");
            sb.append("\"plife\":").append(e.projectileLife).append(',');
            sb.append("\"pvx\":").append(e.projectileSpeedX).append(',');
            sb.append("\"pvy\":").append(e.projectileSpeedY);
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void appendQuoted(StringBuilder sb, String key, String value) {
        sb.append('\"').append(key).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append('\"').append(value.replace("\"", "\\\"")).append('\"');
        }
    }

    private static SaveState fromJson(String json) {
        SaveState state = new SaveState();
        state.version = (int) RecordingJson.parseDouble(RecordingJson.field(json, "version"));
        state.score = (int) RecordingJson.parseDouble(RecordingJson.field(json, "score"));
        state.lives = (int) RecordingJson.parseDouble(RecordingJson.field(json, "lives"));
        state.spawnTimer = (float) RecordingJson.parseDouble(RecordingJson.field(json, "spawn"));
        state.timeSinceLastShot = (float) RecordingJson.parseDouble(RecordingJson.field(json, "shot"));
        state.randomSeed = (long) RecordingJson.parseDouble(RecordingJson.field(json, "seed"));
        int idx = json.indexOf("\"entities\"");
        if (idx >= 0) {
            int start = json.indexOf('[', idx);
            if (start >= 0) {
                String array = RecordingJson.extractArray(json, start);
                String[] entries = RecordingJson.splitTopLevel(array);
                for (String entry : entries) {
                    if (entry.isEmpty()) continue;
                    SaveState.EntityState es = new SaveState.EntityState();
                    es.type = RecordingJson.stripQuotes(RecordingJson.field(entry, "type"));
                    es.name = RecordingJson.stripQuotes(RecordingJson.field(entry, "name"));
                    es.x = (float) RecordingJson.parseDouble(RecordingJson.field(entry, "x"));
                    es.y = (float) RecordingJson.parseDouble(RecordingJson.field(entry, "y"));
                    es.vx = (float) RecordingJson.parseDouble(RecordingJson.field(entry, "vx"));
                    es.vy = (float) RecordingJson.parseDouble(RecordingJson.field(entry, "vy"));
                    es.width = (float) RecordingJson.parseDouble(RecordingJson.field(entry, "w"));
                    es.height = (float) RecordingJson.parseDouble(RecordingJson.field(entry, "h"));
                    String color = RecordingJson.field(entry, "color");
                    if (color != null && color.startsWith("[")) {
                        int end = color.indexOf(']');
                        if (end > 1) {
                            String[] parts = color.substring(1, end).split(",");
                            if (parts.length >= 4) {
                                es.colorR = parse(parts[0]);
                                es.colorG = parse(parts[1]);
                                es.colorB = parse(parts[2]);
                                es.colorA = parse(parts[3]);
                            }
                        }
                    }
                    es.projectileLife = (float) RecordingJson.parseDouble(RecordingJson.field(entry, "plife"));
                    es.projectileSpeedX = (float) RecordingJson.parseDouble(RecordingJson.field(entry, "pvx"));
                    es.projectileSpeedY = (float) RecordingJson.parseDouble(RecordingJson.field(entry, "pvy"));
                    state.entities.add(es);
                }
            }
        }
        return state;
    }

    private static float parse(String value) {
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception e) {
            return 0f;
        }
    }
}

