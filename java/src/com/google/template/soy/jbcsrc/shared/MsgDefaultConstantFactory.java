/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.jbcsrc.shared;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.Iterators.forArray;
import static com.google.common.collect.Iterators.peekingIterator;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.PeekingIterator;
import com.google.errorprone.annotations.Keep;
import com.google.template.soy.msgs.restricted.PlaceholderName;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPart.Case;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawParts;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import java.lang.invoke.MethodHandles;
import java.util.IdentityHashMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * A {@code constantdynamic} bootstrap for handling msg constant defaults
 *
 * <p>In soy it is not unreasonable for there to be a very large number of msgs. To support missing
 * translations we need to encode defaults in the class file. A naive approach generates a lot of
 * code just to construct these defaults and requires a lot of constant fields
 *
 * <p>The benefit of using {@code constantdynamic} to construct these defaults as opposed to just
 * generating code that performs it inline is that we can ensure that the construction is only
 * performed once (and lazily) without needing to allocate {@code static final} fields to hold the
 * result. This saves on instructions and fields.
 *
 * <p>The downside is that we need to find an efficient way to pass the message to the bootstrap
 * method. For this we use a simple encoding/decoding routine to transform the objects into a form
 * suitable as constant bootstrap arguments.
 */
public final class MsgDefaultConstantFactory {
  // The general form of the constant arguments is a Tag followed by one or more values defined by
  // the tag.
  // This is not the most efficient encoding but is fairly simple to parse and produce.  A more
  // efficient approach would be to encode the tags separately as a key for the rest of the values.
  // This would allow us to encode the tags in a single slot (e.b. a String where each character is
  // a tag), which may be useful since there is a limit of 251 parameters
  // (https://docs.oracle.com/javase/7/docs/api/java/lang/invoke/package-summary.html).

  /** A marker object for parsing our constant bootstrap arguments. */
  private enum Tag {
    /** will be followed by a single string representing the raw text. */
    RAW,
    /** will be followed by a single string representing the placeholder name. */
    PLACEHOLDER,
    BEGIN_PLURAL,
    BEGIN_SELECT,
    /**
     * will be followed by an object which will either be a long or a string on if this is a plural
     * or a select case.
     */
    BEGIN_CASE,
    /** Marks the end of a plural/select */
    END;

    private static final Tag[] cached = Tag.values();

    static Tag fromRaw(Object object) {
      return cached[(Integer) object];
    }
  }

  /**
   * Transforms a list of message parts into a list of objects that can be encoded as bootstrap
   * methods arguments for {@link #bootstrapMsgConstant}.
   */
  public static ImmutableList<Object> msgToPartsList(ImmutableList<SoyMsgPart> parts) {
    ImmutableList<Object> constantParts = partsToConstantPartsList(parts);
    // remove trailing END markers, these are redundant with the end of the array
    // NOTE: there is no ambiguity with other integer values in the array (like plural offsets)
    // because those cannot appear in trailing position.
    Object last;
    int lastElement = constantParts.size() - 1;
    while ((last = constantParts.get(lastElement)) instanceof Integer
        && ((Integer) last) == Tag.END.ordinal()) {
      lastElement--;
    }
    return constantParts.subList(0, lastElement + 1);
  }

  private static ImmutableList<Object> partsToConstantPartsList(
      ImmutableList<SoyMsgPart> msgParts) {
    ImmutableList.Builder<Object> builder = ImmutableList.builder();
    for (SoyMsgPart msgPart : msgParts) {
      builder.addAll(partToConstantPartsList(msgPart));
    }
    return builder.build();
  }

  /** Decomposes a part into a set of constant arguments. */
  private static ImmutableList<Object> partToConstantPartsList(SoyMsgPart part) {
    ImmutableList.Builder<Object> builder = ImmutableList.builder();

    if (part instanceof SoyMsgPlaceholderPart) {
      builder
          .add(Tag.PLACEHOLDER.ordinal())
          .add(((SoyMsgPlaceholderPart) part).getPlaceholderName());
    } else if (part instanceof SoyMsgPluralPart) {
      SoyMsgPluralPart pluralPart = (SoyMsgPluralPart) part;
      builder
          .add(Tag.BEGIN_PLURAL.ordinal())
          .add(pluralPart.getPluralVarName())
          .add(pluralPart.getOffset());

      for (Case<SoyMsgPluralCaseSpec> item : pluralPart.getCases()) {
        builder.add(Tag.BEGIN_CASE.ordinal());
        if (item.spec().getType() == SoyMsgPluralCaseSpec.Type.EXPLICIT) {
          builder.add(item.spec().getExplicitValue());
        } else {
          builder.add(item.spec().getType().name());
        }
        builder.addAll(partsToConstantPartsList(item.parts()));
      }
      builder.add(Tag.END.ordinal());
    } else if (part instanceof SoyMsgRawTextPart) {
      builder.add(Tag.RAW.ordinal()).add(((SoyMsgRawTextPart) part).getRawText());
    } else if (part instanceof SoyMsgSelectPart) {
      SoyMsgSelectPart selectPart = (SoyMsgSelectPart) part;
      builder.add(Tag.BEGIN_SELECT.ordinal()).add(selectPart.getSelectVarName());

      for (Case<String> item : selectPart.getCases()) {
        builder.add(Tag.BEGIN_CASE.ordinal());
        // can't store null as a bootstrap method argument so encode as empty
        builder.add(nullToEmpty(item.spec()));
        builder.addAll(partsToConstantPartsList(item.parts()));
      }
      builder.add(Tag.END.ordinal());
    } else {
      throw new AssertionError("unrecognized part: " + part);
    }
    return builder.build();
  }

  /**
   * A JVM bootstrap method for constructing defaults for {@code msg} commands.
   *
   * @param lookup An object that allows us to resolve classes/methods in the context of the
   *     callsite. Provided automatically by invokeDynamic JVM infrastructure
   * @param name The name of the invokeDynamic method being called. This is provided by
   *     invokeDynamic JVM infrastructure and currently unused.
   * @param type The type of the method being called. This is always {@code
   *     ()->ImmutableList<SoyMsgPart>}
   * @param rawParts The pieces of the message
   */
  public static SoyMsgRawParts bootstrapMsgConstant(
      MethodHandles.Lookup lookup, String name, Class<?> type, Object... rawParts) {
    PeekingIterator<Object> itr = peekingIterator(forArray(rawParts));
    ImmutableList<SoyMsgPart> parts = parseParts(itr);
    checkState(!itr.hasNext()); // sanity
    return SoyMsgRawParts.fromMsgParts(parts);
  }

  private static ImmutableList<SoyMsgPart> parseParts(PeekingIterator<Object> rawParts) {
    return parseParts(rawParts, /* isCase= */ false);
  }

  private static ImmutableList<SoyMsgPart> parseParts(
      PeekingIterator<Object> rawParts, boolean isCase) {
    ImmutableList.Builder<SoyMsgPart> parts = ImmutableList.builder();
    while (rawParts.hasNext()) {
      Tag tag = Tag.fromRaw(rawParts.peek());
      if (isCase && (tag == Tag.BEGIN_CASE || tag == Tag.END)) {
        // These happen when parsing cases, we are either about to begin a new case or we are done
        // with the whole sequence of cases.
        return parts.build();
      }
      rawParts.next(); // consume the tag
      switch (tag) {
        case RAW:
          parts.add(SoyMsgRawTextPart.of((String) rawParts.next()));
          break;
        case PLACEHOLDER:
          parts.add(new SoyMsgPlaceholderPart((String) rawParts.next()));
          break;
        case BEGIN_PLURAL:
          {
            String pluralVarName = (String) rawParts.next();
            int offset = (Integer) rawParts.next();
            ImmutableList<Case<SoyMsgPluralCaseSpec>> cases =
                parseCases(
                    rawParts,
                    spec -> {
                      if (spec instanceof Number) {
                        return new SoyMsgPluralCaseSpec(((Number) spec).longValue());
                      } else {
                        return SoyMsgPluralCaseSpec.forType((String) spec);
                      }
                    });
            parts.add(new SoyMsgPluralPart(pluralVarName, offset, cases));
            break;
          }
        case BEGIN_SELECT:
          {
            String selectVarName = (String) rawParts.next();
            ImmutableList<Case<String>> cases =
                parseCases(
                    rawParts,
                    spec -> {
                      String s = (String) spec;
                      return s.isEmpty() ? null : s;
                    });
            parts.add(new SoyMsgSelectPart(selectVarName, cases));
            break;
          }
        case BEGIN_CASE:
        case END:
          throw new AssertionError();
      }
    }
    return parts.build();
  }

  private static <T> ImmutableList<Case<T>> parseCases(
      PeekingIterator<Object> rawParts, Function<Object, T> specFactory) {
    ImmutableList.Builder<Case<T>> cases = ImmutableList.builder();
    while (rawParts.hasNext()) {
      Tag next = Tag.fromRaw(rawParts.next());
      if (next == Tag.BEGIN_CASE) {
        T spec = specFactory.apply(rawParts.next());
        cases.add(Case.create(spec, parseParts(rawParts, /* isCase= */ true)));
      } else if (next == Tag.END) {
        break;
      } else {
        throw new AssertionError();
      }
    }
    return cases.build();
  }

  @Keep
  public static ToIntFunction<PlaceholderName> placeholderIndexFunction(
      MethodHandles.Lookup lookup, String name, Class<?> type, String... names) {
    // We need to compute a function from placeholder name to corresponding index in the list of
    // placeholders.
    // From an analysis of VFE we can see this distribution of placeholder counts:
    // #=ocurrences = subtotal?
    // 0=307,557
    // 1=57,169
    // 2=32,635
    // 3=8,553  = 98357
    // 4=4,040
    // 5=1,420
    // 6=1,260
    // 7=554
    // 8=401
    // 9=79
    // 12=162  = 106273
    //
    // Which is unsurprisingly very skewed towards low numbers. Also, N.B. messages with no
    // placeholders don't call this function at all! So covering 1-4 handles 96% of all messages.
    //
    // We also know a priori that a linear search beats a hashtable lookup for small sizes, so we
    // just hardcode those for sizes 1-4 t.
    for (int i = 1; i < names.length; i++) {
      checkArgument(names[i - 1].compareTo(names[i]) < 0, "Expected names to be sorted.");
    }
    PlaceholderName[] placeholderNames =
        stream(names).map(PlaceholderName::create).toArray(PlaceholderName[]::new);
    switch (names.length) {
      case 0:
        throw new IllegalArgumentException("No placeholders, should not have been called.");
      case 1:
        {
          final var name0 = placeholderNames[0];
          return (placeholder) -> {
            if (placeholder == name0) {
              return 0;
            } else {
              throw new IllegalArgumentException("Unknown placeholder: " + placeholder);
            }
          };
        }
      case 2:
        {
          final var name0 = placeholderNames[0];
          final var name1 = placeholderNames[1];
          return (placeholder) -> {
            if (placeholder == name0) {
              return 0;
            } else if (placeholder == name1) {
              return 1;
            } else {
              throw new IllegalArgumentException("Unknown placeholder: " + placeholder);
            }
          };
        }
      case 3:
        {
          final var name0 = placeholderNames[0];
          final var name1 = placeholderNames[1];
          final var name2 = placeholderNames[2];
          return (placeholder) -> {
            if (placeholder == name0) {
              return 0;
            } else if (placeholder == name1) {
              return 1;
            } else if (placeholder == name2) {
              return 2;
            } else {
              throw new IllegalArgumentException("Unknown placeholder: " + placeholder);
            }
          };
        }
      case 4:
        {
          final var name0 = placeholderNames[0];
          final var name1 = placeholderNames[1];
          final var name2 = placeholderNames[2];
          final var name3 = placeholderNames[3];
          return (placeholder) -> {
            if (placeholder == name0) {
              return 0;
            } else if (placeholder == name1) {
              return 1;
            } else if (placeholder == name2) {
              return 2;
            } else if (placeholder == name3) {
              return 3;
            } else {
              throw new IllegalArgumentException("Unknown placeholder: " + placeholder);
            }
          };
        }
      default:
        {
          if (names.length <= 8) {
            return (placeholder) -> {
              for (int i = 0; i < placeholderNames.length; i++) {
                if (placeholderNames[i] == placeholder) {
                  return i;
                }
              }
              throw new IllegalArgumentException("Unknown placeholder: " + placeholder);
            };
          }
          IdentityHashMap<PlaceholderName, Integer> result = new IdentityHashMap<>();
          for (int i = 0; i < names.length; i++) {
            result.put(placeholderNames[i], i);
          }
          return (placeholder) -> {
            Integer index = result.get(placeholder);
            if (index == null) {
              throw new IllegalArgumentException("Unknown placeholder: " + placeholder);
            }
            return index.intValue();
          };
        }
    }
  }

  @Keep
  public static ImmutableSetMultimap<PlaceholderName, PlaceholderName> placeholderOrdering(
      MethodHandles.Lookup lookup, String name, Class<?> type, String... endStartPlaceholderPairs) {
    checkArgument(
        endStartPlaceholderPairs.length % 2 == 0, "Expected an even number of placeholder pairs");
    checkArgument(endStartPlaceholderPairs.length > 1, "Expected at least one  placeholder pair");
    ImmutableSetMultimap.Builder<PlaceholderName, PlaceholderName> result =
        ImmutableSetMultimap.builder();
    for (int i = 0; i < endStartPlaceholderPairs.length; i += 2) {
      result.put(
          PlaceholderName.create(endStartPlaceholderPairs[i]),
          PlaceholderName.create(endStartPlaceholderPairs[i + 1]));
    }
    var finalResult = result.build();
    for (var entry : finalResult.entries()) {
      var end = entry.getKey();
      var start = entry.getValue();
      if (finalResult.containsKey(start)) {
        throw new IllegalArgumentException(
            "Expected placeholder "
                + start
                + " is supposed to come before "
                + end
                + ", but it also is configured to come after "
                + finalResult.get(start)
                + ". Order constraints cannot be transitive");
      }
    }
    return finalResult;
  }

  private MsgDefaultConstantFactory() {}
}
