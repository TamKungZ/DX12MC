package net.dx12mc.bridge.target;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dx12mc.graphics.bgfx.Target;

public final class Targets implements AutoCloseable {
  private final Map<Integer, Target> live = new HashMap<>();
  private final List<Target> retired = new ArrayList<>();
  private int lastId;
  private Target last;

  public Target resolve(int colorTextureId, int width, int height, boolean depth) {
    if (colorTextureId <= 0 || width <= 0 || height <= 0) return null;
    Target target = colorTextureId == lastId && last != null ? last : live.get(colorTextureId);
    if (target != null) {
      if (target.valid()
          && target.width() == width
          && target.height() == height
          && target.hasDepth() == depth) {
        lastId = colorTextureId;
        last = target;
        return target;
      }
      release(colorTextureId);
    }
    target = Target.create(width, height, depth);
    live.put(colorTextureId, target);
    lastId = colorTextureId;
    last = target;
    return target;
  }

  public Target find(int colorTextureId) {
    Target target = live.get(colorTextureId);
    return target != null && target.valid() ? target : null;
  }

  public void release(int colorTextureId) {
    Target target = live.remove(colorTextureId);
    if (colorTextureId == lastId) {
      lastId = 0;
      last = null;
    }
    if (target != null) retired.add(target);
  }

  public int liveCount() {
    return live.size();
  }

  public int retiredCount() {
    return retired.size();
  }

  public void onFrameBoundary() {
    closeRetired();
  }

  @Override
  public void close() {
    RuntimeException failure = null;
    for (Target target : live.values()) failure = closeQuietly(target, failure);
    live.clear();
    lastId = 0;
    last = null;
    try {
      closeRetired();
    } catch (RuntimeException exception) {
      if (failure == null) failure = exception;
      else failure.addSuppressed(exception);
    }
    if (failure != null) throw failure;
  }

  private void closeRetired() {
    RuntimeException failure = null;
    for (Target target : retired) failure = closeQuietly(target, failure);
    retired.clear();
    if (failure != null) throw failure;
  }

  private static RuntimeException closeQuietly(Target target, RuntimeException failure) {
    try {
      target.close();
    } catch (RuntimeException exception) {
      if (failure == null) return exception;
      failure.addSuppressed(exception);
    }
    return failure;
  }
}
