package com.gameengine.components;

import com.gameengine.core.Component;
import com.gameengine.math.Vector2;

/**
 * 简单投射物组件：按速度移动并有寿命，到期或超出边界自我销毁
 */
public class ProjectileComponent extends Component<ProjectileComponent> {
    private Vector2 velocity;
    private float lifetime;

    public ProjectileComponent(Vector2 velocity, float lifetime) {
        this.velocity = new Vector2(velocity);
        this.lifetime = lifetime;
    }

    @Override
    public void initialize() {
        // nothing
    }

    @Override
    public void update(float deltaTime) {
        TransformComponent tf = owner.getComponent(TransformComponent.class);
        if (tf == null) return;

        // 移动
        Vector2 pos = tf.getPosition();
        pos.x += velocity.x * deltaTime;
        pos.y += velocity.y * deltaTime;
        tf.setPosition(pos);

        lifetime -= deltaTime;
        if (lifetime <= 0f) {
            owner.destroy();
        }

        // 简单边界处理（确保不会无限飞出）
        if (pos.x < -50 || pos.y < -50 || pos.x > 850 || pos.y > 650) {
            owner.destroy();
        }
    }

    @Override
    public void render() {
        // 无需特殊渲染，RenderComponent 负责
    }

    public Vector2 getVelocity() {
        return new Vector2(velocity);
    }

    public void setVelocity(Vector2 velocity) {
        if (velocity != null) {
            this.velocity = new Vector2(velocity);
        }
    }

    public float getLifetime() {
        return lifetime;
    }

    public void setLifetime(float lifetime) {
        this.lifetime = lifetime;
    }
}
