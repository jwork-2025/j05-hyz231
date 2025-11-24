package com.gameengine.example;

import com.gameengine.core.GameEngine;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.save.SaveIO;
import com.gameengine.save.SaveState;
import com.gameengine.scene.Scene;

import java.io.File;
import java.util.List;

/**
 * 存档读取场景：列出可用的 saveX.json，并允许选择后加载。
 */
public class LoadGameScene extends Scene {
    private final GameEngine engine;
    private List<File> saves;
    private int index;
    private InputManager input;

    public LoadGameScene(GameEngine engine) {
        super("LoadGame");
        this.engine = engine;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.input = engine.getInputManager();
        this.saves = SaveIO.listSaves();
        this.index = 0;
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        if (input.isKeyJustPressed(27) || input.isKeyJustPressed(256)) {
            engine.setScene(new MenuScene(engine, "MainMenu"));
            return;
        }
        if (saves.isEmpty()) return;
        if (input.isKeyJustPressed(38) || input.isKeyJustPressed(265)) {
            index = (index - 1 + saves.size()) % saves.size();
        } else if (input.isKeyJustPressed(40) || input.isKeyJustPressed(264)) {
            index = (index + 1) % saves.size();
        } else if (input.isKeyJustPressed(10) || input.isKeyJustPressed(32)
            || input.isKeyJustPressed(257) || input.isKeyJustPressed(335)) {
            loadSelected();
        } else if (input.isMouseButtonJustPressed(0)) {
            Vector2 mouse = input.getMousePosition();
            for (int i = 0; i < saves.size(); i++) {
                float boxX = 80f;
                float boxY = 120f + i * 36f;
                float boxW = engine.getRenderer().getWidth() - 160f;
                float boxH = 28f;
                if (mouse.x >= boxX && mouse.x <= boxX + boxW &&
                    mouse.y >= boxY && mouse.y <= boxY + boxH) {
                    index = i;
                    loadSelected();
                    break;
                }
            }
        }
    }

    @Override
    public void render() {
        engine.getRenderer().drawRect(0, 0,
            engine.getRenderer().getWidth(),
            engine.getRenderer().getHeight(),
            0.08f, 0.08f, 0.12f, 1f);
        float width = engine.getRenderer().getWidth();
        float y = 80f;
        engine.getRenderer().drawText(width / 2f - 120, y, "SELECT SAVE", 1f, 1f, 1f, 1f);
        y += 40f;
        if (saves.isEmpty()) {
            engine.getRenderer().drawText(width / 2f - 200, y, "NO SAVES FOUND", 0.9f, 0.8f, 0.2f, 1f);
            engine.getRenderer().drawText(width / 2f - 220, y + 40, "ESC TO RETURN", 0.7f, 0.7f, 0.7f, 1f);
            return;
        }
        for (int i = 0; i < saves.size(); i++) {
            File f = saves.get(i);
            String name = f.getName();
            float boxX = 80f;
            float boxY = y + i * 44f + 10f;
            float boxW = width - 160f;
            float boxH = 28f;
            if (i == index) {
                engine.getRenderer().drawRect(boxX - 10, boxY - 10, boxW + 20, boxH + 20, 0.3f, 0.3f, 0.45f, 0.7f);
            }
            engine.getRenderer().drawRect(boxX, boxY, boxW, boxH, 0.18f, 0.18f, 0.25f, 0.85f);
            float textX = boxX + 16f;
            float textY = boxY + (boxH / 2f) + 6f;
            engine.getRenderer().drawText(textX, textY, name, 0.95f, 0.95f, 0.95f, 1f);
        }
        engine.getRenderer().drawText(120, engine.getRenderer().getHeight() - 60,
            "UP/DOWN OR CLICK TO SELECT, ENTER LOAD, ESC BACK", 0.7f, 0.7f, 0.7f, 1f);
    }

    private void loadSelected() {
        if (saves.isEmpty()) return;
        File file = saves.get(index);
        try {
            SaveState state = SaveIO.read(file.getPath());
            Scene scene = Game.createClassicScene(engine, state);
            engine.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

