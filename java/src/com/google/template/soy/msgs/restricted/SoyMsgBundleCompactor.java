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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsgPart.Case;
import java.util.Objects;

/**
 * Utility to compact message bundles.
 *
 * <p>Important: Only use this class from message plugins!
 *
 * <p>This instance will canonicalize different parts of messages to avoid storing the same objects
 * in memory multiple times, at the expense of static use of memory.
 *
 * <p>By using the static factory methods in this class, you ensure that message related objects are
 * not duplicated in memory, at the cost of having one copy that cannot be garbage collected.
 *
 * <p>This saves an enormous amount of memory, especially since in gender/plural messages, there are
 * many repeated parts.
 *
 */
public final class SoyMsgBundleCompactor {

  /** The default case spec for plural blocks. */
  private static final SoyMsgPluralCaseSpec DEFAULT_PLURAL_CASE_SPEC =
      new SoyMsgPluralCaseSpec(SoyMsgPluralCaseSpec.Type.OTHER);

  /** The default case spec for select blocks. */
  private static final String DEFAULT_SELECT_CASE_SPEC = null;

  private final CompactInterner interner = new CompactInterner();

  /**
   * Returns a more memory-efficient version of the internal message bundle.
   *
   * <p>Only enough information is retained for rendering; not enough for message extraction. As a
   * side effect, this SoyMsgBundleCompactor instance will also retain references to parts of the
   * messages in order to reuse identical objects.
   */
  public SoyMsgBundle compact(SoyMsgBundle input) {
    ImmutableList.Builder<SoyMsg> builder = ImmutableList.builder();
    for (SoyMsg msg : input) {
      ImmutableList<SoyMsgPart> parts = compactParts(msg.getParts());
      builder.add(
          SoyMsg.builder()
              .setId(msg.getId())
              .setLocaleString(msg.getLocaleString())
              .setIsPlrselMsg(MsgPartUtils.hasPlrselPart(parts))
              .setParts(parts)
              .build());
    }
    return new RenderOnlySoyMsgBundleImpl(input.getLocaleString(), builder.build());
  }

  /** Compacts a set of message parts. */
  private ImmutableList<SoyMsgPart> compactParts(ImmutableList<SoyMsgPart> parts) {
    ImmutableList.Builder<SoyMsgPart> builder = ImmutableList.builder();
    for (SoyMsgPart part : parts) {
      builder.add(compactPart(part));
    }
    return builder.build();
  }

  /**
   * Compacts a single message part.
   *
   * <p>If the part is a plural/select part, it might be expanded into multiple parts.
   */
  private SoyMsgPart compactPart(SoyMsgPart part) {
    if (part instanceof SoyMsgPluralPart) {
      part = compactPlural((SoyMsgPluralPart) part);
    } else if (part instanceof SoyMsgSelectPart) {
      part = compactSelect((SoyMsgSelectPart) part);
    } else if (part instanceof SoyMsgPlaceholderPart) {
      part = compactPlaceholder((SoyMsgPlaceholderPart) part);
    }
    // Now intern the message part.
    return intern(part);
  }

  private SoyMsgPart compactPlaceholder(SoyMsgPlaceholderPart part) {
    return new SoyMsgPlaceholderPart(intern(part.getPlaceholderName()));
  }

  private SoyMsgPart compactSelect(SoyMsgSelectPart select) {
    // TODO: Turn into a non-select message if there's only one unique case.
    // Select variable names tend to be repeated across many templates, like "gender".
    return new SoyMsgSelectPart(
        intern(select.getSelectVarName()),
        compactCases(select.getCases(), DEFAULT_SELECT_CASE_SPEC));
  }

  private SoyMsgPart compactPlural(SoyMsgPluralPart plural) {
    // Plural variable names tend to be repeated across templates, such as "count".
    return new SoyMsgPluralPart(
        intern(plural.getPluralVarName()),
        plural.getOffset(),
        compactCases(plural.getCases(), DEFAULT_PLURAL_CASE_SPEC));
  }

  /**
   * Recursively compacts plural/select cases.
   *
   * <p>This will attempt to remove unnecessary cases that can easily fall back to the default.
   *
   * @param cases Mapping (as pairs) from case spec to the message parts for that case.
   * @param defaultCaseSpec The default or "other" case specification value.
   */
  private <T> ImmutableList<Case<T>> compactCases(ImmutableList<Case<T>> cases, T defaultCaseSpec) {
    // Determine the fallback/other case value.
    ImmutableList<SoyMsgPart> defaultValue = null;
    for (Case<T> caseAndValue : cases) {
      if (Objects.equals(caseAndValue.spec(), defaultCaseSpec)) {
        defaultValue = caseAndValue.parts();
        break;
      }
    }

    ImmutableList.Builder<Case<T>> builder = ImmutableList.builder();
    for (Case<T> caseAndValue : cases) {

      // See if this case is the same as the default/other case, but isn't itself the default/other
      // case, and can be pruned.
      if (defaultValue != null
          && !Objects.equals(caseAndValue.spec(), defaultCaseSpec)
          && defaultValue.equals(caseAndValue.parts())) {
        continue;
      }

      // Intern the case value, since they tend to be very common among templates. For select,
      // they tend to be strings like "male" or "female", and for plurals, it tends to be one
      // of the few in the enum.
      builder.add(
          Case.create(
              caseAndValue.spec() != null ? intern(caseAndValue.spec()) : null,
              compactParts(caseAndValue.parts())));
    }
    return builder.build();
  }

  /**
   * Returns a possibly canonicalized version of the input. This causes a permanent reference to the
   * input.
   */
  private <T> T intern(T input) {
    return interner.intern(input);
  }
}
