package com.gameengine.core;

import com.gameengine.components.TransformComponent;
import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.EnemyAIComponent;
import com.gameengine.math.Vector2;
import com.gameengine.input.InputManager;
import com.gameengine.scene.Scene;
import com.gameengine.graphics.IRenderer;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


/**
 * 游戏逻辑类，处理具体的游戏规则（含射击、得分、生命）
 */
public class GameLogic {
    private final Scene scene;
    private final InputManager inputManager;


    private final ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()));

    // HUD
    private int score = 0;
    private int lives = 3;

    // 世界/画布尺寸（用于边界检测），默认与示例一致
    private int worldWidth = 800;
    private int worldHeight = 600;

    public void setWorldSize(int w, int h) {
        this.worldWidth = w;
        this.worldHeight = h;
    }

    // Shooting
    private float fireCooldown = 0.25f;
    private float timeSinceLastShot = 0f;
    private float bulletSpeed = 600f;
    // Crosshair distance (optional) - not used currently, crosshair follows mouse
    private float crosshairDistance = 60f;

    public GameLogic(Scene scene) {
        this.scene = scene;
        this.inputManager = InputManager.getInstance();
    }

    /**
     * 处理玩家输入（移动）
     */
    public void setGameEngine(GameEngine engine) {
        if (engine != null) {
            IRenderer renderer = engine.getRenderer();
            if (renderer != null) {
                setWorldSize(renderer.getWidth(), renderer.getHeight());
            }
        }
    }

    public void handlePlayerInput(float deltaTime) {
        handlePlayerInput();
        handleShooting(deltaTime);
    }

    public void handlePlayerInput() {
        List<GameObject> players = scene.findGameObjectsByComponent(TransformComponent.class);
        if (players.isEmpty()) return;

        GameObject player = players.get(0);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        PhysicsComponent physics = player.getComponent(PhysicsComponent.class);

        if (transform == null || physics == null) return;

        Vector2 movement = new Vector2();

        boolean upPressed = inputManager.isKeyPressed(87) || inputManager.isKeyPressed(38) || inputManager.isKeyPressed(265);
        boolean downPressed = inputManager.isKeyPressed(83) || inputManager.isKeyPressed(40) || inputManager.isKeyPressed(264);
        boolean leftPressed = inputManager.isKeyPressed(65) || inputManager.isKeyPressed(37) || inputManager.isKeyPressed(263);
        boolean rightPressed = inputManager.isKeyPressed(68) || inputManager.isKeyPressed(39) || inputManager.isKeyPressed(262);

        if (upPressed) { // W或上箭头
            movement.y -= 1;
        }
        if (downPressed) { // S或下箭头
            movement.y += 1;
        }
        if (leftPressed) { // A或左箭头
            movement.x -= 1;
        }
        if (rightPressed) { // D或右箭头
            movement.x += 1;
        }

        if (movement.magnitude() > 0) {
            movement = movement.normalize().multiply(200);
            physics.setVelocity(movement);
        }

        // 边界检查
        Vector2 pos = transform.getPosition();
        if (pos.x < 0) pos.x = 0;
        if (pos.y < 0) pos.y = 0;
        if (pos.x > worldWidth - 20) pos.x = worldWidth - 20;
        if (pos.y > worldHeight - 20) pos.y = worldHeight - 20;
        transform.setPosition(pos);
    }

    /**
     * 更新物理系统
     */
    public void updatePhysics() {
        List<PhysicsComponent> physicsComponents = scene.getComponents(PhysicsComponent.class);
        int total = physicsComponents.size();
        if (total == 0) return;

        int parallelThreshold = 10;
        if (total < parallelThreshold) {
            for (PhysicsComponent physics : physicsComponents) {
                applyBoundaryAndClamp(physics);
            }
            return;
        }

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        int batchSize = (total + threads - 1) / threads;

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < total; i += batchSize) {
            final int start = i;
            final int end = Math.min(total, i + batchSize);
            tasks.add(() -> {
                for (int j = start; j < end; j++) {
                    PhysicsComponent physics = physicsComponents.get(j);
                    applyBoundaryAndClamp(physics);
                }
                return null;
            });
        }

        try {
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException ee) {
                    ee.printStackTrace();
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void applyBoundaryAndClamp(PhysicsComponent physics) {
        TransformComponent transform = physics.getOwner().getComponent(TransformComponent.class);
        if (transform != null) {
            Vector2 pos = transform.getPosition();
            Vector2 velocity = physics.getVelocity();

            float maxX = worldWidth - 15;
            float maxY = worldHeight - 15;
            com.gameengine.components.RenderComponent rc = physics.getOwner().getComponent(com.gameengine.components.RenderComponent.class);
            if (rc != null) {
                com.gameengine.math.Vector2 sz = rc.getSize();
                if (sz != null) {
                    maxX = worldWidth - sz.x;
                    maxY = worldHeight - sz.y;
                }
            }

            if (pos.x <= 0 || pos.x >= maxX) {
                velocity.x = -velocity.x;
                physics.setVelocity(velocity);
            }
            if (pos.y <= 0 || pos.y >= maxY) {
                velocity.y = -velocity.y;
                physics.setVelocity(velocity);
            }

            // 确保在边界内
            if (pos.x < 0) pos.x = 0;
            if (pos.y < 0) pos.y = 0;
            if (pos.x > maxX) pos.x = maxX;
            if (pos.y > maxY) pos.y = maxY;
            transform.setPosition(pos);
        }
    }

    /**
     * 自适应并行的 AI 避障处理：当敌人数量达到一定规模时并行计算每个敌人的避让向量并施加到其速度上。
     */
    public void handleAIPlayerMovement(float deltaTime) {
        updatePhysics();
    }

    public void handleAIPlayerAvoidance() {
        List<GameObject> enemies = scene.findGameObjectsByComponent(EnemyAIComponent.class);
        int total = enemies.size();
        if (total <= 1) return;


        int serialThreshold = 10;
    final float avoidDist = 80f;
    final float strength = 30f;

        if (total < serialThreshold) {
            for (GameObject enemy : enemies) {
                applyAvoidanceForOne(enemy, enemies, avoidDist, strength);
            }
            return;
        }

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        int batchSize = (total + threads - 1) / threads;
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < total; i += batchSize) {
            final int start = i;
            final int end = Math.min(total, i + batchSize);
            tasks.add(() -> {
                for (int j = start; j < end; j++) {
                    GameObject enemy = enemies.get(j);
                    applyAvoidanceForOne(enemy, enemies, avoidDist, strength);
                }
                return null;
            });
        }

        try {
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> f : futures) {
                try { f.get(); } catch (ExecutionException ee) { ee.printStackTrace(); }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }


    private void applyAvoidanceForOne(GameObject self, List<GameObject> allEnemies, float avoidDist, float strength) {
        TransformComponent selfT = self.getComponent(TransformComponent.class);
        PhysicsComponent selfP = self.getComponent(PhysicsComponent.class);
        if (selfT == null || selfP == null) return;

        Vector2 selfPos = selfT.getPosition();
        Vector2 repel = new Vector2(0,0);

        for (GameObject other : allEnemies) {
            if (other == self) continue;
            TransformComponent ot = other.getComponent(TransformComponent.class);
            if (ot == null) continue;
            Vector2 op = ot.getPosition();
            float dist = selfPos.distance(op);
            if (dist > 0 && dist < avoidDist) {
                Vector2 away = selfPos.subtract(op).normalize();
                float factor = (avoidDist - dist) / avoidDist;
                repel = repel.add(away.multiply(factor));
            }
        }

        if (repel.magnitude() > 0) {
            Vector2 v = selfP.getVelocity();
            float factor = repel.magnitude();
       
            float proximity = Math.min(1.0f, factor / avoidDist);
            float effect = strength * (proximity * proximity);

            Vector2 delta = repel.normalize().multiply(effect);

            float maxDelta = 30f;
            if (delta.magnitude() > maxDelta) {
                delta = delta.normalize().multiply(maxDelta);
            }

            Vector2 newV = v.add(delta);
            selfP.setVelocity(newV);
        }
    }

    /**
     * 检查碰撞
     */
    public void checkCollisions() {
        // 直接查找玩家对象
        List<GameObject> players = scene.findGameObjectsByComponent(TransformComponent.class);
        if (players.isEmpty()) return;

        GameObject player = players.get(0);
        TransformComponent playerTransform = player.getComponent(TransformComponent.class);
        if (playerTransform == null) return;
        // 使用 RenderComponent 的尺寸和类型进行更精确的碰撞判定（像素重合判定）
        for (GameObject obj : scene.getGameObjects()) {
            if (!obj.getName().equals("Enemy")) continue;
            if (isColliding(player, obj)) {
                // 碰撞！玩家受伤并复位
                lives -= 1;
                playerTransform.setPosition(new Vector2(400, 300));
                if (lives <= 0) {
                    // 标记所有对象停用（简单处理）
                    for (GameObject gobj : scene.getGameObjects()) {
                        gobj.setActive(false);
                    }
                }
                break;
            }
        }

        // 子弹与敌人碰撞：遍历 bullets，使用像素重合判定
        for (GameObject bullet : scene.getGameObjects()) {
            if (!"Bullet".equals(bullet.getName())) continue;
            for (GameObject enemy : scene.getGameObjects()) {
                if (!"Enemy".equals(enemy.getName())) continue;
                if (isColliding(bullet, enemy)) {
                    // 命中：销毁子弹，销毁或移除敌人并加分
                    bullet.destroy();
                    enemy.destroy();
                    score += 1;
                    break;
                }
            }
        }

        // 敌人之间的碰撞分离：防止重叠，做小幅移动并加上小的速度冲量以产生可见的短距离分离
        List<GameObject> enemies = scene.findGameObjectsByComponent(EnemyAIComponent.class);
        int n = enemies.size();
        final float desiredSeparation = 8f; // 希望的最小分离距离（像素）
        final float separationVelocity = 40f; // 施加到速度上的分量量级
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                GameObject a = enemies.get(i);
                GameObject b = enemies.get(j);
                if (a == null || b == null) continue;
                if (!isColliding(a, b)) continue;

                // 计算中心和当前距离
                Vector2 ca = getObjectCenter(a);
                Vector2 cb = getObjectCenter(b);
                Vector2 diff = ca.subtract(cb);
                float dist = diff.magnitude();
                if (dist == 0) {
                    // 任意小偏移，避免完全重合造成 NaN
                    diff = new Vector2(0.01f, 0.01f);
                    dist = diff.magnitude();
                }

                float overlap = Math.max(0f, desiredSeparation - dist);
                if (overlap <= 0f) {
                    
                    overlap = 2f;
                }

                Vector2 pushDir = diff.normalize();

                TransformComponent ta = a.getComponent(TransformComponent.class);
                TransformComponent tb = b.getComponent(TransformComponent.class);
                if (ta != null && tb != null) {
                    Vector2 pa = ta.getPosition();
                    Vector2 pb = tb.getPosition();
                    Vector2 shift = pushDir.multiply(overlap / 2f);
                    ta.setPosition(pa.add(shift));
                    tb.setPosition(pb.subtract(shift));
                }


                PhysicsComponent paPhys = a.getComponent(PhysicsComponent.class);
                PhysicsComponent pbPhys = b.getComponent(PhysicsComponent.class);
                if (paPhys != null && pbPhys != null) {
                    Vector2 dv = pushDir.multiply(separationVelocity * (overlap / desiredSeparation));

                    float maxDv = 30f;
                    if (dv.magnitude() > maxDv) dv = dv.normalize().multiply(maxDv);
                    paPhys.setVelocity(paPhys.getVelocity().add(dv));
                    pbPhys.setVelocity(pbPhys.getVelocity().subtract(dv));
                }
            }
        }
    }


    private Vector2 getObjectCenter(GameObject obj) {
        TransformComponent t = obj.getComponent(TransformComponent.class);
        com.gameengine.components.RenderComponent r = obj.getComponent(com.gameengine.components.RenderComponent.class);
        if (t == null) return new Vector2();
        Vector2 p = t.getPosition();
        if (r == null) return p;
        Vector2 s = r.getSize();
        if (r.getRenderType() == com.gameengine.components.RenderComponent.RenderType.CIRCLE) {
            return new Vector2(p.x + s.x / 2f, p.y + s.y / 2f);
        }

        return new Vector2(p.x + s.x / 2f, p.y + s.y / 2f);
    }

    // 基于 RenderComponent 类型和尺寸做两个 GameObject 的像素级重合判定
    private boolean isColliding(GameObject a, GameObject b) {
        com.gameengine.components.RenderComponent ra = a.getComponent(com.gameengine.components.RenderComponent.class);
        com.gameengine.components.RenderComponent rb = b.getComponent(com.gameengine.components.RenderComponent.class);
        TransformComponent ta = a.getComponent(TransformComponent.class);
        TransformComponent tb = b.getComponent(TransformComponent.class);

        // 若缺少渲染组件或变换组件，回退到中心点距离判定（保守策略）
        if (ra == null || rb == null || ta == null || tb == null) {
            Vector2 pa = ta != null ? ta.getPosition() : new Vector2();
            Vector2 pb = tb != null ? tb.getPosition() : new Vector2();
            return pa.distance(pb) < 25;
        }

        // 获取类型与尺寸
        com.gameengine.components.RenderComponent.RenderType taType = ra.getRenderType();
        com.gameengine.components.RenderComponent.RenderType tbType = rb.getRenderType();
        Vector2 pa = ta.getPosition();
        Vector2 pb = tb.getPosition();
        Vector2 sa = ra.getSize();
        Vector2 sb = rb.getSize();

        // 统一坐标约定：
        // - RECTANGLE: position = top-left, size = width/height
        // - CIRCLE: RenderComponent.render 绘制时以 (pos.x + size.x/2, pos.y + size.y/2) 作为圆心，半径 = size.x/2

        // 判断几种组合
        if (taType == com.gameengine.components.RenderComponent.RenderType.RECTANGLE && tbType == com.gameengine.components.RenderComponent.RenderType.RECTANGLE) {
            return rectRectOverlap(pa, sa, pb, sb);
        }
        if (taType == com.gameengine.components.RenderComponent.RenderType.CIRCLE && tbType == com.gameengine.components.RenderComponent.RenderType.CIRCLE) {
            float raRadius = sa.x / 2f;
            float rbRadius = sb.x / 2f;
            Vector2 ca = new Vector2(pa.x + sa.x/2f, pa.y + sa.y/2f);
            Vector2 cb = new Vector2(pb.x + sb.x/2f, pb.y + sb.y/2f);
            return ca.distance(cb) <= (raRadius + rbRadius);
        }
        // rect - circle combinations
        if (taType == com.gameengine.components.RenderComponent.RenderType.RECTANGLE && tbType == com.gameengine.components.RenderComponent.RenderType.CIRCLE) {
            Vector2 centerB = new Vector2(pb.x + sb.x/2f, pb.y + sb.y/2f);
            return rectCircleOverlap(pa, sa, centerB, sb.x/2f);
        }
        if (taType == com.gameengine.components.RenderComponent.RenderType.CIRCLE && tbType == com.gameengine.components.RenderComponent.RenderType.RECTANGLE) {
            Vector2 centerA = new Vector2(pa.x + sa.x/2f, pa.y + sa.y/2f);
            return rectCircleOverlap(pb, sb, centerA, sa.x/2f);
        }

        // 其他情况（例如 LINE）回退到中心距离判定
        Vector2 ca = ta.getPosition();
        Vector2 cb = tb.getPosition();
        return ca.distance(cb) < 25;
    }

    private boolean rectRectOverlap(Vector2 p1, Vector2 s1, Vector2 p2, Vector2 s2) {
        return p1.x < p2.x + s2.x && p1.x + s1.x > p2.x && p1.y < p2.y + s2.y && p1.y + s1.y > p2.y;
    }

    private boolean rectCircleOverlap(Vector2 rectPos, Vector2 rectSize, Vector2 circleCenter, float radius) {
        float closestX = clamp(circleCenter.x, rectPos.x, rectPos.x + rectSize.x);
        float closestY = clamp(circleCenter.y, rectPos.y, rectPos.y + rectSize.y);
        float dx = circleCenter.x - closestX;
        float dy = circleCenter.y - closestY;
        return dx*dx + dy*dy <= radius * radius;
    }

    private float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    // Shooting related: expose a public method to be called from Scene.update
    public void handleShooting(float deltaTime) {
        timeSinceLastShot += deltaTime;
        boolean mousePressed = inputManager.isMouseButtonPressed(0) || inputManager.isMouseButtonPressed(1);
        boolean spaceJust = inputManager.isKeyJustPressed(32);

        // On each shot use current mouse/crosshair direction. Long press does not lock aim.
        if ((mousePressed || spaceJust) && timeSinceLastShot >= fireCooldown && lives > 0) {
            timeSinceLastShot = 0f;
            spawnBullet();
        }
    }

    private void spawnBullet() {
        List<GameObject> players = scene.findGameObjectsByComponent(TransformComponent.class);
        if (players.isEmpty()) return;
        GameObject player = players.get(0);
        TransformComponent pt = player.getComponent(TransformComponent.class);
        if (pt == null) return;

    Vector2 playerPos = pt.getPosition();
    // Determine shoot direction: use current crosshair position (on circle around player)
    Vector2 aimPos = getAimPosition();
    Vector2 dir = aimPos.subtract(playerPos).normalize();
        if (dir.magnitude() == 0) dir = new Vector2(0, -1);
        Vector2 vel = dir.multiply(bulletSpeed);

        GameObject bullet = new GameObject("Bullet");
        bullet.addComponent(new com.gameengine.components.TransformComponent(new Vector2(playerPos)));
        bullet.addComponent(new com.gameengine.components.RenderComponent(com.gameengine.components.RenderComponent.RenderType.CIRCLE, new Vector2(6,6), new com.gameengine.components.RenderComponent.Color(1f,1f,0f,1f)));
        bullet.addComponent(new com.gameengine.components.ProjectileComponent(vel, 3.0f));

        scene.addGameObject(bullet);
    }

    public Vector2 getAimPosition() {
        // Place crosshair at a fixed distance from the player along the direction to the mouse
        List<GameObject> players = scene.findGameObjectsByComponent(TransformComponent.class);
        if (players.isEmpty()) return inputManager.getMousePosition();
        GameObject player = players.get(0);
        TransformComponent pt = player.getComponent(TransformComponent.class);
        if (pt == null) return inputManager.getMousePosition();
        Vector2 playerPos = pt.getPosition();
        Vector2 mouse = inputManager.getMousePosition();
        Vector2 dir = mouse.subtract(playerPos).normalize();
        if (dir.magnitude() == 0) dir = new Vector2(0, -1);
        return new Vector2(playerPos.x + dir.x * crosshairDistance, playerPos.y + dir.y * crosshairDistance);
    }

    public boolean isAiming() {
        return inputManager.isMouseButtonPressed(1);
    }

    // HUD getters
    public int getScore() { return score; }
    public int getLives() { return lives; }
    public boolean isGameOver() { return lives <= 0; }
    public float getTimeSinceLastShot() { return timeSinceLastShot; }

    public void setScore(int score) { this.score = Math.max(0, score); }
    public void setLives(int lives) { this.lives = Math.max(0, lives); }
    public void setTimeSinceLastShot(float value) { this.timeSinceLastShot = Math.max(0f, value); }

    public GameObject getUserPlayer() {
        for (GameObject obj : scene.getGameObjects()) {
            if ("Player".equalsIgnoreCase(obj.getName())) {
                return obj;
            }
        }
        return null;
    }

    public List<GameObject> getAIPlayers() {
        List<GameObject> result = new ArrayList<>();
        for (GameObject obj : scene.getGameObjects()) {
            if ("AIPlayer".equalsIgnoreCase(obj.getName())) {
                result.add(obj);
            }
        }
        return result;
    }

    public void cleanup() {
        if (!executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    public void handleAIPlayerAvoidance(float deltaTime) {
        handleAIPlayerAvoidance();
    }

}
