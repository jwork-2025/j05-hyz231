package com.gameengine.components;

import com.gameengine.core.Component;
import com.gameengine.math.Vector2;
import java.util.Random;

/**
 * 简单敌人AI：每隔一段随机时间改变移动方向和速度
 */
public class EnemyAIComponent extends Component<EnemyAIComponent> {
    private Random random = new Random();
    private float changeIntervalMin = 0.5f;
    private float changeIntervalMax = 2.0f;
    private float timeUntilChange = 0f;
    private float speedMin = 30f;
    private float speedMax = 120f;

    @Override
    public void initialize() {
        scheduleNext();
    }

    @Override
    public void update(float deltaTime) {
        if (!enabled) return;
        timeUntilChange -= deltaTime;
        if (timeUntilChange <= 0f) {
            // pick new random direction & speed
            float angle = (float) (random.nextFloat() * Math.PI * 2);
            float speed = speedMin + random.nextFloat() * (speedMax - speedMin);
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed;

            // set velocity on physics component if available
            PhysicsComponent phys = owner.getComponent(PhysicsComponent.class);
            if (phys != null) {
                phys.setVelocity(vx, vy);
            } else {
                // fallback: move transform directly
                TransformComponent tf = owner.getComponent(TransformComponent.class);
                if (tf != null) {
                    Vector2 pos = tf.getPosition();
                    pos.x += vx * 0.1f; // small nudge
                    pos.y += vy * 0.1f;
                    tf.setPosition(pos);
                }
            }

            scheduleNext();
        }
    }

    @Override
    public void render() {
        // AI 不直接渲染
    }

    private void scheduleNext() {
        timeUntilChange = changeIntervalMin + random.nextFloat() * (changeIntervalMax - changeIntervalMin);
    }
}
