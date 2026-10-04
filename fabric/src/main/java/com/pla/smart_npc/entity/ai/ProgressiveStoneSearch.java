package com.pla.smart_npc.entity.ai;

/** Pure cursor for a bounded nearest-ring survey; retaining it prevents stationary scan starvation. */
public final class ProgressiveStoneSearch {
    public static final int MAX_RADIUS = 24;
    private static final int VERTICAL_LAYERS = 13;
    public record Offset(int x, int y, int z) { }
    private final int radius;
    private final int size;
    private int index;

    public ProgressiveStoneSearch(int radius) {
        this.radius = Math.clamp(radius, 0, MAX_RADIUS);
        int width = 2 * this.radius + 1;
        this.size = width * width * VERTICAL_LAYERS;
    }

    public int radius() { return this.radius; }
    public int size() { return this.size; }

    public Offset next() {
        int current = this.index;
        this.index = (this.index + 1) % this.size;
        int ring = 0;
        while ((2 * ring + 1) * (2 * ring + 1) * VERTICAL_LAYERS <= current) ring++;
        int previousWidth = Math.max(0, 2 * ring - 1);
        int withinRing = current - previousWidth * previousWidth * VERTICAL_LAYERS;
        int columns = ring == 0 ? 1 : 8 * ring;
        int verticalIndex = withinRing / columns;
        int dy = verticalIndex == 0 ? 0 : (verticalIndex % 2 == 1 ? -1 : 1) * ((verticalIndex + 1) / 2);
        if (ring == 0) return new Offset(0, dy, 0);
        int column = withinRing % columns;
        int sideLength = 2 * ring + 1;
        if (column < sideLength) return new Offset(-ring, dy, column - ring);
        column -= sideLength;
        int middleLength = 2 * (2 * ring - 1);
        if (column < middleLength) {
            return new Offset(-ring + 1 + column / 2, dy, column % 2 == 0 ? -ring : ring);
        }
        return new Offset(ring, dy, column - middleLength - ring);
    }
}
