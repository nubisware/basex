package org.basex.query.func.async;

import java.util.concurrent.*;

import org.basex.query.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.util.*;

/**
 * Forks a set of tasks, performing their computation in parallel followed by rejoining the results.
 *
 * @author James Wright
 */
public final class ForkJoinTask extends RecursiveTask<Value> {
  /** Functions to evaluate in parallel. */
  private final Value funcs;
  /** Query context. */
  private final QueryContext qc;
  /** Input info. */
  private final InputInfo ii;
  /** First function to evaluate. */
  private final int start;
  /** Last function to evaluate. */
  private final int end;

  /**
   * Constructor.
   * @param funcs functions to evaluate
   * @param qc query context
   * @param ii input info
   */
  public ForkJoinTask(final Value funcs, final QueryContext qc, final InputInfo ii) {
    this(funcs, qc, ii, 0, (int) funcs.size());
  }

  /**
   * Private constructor.
   * @param funcs functions to evaluate
   * @param qc query context
   * @param ii input info
   * @param start first function to evaluate
   * @param end last function to evaluate
   */
  private ForkJoinTask(final Value funcs, final QueryContext qc, final InputInfo ii,
      final int start, final int end) {
    this.funcs = funcs;
    this.qc = new QueryContext(qc);
    this.ii = ii;
    this.start = start;
    this.end = end;
  }

  @Override
  protected Value compute() {
    final ValueBuilder vb = new ValueBuilder();
    final int s = start, e = end, l = e - s;
    if(l == 1) {
      // perform the work
      try {
        vb.add(((FItem) funcs.itemAt(s)).invokeValue(qc, ii));
      } catch(final QueryException ex) {
        completeExceptionally(ex);
        cancel(true);
      }
    } else {
      // split the work and join the results in the correct order
      final int m = s + l / 2;
      final ForkJoinTask task2 = new ForkJoinTask(funcs, qc, ii, m, e);
      task2.fork();
      final ForkJoinTask task1  = new ForkJoinTask(funcs, qc, ii, s, m);
      vb.add(task1.invoke()).add(task2.join());
    }
    return vb.value();
  }
}
