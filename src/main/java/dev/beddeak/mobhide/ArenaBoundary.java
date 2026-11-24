package dev.beddeak.mobhide;

/** 원형 경계 밖 위치를 같은 방향의 경계 안쪽 한 블록으로 돌립니다. */
record ArenaBoundary(double x, double y, double z, double radius) implements ArenaRegion {
    record Point(double x, double y, double z) {}

    public boolean contains(double px, double py, double pz) {
        double dx = px - x, dz = pz - z;
        return dx * dx + dz * dz <= radius * radius && Math.abs(py - y) <= 16;
    }

    public Point inset(double px, double py, double pz) {
        double dx = px - x, dz = pz - z, distance = Math.hypot(dx, dz);
        if (distance > radius) {
            double scale = (radius - 1) / distance;
            px = x + dx * scale;
            pz = z + dz * scale;
        }
        if (py > y + 16) py = y + 15;
        else if (py < y - 16) py = y - 15;
        return new Point(px, py, pz);
    }
}
