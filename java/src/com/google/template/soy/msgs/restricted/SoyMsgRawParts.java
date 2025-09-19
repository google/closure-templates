/*
 * Copyright 2024 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.msgs.restricted;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * A collection of message parts using a raw representation.
 *
 * <p>For internal use only. For server rendering use cases.
 */
@Immutable
public abstract class SoyMsgRawParts implements Iterable<Object> {
  /** The 'raw' dual of {@link SoyMsgPart.Case}. Aids in some compaction algorithms. */
  @AutoValue
  public abstract static class RawCase<T> {
    public static <T> RawCase<T> create(SoyMsgPart.Case<T> fullCase) {
      return create(fullCase.spec(), fromMsgParts(fullCase.parts()));
    }

    public static <T> RawCase<T> create(T spec, SoyMsgRawParts parts) {
      return new AutoValue_SoyMsgRawParts_RawCase<>(spec, parts);
    }

    // null means default case
    @Nullable
    public abstract T spec();

    public abstract SoyMsgRawParts parts();
  }

  /** An empty set of parts. */
  public static final SoyMsgRawParts EMPTY = new MultipleParts(new Object[0]);

  public static SoyMsgRawParts fromMsgParts(Iterable<SoyMsgPart> parts) {
    var builder = builder();
    for (var part : parts) {
      builder.add(part);
    }
    return builder.build();
  }

  private static SoyMsgPart fromRawPart(Object part) {
    if (part instanceof String) {
      return SoyMsgRawTextPart.of((String) part);
    } else if (part instanceof PlaceholderName) {
      return new SoyMsgPlaceholderPart(((PlaceholderName) part).name());
    } else if (part instanceof SoyMsgPluralPartForRendering) {
      return ((SoyMsgPluralPartForRendering) part).toPluralPart();
    } else if (part instanceof SoyMsgSelectPartForRendering) {
      return ((SoyMsgSelectPartForRendering) part).toSelectPart();
    } else if (part instanceof SoyMsgViewerGrammaticalGenderPartForRendering) {
      return ((SoyMsgViewerGrammaticalGenderPartForRendering) part).toGenderPart();
    }
    throw new IllegalArgumentException("Unsupported part type: " + part.getClass());
  }

  ImmutableList<SoyMsgPart> toSoyMsgParts() {
    var builder = ImmutableList.<SoyMsgPart>builderWithExpectedSize(numParts());
    forEach(part -> builder.add(fromRawPart(part)));
    return builder.build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public abstract boolean isPlrselMsg();

  public abstract int numParts();

  public abstract Object getPart(int i);

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();

  public static SoyMsgRawParts of(String part) {
    return new SinglePart(part);
  }

  public static Builder builderWithExpectedSize(int expectedSize) {
    return new Builder(expectedSize);
  }

  private static Object toRawPart(SoyMsgPart part) {
    if (part instanceof SoyMsgRawTextPart) {
      return ((SoyMsgRawTextPart) part).getRawText();
    } else if (part instanceof SoyMsgPlaceholderPart) {
      return PlaceholderName.create(((SoyMsgPlaceholderPart) part).getPlaceholderName());
    } else if (part instanceof SoyMsgPluralPart) {
      return new SoyMsgPluralPartForRendering((SoyMsgPluralPart) part);
    } else if (part instanceof SoyMsgSelectPart) {
      return new SoyMsgSelectPartForRendering(((SoyMsgSelectPart) part));
    } else if (part instanceof SoyMsgViewerGrammaticalGenderPart) {
      return new SoyMsgViewerGrammaticalGenderPartForRendering(
          ((SoyMsgViewerGrammaticalGenderPart) part));
    }
    throw new IllegalArgumentException("Unsupported part type: " + part.getClass());
  }

  /** A builder for raw parts */
  public static final class Builder {
    final List<Object> parts;

    private Builder() {
      this.parts = new ArrayList<>();
    }

    private Builder(int expectedSize) {
      this.parts = new ArrayList<>(expectedSize);
    }

    @CanIgnoreReturnValue
    public Builder addRawPart(Object part) {
      checkArgument(
          part instanceof String
              || part instanceof PlaceholderName
              || part instanceof SoyMsgPluralPartForRendering
              || part instanceof SoyMsgSelectPartForRendering
              || part instanceof SoyMsgViewerGrammaticalGenderPartForRendering,
          "Unsupported part type: %s",
          part.getClass());
      parts.add(part);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder add(SoyMsgPart part) {
      parts.add(toRawPart(part));
      return this;
    }

    @CanIgnoreReturnValue
    public Builder add(String part) {
      parts.add(part);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder add(PlaceholderName part) {
      parts.add(part);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder add(SoyMsgPluralPartForRendering part) {
      parts.add(part);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder add(SoyMsgSelectPartForRendering part) {
      parts.add(part);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder add(SoyMsgViewerGrammaticalGenderPartForRendering part) {
      parts.add(part);
      return this;
    }

    public SoyMsgRawParts build() {
      switch (parts.size()) {
        case 0:
          return EMPTY;
        case 1:
          var singlePart = parts.get(0);
          if (singlePart instanceof String || singlePart instanceof PlaceholderName) {
            return new SinglePart(singlePart);
          } else if (singlePart instanceof SoyMsgPluralPartForRendering
              || singlePart instanceof SoyMsgSelectPartForRendering
              || singlePart instanceof SoyMsgViewerGrammaticalGenderPartForRendering) {
            return (SoyMsgRawParts) singlePart;
          }
          throw new AssertionError("Unexpected part type: " + singlePart.getClass());
        default:
          return new MultipleParts(parts.toArray());
      }
    }
  }

  private static final class SinglePart extends SoyMsgRawParts {
    @SuppressWarnings("Immutable") // constructor enforces immutableness
    private final Object part;

    SinglePart(Object part) {
      checkArgument(
          part instanceof String || part instanceof PlaceholderName,
          "Unsupported part type: %s",
          part.getClass());
      this.part = part;
    }

    @Override
    public int numParts() {
      return 1;
    }

    @Override
    public Iterator<Object> iterator() {
      return Iterators.singletonIterator(part);
    }

    @Override
    public void forEach(Consumer<Object> action) {
      action.accept(part);
    }

    @Override
    public boolean isPlrselMsg() {
      return false;
    }

    @Override
    public int hashCode() {
      return part.hashCode();
    }

    @Override
    public Object getPart(int i) {
      checkArgument(i == 0);
      return part;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof SinglePart && part.equals(((SinglePart) other).part);
    }

    @Override
    public String toString() {
      return "SoyMsgRawParts.SinglePart{" + part + "}";
    }
  }

  private static final class MultipleParts extends SoyMsgRawParts {
    // This is a mix of String, PlaceholderName objects
    @SuppressWarnings("Immutable") // constructor enforces immutableness
    private final Object[] parts;

    MultipleParts(Object[] parts) {
      checkArgument(parts.length != 1);
      for (int i = 0; i < parts.length; i++) {
        var part = parts[i];
        checkArgument(
            part instanceof String || part instanceof PlaceholderName,
            "Unsupported part type: %s at index %s",
            part.getClass(),
            i);
      }
      this.parts = parts;
    }

    @Override
    public int numParts() {
      return parts.length;
    }

    @Override
    public Iterator<Object> iterator() {
      return Iterators.forArray(parts);
    }

    @Override
    public void forEach(Consumer<Object> action) {
      for (var part : parts) {
        action.accept(part);
      }
    }

    @Override
    public boolean isPlrselMsg() {
      return false;
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(parts);
    }

    @Override
    public Object getPart(int i) {
      return parts[i];
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof MultipleParts && Arrays.equals(parts, ((MultipleParts) other).parts);
    }

    @Override
    public String toString() {
      return "SoyMsgRawParts.MultipleParts{" + Arrays.toString(parts) + "}";
    }
  }
}
