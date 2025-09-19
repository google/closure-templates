/*
 * Copyright 2013 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.msgs.SoyMsgBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Utility to compact message bundles.
 *
 * <p>This instance will canonicalize different parts of messages to avoid storing the same objects
 * in memory multiple times, at the expense of static use of memory.
 *
 * <p>By using the static factory methods in this class, you ensure that message related objects are
 * not duplicated in memory, at the cost of having one copy that cannot be garbage collected.
 *
 * <p>This saves an enormous amount of memory, especially since in gender/plural messages, there are
 * many repeated parts.
 */
public final class SoyMsgBundleCompactor {

  private final ConcurrentMap<Object, Object> interner = new ConcurrentHashMap<>();

  private final RenderOnlyMsgIndex index = new RenderOnlyMsgIndex();

  /**
   * Returns a more memory-efficient version of the internal message bundle.
   *
   * <p>Only enough information is retained for rendering; not enough for message extraction. As a
   * side effect, this SoyMsgBundleCompactor instance will also retain references to parts of the
   * messages in order to reuse identical objects.
   */
  public SoyMsgBundle compact(SoyMsgBundle input) {
    ImmutableList.Builder<RenderOnlySoyMsgBundleImpl.RenderOnlySoyMsg> builder =
        ImmutableList.builderWithExpectedSize(input.getNumMsgs());
    for (SoyMsg msg : input) {
      if (RenderOnlySoyMsgBundleImpl.isValidMsgPartsForSoyRendering(msg.getParts())) {
        try {
          builder.add(
              RenderOnlySoyMsgBundleImpl.RenderOnlySoyMsg.create(
                  msg.getId(), compactParts(msg.getParts())));
        } catch (RuntimeException e) {
          throw new IllegalArgumentException("Failed to compact message: " + msg, e);
        }
      }
    }
    return new RenderOnlySoyMsgBundleImpl(index, input.getLocaleString(), builder.build());
  }

  /** Compacts a set of message parts. */
  private SoyMsgRawParts compactParts(ImmutableList<SoyMsgPart> parts) {
    SoyMsgRawParts.Builder builder = SoyMsgRawParts.builderWithExpectedSize(parts.size());
    for (SoyMsgPart part : parts) {
      builder.addRawPart(compactPart(part));
    }
    return intern(builder.build());
  }

  /**
   * Compacts a single message part.
   *
   * <p>If the part is a plural/select part, it might be expanded into multiple parts.
   */
  private Object compactPart(SoyMsgPart part) {
    Object result;
    if (part instanceof SoyMsgPluralPart) {
      result = compactPlural((SoyMsgPluralPart) part);
    } else if (part instanceof SoyMsgSelectPart) {
      result = compactSelect((SoyMsgSelectPart) part);
    } else if (part instanceof SoyMsgViewerGrammaticalGenderPart) {
      result = compactViewerGrammaticalGender((SoyMsgViewerGrammaticalGenderPart) part);
    } else if (part instanceof SoyMsgPlaceholderPart) {
      return PlaceholderName.create(
          ((SoyMsgPlaceholderPart) part).getPlaceholderName()); // already interned
    } else {
      result = ((SoyMsgRawTextPart) part).getRawText();
    }
    // Now intern the message part.
    return intern(result);
  }

  private SoyMsgSelectPartForRendering compactSelect(SoyMsgSelectPart select) {
    // TODO: Turn into a non-select message if there's only one unique case.
    // Select variable names tend to be repeated across many templates, like "gender".
    return new SoyMsgSelectPartForRendering(
        PlaceholderName.create(select.getSelectVarName()),
        compactCases(
            select.getCases(),
            // Intern the select strings
            spec -> spec == null ? null : intern(spec)));
  }

  private SoyMsgViewerGrammaticalGenderPartForRendering compactViewerGrammaticalGender(
      SoyMsgViewerGrammaticalGenderPart genderPart) {
    return new SoyMsgViewerGrammaticalGenderPartForRendering(
        compactCases(
            genderPart.getCases(),
            // Intern the GrammaticalGender enum values
            spec -> spec == null ? null : intern(spec)));
  }

  private SoyMsgPluralPartForRendering compactPlural(SoyMsgPluralPart plural) {
    // Plural variable names tend to be repeated across templates, such as "count".
    return new SoyMsgPluralPartForRendering(
        PlaceholderName.create(plural.getPluralVarName()),
        plural.getOffset(),
        compactCases(
            plural.getCases(),
            // Don't intern the plural case spec.  The explicit specs will get pulled apart and the
            // non-explicit ones are already interned.
            spec -> spec));
  }

  /**
   * Recursively compacts plural/select cases.
   *
   * <p>This will attempt to remove unnecessary cases that can easily fall back to the default.
   *
   * @param cases Mapping (as pairs) from case spec to the message parts for that case.
   */
  private <T> ImmutableList<SoyMsgRawParts.RawCase<T>> compactCases(
      ImmutableList<SoyMsgPart.Case<T>> cases, Function<T, T> specInterner) {
    ImmutableList.Builder<SoyMsgRawParts.RawCase<T>> builder = ImmutableList.builder();
    for (var caseAndValue : cases) {
      builder.add(
          SoyMsgRawParts.RawCase.create(
              specInterner.apply(caseAndValue.spec()), compactParts(caseAndValue.parts())));
    }
    return builder.build();
  }

  /**
   * Returns a possibly canonicalized version of the input. This causes a permanent reference to the
   * input.
   */
  private <T> T intern(T input) {
    checkNotNull(input); // sanity
    Object result = interner.putIfAbsent(input, input);
    if (result == null) {
      return input;
    }
    if (result.getClass() != input.getClass()) {
      throw new IllegalStateException();
    }
    @SuppressWarnings("unchecked") // safe due to the class check above
    T typedResult = (T) result;
    return typedResult;
  }
}
