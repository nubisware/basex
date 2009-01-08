package org.basex.core.proc;

import static org.basex.Text.*;
import java.io.IOException;
import org.basex.BaseX;
import org.basex.build.mediovis.MAB2;
import org.basex.core.Prop;
import org.basex.data.Data;
import org.basex.data.Nodes;
import org.basex.data.XMLSerializer;
import org.basex.io.NullOutput;
import org.basex.io.PrintOutput;
import org.basex.query.QueryException;
import org.basex.query.xpath.item.Nod;
import org.basex.query.xquery.XQueryProcessor;
import org.basex.util.Array;
import org.basex.util.IntList;
import org.basex.util.Performance;
import org.basex.util.Set;
import org.basex.util.Token;

/**
 * Evaluates the 'xpathmv' command.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
public final class XPathMV extends AQuery {
  /** Index reference. */
  private IDSet ids;
  /** Maximum hits. */
  private int hits;
  /** Maximum subhits. */
  private int sub;
  /** Approximate hits. */
  //private boolean approx;
  /** Maximum hits. */
  private int maxhits;
  
  /**
   * Constructor.
   * @param h number of hits
   * @param s number of sub hits
   * @param q query
   */
  public XPathMV(final String h, final String s, final String q) {
    super(DATAREF | PRINTING, h, s, q);
  }
  
  @Override
  protected boolean exec() {
    // cache verbose flag
    hits = Token.toInt(args[0]);
    sub = Token.toInt(args[1]);
    final String query = args[2] + "[position() <= " + (hits * sub) + "]";

    long pars = 0;
    long comp = 0;
    long eval = 0;
    long fini = 0;

    XQueryProcessor qu = null;
    try {
      for(int i = 0; i < Prop.runs; i++) {
        qu = new XQueryProcessor(query);
        final Nodes nodes = context.current();
        progress(qu);

        qu.parse();
        pars += per.getTime();
        qu.compile(nodes);
        comp += per.getTime();
        result = qu.query(nodes);
        eval += per.getTime();

        if(!(result instanceof Nod))
          throw new QueryException(QUERYNODESERR);
        
        final Nod ns = (Nod) result;
        result = new Nodes(ns.nodes, ns.data);

        /* no results found.. try approximate search
        boolean approx = result.size == 0;
        if(approx) {
          arg = arg.replaceAll(" ftcontains ", " ~> ");
          result = qu.query(arg, context.current);
        }*/

        // get index references for mediovis attributes
        final Data data = context.data();
        final int medid = data.tagID(MAB2.MEDIUM);
        final int mvid  = data.attNameID(MAB2.MV_ID);

        // gather IDs for titles on top
        final Nodes res = (Nodes) result;
        final int[] pres = res.nodes;
        final int size = res.size;
        ids = new IDSet();
        
        for(int n = 0; n < size; n++) {
          // super-ordinate title found?
          int pre = data.parent(pres[n], data.kind(pres[n]));
          final boolean s = data.tagID(pre) == medid;
          if(!s) pre = pres[n];

          // add ID(s) to index
          final byte[] id = data.attValue(mvid, pre);
          if(s) {
            ids.index(id, pre, pres[n]);
          } else {
            ids.index(id, pre);
          }
          if(ids.size() == hits) break;
        }
        maxhits = ids.size();
        fini += per.getTime();
      }
      if(Prop.info) {
        info(qu.info());
        info(QUERYPARSE + Performance.getTimer(pars, Prop.runs) + NL);
        info(QUERYCOMPILE + Performance.getTimer(comp, Prop.runs) + NL);
        info(QUERYEVALUATE + Performance.getTimer(eval, Prop.runs) + NL);
        info(QUERYFINISH + Performance.getTimer(fini, Prop.runs) + NL);
      }
    } catch(final Exception ex) {
      BaseX.debug(ex);
      return error(ex.getMessage());
    }
    return true;
  }

  @Override
  protected void out(final PrintOutput o) throws IOException {
    PrintOutput out = Prop.serialize ? o : new NullOutput();

    for(int i = 0; i < Prop.runs; i++) {
      if(i != 0) out = new NullOutput();
      show(new XMLSerializer(out, false, true));
    }
    if(Prop.info) outInfo(o, maxhits);
  }

  /**
   * Returns an MedioVis XML document.
   * @param ser serializer
   * @throws IOException exception
   */
  private void show(final XMLSerializer ser) throws IOException {
    // get index references for mediovis attributes
    final Data data = context.data();
    final int bibid = data.attNameID(MAB2.BIB_ID);
    final int maxid = data.attNameID(MAB2.MAX);
    final int medid = data.tagID(MAB2.MEDIUM);

    final Nodes res = (Nodes) result;
    final int size = res.size;

    // write root tag
    ser.openElement(MAB2.ROOT);
    ser.attribute(MAB2.HITS, Token.token(maxhits));
    ser.attribute(MAB2.MAX, Token.token(size));
    //ser.attribute(MAB2.APPROX, Token.token(approx));

    // loop through titles
    for(int i = 0; i < maxhits; i++) {
      final byte[] mv = ids.key(i + 1);
      final IntList medium = ids.get(mv);

      // write medium tag
      final int max = (int) attNum(data, maxid, medium.list[0]);
      ser.openElement(MAB2.MEDIUM, MAB2.MV_ID, mv,
          MAB2.BIB_ID, data.attValue(bibid, medium.list[0]),
          MAB2.MAX, Token.token(max));

      // print medium
      final int par = medium.list[0];
      int pp = par + data.attSize(par, data.kind(par));
      while(pp != data.meta.size) {
        if(data.tagID(pp) == medid) break;
        pp = ser.node(data, pp);
      }

      // print subordinate titles of query results first
      final int maxsubs = Math.min(medium.size, sub + 1);
      for(int s = 1; s < maxsubs; s++) ser.node(data, medium.list[s]);

      // print remaining subordinate titles
      final int leftsubs = Math.min(sub, max) - maxsubs + 1;
      int m = 1;
      for(int s = 0; s < leftsubs;) {
        if(m < maxsubs && medium.list[m] == pp) {
          m++;
          pp += data.size(pp, data.kind(pp));
        } else {
          pp = ser.node(data, pp);
          s++;
        }
      }
      // close medium
      ser.closeElement();
    }

    // close root tag
    if(maxhits != 0) ser.closeElement();
  }

  /**
   * Finds the specified attribute for the specified element and returns
   * the numeric attribute value.
   * @param data data reference
   * @param att attribute to be found
   * @param pre pre value
   * @return attribute value
   */
  private double attNum(final Data data, final int att, final int pre) {
    final int atts = pre + data.attSize(pre, data.kind(pre));
    int p = pre;
    while(++p != atts) if(data.attNameID(p) == att) return data.attNum(p);
    return 0;
  }

  /**
   * This is a hash map for MedioVis IDs.
   */
  static final class IDSet extends Set {
    /** Hash values. */
    private IntList[] values = new IntList[CAP];

    /**
     * Indexes the specified keys and values.
     * @param par parent key
     * @param pre pre value
     * @return index offset
     */
    int index(final byte[] par, final int pre) {
      int i = add(par);
      if(i < 0) {
        i = -i;
      } else {
        values[i] = new IntList();
        values[i].add(pre);
      }
      return i;
    }

    /**
     * Indexes the specified keys and values.
     * @param par parent key
     * @param pre pre value
     * @param sub sub id
     */
    void index(final byte[] par, final int pre, final int sub) {
      final int i = index(par, pre);
      values[i].add(sub);
    }

    /**
     * Returns the value for the specified key.
     * @param tok key to be found
     * @return value or null if nothing was found
     */
    IntList get(final byte[] tok) {
      return tok != null ? values[id(tok)] : null;
    }

    @Override
    protected void rehash() {
      super.rehash();
      values = Array.extend(values);
    }
  }
}
