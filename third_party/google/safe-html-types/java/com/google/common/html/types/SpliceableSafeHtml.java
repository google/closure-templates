package com.google.common.html.types;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.errorprone.annotations.CheckReturnValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * A version of SafeHtml that allows insertion of other SafeHtml in named locations, similar to a
 * format string or template.
 *
 * <p>This works around the fact that SafeHtml is an immutable string, not supporting insertion or
 * string replacement without losing its safe-ness, by allowing the producer to pre-approve
 * locations that can accept other SafeHtml while preserving safe-ness.
 *
 * <p>Technically, the same can be accomplished by concatenating SafeHtmls, so although parallel to
 * SafeHtml, this is simply a helper class, *without* any need for access restrictions.
 *
 * <p>This is not marked with {@link com.google.errorprone.annotations.Immutable} because it doesn't
 * have an iOS target.
 */
@CheckReturnValue
public final class SpliceableSafeHtml {
  /** List of segments in the template. */
  private final List<Segment> segments;

  /** Serializes a {@link SpliceableSafeHtml} into its protocol message representation. */
  public SpliceableSafeHtmlProto toProto() {
    SpliceableSafeHtmlProto.Builder builder = SpliceableSafeHtmlProto.newBuilder();
    for (Segment segment : this.getSegments()) {
      SpliceableSafeHtmlProto.Segment.Builder segmentBuilder =
          SpliceableSafeHtmlProto.Segment.newBuilder();
      switch (segment.getType()) {
        case SAFE_HTML:
          {
            SafeHtml safeHtml = checkNotNull(segment.getSafeHtml());
            segmentBuilder.setSafeHtml(SafeHtmls.toProto(safeHtml));
            break;
          }
        case PLACEHOLDER_LABEL:
          {
            String label = checkNotNull(segment.getPlaceholderLabel());
            segmentBuilder.setPlaceholderLabel(label);
            break;
          }
      }
      builder.addSegment(segmentBuilder.build());
    }
    return builder.build();
  }

  /** Deserializes a {@link SpliceableSafeHtml} from its protocol message representation. */
  public static SpliceableSafeHtml fromProto(SpliceableSafeHtmlProto proto) {
    List<Segment> segments = new ArrayList<>();
    for (SpliceableSafeHtmlProto.Segment protoSegment : proto.getSegmentList()) {
      if (protoSegment.hasSafeHtml()) {
        SafeHtml safeHtml = SafeHtmls.fromProto(protoSegment.getSafeHtml());
        segments.add(Segment.fromSafeHtml(safeHtml));
      } else if (protoSegment.hasPlaceholderLabel()) {
        String label = protoSegment.getPlaceholderLabel();
        segments.add(Segment.fromPlaceholderLabel(label));
      }
    }
    return new SpliceableSafeHtml(segments);
  }

  /**
   * Constructs an empty {@link SpliceableSafeHtml}, used internally for incremental construction by
   * {@link #spliceSomePreservingPlaceholders(Map)}.
   */
  private SpliceableSafeHtml() {
    this.segments = new ArrayList<>();
  }

  /**
   * Construct a SpliceableSafeHtml from a list of segments. To construct from a proto, see {@link
   * SpliceableSafeHtmls#fromProto(SpliceableSafeHtmlProto)}.
   */
  public SpliceableSafeHtml(List<Segment> segments) {
    this.segments = new ArrayList<>();
    this.segments.addAll(segments);
  }

  /** Construct a SpliceableSafeHtml from a single SafeHtml. */
  public SpliceableSafeHtml(SafeHtml html) {
    this.segments = new ArrayList<>();
    this.segments.add(Segment.fromSafeHtml(html));
  }

  /** @return A read-only list of segments that make up the template. */
  public List<Segment> getSegments() {
    return Collections.unmodifiableList(this.segments);
  }

  /** @return The set of placeholder labels. */
  public Set<String> getPlaceholderLabels() {
    Set<String> labels = new HashSet<>();
    for (Segment segment : this.segments) {
      if (segment.getType() == Segment.Type.PLACEHOLDER_LABEL) {
        labels.add(checkNotNull(segment.getPlaceholderLabel()));
      }
    }
    return labels;
  }

  /**
   * Returns a SafeHtml with a single placeholder replaced by the provided SafeHtml.
   *
   * @param placeholderLabel Label of placeholder to replace.
   * @param safeHtml SafeHtml to place in placeholder's location.
   * @return The single, merged SafeHtml.
   * @throws IllegalArgumentException if there were other placeholders.
   */
  public SafeHtml spliceOne(String placeholderLabel, SafeHtml safeHtml) {
    return splice(Collections.singletonMap(placeholderLabel, safeHtml), true);
  }

  /**
   * Returns a SafeHtml with all placeholders replaced by the provided SafeHtmls.
   *
   * @param substitutions A map of placeholder labels to the SafeHtmls that are to take their place.
   * @return The single, merged SafeHtml.
   * @throws IllegalArgumentException if a placeholder is encountered without a provided
   *     substitution.
   */
  public SafeHtml spliceAll(Map<String, SafeHtml> substitutions) {
    return splice(substitutions, true);
  }

  /**
   * Returns a SafeHtml with any placeholders that have matching substitutions replaced.
   * Placeholders without a provided substitution are ignored (replaced with empty string).
   *
   * @param substitutions A map of placeholder labels to the SafeHtmls that are to take their place.
   * @return A single, merged SafeHtml.
   */
  public SafeHtml spliceSome(Map<String, SafeHtml> substitutions) {
    return splice(substitutions, false);
  }

  /**
   * Returns a SpliceableSafeHtml with the placeholders that have matching substitutions replaced,
   * and the remaining placeholders left intact.
   *
   * @param substitutions A map of placeholder labels to the SafeHtmls that are to take their place.
   * @return SpliceableSafeHtml with remaining placeholders.
   */
  public SpliceableSafeHtml spliceSomePreservingPlaceholders(Map<String, SafeHtml> substitutions) {
    SpliceableSafeHtml substituted = new SpliceableSafeHtml();
    for (Segment segment : this.segments) {
      switch (segment.getType()) {
        case SAFE_HTML:
          {
            substituted.segments.add(segment);
            break;
          }
        case PLACEHOLDER_LABEL:
          {
            // Find the provided content for this insertion point.
            String placeholderLabel = segment.getPlaceholderLabel();
            SafeHtml safeHtmlToInsert = substitutions.get(placeholderLabel);
            if (safeHtmlToInsert != null) {
              substituted.segments.add(Segment.fromSafeHtml(safeHtmlToInsert));
            } else {
              substituted.segments.add(segment);
            }
            break;
          }
      }
    }
    return substituted;
  }

  /**
   * Returns a plain SafeHtml, assuming there are no placeholders.
   *
   * @return The single, merged SafeHtml.
   * @throws IllegalArgumentException if there are placeholders.
   */
  public SafeHtml getSafeHtml() {
    return splice(Collections.<String, SafeHtml>emptyMap(), true);
  }

  /**
   * Returns a plain SafeHtml, ignoring any placeholders (replaced with empty strings).
   *
   * @return The single, merged SafeHtml.
   */
  public SafeHtml getSafeHtmlIgnoringPlaceholders() {
    return splice(Collections.<String, SafeHtml>emptyMap(), false);
  }

  /**
   * Renders the template as a SafeHtml. For each insertion point, a SafeHtml chunk is pulled by
   * name from the substitutions map.
   *
   * @param substitutions Map of insertion point name to the SafeHtml to be inserted at that point.
   * @return A SafeHtml with the given SafeHtml fragments inserted at each insertion point.
   * @throws IllegalArgumentException if any of the placeholders are missing an assignment, and
   *     throwWhenMissing is true.
   */
  private SafeHtml splice(Map<String, SafeHtml> substitutions, boolean throwWhenMissing) {
    if (this.segments.isEmpty()) {
      return SafeHtml.EMPTY;
    }
    if ((this.segments.size() == 1) && (this.segments.get(0).getType() == Segment.Type.SAFE_HTML)) {
      return checkNotNull(this.segments.get(0).getSafeHtml());
    }
    List<SafeHtml> htmls = new ArrayList<>();
    for (Segment segment : this.segments) {
      switch (segment.getType()) {
        case SAFE_HTML:
          {
            htmls.add(checkNotNull(segment.getSafeHtml()));
            break;
          }
        case PLACEHOLDER_LABEL:
          {
            // Find the provided content for this insertion point.
            String placeholderLabel = segment.getPlaceholderLabel();
            SafeHtml safeHtmlToInsert = substitutions.get(placeholderLabel);
            if (safeHtmlToInsert != null) {
              htmls.add(safeHtmlToInsert);
            } else if (throwWhenMissing) {
              throw new IllegalArgumentException(
                  "Assignment missing for placeholder " + placeholderLabel);
            }
            break;
          }
      }
    }
    return SafeHtmls.concat(htmls);
  }

  /**
   * Renders the template with each insertion point denoted by its own name in an html comment
   * block.
   */
  @Override
  public String toString() {
    Map<String, SafeHtml> substitutions = new HashMap<>();
    for (String label : this.getPlaceholderLabels()) {
      substitutions.put(label, SafeHtmls.comment(label));
    }
    return spliceAll(substitutions).getSafeHtmlString();
  }

  /**
   * Element in the sequence of segments that make up a SafeHtml template. Can be one of constant
   * SafeHtml content or a placeholder label for insertion.
   *
   * @see SpliceableSafeHtmlProto.Segment
   */
  @Immutable
  public static final class Segment {
    /** Static SafeHtml for the segment, or null if a placeholder is set. */
    @Nullable private final SafeHtml safeHtml;
    /** Name for a location to insert SafeHtml in a segment sequence, or null if safeHtml is set. */
    @Nullable private final String placeholderLabel;

    /**
     * Type of segment. Provides a switch-able value indicating which getter will return a non-null
     * value.
     */
    public enum Type {
      SAFE_HTML,
      PLACEHOLDER_LABEL
    }

    /**
     * Use {@link #fromPlaceholderLabel(String)} or {@link #fromSafeHtml(SafeHtml)} to construct new
     * instances.
     */
    private Segment(@Nullable SafeHtml safeHtml, @Nullable String placeholderLabel) {
      this.safeHtml = safeHtml;
      this.placeholderLabel = placeholderLabel;
    }

    /** Constructs a new SafeHtml segment. */
    public static Segment fromSafeHtml(SafeHtml safeHtml) {
      return new Segment(checkNotNull(safeHtml), null);
    }

    /** Constructs a new placeholder segment. */
    public static Segment fromPlaceholderLabel(String placeholderLabel) {
      return new Segment(null, checkNotNull(placeholderLabel));
    }

    /** Gets the type of segment, either SAFE_HTML or PLACEHOLDER_LABEL. */
    public Type getType() {
      return (safeHtml != null) ? Type.SAFE_HTML : Type.PLACEHOLDER_LABEL;
    }

    /** Returns the SafeHtml value, or null if this is a placeholder label segment. */
    @Nullable
    public SafeHtml getSafeHtml() {
      return safeHtml;
    }

    /** Returns the placeholder label, or null if this is a SafeHtml segment. */
    @Nullable
    public String getPlaceholderLabel() {
      return placeholderLabel;
    }
  }
}
