package com.pla.smart_npc.entity.ai;

/**
 * Pure cursor for a bounded survey; retaining it prevents stationary scan starvation.
 * Shells use max(abs(x), abs(z), 2 * abs(y)) so nearby ground is surveyed before
 * distant vertical layers. Each shell checks foot level first, then alternates down/up.
 */
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
        int shell = 0;
        while (volumeThrough(shell) <= current) shell++;
        int column = current - (shell == 0 ? 0 : volumeThrough(shell - 1));
        int ring = Math.min(shell, this.radius);
        int sideLength = 2 * ring + 1;
        for (int verticalIndex = 0; verticalIndex < VERTICAL_LAYERS; verticalIndex++) {
            int dy = verticalIndex == 0 ? 0
                    : (verticalIndex % 2 == 1 ? -1 : 1) * ((verticalIndex + 1) / 2);
            int verticalDistance = 2 * Math.abs(dy);
            // A newly reached height contributes its entire square; older heights
            // contribute only the newly reached horizontal perimeter.
            boolean fullSquare = verticalDistance == shell;
            int columns = fullSquare ? sideLength * sideLength
                    : verticalDistance < shell && shell <= this.radius ? 8 * ring : 0;
            if (column >= columns) {
                column -= columns;
                continue;
            }
            if (fullSquare) return new Offset(column / sideLength - ring, dy, column % sideLength - ring);
            return perimeter(ring, dy, column);
        }
        throw new IllegalStateException("Survey index outside bounded shells");
    }

    private int volumeThrough(int shell) {
        int width = 2 * Math.min(shell, this.radius) + 1;
        int height = 2 * Math.min(shell / 2, VERTICAL_LAYERS / 2) + 1;
        return width * width * height;
    }

    private static Offset perimeter(int ring, int dy, int column) {
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
