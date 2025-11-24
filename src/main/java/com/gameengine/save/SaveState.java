package com.gameengine.save;

import java.util.ArrayList;
import java.util.List;

/**
 * 表示一次可恢复的游戏状态。
 */
public class SaveState {
    public int version = 1;
    public int score;
    public int lives;
    public float spawnTimer;
    public float timeSinceLastShot;
    public long randomSeed;
    public final List<EntityState> entities = new ArrayList<>();

    public static class EntityState {
        public String type;
        public String name;
        public float x;
        public float y;
        public float vx;
        public float vy;
        public float width;
        public float height;
        public float colorR = 1f;
        public float colorG = 1f;
        public float colorB = 1f;
        public float colorA = 1f;
        public float projectileLife = 0f;
        public float projectileSpeedX = 0f;
        public float projectileSpeedY = 0f;
    }
}

