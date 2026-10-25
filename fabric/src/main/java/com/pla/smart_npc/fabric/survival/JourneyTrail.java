package com.pla.smart_npc.fabric.survival;

import java.util.ArrayList;
import java.util.List;

/** Bounded observed walking history. Never invent a connecting edge after a teleport. */
public final class JourneyTrail {
    public static final int LIMIT = 4096;
    public record Point(int x, int y, int z) {
        public double distance(Point b) {
            double dx=x-b.x,dy=y-b.y,dz=z-b.z;return dx*dx+dy*dy+dz*dz;
        }
    }
    private final ArrayList<Point> points = new ArrayList<>();
    public List<Point> points() { return java.util.Collections.unmodifiableList(points); }
    public void clear() { points.clear(); }
    public boolean full() { return points.size() >= LIMIT; }
    public boolean observe(Point p) {
        if(full()) return false;
        if (!points.isEmpty()) {
            double d=points.getLast().distance(p);
            if(d<4) return false;
            if(d>32*32) { points.clear(); points.add(p); return true; }
            // Close an actually revisited loop, without dropping the anchored beginning.
            for(int i=points.size()-2;i>=0;i--) if(points.get(i).distance(p)<=1) {
                points.subList(i+1,points.size()).clear();return true;
            }
        }
        if(full()) return false;
        points.add(p);return true;
    }
    public void restore(List<Point> saved) {
        points.clear(); points.addAll(saved.subList(0,Math.min(LIMIT,saved.size())));
    }
}
