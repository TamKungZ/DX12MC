package net.dx12mc.bridge.mesh;

final class FrameBudget {
  private final int limit;
  private int used;

  FrameBudget(int limit) {
    if (limit < 1) throw new IllegalArgumentException("limit must be positive: " + limit);
    this.limit = limit;
  }

  boolean tryTake() {
    if (used >= limit) return false;
    used++;
    return true;
  }

  boolean exhausted() {
    return used >= limit;
  }

  void reset() {
    used = 0;
  }
}
