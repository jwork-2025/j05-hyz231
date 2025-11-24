package com.gameengine.example;

import com.gameengine.core.GameEngine;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;
import com.gameengine.save.SaveIO;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MenuScene extends Scene {
    public enum MenuOption {
        START_GAME,
        LOAD_GAME,
        EXIT
    }
    
    private IRenderer renderer;
    private InputManager inputManager;
    private GameEngine engine;
    private int selectedIndex;
    private MenuOption[] options;
    private boolean selectionMade;
    private MenuOption selectedOption;
    private List<String> replayFiles;
    private boolean showReplayInfo;
    private int debugFrames;
    
    public MenuScene(GameEngine engine, String name) {
        super(name);
        this.engine = engine;
        this.renderer = engine.getRenderer();
        this.inputManager = InputManager.getInstance();
        this.selectedIndex = 0;
        this.options = new MenuOption[]{MenuOption.START_GAME, MenuOption.LOAD_GAME, MenuOption.EXIT};
        this.selectionMade = false;
        this.selectedOption = null;
        this.replayFiles = new ArrayList<>();
        this.showReplayInfo = false;
    }
    
    private void loadReplayFiles() {
        replayFiles.clear();
        List<File> files = SaveIO.listSaves();
        if (files.isEmpty()) {
            showReplayInfo = false;
            return;
        }

        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
        int limit = Math.min(3, files.size());
        for (int i = 0; i < limit; i++) {
            File f = files.get(i);
            String label = String.format("%s (%s)", f.getName(), fmt.format(new Date(f.lastModified())));
            replayFiles.add(label);
        }
        showReplayInfo = true;
    }
    
    @Override
    public void initialize() {
        super.initialize();
        loadReplayFiles();
        selectedIndex = 0;
        selectionMade = false;
        debugFrames = 0;
        
    }
    
    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        
        handleMenuSelection();
        
        if (selectionMade) {
            processSelection();
        }
    }
    
    private void handleMenuSelection() {
        boolean upPressed = inputManager.isKeyJustPressed(38) || inputManager.isKeyJustPressed(265); // AWT / GLFW
        boolean downPressed = inputManager.isKeyJustPressed(40) || inputManager.isKeyJustPressed(264);
        boolean confirmPressed = inputManager.isKeyJustPressed(10) || inputManager.isKeyJustPressed(32)
            || inputManager.isKeyJustPressed(257) || inputManager.isKeyJustPressed(335);
        boolean escPressed = inputManager.isKeyJustPressed(27) || inputManager.isKeyJustPressed(256);

        if (upPressed) {
            selectedIndex = (selectedIndex - 1 + options.length) % options.length;
        } else if (downPressed) {
            selectedIndex = (selectedIndex + 1) % options.length;
        } else if (confirmPressed) {
            selectionMade = true;
            selectedOption = options[selectedIndex];
        } else if (escPressed) {
            selectionMade = true;
            selectedOption = MenuOption.EXIT;
        }
        
        Vector2 mousePos = inputManager.getMousePosition();
        if (inputManager.isMouseButtonJustPressed(0)) {
            float centerY = renderer.getHeight() / 2.0f;
            float buttonY1 = centerY - 80;
            float buttonY2 = centerY + 0;
            float buttonY3 = centerY + 80;
            
            if (mousePos.y >= buttonY1 - 30 && mousePos.y <= buttonY1 + 30) {
                selectedIndex = 0;
                selectionMade = true;
                selectedOption = MenuOption.START_GAME;
            } else if (mousePos.y >= buttonY2 - 30 && mousePos.y <= buttonY2 + 30) {
                selectedIndex = 1;
                selectionMade = true;
                selectedOption = MenuOption.LOAD_GAME;
            } else if (mousePos.y >= buttonY3 - 30 && mousePos.y <= buttonY3 + 30) {
                selectedIndex = 2;
                selectionMade = true;
                selectedOption = MenuOption.EXIT;
            }
        }
    }

    private void processSelection() {
        if (!selectionMade || selectedOption == null) {
            return;
        }

        switch (selectedOption) {
            case START_GAME -> switchToGameScene();
            case LOAD_GAME -> switchToLoadScene();
            case EXIT -> {
                engine.stop();
                engine.cleanup();
                System.exit(0);
            }
        }

        selectionMade = false;
        selectedOption = null;
    }
    
    private void switchToGameScene() {
        Scene classicScene = Game.createClassicScene(engine);
        engine.setScene(classicScene);
    }

    private void switchToLoadScene() {
        Scene load = new LoadGameScene(engine);
        engine.setScene(load);
    }
    
    @Override
    public void render() {
        if (renderer == null) return;
        
        int width = renderer.getWidth();
        int height = renderer.getHeight();
        if (debugFrames < 5) {
            
            debugFrames++;
        }
        
        renderer.drawRect(0, 0, width, height, 0.25f, 0.25f, 0.35f, 1.0f);
        
        super.render();
        
        renderMainMenu();
    }
    
    private void renderMainMenu() {
        if (renderer == null) return;
        
        int width = renderer.getWidth();
        int height = renderer.getHeight();
        
        float centerX = width / 2.0f;
        float centerY = height / 2.0f;
        
        String title = "GAME ENGINE";
        float titleWidth = title.length() * 20.0f;
        float titleX = centerX - titleWidth / 2.0f;
        float titleY = 120.0f;
        
        renderer.drawRect(centerX - titleWidth / 2.0f - 20, titleY - 40, titleWidth + 40, 80, 0.4f, 0.4f, 0.5f, 1.0f);
        renderer.drawText(titleX, titleY, title, 1.0f, 1.0f, 1.0f, 1.0f);
        
        for (int i = 0; i < options.length; i++) {
            String text = "";
            if (options[i] == MenuOption.START_GAME) {
                text = "START GAME";
            } else if (options[i] == MenuOption.LOAD_GAME) {
                text = "LOAD GAME";
            } else if (options[i] == MenuOption.EXIT) {
                text = "EXIT";
            }
            
            float textWidth = text.length() * 20.0f;
            float textX = centerX - textWidth / 2.0f;
            float textY = centerY - 80.0f + i * 80.0f;
            
            float r, g, b;
            
            if (i == selectedIndex) {
                r = 1.0f;
                g = 1.0f;
                b = 0.5f;
                renderer.drawRect(textX - 20, textY - 20, textWidth + 40, 50, 0.6f, 0.5f, 0.2f, 0.9f);
            } else {
                r = 0.95f;
                g = 0.95f;
                b = 0.95f;
                renderer.drawRect(textX - 20, textY - 20, textWidth + 40, 50, 0.2f, 0.2f, 0.3f, 0.5f);
            }
            
            renderer.drawText(textX, textY, text, r, g, b, 1.0f);
        }
        
        String hint1 = "USE ARROWS OR MOUSE TO SELECT, ENTER TO CONFIRM";
        float hint1Width = hint1.length() * 20.0f;
        float hint1X = centerX - hint1Width / 2.0f;
        renderer.drawText(hint1X, height - 100, hint1, 0.6f, 0.6f, 0.6f, 1.0f);
        
        String hint2 = "ESC TO EXIT";
        float hint2Width = hint2.length() * 20.0f;
        float hint2X = centerX - hint2Width / 2.0f;
        renderer.drawText(hint2X, height - 70, hint2, 0.6f, 0.6f, 0.6f, 1.0f);

        if (showReplayInfo && !replayFiles.isEmpty()) {
            String label = "RECENT SAVES";
            float lw = label.length() * 12.0f;
            renderer.drawText(centerX - lw / 2.0f, height - 160, label, 0.9f, 0.8f, 0.2f, 1.0f);
            for (int i = 0; i < replayFiles.size(); i++) {
                String entry = replayFiles.get(i);
                float ew = entry.length() * 10.0f;
                renderer.drawText(centerX - ew / 2.0f, height - 140 + i * 18, entry, 0.8f, 0.8f, 0.8f, 1.0f);
            }
        }
    }
    
    
}

