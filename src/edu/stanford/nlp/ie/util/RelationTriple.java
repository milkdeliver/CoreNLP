package edu.stanford.nlp.ie.util;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.PriorityQueue;

import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Function;

/**
 * A (subject, relation, object) triple; e.g., as used in the KBP challenges or in OpenIE systems.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("UnusedDeclaration")
public class RelationTriple implements Comparable<RelationTriple> {
  /** The subject (first argument) of this triple */
  public final List<CoreLabel> subject;
  /** The relation (second argument) of this triple */
  public final List<CoreLabel> relation;
  /** The object (third argument) of this triple */
  public final List<CoreLabel> object;
  /** An optional score (confidence) for this triple */
  public final double confidence;

  /**
   * Create a new triple with known values for the subject, relation, and object.
   * For example, "(cats, play with, yarn)"
   * @param subject The subject of this triple; e.g., "cats".
   * @param relation The relation of this triple; e.g., "play with".
   * @param object The object of this triple; e.g., "yarn".
   */
  public RelationTriple(List<CoreLabel> subject, List<CoreLabel> relation, List<CoreLabel> object,
                        double confidence) {
    this.subject = subject;
    this.relation = relation;
    this.object = object;
    this.confidence = confidence;
  }

  /**
   * @see edu.stanford.nlp.ie.util.RelationTriple#RelationTriple(java.util.List, java.util.List, java.util.List, double)
   */
  public RelationTriple(List<CoreLabel> subject, List<CoreLabel> relation, List<CoreLabel> object) {
    this(subject, relation, object, 1.0);
  }

  /**
   * Returns all the tokens in the extraction, in the order subject then relation then object.
   */
  public List<CoreLabel> allTokens() {
    List<CoreLabel> allTokens = new ArrayList<>();
    allTokens.addAll(subject);
    allTokens.addAll(relation);
    allTokens.addAll(object);
    return allTokens;
  }

  /** The subject of this relation triple, as a String */
  public String subjectGloss() {
    return StringUtils.join(subject.stream().map(CoreLabel::word), " ");
  }

  /** The object of this relation triple, as a String */
  public String objectGloss() {
    return StringUtils.join(object.stream().map(CoreLabel::word), " ");
  }

  /** The relation of this relation triple, as a String */
  public String relationGloss() {
    return StringUtils.join(relation.stream().map(CoreLabel::word), " ");
  }

  /** A textual representation of the confidence. */
  public String confidenceGloss() {
    return new DecimalFormat("0.000").format(confidence);
  }

  /** An optional method, returning the dependency tree this triple was extracted from */
  public Optional<SemanticGraph> asDependencyTree() {
    return Optional.empty();
  }

  /** Return the given relation triple as a flat sentence */
  public List<CoreLabel> asSentence() {
    PriorityQueue<CoreLabel> orderedSentence = new FixedPrioritiesPriorityQueue<>();
    double defaultIndex = 0.0;
    for (CoreLabel token : subject) {
      orderedSentence.add(token, token.index() >= 0 ? (double) -token.index() : -defaultIndex);
      defaultIndex += 1.0;
    }
    for (CoreLabel token : relation) {
      orderedSentence.add(token, token.index() >= 0 ? (double) -token.index() : -defaultIndex);
      defaultIndex += 1.0;
    }
    for (CoreLabel token : object) {
      orderedSentence.add(token, token.index() >= 0 ? (double) -token.index() : -defaultIndex);
      defaultIndex += 1.0;
    }
    return orderedSentence.toSortedList();
  }


  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RelationTriple)) return false;
    RelationTriple that = (RelationTriple) o;
    return object.equals(that.object) && relation.equals(that.relation) && subject.equals(that.subject);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    int result = subject.hashCode();
    result = 31 * result + relation.hashCode();
    result = 31 * result + object.hashCode();
    return result;
  }

  /** Print a human-readable description of this relation triple, as a tab-separated line */
  @Override
  public String toString() {
    return "" + this.confidence + "\t" + subjectGloss() + "\t" + relationGloss() + "\t" + objectGloss();
  }

  @Override
  public int compareTo(RelationTriple o) {
    if (this.confidence < o.confidence) {
      return -1;
    } else if (this.confidence > o.confidence) {
      return 1;
    } else {
      return 0;
    }
  }

  /**
   * A {@link edu.stanford.nlp.ie.util.RelationTriple}, but with the tree saved as well.
   */
  public static class WithTree extends RelationTriple {
    public final SemanticGraph sourceTree;

    /**
     * Create a new triple with known values for the subject, relation, and object.
     * For example, "(cats, play with, yarn)"
     *
     * @param subject  The subject of this triple; e.g., "cats".
     * @param relation The relation of this triple; e.g., "play with".
     * @param object   The object of this triple; e.g., "yarn".
     * @param tree     The tree this extraction was created from; we create a deep copy of the tree.
     */
    public WithTree(List<CoreLabel> subject, List<CoreLabel> relation, List<CoreLabel> object, SemanticGraph tree,
                    double confidence) {
      super(subject, relation, object, confidence);
      this.sourceTree = new SemanticGraph(tree);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<SemanticGraph> asDependencyTree() {
      return Optional.of(sourceTree);
    }
  }

  /** A list of patterns to match relation extractions against */
  private static final List<SemgrexPattern> PATTERNS = Collections.unmodifiableList(new ArrayList<SemgrexPattern>() {{
    // { blue cats play [quietly] with yarn }
    add(SemgrexPattern.compile("{$}=verb ?>/cop|auxpass/ {}=be >/.subj(pass)?/ {}=subject >/prepc?/ ({}=prep >/pobj/ ( {}=object ?>/appos/ {}=appos ) )"));
    add(SemgrexPattern.compile("{$}=verb ?>/cop|auxpass/ {}=be >/.subj(pass)?/ {}=subject >/prepc?_.*/=prepEdge ( {}=object ?>/appos/ {} = appos )"));
    // { fish like to swim }
    add(SemgrexPattern.compile("{$}=verb >/.subj(pass)?/ {}=subject >/xcomp/ ( {}=object ?>/appos/ {}=appos )"));
    // { cats have tails }
    add(SemgrexPattern.compile("{$}=verb ?>/auxpass/ {}=be >/.subj(pass)?/ {}=subject >/[di]obj|xcomp/ ( {}=object ?>/appos/ {}=appos )"));
    // { cats are cute  }
    add(SemgrexPattern.compile("{$}=object >/.subj(pass)?/ {}=subject >/cop/ {}=verb"));
    // { Unicredit 's Bank Austria Creditanstalt }
    add(SemgrexPattern.compile("{$}=object >/poss/=verb {}=subject"));
    // { Obama in Tucson }
    add(SemgrexPattern.compile("[ !{ner:/O/} & {tag:/NNP/}=subject ] >/prep_.*/=verb {}=object"));
  }});

  /** A set of valid arcs denoting an entity we are interested in */
  private static final Set<String> VALID_ENTITY_ARCS = Collections.unmodifiableSet(new HashSet<String>(){{
    add("amod"); add("nn"); add("aux"); add("num"); add("prep"); add("nsubj"); add("prep_*");
  }});

  /** A set of valid arcs denoting an entity we are interested in */
  private static final Set<String> VALID_ADVERB_ARCS = Collections.unmodifiableSet(new HashSet<String>(){{
    add("amod"); add("advmod"); add("conj"); add("cc"); add("conj_and"); add("conj_or"); add("auxpass");
  }});

  private static CoreLabel mockNode(CoreLabel toCopy, int offset, String word, String POS) {
    CoreLabel mock = new CoreLabel(toCopy);
    mock.setWord(word);
    mock.setLemma(word);
    mock.setValue(word);
    mock.setNER("O");
    mock.setTag(POS);
    mock.setIndex(toCopy.index() + offset);
    return mock;
  }

  /**
   * @see RelationTriple#getValidEntityChunk(edu.stanford.nlp.semgraph.SemanticGraph, edu.stanford.nlp.ling.IndexedWord, Optional)
   * @see RelationTriple#getValidAdverbChunk(edu.stanford.nlp.semgraph.SemanticGraph, edu.stanford.nlp.ling.IndexedWord, Optional)
   */
  private static Optional<List<CoreLabel>> getValidChunk(SemanticGraph parse, IndexedWord originalRoot,
                                                         Set<String> validArcs, Optional<String> ignoredArc) {
    PriorityQueue<CoreLabel> chunk = new FixedPrioritiesPriorityQueue<>();
    Queue<IndexedWord> fringe = new LinkedList<>();
    IndexedWord root = originalRoot;
    fringe.add(root);

    boolean isCopula = false;
    for (SemanticGraphEdge edge : parse.outgoingEdgeIterable(originalRoot)) {
      if (edge.getRelation().getShortName().equals("cop")) {
        isCopula = true;
      }
    }

    while (!fringe.isEmpty()) {
      root = fringe.poll();
      chunk.add(root.backingLabel(), -root.index());
      for (SemanticGraphEdge edge : parse.incomingEdgeIterable(root)) {
        if (edge.getDependent() != originalRoot) {
          if (edge.getRelation().toString().startsWith("prep_") || edge.getRelation().toString().startsWith("prepc_")) {
            chunk.add(mockNode(edge.getGovernor().backingLabel(), 1, edge.getRelation().toString().substring(edge.getRelation().toString().indexOf("_") + 1), "PP"), -(((double) edge.getGovernor().index()) + 0.9));
          }
          if (edge.getRelation().getShortName().equals("conj")) {
            chunk.add(mockNode(root.backingLabel(), -1, edge.getRelation().getSpecific(), "CC"), -(((double) root.index()) - 0.9));
          }
        }
      }
      for (SemanticGraphEdge edge : parse.getOutEdgesSorted(root)) {
        String shortName = edge.getRelation().getShortName();
        String name = edge.getRelation().toString();
        //noinspection StatementWithEmptyBody
        if (isCopula && (shortName.equals("cop") || shortName.contains("subj"))) {
          // noop; ignore nsubj, cop for extractions with copula
        } else if (ignoredArc.isPresent() && ignoredArc.get().equals(name)) {
          // noop; ignore explicitly requested noop arc.
        } else if (!validArcs.contains(edge.getRelation().getShortName().replaceAll("_.*","_*"))) {
          return Optional.empty();
        } else {
          fringe.add(edge.getDependent());
        }
      }
    }

    return Optional.of(chunk.toSortedList());
  }

  /**
   * Get the yield of a given subtree, if it is a valid entity.
   * Otherwise, return {@link java.util.Optional#empty()}}.
   * @param parse The parse tree we are extracting a subtree from.
   * @param root The root of the subtree.
   * @param noopArc An optional edge type to ignore in gathering the chunk.
   * @return If this subtree is a valid entity, we return its yield. Otherwise, we return empty.
   */
  private static Optional<List<CoreLabel>> getValidEntityChunk(SemanticGraph parse, IndexedWord root, Optional<String> noopArc) {
    return getValidChunk(parse, root, VALID_ENTITY_ARCS, noopArc);
  }

  /**
   * Get the yield of a given subtree, if it is a adverb chunk.
   * Otherwise, return {@link java.util.Optional#empty()}}.
   * @param parse The parse tree we are extracting a subtree from.
   * @param root The root of the subtree.
   * @param noopArc An optional edge type to ignore in gathering the chunk.
   * @return If this subtree is a valid adverb, we return its yield. Otherwise, we return empty.
   */
  private static Optional<List<CoreLabel>> getValidAdverbChunk(SemanticGraph parse, IndexedWord root, Optional<String> noopArc) {
    return getValidChunk(parse, root, VALID_ADVERB_ARCS, noopArc);
  }

  /**
   * <p>
   * Try to segment this sentence as a relation triple.
   * This sentence must already match one of a few strict patterns for a valid OpenIE extraction.
   * If it does not, then no relation triple is created.
   * That is, this is <b>not</b> a relation extractor; it is just a utility to segment what is already a
   * (subject, relation, object) triple into these three parts.
   * </p>
   *
   * @param parse The sentence to process, as a dependency tree.
   * @param confidence An optional confidence to pass on to the relation triple.
   * @return A relation triple, if this sentence matches one of the patterns of a valid relation triple.
   */
  public static Optional<RelationTriple> segment(SemanticGraph parse, Optional<Double> confidence) {
    PATTERN_LOOP: for (SemgrexPattern pattern : PATTERNS) {  // For every candidate pattern...
      SemgrexMatcher m = pattern.matcher(parse);
      if (m.matches()) {  // ... see if it matches the sentence
        // Object
        IndexedWord object = m.getNode("appos");
        if (object == null) {
          object = m.getNode("object");
        }
        assert object != null;
        // Verb
        PriorityQueue<CoreLabel> verbChunk = new FixedPrioritiesPriorityQueue<>();
        IndexedWord verb = m.getNode("verb");
        List<IndexedWord> adverbs = new ArrayList<>();
        Optional<String> subjNoopArc = Optional.empty();
        Optional<String> objNoopArc = Optional.empty();
        if (verb != null) {
          for (SemanticGraphEdge edge : parse.outgoingEdgeIterable(verb)) {
            if ("advmod".equals(edge.getRelation().toString()) || "amod".equals(edge.getRelation().toString())) {
              String tag = edge.getDependent().backingLabel().tag();
              if (tag == null ||
                  (!tag.startsWith("W") && !edge.getDependent().backingLabel().word().equalsIgnoreCase("then"))) {  // prohibit advmods like "where"
                adverbs.add(edge.getDependent());
              }
            }
          }
        } else {
          String verbName = m.getRelnString("verb");
          if ("poss".equals(verbName)) {
            IndexedWord subject = m.getNode("subject");
            verb = new IndexedWord(mockNode(subject.backingLabel(), 1, "'s", "POS"));
            objNoopArc = Optional.of("poss");
          } else if (verbName != null && verbName.startsWith("prep_")) {
            verbName = verbName.substring("prep_".length());
            IndexedWord subject = m.getNode("subject");
            verb = new IndexedWord(mockNode(subject.backingLabel(), 1, verbName, "IN"));
            subjNoopArc = Optional.of("prep_" + verbName);
          } else {
            throw new IllegalStateException("Pattern matched without a verb!");
          }
        }
        verbChunk.add(verb.backingLabel(), -verb.index());
        int numKnownDependents = 2;  // subject and object, at minimum
        // Prepositions
        IndexedWord prep = m.getNode("prep");
        String prepEdge = m.getRelnString("prepEdge");
        if (prep != null) { verbChunk.add(prep.backingLabel(), -prep.index()); numKnownDependents += 1; }
        // Auxilliary "be"
        IndexedWord be = m.getNode("be");
        if (be != null) { verbChunk.add(be.backingLabel(), -be.index()); numKnownDependents += 1; }
        // (adverbs have to be well-formed)
        if (!adverbs.isEmpty()) {
          Set<CoreLabel> adverbialModifiers = new HashSet<>();
          for (IndexedWord adv : adverbs) {
            Optional<List<CoreLabel>> adverbChunk = getValidAdverbChunk(parse, adv, Optional.empty());
            if (adverbChunk.isPresent()) {
              for (CoreLabel token : adverbChunk.get()) {
                adverbialModifiers.add(token);
              }
            } else {
              continue PATTERN_LOOP;  // Invalid adverbial phrase
            }
            numKnownDependents += 1;
          }
          for (CoreLabel adverbToken : adverbialModifiers) {
            verbChunk.add(adverbToken, -adverbToken.index());
          }
        }
        // (add preposition edge)
        if (prepEdge != null) {
          verbChunk.add(mockNode(verb.backingLabel(), 1, prepEdge.substring(prepEdge.indexOf("_") + 1), "PP"), -(verb.index() + 10));
        }
        // (check for additional edges)
        if (parse.outDegree(verb) > numKnownDependents) {
          //noinspection UnnecessaryLabelOnContinueStatement
          continue PATTERN_LOOP;  // Too many outgoing edges; we didn't consume them all.
        }
        List<CoreLabel> relation = verbChunk.toSortedList();

        // Subject+Object
        Optional<List<CoreLabel>> subjectSpan = getValidEntityChunk(parse, m.getNode("subject"), subjNoopArc);
        Optional<List<CoreLabel>> objectSpan = getValidEntityChunk(parse, object, objNoopArc);
        // Create relation
        if (subjectSpan.isPresent() && objectSpan.isPresent()) {  // ... and has a valid subject+object
          // Success! Found a valid extraction.
          return Optional.of(new WithTree(subjectSpan.get(), relation, objectSpan.get(), parse, confidence.orElse(1.0)));
        }
      }
    }
    // Failed to match any pattern; return failure
    return Optional.empty();
  }


  /**
   * A {@link edu.stanford.nlp.ie.util.RelationTriple}, optimized for tasks such as KBP
   * where we care about named entities, and care about things like provenance and coref.
   */
  public static class AsKBEntry extends RelationTriple {
    public final String docid;
    public final int sentenceIndex;
    private final Optional<List<CoreLabel>> originalSubject;
    private final Optional<List<CoreLabel>> originalObject;

    /**
     * {@inheritDoc}
     */
    public AsKBEntry(List<CoreLabel> subject, List<CoreLabel> relation, List<CoreLabel> object, double confidence,
                     String docid, int sentenceIndex,
                     Optional<List<CoreLabel>> originalSubject, Optional<List<CoreLabel>> originalObject) {
      super(subject, relation, object, confidence);
      this.docid = docid;
      this.sentenceIndex = sentenceIndex;
      this.originalSubject = originalSubject;
      this.originalObject = originalObject;
    }

    /** @see edu.stanford.nlp.ie.util.RelationTriple.AsKBEntry#AsKBEntry(java.util.List, java.util.List, java.util.List, double, String, int, Optional, Optional) */
    public AsKBEntry(RelationTriple source, String docid, int sentenceIndex) {
      this(source.subject, source.relation, source.object, source.confidence, docid, sentenceIndex,
          Optional.empty(), Optional.empty());
    }

    /** The subject of this relation triple, as a String */
    @Override
    public String subjectGloss() {
      if (subject.get(0).lemma() != null) {
        return StringUtils.join(subject.stream().map(CoreLabel::lemma), " ");
      } else {
        return super.relationGloss();
      }
    }

    /** The object of this relation triple, as a String */
    @Override
    public String objectGloss() {
      if (object.get(0).lemma() != null) {
        return StringUtils.join(object.stream().map(CoreLabel::lemma), " ");
      } else {
        return super.objectGloss();
      }
    }

    /** The relation of this relation triple, as a String */
    @Override
    public String relationGloss() {
      if (relation.get(0).lemma() != null) {
        return StringUtils.join(relation.stream().map(CoreLabel::lemma), " ");
      } else {
        return super.relationGloss();
      }
    }

    private Pair<Integer, Integer> getSpan(List<CoreLabel> tokens, Function<CoreLabel, Integer> toMin, Function<CoreLabel, Integer> toMax) {
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;
      for (CoreLabel token : tokens) {
        min = Math.min(min, toMin.apply(token));
        max = Math.max(max, toMax.apply(token) + 1);
      }
      return Pair.makePair(min, max);
    }

    public Pair<Integer, Integer> subjectTokenSpan() {
      return getSpan(subject, CoreLabel::index, CoreLabel::index);
    }

    public Pair<Integer, Integer> objectTokenSpan() {
      return getSpan(subject, CoreLabel::index, CoreLabel::index);
    }

    public Optional<Pair<Integer, Integer>> originalSubjectTokenSpan() {
      return originalSubject.map(x -> getSpan(x, CoreLabel::index, CoreLabel::index));
    }

    public Optional<Pair<Integer, Integer>> originalObjectTokenSpan() {
      return originalObject.map(x -> getSpan(x, CoreLabel::index, CoreLabel::index));
    }

    public Pair<Integer, Integer> extractionTokenSpan() {
      return getSpan(allTokens(), CoreLabel::index, CoreLabel::index);
    }

    public Pair<Integer, Integer> subjectCharacterSpan() {
      return getSpan(subject, CoreLabel::beginPosition, CoreLabel::endPosition);
    }

    public Pair<Integer, Integer> objectCharacterSpan() {
      return getSpan(subject, CoreLabel::beginPosition, CoreLabel::endPosition);
    }

    public Optional<Pair<Integer, Integer>> originalSubjectCharacterSpan() {
      return originalSubject.map(x -> getSpan(x, CoreLabel::beginPosition, CoreLabel::endPosition));
    }

    public Optional<Pair<Integer, Integer>> originalObjectCharacterSpan() {
      return originalObject.map(x -> getSpan(x, CoreLabel::beginPosition, CoreLabel::endPosition));
    }

    public Pair<Integer, Integer> extractionCharacterSpan() {
      return getSpan(allTokens(), CoreLabel::beginPosition, CoreLabel::endPosition);
    }

    private static String gloss(Pair<Integer,Integer> pair) {
      return "" + pair.first + "\t" + pair.second;
    }

    private static String gloss(Optional<Pair<Integer,Integer>> pair) {
      if (pair.isPresent()) {
        return "" + pair.get().first + "\t" + pair.get().second;
      } else {
        return "0\t0";
      }
    }

    @Override
    public String toString() {
      return new StringBuilder()
          .append(confidenceGloss()).append("\t")
          .append(subjectGloss().replace('\t', ' ')).append("\t")
          .append(relationGloss().replace('\t', ' ')).append("\t")
          .append(objectGloss().replace('\t', ' ')).append("\t")
          .append(docid.replace('\t', ' ')).append("\t")
          .append(sentenceIndex).append("\t")
          .append(gloss(subjectTokenSpan())).append("\t")
          .append(gloss(objectTokenSpan())).append("\t")
          .append(gloss(extractionTokenSpan())).append("\t")
          .append(gloss(subjectCharacterSpan())).append("\t")
          .append(gloss(objectCharacterSpan())).append("\t")
          .append(gloss(extractionCharacterSpan())).append("\t")
          .append(gloss(originalSubjectTokenSpan())).append("\t")
          .append(gloss(originalObjectTokenSpan())).append("\t")
          .append(gloss(originalSubjectTokenSpan())).append("\t")
          .append(gloss(originalObjectTokenSpan())).append("\t")
          .toString();
    }

  }

  public static Optional<RelationTriple> optimizeForKB(RelationTriple input, CoreMap sentence, Map<CoreLabel, List<CoreLabel>> canonicalMentions) {
    // Get some metadata
    String docid = sentence.get(CoreAnnotations.DocIDAnnotation.class);
    if (docid == null) { docid = "no_doc_id"; }
    Integer sentenceIndex = sentence.get(CoreAnnotations.SentenceIndexAnnotation.class);
    if (sentenceIndex == null) { sentenceIndex = -1; }

    // Pass 1: resolve Coref
    List<CoreLabel> subject = null;
    for (int i = input.subject.size() - 1; i >= 0; --i) {
      if ( (subject = canonicalMentions.get(input.subject.get(i))) != null) {
        break;
      }
    }
    if (subject == null) {
      subject = input.subject;
    }
    List<CoreLabel> object = null;
    for (int i = input.object.size() - 1; i >= 0; --i) {
      if ( (object = canonicalMentions.get(input.object.get(i))) != null) {
        break;
      }
    }
    if (object == null) {
      object = input.object;
    }

    // Pass 2: Filter prepositions
    for (CoreLabel subjToken : subject) {
      if ("PRP".equals(subjToken.tag())) {
        return Optional.empty();
      }
    }
    for (CoreLabel objToken : object) {
      if ("PRP".equals(objToken.tag())) {
        return Optional.empty();
      }
    }

    // Pass 3: Filter invalid subjects
    boolean hasNER = false;
    for (CoreLabel subjToken : subject) {
      if (!"O".equals(subjToken.ner())) {
        hasNER = true;
      }
    }
    if (!hasNER) {
      return Optional.empty();
    }

    // Return
    return Optional.of(new AsKBEntry(subject, input.relation, object, input.confidence, docid, sentenceIndex,
        subject == input.subject ? Optional.empty() : Optional.of(input.subject),
        object == input.object ? Optional.empty() : Optional.of(input.object) ));
  }
}
