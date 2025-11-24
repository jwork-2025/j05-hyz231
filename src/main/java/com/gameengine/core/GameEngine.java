package com.gameengine.core;

import com.gameengine.graphics.IRenderer;
import com.gameengine.graphics.RenderBackend;
import com.gameengine.graphics.RendererFactory;
import com.gameengine.input.InputManager;
import com.gameengine.recording.RecordingService;
import com.gameengine.scene.Scene;

/**
 * 基于 LWJGL + OpenGL 的主循环驱动
 */
public class GameEngine {
    private final IRenderer renderer;
    private final InputManager inputManager;
    private Scene currentScene;
    private boolean running;
    private float targetFPS = 165f;
    private float deltaTime;
    private RecordingService recordingService;
    private final Object recordingLock = new Object();

    public GameEngine(int width, int height, String title, RenderBackend backend) {
        this.renderer = RendererFactory.createRenderer(backend, width, height, title);
        this.inputManager = InputManager.getInstance();
    }

    /**
     * 启动主循环
     */
    public void run() {
        running = true;
        long lastTime = System.nanoTime();
        if (currentScene != null) {
            currentScene.initialize();
        }
        startRecordingIfNeeded();

        while (running) {
            long frameStart = System.nanoTime();
            deltaTime = (frameStart - lastTime) / 1_000_000_000.0f;
            lastTime = frameStart;

            renderer.pollEvents();
            if (renderer.shouldClose()) {
                running = false;
                break;
            }

            if (currentScene != null) {
                currentScene.update(deltaTime);
            }

            if (recordingService != null && recordingService.isRecording() && currentScene != null) {
                recordingService.update(deltaTime, currentScene, inputManager);
            }

            renderer.beginFrame();
            if (currentScene != null) {
                currentScene.render();
            }
            renderer.endFrame();

            inputManager.update();

            throttleFrame(frameStart);
        }

        shutdown();
    }

    /**
     * 切换场景
     */
    public void setScene(Scene scene) {
        this.currentScene = scene;
        if (scene != null && running) {
            scene.initialize();
            startRecordingIfNeeded();
        }
    }

    public Scene getCurrentScene() {
        return currentScene;
    }

    public void stop() {
        running = false;
    }

    public IRenderer getRenderer() {
        return renderer;
    }

    public InputManager getInputManager() {
        return inputManager;
    }

    public float getDeltaTime() {
        return deltaTime;
    }

    public void setTargetFPS(float fps) {
        if (fps <= 0) return;
        this.targetFPS = fps;
    }

    public float getTargetFPS() {
        return targetFPS;
    }

    public boolean isRunning() {
        return running;
    }

    public void enableRecording(RecordingService service) {
        if (service == null) return;
        synchronized (recordingLock) {
            if (recordingService != null) {
                recordingService.stop();
            }
            recordingService = service;
            startRecordingIfNeeded();
        }
    }

    public void disableRecording() {
        synchronized (recordingLock) {
            if (recordingService != null) {
                recordingService.stop();
                recordingService = null;
            }
        }
    }

    public void cleanup() {
        if (currentScene != null) {
            currentScene.clear();
        }
        renderer.cleanup();
    }

    private void startRecordingIfNeeded() {
        synchronized (recordingLock) {
            if (recordingService == null || currentScene == null || recordingService.isRecording()) {
                return;
            }
            try {
                recordingService.start(currentScene, renderer.getWidth(), renderer.getHeight());
            } catch (Exception e) {
                System.err.println("录制服务启动失败: " + e.getMessage());
                e.printStackTrace();
                recordingService = null;
            }
        }
    }

    private void throttleFrame(long frameStart) {
        if (targetFPS <= 0) return;
        double targetMs = 1000.0 / targetFPS;
        double elapsedMs = (System.nanoTime() - frameStart) / 1_000_000.0;
        double remaining = targetMs - elapsedMs;
        if (remaining > 0.5) {
            long sleepMs = (long) remaining;
            int sleepNs = (int) ((remaining - sleepMs) * 1_000_000);
            try {
                Thread.sleep(Math.max(0, sleepMs), Math.max(0, sleepNs));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void shutdown() {
        disableRecording();
        cleanup();
    }
}
