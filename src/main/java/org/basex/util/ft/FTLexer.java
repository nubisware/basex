package org.basex.util.ft;

import java.util.EnumSet;
import org.basex.data.XMLSerializer;
import org.basex.index.IndexToken;
import org.basex.query.ft.FTFilter;
import org.basex.util.Token;

/**
 * Performs full-text lexing on token. Calls tokenizers, stemmers matching to
 * full-text options to achieve this.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-10, ISC License
 * @author Jens Erat
 */
public final class FTLexer extends FTIterator implements IndexToken {
  /** Tokenizer. */
  private final Tokenizer tok;
  /** Full-text options. */
  private final FTOpt fto;
  /** Text to be tokenized. */
  private byte[] text = Token.EMPTY;

  /** Iterator over result tokens. */
  private FTIterator iter;
  /** The last parsed span. */
  private Span curr;
  /** The last parsed text. */
  private byte[] ctxt;

  /**
   * Constructor. Called by the {@link XMLSerializer}, {@link FTFilter},
   * and the map visualizations.
   */
  public FTLexer() {
    this(null, false);
  }

  /**
   * Constructor. Special character,
   * @param sc include special characters
   */
  public FTLexer(final boolean sc) {
    this(null, sc);
  }

  /**
   * Default constructor.
   * @param f full-text options
   */
  public FTLexer(final FTOpt f) {
    this(f, false);
  }

  /**
   * Constructor. Called by the map visualization.
   * @param opt full-text options
   * @param sc include special characters
   */
  private FTLexer(final FTOpt opt, final boolean sc) {
    fto = opt;

    // check if language option is provided:
    Language lang = opt != null ? opt.ln : null;
    if(lang == null) lang = Language.DEFAULT;

    // use default tokenizer if specific tokenizer is not available.
    Tokenizer tk = Tokenizer.IMPL.getFirst();
    for(final Tokenizer t : Tokenizer.IMPL) {
      if(t.supports(lang)) {
        tk = t;
        break;
      }
    }
    tok = tk.get(opt, sc);
    iter = tok.iter();

    // check if stemming is required:
    if(opt != null && opt.is(FTFlag.ST)) {
      if(opt.sd == null) {
        // use default stemmer if specific stemmer is not available.
        Stemmer st = Stemmer.IMPL.getFirst();
        for(final Stemmer stem : Stemmer.IMPL) {
          if(stem.supports(lang)) {
            st = stem;
            break;
          }
        }
        iter = st.get(lang).iter(iter);
      } else {
        iter = new DictStemmer(opt.sd).iter(iter);
      }
    }
  }

  /**
   * Returns total number of tokens.
   * @return token count
   */
  public int count() {
    init();
    int c = 0;
    while(hasNext()) {
      nextToken();
      c++;
    }
    return c;
  }

  /**
   * Initializes the iterator.
   */
  public void init() {
    init(text);
  }
  
  @Override
  public FTLexer init(final byte[] txt) {
    text = txt;
    iter.init(txt);
    return this;
  }

  @Override
  public boolean hasNext() {
    return iter.hasNext();
  }

  @Override
  public Span next() {
    curr = iter.next();
    return curr;
  }

  @Override
  public byte[] nextToken() {
    ctxt = iter.nextToken();
    return ctxt;
  }

  @Override
  public IndexType type() {
    return IndexType.FULLTEXT;
  }

  /**
   * Returns the original token. Inherited from {@link IndexToken};
   * use {@link #next} or {@link #nextToken} if not using this interface.
   * @return current token.
   */
  @Override
  public byte[] get() {
    return ctxt != null ? ctxt : curr.text;
  }

  /**
   * Is paragraph? Does not have to be implemented by all tokenizers.
   * Returns false if not implemented.
   * @return boolean
   */
  public boolean paragraph() {
    return tok.paragraph();
  }

  /**
   * Calculates a position value, dependent on the specified unit. Does not have
   * to be implemented by all tokenizers. Returns 0 if not implemented.
   * @param w word position
   * @param u unit
   * @return new position
   */
  public int pos(final int w, final FTUnit u) {
    return tok.pos(w, u);
  }

  /**
   * Returns the full-text options. Can be {@code null}.
   * @return full-text options
   */
  public FTOpt ftOpt() {
    return fto;
  }

  /**
   * Returns the text to be processed.
   * @return text
   */
  public byte[] text() {
    return text;
  }

  /**
   * Gets full-text info for the specified token; needed for visualizations.
   * Does not have to be implemented by all tokenizers.
   * <ul>
   * <li/>int[0]: length of each token
   * <li/>int[1]: sentence info, length of each sentence
   * <li/>int[2]: paragraph info, length of each parap.get(Prop.FTLANGUAGE))
   * graph
   * <li/>int[3]: each token as int[]
   * <li/>int[4]: punctuation marks of each sentence
   * </ul>
   * @return int arrays or empty array if not implemented
   */
  public int[][] info() {
    return tok.info();
  }

  /**
   * Lists all languages for which tokenizers and stemmers are available.
   * @return supported languages
   */
  public static EnumSet<Language> languages() {
    final EnumSet<Language> ln = EnumSet.noneOf(Language.class);
    for(final Tokenizer t : Tokenizer.IMPL) ln.addAll(t.languages());
    final EnumSet<Language> sln = EnumSet.noneOf(Language.class);
    for(final Stemmer stem : Stemmer.IMPL) sln.addAll(stem.languages());
    // intersection of languages tokenizers and stemmers support
    ln.retainAll(sln);
    return ln;
  }
}
