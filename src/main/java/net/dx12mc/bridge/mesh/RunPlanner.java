package net.dx12mc.bridge.mesh;

final class RunPlanner {
  static final int FIELDS = 4;

  private RunPlanner() {}

  static int plan(
      int memberCount,
      int[] memberPage,
      int[] memberFirstIndex,
      int[] memberIndexCount,
      int[] memberVertexCount,
      boolean[] memberVisible,
      int[] pageIndexCount,
      int[] pageVertexCount,
      long drawCostIndices,
      int[] runs) {
    int count = 0;
    int previous = -1;
    for (int member = 0; member < memberCount; member++) {
      if (!memberVisible[member]) {
        previous = -1;
        continue;
      }
      if (previous < 0
          || memberPage[previous] != memberPage[member]
          || memberFirstIndex[previous] + memberIndexCount[previous] != memberFirstIndex[member]) {
        int base = count * FIELDS;
        runs[base] = memberPage[member];
        runs[base + 1] = memberFirstIndex[member];
        runs[base + 2] = 0;
        runs[base + 3] = 0;
        count++;
      }
      int base = (count - 1) * FIELDS;
      runs[base + 2] += memberIndexCount[member];
      runs[base + 3] += memberVertexCount[member];
      previous = member;
    }
    return collapseToWholePages(count, pageIndexCount, pageVertexCount, drawCostIndices, runs);
  }

  private static int collapseToWholePages(
      int count, int[] pageIndexCount, int[] pageVertexCount, long drawCostIndices, int[] runs) {
    int written = 0;
    int start = 0;
    while (start < count) {
      int page = runs[start * FIELDS];
      int end = start;
      long visibleIndices = 0L;
      while (end < count && runs[end * FIELDS] == page) {
        visibleIndices += runs[end * FIELDS + 2];
        end++;
      }
      long runCost = (long) (end - start) * drawCostIndices + visibleIndices;
      long wholeCost = drawCostIndices + pageIndexCount[page];
      if (wholeCost <= runCost) {
        int base = written * FIELDS;
        runs[base] = page;
        runs[base + 1] = 0;
        runs[base + 2] = pageIndexCount[page];
        runs[base + 3] = pageVertexCount[page];
        written++;
      } else {
        for (int run = start; run < end; run++) {
          if (written != run) {
            System.arraycopy(runs, run * FIELDS, runs, written * FIELDS, FIELDS);
          }
          written++;
        }
      }
      start = end;
    }
    return written;
  }
}
