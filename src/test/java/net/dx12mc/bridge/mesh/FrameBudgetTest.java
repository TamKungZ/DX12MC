package net.dx12mc.bridge.mesh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameBudgetTest {
  @Test
  void allowsExactlyTheLimitPerFrame() {
    FrameBudget budget = new FrameBudget(2);
    assertTrue(budget.tryTake());
    assertFalse(budget.exhausted());
    assertTrue(budget.tryTake());
    assertTrue(budget.exhausted());
    assertFalse(budget.tryTake());
  }

  @Test
  void resetRestoresTheBudget() {
    FrameBudget budget = new FrameBudget(1);
    budget.tryTake();
    budget.reset();
    assertTrue(budget.tryTake());
  }

  @Test
  void rejectsNonPositiveLimits() {
    assertThrows(IllegalArgumentException.class, () -> new FrameBudget(0));
  }
}
