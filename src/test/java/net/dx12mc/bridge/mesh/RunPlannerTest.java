package net.dx12mc.bridge.mesh;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RunPlannerTest {
  private static final int F = RunPlanner.FIELDS;

  private static int[] plan(
      boolean[] visible, int[] indices, int[] pageOf, long drawCost, int[] pageIndexTotals) {
    int n = visible.length;
    int[] first = new int[n];
    int[] pageRunning = new int[8];
    for (int i = 0; i < n; i++) {
      first[i] = pageRunning[pageOf[i]];
      pageRunning[pageOf[i]] += indices[i];
    }
    int[] vertices = new int[n];
    for (int i = 0; i < n; i++) vertices[i] = indices[i] / 3 * 2;
    int[] pageVertices = new int[pageIndexTotals.length];
    for (int i = 0; i < n; i++) pageVertices[pageOf[i]] += vertices[i];
    int[] runs = new int[n * F];
    int count =
        RunPlanner.plan(
            n,
            pageOf,
            first,
            indices,
            vertices,
            visible,
            pageIndexTotals,
            pageVertices,
            drawCost,
            runs);
    int[] result = new int[count * F];
    System.arraycopy(runs, 0, result, 0, result.length);
    return result;
  }

  @Test
  void adjacentVisibleMembersShareOneRun() {
    int[] runs =
        plan(
            new boolean[] {true, true, false, false},
            new int[] {3000, 3000, 3000, 3000},
            new int[] {0, 0, 0, 0},
            0L,
            new int[] {12000});
    assertEquals(F, runs.length);
    assertEquals(0, runs[0]);
    assertEquals(0, runs[1]);
    assertEquals(6000, runs[2]);
  }

  @Test
  void hiddenMemberBetweenVisibleOnesSplitsTheRun() {
    int[] runs =
        plan(
            new boolean[] {true, false, true},
            new int[] {3000, 3000, 3000},
            new int[] {0, 0, 0},
            0L,
            new int[] {9000});
    assertEquals(2 * F, runs.length);
    assertEquals(0, runs[1]);
    assertEquals(3000, runs[2]);
    assertEquals(6000, runs[F + 1]);
    assertEquals(3000, runs[F + 2]);
  }

  @Test
  void runsNeverCrossPages() {
    int[] runs =
        plan(
            new boolean[] {true, true},
            new int[] {3000, 3000},
            new int[] {0, 1},
            0L,
            new int[] {3000, 3000});
    assertEquals(2 * F, runs.length);
    assertEquals(0, runs[0]);
    assertEquals(1, runs[F]);
    assertEquals(0, runs[F + 1]);
  }

  @Test
  void expensiveDrawsCollapseSparseRunsIntoWholePage() {
    int[] runs =
        plan(
            new boolean[] {true, false, true, false},
            new int[] {3000, 3000, 3000, 3000},
            new int[] {0, 0, 0, 0},
            12_000L,
            new int[] {12000});
    assertEquals(F, runs.length);
    assertEquals(0, runs[1]);
    assertEquals(12000, runs[2]);
    assertEquals(8000, runs[3]);
  }

  @Test
  void cheapDrawsKeepRunsWhenMostOfThePageIsHidden() {
    int[] runs =
        plan(
            new boolean[] {true, false, false, false, false, false, false, true},
            new int[] {30000, 30000, 30000, 30000, 30000, 30000, 30000, 30000},
            new int[] {0, 0, 0, 0, 0, 0, 0, 0},
            12_000L,
            new int[] {240000});
    assertEquals(2 * F, runs.length);
    assertEquals(30000, runs[2]);
  }

  @Test
  void nothingVisibleProducesNoRuns() {
    assertEquals(
        0,
        plan(new boolean[] {false, false}, new int[] {3, 3}, new int[] {0, 0}, 0L, new int[] {6})
            .length);
  }
}
