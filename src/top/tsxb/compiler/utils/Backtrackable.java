package top.tsxb.compiler.utils;

/**
 * The interface Backtrackable.
 *
 * @param <T> the type parameter
 */
public interface Backtrackable<T> {
  /**
   * Save t.
   *
   * @return the t
   */
  T save();

  /**
   * Restore.
   *
   * @param memento the memento
   */
  void restore(T memento);
}
