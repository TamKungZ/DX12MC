package net.dx12mc.api;

/**
 * The result of loading one shader pack.
 *
 * @param id the namespace the pack manifest was found in
 * @param source the resource pack that supplied the manifest
 * @param message the reason for {@link State#FAILED} or {@link State#DENIED}, otherwise empty
 */
public record PackStatus(String id, String source, State state, String message) {
  public enum State {
    ACTIVE,
    FAILED,
    DENIED
  }
}
