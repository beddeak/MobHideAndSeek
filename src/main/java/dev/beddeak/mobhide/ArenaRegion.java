package dev.beddeak.mobhide;

interface ArenaRegion {
    boolean contains(double x, double y, double z);
    ArenaBoundary.Point inset(double x, double y, double z);
}
