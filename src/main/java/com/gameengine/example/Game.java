package com.gameengine.example;

import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.ProjectileComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameLogic;
import com.gameengine.core.GameObject;
import com.gameengine.graphics.IRenderer;
import com.gameengine.graphics.RenderBackend;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.save.SaveIO;
import com.gameengine.save.SaveState;
import com.gameengine.scene.Scene;

import java.io.File;
import java.util.List;
import java.util.Random;

public class Game {
    public static void main(String[] args) {
        runMenuGame();
    }

    private static void runMenuGame() {
        System.out.println("启动游戏引擎...");

        GameEngine engine = null;
        try {
            System.out.println("使用渲染后端: GPU");
            engine = new GameEngine(800, 600, "游戏引擎", RenderBackend.GPU);

            MenuScene menuScene = new MenuScene(engine, "MainMenu");
            engine.setScene(menuScene);
            engine.run();
        } catch (Exception e) {
            System.err.println("游戏运行出错: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("游戏结束");
    }

    /**
     * 将原 GameExample 的玩法嵌入到 Game 中，方便通过 --example 运行。
     */
    public static void runClassicDemo() {
        System.out.println("启动游戏引擎（Classic Demo）...");
        try {
            GameEngine engine = new GameEngine(800, 600, "游戏引擎", RenderBackend.GPU);
            Scene scene = createClassicScene(engine);
            engine.setScene(scene);
            engine.run();
        } catch (Exception e) {
            System.err.println("游戏运行出错: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("游戏结束");
    }

    public static Scene createClassicScene(GameEngine engine) {
        return createClassicScene(engine, null);
    }

    public static Scene createClassicScene(GameEngine engine, SaveState initialState) {
        return new Scene("GameScene") {
            private IRenderer renderer;
            private Random random;
            private float time;
            private GameLogic gameLogic;
            private float fpsAccumulator = 0f;
            private int fpsFrames = 0;
            private String fpsText = "FPS: 0.0";
            private boolean waitingReturn = false;
            private float waitTimer = 0f;
            private final float waitDelay = 0.5f;
            private boolean paused = false;
            private int pauseIndex = 0;
            private final String[] pauseOptions = {"RESUME", "SAVE", "RETURN MENU"};
            private final float[] pauseButtonX = new float[pauseOptions.length];
            private final float[] pauseButtonY = new float[pauseOptions.length];
            private final float pauseButtonWidth = 240f;
            private final float pauseButtonHeight = 32f;
            private String pauseMessage = "";
            private float pauseMessageTimer = 0f;
            private final SaveState savedState = initialState;
            private long randomSeed;

            @Override
            public void initialize() {
                super.initialize();
                this.renderer = engine.getRenderer();
                this.randomSeed = savedState != null && savedState.randomSeed != 0
                    ? savedState.randomSeed
                    : System.nanoTime();
                this.random = new Random(randomSeed);
                this.time = 0;
                this.gameLogic = new GameLogic(this);
                this.gameLogic.setWorldSize(renderer.getWidth(), renderer.getHeight());
                this.waitingReturn = false;
                this.waitTimer = 0f;
                this.paused = false;
                this.pauseIndex = 0;
                this.pauseMessage = "";
                this.pauseMessageTimer = 0f;
                if (savedState != null) {
                    applySavedState(savedState);
                } else {
                    createPlayer();
                    createEnemies();
                    createDecorations();
                }
            }

            @Override
            public void update(float deltaTime) {
                super.update(deltaTime);
                InputManager input = engine.getInputManager();

                boolean escPressed = input.isKeyJustPressed(27) || input.isKeyJustPressed(256);
                if (!waitingReturn && escPressed) {
                    paused = !paused;
                    pauseIndex = 0;
                    pauseMessageTimer = 0f;
                    return;
                }

                if (waitingReturn) {
                    handleReturnPrompt(deltaTime);
                    return;
                }

                if (paused) {
                    handlePauseMenu(deltaTime, input);
                    return;
                }

                time += deltaTime;

                this.gameLogic.setWorldSize(renderer.getWidth(), renderer.getHeight());

                fpsAccumulator += deltaTime;
                fpsFrames += 1;
                if (fpsAccumulator >= 1.0f) {
                    float fps = fpsFrames / fpsAccumulator;
                    fpsText = String.format("FPS: %.1f", fps);
                    fpsAccumulator = 0f;
                    fpsFrames = 0;
                }

                gameLogic.handlePlayerInput();
                gameLogic.updatePhysics();
                gameLogic.handleAIPlayerAvoidance();
                gameLogic.checkCollisions();
                gameLogic.handleShooting(deltaTime);

                for (RenderComponent rc : getComponents(RenderComponent.class)) {
                    rc.setRenderer(renderer);
                }

                if (time > 0.4f) {
                    createEnemy();
                    time = 0;
                }

                if (gameLogic.isGameOver()) {
                    waitingReturn = true;
                    waitTimer = 0f;
                }
            }

            @Override
            public void render() {
                int width = renderer.getWidth();
                int height = renderer.getHeight();
                renderer.drawRect(0, 0, width, height, 0.1f, 0.1f, 0.2f, 1.0f);
                super.render();

                String hud = String.format("Score: %d", gameLogic.getScore());
                renderer.drawText(10, 20, hud, 1f, 1f, 1f, 1f);
                String life = String.format("Lives: %d", gameLogic.getLives());
                renderer.drawText(10, 40, life, 1f, 1f, 1f, 1f);
                renderer.drawText(width - 160, 20, fpsText, 1f, 1f, 0.2f, 1f);

                if (gameLogic.isGameOver()) {
                    float cx = width / 2f;
                    float cy = height / 2f;
                    renderer.drawText(cx - 80, cy - 10, "GAME OVER", 1f, 0f, 0f, 1f);
                    String prompt = waitingReturn && waitTimer >= waitDelay
                        ? "PRESS ANY KEY TO RETURN"
                        : "PLEASE WAIT...";
                    renderer.drawText(cx - 170, cy + 30, prompt, 0.9f, 0.9f, 0.9f, 1f);
                }

                if (paused) {
                    drawPauseOverlay(width, height);
                }

                Vector2 aimPos = gameLogic.getAimPosition();
                boolean holding = gameLogic.isAiming();
                float col = holding ? 1f : 0.7f;
                renderer.drawCircle(aimPos.x, aimPos.y, 6, 12, col, col, col, 1f);
            }
            
            private void drawPauseOverlay(int width, int height) {
                renderer.drawRect(0, 0, width, height, 0f, 0f, 0f, 0.55f);
                float cx = width / 2f;
                float cy = height / 2f;
                renderer.drawRect(cx - 180, cy - 120, 360, 240, 0.1f, 0.1f, 0.12f, 0.9f);
                renderer.drawText(cx - 50, cy - 80, "PAUSED", 1f, 1f, 1f, 1f);
                for (int i = 0; i < pauseOptions.length; i++) {
                    String text = pauseOptions[i];
                    float boxX = cx - pauseButtonWidth / 2f;
                    float boxY = cy - 40 + i * 50;
                    pauseButtonX[i] = boxX;
                    pauseButtonY[i] = boxY;
                    boolean selected = (i == pauseIndex);
                    float bgR = selected ? 0.6f : 0.25f;
                    float bgG = selected ? 0.55f : 0.25f;
                    float bgB = selected ? 0.2f : 0.3f;
                    renderer.drawRect(boxX, boxY, pauseButtonWidth, pauseButtonHeight, bgR, bgG, bgB, 0.9f);
                    float r = selected ? 1f : 0.95f;
                    float g = selected ? 1f : 0.95f;
                    float b = selected ? 0.6f : 0.7f;
                    float estimatedWidth = text.length() * 12f;
                    float textX = boxX + (pauseButtonWidth - estimatedWidth) / 2f;
                    float textY = boxY + pauseButtonHeight / 2f + 6f;
                    renderer.drawText(textX, textY, text, r, g, b, 1f);
                }
                if (pauseMessageTimer > 0f && pauseMessage != null && !pauseMessage.isEmpty()) {
                    renderer.drawText(cx - 150, cy + 120, pauseMessage, 0.9f, 0.9f, 0.2f, 1f);
                }
            }

            private void handleReturnPrompt(float deltaTime) {
                waitTimer += deltaTime;
                if (waitTimer < waitDelay) return;
                if (engine.getInputManager().isAnyKeyJustPressed()
                    || engine.getInputManager().isMouseButtonJustPressed(0)) {
                    MenuScene menu = new MenuScene(engine, "MainMenu");
                    engine.setScene(menu);
                }
            }

            private void handlePauseMenu(float deltaTime, InputManager input) {
                if (pauseMessageTimer > 0f) {
                    pauseMessageTimer = Math.max(0f, pauseMessageTimer - deltaTime);
                }
                if (input.isKeyJustPressed(38) || input.isKeyJustPressed(265)) {
                    pauseIndex = (pauseIndex - 1 + pauseOptions.length) % pauseOptions.length;
                } else if (input.isKeyJustPressed(40) || input.isKeyJustPressed(264)) {
                    pauseIndex = (pauseIndex + 1) % pauseOptions.length;
                } else if (input.isKeyJustPressed(10) || input.isKeyJustPressed(32) ||
                           input.isKeyJustPressed(257) || input.isKeyJustPressed(335)) {
                    executePauseAction();
                } else if (input.isMouseButtonJustPressed(0)) {
                    Vector2 mouse = input.getMousePosition();
                    for (int i = 0; i < pauseOptions.length; i++) {
                        float bx = pauseButtonX[i];
                        float by = pauseButtonY[i];
                        if (mouse.x >= bx && mouse.x <= bx + pauseButtonWidth &&
                            mouse.y >= by && mouse.y <= by + pauseButtonHeight) {
                            pauseIndex = i;
                            executePauseAction();
                            break;
                        }
                    }
                }
            }

            private void executePauseAction() {
                switch (pauseOptions[pauseIndex]) {
                    case "RESUME" -> paused = false;
                    case "SAVE" -> performManualSave();
                    case "RETURN MENU" -> {
                        MenuScene menu = new MenuScene(engine, "MainMenu");
                        engine.setScene(menu);
                    }
                }
            }

            private void createPlayer() {
                spawnPlayerAt(new Vector2(renderer.getWidth() / 2f, renderer.getHeight() / 2f), new Vector2());
            }

            private void spawnPlayerAt(Vector2 position, Vector2 velocity) {
                GameObject player = new GameObject("Player") {
                    private Vector2 basePosition;

                    @Override
                    public void update(float deltaTime) {
                        super.update(deltaTime);
                        updateComponents(deltaTime);
                        updateBodyParts();
                    }

                    @Override
                    public void render() {
                        renderBodyParts();
                    }

                    private void updateBodyParts() {
                        TransformComponent transform = getComponent(TransformComponent.class);
                        if (transform != null) {
                            basePosition = transform.getPosition();
                        }
                    }

                    private void renderBodyParts() {
                        if (basePosition == null) return;

                        renderer.drawRect(basePosition.x - 8, basePosition.y - 10, 16, 20, 1.0f, 0.0f, 0.0f, 1.0f);
                        renderer.drawRect(basePosition.x - 6, basePosition.y - 22, 12, 12, 1.0f, 0.5f, 0.0f, 1.0f);
                        renderer.drawRect(basePosition.x - 13, basePosition.y - 5, 6, 12, 1.0f, 0.8f, 0.0f, 1.0f);
                        renderer.drawRect(basePosition.x + 7, basePosition.y - 5, 6, 12, 0.0f, 1.0f, 0.0f, 1.0f);
                    }
                };

                player.addComponent(new TransformComponent(new Vector2(position)));
                PhysicsComponent physics = player.addComponent(new PhysicsComponent(1.0f));
                physics.setFriction(0.95f);
                if (velocity != null) {
                    physics.setVelocity(velocity);
                }
                addGameObject(player);
            }

            private void createEnemies() {
                for (int i = 0; i < 3; i++) {
                    createEnemy();
                }
            }

            private void createEnemy() {
                Vector2 position = new Vector2(
                    random.nextFloat() * renderer.getWidth(),
                    random.nextFloat() * renderer.getHeight()
                );
                Vector2 velocity = new Vector2(
                    (random.nextFloat() - 0.5f) * 100,
                    (random.nextFloat() - 0.5f) * 100
                );
                spawnEnemyAt(position, velocity);
            }

            private void spawnEnemyAt(Vector2 position, Vector2 velocity) {
                GameObject enemy = new GameObject("Enemy") {
                    @Override
                    public void update(float deltaTime) {
                        super.update(deltaTime);
                        updateComponents(deltaTime);
                    }

                    @Override
                    public void render() {
                        renderComponents();
                    }
                };

                enemy.addComponent(new TransformComponent(new Vector2(position)));
                RenderComponent render = enemy.addComponent(new RenderComponent(
                    RenderComponent.RenderType.RECTANGLE,
                    new Vector2(20, 20),
                    new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f)
                ));
                render.setRenderer(renderer);

                PhysicsComponent physics = enemy.addComponent(new PhysicsComponent(0.5f));
                if (velocity != null) {
                    physics.setVelocity(new Vector2(velocity));
                }
                physics.setFriction(0.98f);
                enemy.addComponent(new com.gameengine.components.EnemyAIComponent());

                addGameObject(enemy);
            }

            private void createDecorations() {
                for (int i = 0; i < 5; i++) {
                    createDecoration();
                }
            }

            private void createDecoration() {
                Vector2 position = new Vector2(
                    random.nextFloat() * renderer.getWidth(),
                    random.nextFloat() * renderer.getHeight()
                );
                spawnDecorationAt(position);
            }

            private void spawnDecorationAt(Vector2 position) {
                GameObject decoration = new GameObject("Decoration") {
                    @Override
                    public void update(float deltaTime) {
                        super.update(deltaTime);
                        updateComponents(deltaTime);
                    }

                    @Override
                    public void render() {
                        renderComponents();
                    }
                };

                decoration.addComponent(new TransformComponent(new Vector2(position)));
                RenderComponent render = decoration.addComponent(new RenderComponent(
                    RenderComponent.RenderType.CIRCLE,
                    new Vector2(5, 5),
                    new RenderComponent.Color(0.5f, 0.5f, 1.0f, 0.8f)
                ));
                render.setRenderer(renderer);

                addGameObject(decoration);
            }

            private void spawnBulletFromState(SaveState.EntityState e) {
                GameObject bullet = new GameObject("Bullet");
                bullet.addComponent(new TransformComponent(new Vector2(e.x, e.y)));
                RenderComponent rc = bullet.addComponent(new RenderComponent(
                    RenderComponent.RenderType.CIRCLE,
                    new Vector2(e.width > 0 ? e.width : 6, e.height > 0 ? e.height : 6),
                    new RenderComponent.Color(e.colorR, e.colorG, e.colorB, e.colorA)
                ));
                rc.setRenderer(renderer);
                Vector2 vel = new Vector2(e.projectileSpeedX, e.projectileSpeedY);
                float life = e.projectileLife > 0 ? e.projectileLife : 3.0f;
                ProjectileComponent proj = bullet.addComponent(new ProjectileComponent(vel, life));
                proj.setVelocity(vel);
                proj.setLifetime(life);
                addGameObject(bullet);
            }

            private void performManualSave() {
                try {
                    SaveState snapshot = captureCurrentState();
                    String path = SaveIO.nextSavePath();
                    SaveIO.write(snapshot, path);
                    pauseMessage = "Saved to " + new File(path).getName();
                } catch (Exception e) {
                    pauseMessage = "Save failed: " + e.getMessage();
                }
                pauseMessageTimer = 2.5f;
            }

            private SaveState captureCurrentState() {
                SaveState state = new SaveState();
                state.score = gameLogic.getScore();
                state.lives = gameLogic.getLives();
                state.spawnTimer = time;
                state.timeSinceLastShot = gameLogic.getTimeSinceLastShot();
                state.randomSeed = System.nanoTime();
                List<GameObject> objects = getGameObjects();
                for (GameObject obj : objects) {
                    SaveState.EntityState es = toEntityState(obj);
                    if (es != null) {
                        state.entities.add(es);
                    }
                }
                return state;
            }

            private SaveState.EntityState toEntityState(GameObject obj) {
                TransformComponent tc = obj.getComponent(TransformComponent.class);
                if (tc == null) return null;
                SaveState.EntityState es = new SaveState.EntityState();
                es.name = obj.getName();
                es.type = classifyType(obj.getName());
                Vector2 pos = tc.getPosition();
                es.x = pos.x;
                es.y = pos.y;
                PhysicsComponent pc = obj.getComponent(PhysicsComponent.class);
                if (pc != null) {
                    Vector2 vel = pc.getVelocity();
                    es.vx = vel.x;
                    es.vy = vel.y;
                }
                RenderComponent rc = obj.getComponent(RenderComponent.class);
                if (rc != null) {
                    Vector2 size = rc.getSize();
                    es.width = size.x;
                    es.height = size.y;
                    RenderComponent.Color color = rc.getColor();
                    es.colorR = color.r;
                    es.colorG = color.g;
                    es.colorB = color.b;
                    es.colorA = color.a;
                }
                ProjectileComponent proj = obj.getComponent(ProjectileComponent.class);
                if (proj != null) {
                    Vector2 vel = proj.getVelocity();
                    es.projectileSpeedX = vel.x;
                    es.projectileSpeedY = vel.y;
                    es.projectileLife = proj.getLifetime();
                }
                return es;
            }

            private String classifyType(String name) {
                if (name == null) return "Decoration";
                if (name.equalsIgnoreCase("Player")) return "Player";
                if (name.equalsIgnoreCase("Enemy") || name.equalsIgnoreCase("AIPlayer")) return "Enemy";
                if (name.equalsIgnoreCase("Bullet")) return "Bullet";
                if (name.equalsIgnoreCase("Decoration")) return "Decoration";
                return name;
            }

            private void applySavedState(SaveState state) {
                this.time = state.spawnTimer;
                this.gameLogic.setScore(state.score);
                this.gameLogic.setLives(state.lives);
                this.gameLogic.setTimeSinceLastShot(state.timeSinceLastShot);
                if (state.entities.isEmpty()) {
                    createPlayer();
                } else {
                    for (SaveState.EntityState entity : state.entities) {
                        spawnEntityFromState(entity);
                    }
                }
            }

            private void spawnEntityFromState(SaveState.EntityState entity) {
                Vector2 pos = new Vector2(entity.x, entity.y);
                Vector2 vel = new Vector2(entity.vx, entity.vy);
                switch (entity.type == null ? "Decoration" : entity.type) {
                    case "Player" -> spawnPlayerAt(pos, vel);
                    case "Enemy" -> spawnEnemyAt(pos, vel);
                    case "Bullet" -> spawnBulletFromState(entity);
                    default -> spawnDecorationAt(pos);
                }
            }
        };
    }
}

