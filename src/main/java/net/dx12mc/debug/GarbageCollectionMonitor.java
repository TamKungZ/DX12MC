package net.dx12mc.debug;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

final class GarbageCollectionMonitor {
  private final List<GarbageCollectorMXBean> collectors =
      ManagementFactory.getGarbageCollectorMXBeans();
  private long previousCollections;
  private long previousMillis;
  private boolean initialized;

  Sample sample() {
    long collections = totalCollections();
    long millis = totalMillis();
    long collectionDelta = initialized ? collections - previousCollections : 0L;
    long millisDelta = initialized ? millis - previousMillis : 0L;
    previousCollections = collections;
    previousMillis = millis;
    initialized = true;
    return new Sample(collections, millis, collectionDelta, millisDelta);
  }

  private long totalCollections() {
    long total = 0L;
    for (GarbageCollectorMXBean collector : collectors) {
      total += Math.max(0L, collector.getCollectionCount());
    }
    return total;
  }

  private long totalMillis() {
    long total = 0L;
    for (GarbageCollectorMXBean collector : collectors) {
      total += Math.max(0L, collector.getCollectionTime());
    }
    return total;
  }

  record Sample(long collections, long millis, long collectionDelta, long millisDelta) {}
}
