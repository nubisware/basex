package org.basex.query.func.request;

import org.basex.query.*;
import org.basex.query.iter.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.seq.*;
import org.basex.util.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-19, BSD License
 * @author Christian Gruen
 */
public final class RequestHeader extends RequestFn {
  @Override
  public Iter iter(final QueryContext qc) throws QueryException {
    return value(qc).iter();
  }

  @Override
  public Value value(final QueryContext qc) throws QueryException {
    final String name = Token.string(toToken(exprs[0], qc));
    final String value = request(qc).getHeader(name);
    return value != null ? Str.get(value) : exprs.length == 1 ? Empty.SEQ : exprs[1].value(qc);
  }
}
