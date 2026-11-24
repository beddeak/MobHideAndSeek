package dev.beddeak.mobhide;

/** 선택한 블록 전체를 포함하는 직육면체. max는 마지막 블록의 바깥 면입니다. */
record CuboidBoundary(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) implements ArenaRegion {
    CuboidBoundary {
        if ((long) maxX - minX < 3 || (long) maxY - minY < 3 || (long) maxZ - minZ < 3)
            throw new IllegalArgumentException("각 방향으로 최소 3블록을 선택하세요. 높이를 전부 쓰려면 /mh expandvert");
        if ((long) maxX - minX > 256 || (long) maxZ - minZ > 256 || (long) maxY - minY > 1024)
            throw new IllegalArgumentException("영역은 가로·세로 각각 256블록, 높이 1024블록까지 지원합니다.");
    }
    static CuboidBoundary between(int ax, int ay, int az, int bx, int by, int bz) {
        return new CuboidBoundary(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz),
                Math.addExact(Math.max(ax, bx), 1), Math.addExact(Math.max(ay, by), 1), Math.addExact(Math.max(az, bz), 1));
    }
    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
    }
    public ArenaBoundary.Point inset(double x, double y, double z) {
        return new ArenaBoundary.Point(insetAxis(x, minX, maxX), insetAxis(y, minY, maxY), insetAxis(z, minZ, maxZ));
    }
    private static double insetAxis(double value, int min, int max) {
        return value < min ? min + 1.0 : value >= max ? max - 1.0 : value;
    }
    String dimensions() { return (maxX - minX) + " × " + (maxY - minY) + " × " + (maxZ - minZ); }
}
