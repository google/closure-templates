/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.passes;

import com.google.common.base.Preconditions;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.TagName;
import java.util.ArrayDeque;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A union of {@code TagName} or a {@code ConditionalBranches}.
 *
 * <p>When we perform strict html validation, we use two {@code ArrayDeque}s of {@code
 * HtmlTagEntry}s to save the {@code HtmlOpenTagNode} and the {@code HtmlCloseTagNode},
 * respectively. For nodes that are not in a control branch, we will store {@code TagName}; for
 * nodes that are in a control branch, a {@code ConditionalBranches} will be created and saved.
 */
final class HtmlTagEntry {

  // Exactly one of these is non-null.
  private final TagName tagName;
  private final ConditionalBranches branches;
  private static final SoyErrorKind UNSUPPORTED_HTML_TAG_NODE =
      SoyErrorKind.of(
          "Strict HTML validation only supports matching tags that are raw texts "
              + "or print commands.");
  private static final SoyErrorKind MISMATCH_DYNAMIC_TAG =
      SoyErrorKind.of("The print commands need to be identical for dynamic tags.");
  private static final SoyErrorKind UNEXPECTED_CLOSE_TAG =
      SoyErrorKind.of("Unexpected HTML close tag.");
  private static final SoyErrorKind UNEXPECTED_CLOSE_TAG_WITH_EXPECTATION =
      SoyErrorKind.of("Unexpected HTML close tag. Expected: ''</{0}>''");
  private static final SoyErrorKind OPEN_TAG_NOT_CLOSED =
      SoyErrorKind.of("Expected tag to be closed.");
  // TODO(user): Improve this error message.
  private static final SoyErrorKind MISMATCH_TAG =
      SoyErrorKind.of("Could not find a match for this HTML tag.");

  HtmlTagEntry(TagName tagName) {
    this.tagName = tagName;
    this.branches = null;
  }

  HtmlTagEntry(ConditionalBranches branches) {
    this.tagName = null;
    this.branches = new ConditionalBranches(branches);
  }

  boolean hasTagName() {
    return tagName != null;
  }

  @Nullable
  TagName getTagName() {
    return tagName;
  }

  @Nullable
  ConditionalBranches getBranches() {
    return branches;
  }

  boolean hasEmptyBranches() {
    return branches != null && branches.isEmpty();
  }

  /**
   * Return source location for this entry. If it is a tag name, return its location, otherwise try
   * to recursively find a tag name in branches.
   */
  SourceLocation getSourceLocation() {
    if (hasTagName()) {
      return tagName.getTagLocation();
    }
    for (ConditionalBranches.ConditionalBranch branch : branches.getBranches()) {
      if (!branch.deque().isEmpty()) {
        return branch.deque().peek().getSourceLocation();
      }
    }
    return SourceLocation.UNKNOWN;
  }

  @Override
  public String toString() {
    return hasTagName() ? tagName.toString() : branches.toString();
  }

  /**
   * A method that should be called after we visit a control flow node ({@code IfNode} or {@code
   * SwitchNode}).
   *
   * <p>Compared to {@code matchOrError} methods, this method does not report an error when one of
   * the deques is empty. However, if both deques are not empty but the top do not match, we should
   * report an error.
   *
   * <p>Another difference is that we don't try to remove all optional tags from the openStack, if
   * the closeQueue is empty. It is possible that some of the optional tags are ended in another
   * control block, and we cannot remove them too eagerly.
   *
   * <p>After calling this method,
   *
   * <ul>
   *   <li>if it returns true, at least one of the stack/queue will be empty.
   *   <li>if it returns false, we have already reported an error.
   * </ul>
   */
  static boolean tryMatchOrError(
      ArrayDeque<HtmlTagEntry> openStack,
      ArrayDeque<HtmlTagEntry> closeQueue,
      ErrorReporter errorReporter) {
    while (!openStack.isEmpty() && !closeQueue.isEmpty()) {
      HtmlTagEntry openTag = openStack.peekFirst();
      HtmlTagEntry closeTag = closeQueue.peekFirst();
      if (closeTag.hasTagName()) {
        if (closeTag.getTagName().equals(openTag.getTagName())) {
          openStack.pollFirst();
          closeQueue.pollFirst();
          continue;
        } else {
          // This logic is similar to popOptionalTag(), but there is no good way to extract this.
          if (openTag.hasTagName()
              && TagName.checkOptionalTagShouldBePopped(
                  openTag.getTagName(), closeTag.getTagName())) {
            openStack.pollFirst();
            continue;
          } else if (!openTag.hasTagName()) {
            openTag.getBranches().popOptionalTags(closeTag.getTagName());
            if (openTag.getBranches().isEmpty()) {
              openStack.pollFirst();
              continue;
            }
            // Mutate the stack/queue if we found common prefix.
            if (tryMatchCommonPrefix(openTag, closeTag, errorReporter)) {
              openStack.pollFirst();
              closeQueue.pollFirst();
              continue;
            }
            // We already report an error in tryMatchCommonPrefix.
            return false;
          }
        }
        // Remove optional tags from the open stack before we try to match the current close tag.
        openTag = popOptionalTags(openStack, closeTag.getTagName());
      }
      if (matchOrError(openTag, closeTag, errorReporter)) {
        openStack.pollFirst();
        closeQueue.pollFirst();
      } else {
        // We already reported an error in matchOrError.
        return false;
      }
    }
    // At this point, at least one of the stack/queue should be empty.
    return true;
  }

  /**
   * A helper method that recursively pops optional start tags from a stack. Returns the top of the
   * deque.
   *
   * <p>If {@code closeTag} is present, this method will try to check if the top of the deque is a
   * poppable optional tag. The rule is every optional open tag can only be popped by a certain
   * subset of close tags. For example, {@code <li>} can only be popped by {@code </ul>} or {@code
   * </ol>}, and we will report an error when we see any other close tags.
   *
   * <p>If {@code closeTag} is null, this method pops out all optional tags in the deque. This is
   * useful for the cases where we are at the end of a template, and want to check if the deque is
   * empty before reporting an error.
   */
  static HtmlTagEntry popOptionalTags(ArrayDeque<HtmlTagEntry> deque, @Nullable TagName closeTag) {
    HtmlTagEntry entry = null;
    while (!deque.isEmpty()) {
      entry = deque.peekFirst();
      if (entry.hasTagName()) {
        if (entry.getTagName().equals(closeTag)) {
          // We should not poll the deque here, since we will use the current entry for matching.
          // The deque will be updated in matchOrError method.
          break;
        }
        if ((closeTag == null && entry.getTagName().isDefinitelyOptional())
            || (closeTag != null
                && TagName.checkOptionalTagShouldBePopped(entry.getTagName(), closeTag))) {
          deque.pollFirst();
          continue;
        }
      } else {
        entry.getBranches().popOptionalTags(closeTag);
        if (entry.getBranches().isEmpty()) {
          deque.pollFirst();
          continue;
        }
      }
      break;
    }
    return entry;
  }

  /**
   * A helper method that matches a list of open tags and a list of close tags. We need to compare
   * the last open tag and the first close tag one by one.
   */
  static boolean matchOrError(
      ArrayDeque<HtmlTagEntry> openStack,
      ArrayDeque<HtmlTagEntry> closeQueue,
      ErrorReporter errorReporter) {
    if (!tryMatchOrError(openStack, closeQueue, errorReporter)) {
      return false;
    }
    // Try to remove any remaining optional tags or tags with empty branches in the open stack.
    // At this point we should unconditionally pop any optional tags.
    HtmlTagEntry openTag = popOptionalTags(openStack, null);
    if (!openStack.isEmpty()) {
      errorReporter.report(openTag.getSourceLocation(), OPEN_TAG_NOT_CLOSED);
      return false;
    }
    if (!closeQueue.isEmpty()) {
      errorReporter.report(closeQueue.getFirst().getSourceLocation(), UNEXPECTED_CLOSE_TAG);
      return false;
    }
    return true;
  }

  /**
   * A helper method that compare two tags and report corresponding error. Note that the order of
   * inputs matters.
   */
  static boolean matchOrError(TagName openTag, TagName closeTag, ErrorReporter errorReporter) {
    if (openTag.isStatic() != closeTag.isStatic()) {
      // We only allow the same kind of nodes for dynamic tag names. We do not want to support
      // runtime validations, so something like <div></{$foo}> is not allowed.
      errorReporter.report(closeTag.getTagLocation(), UNSUPPORTED_HTML_TAG_NODE);
      return false;
    }
    if (!openTag.equals(closeTag)) {
      // For static tag names, report detailed information that includes the expected tag name.
      if (openTag.isStatic()) {
        errorReporter.report(
            closeTag.getTagLocation(),
            UNEXPECTED_CLOSE_TAG_WITH_EXPECTATION,
            openTag.getStaticTagNameAsLowerCase());
      } else {
        errorReporter.report(closeTag.getTagLocation(), MISMATCH_DYNAMIC_TAG);
      }
      return false;
    }
    return true;
  }

  /** A helper method that compare two {@code HtmlTagEntry}s. */
  public static boolean matchOrError(
      @Nullable HtmlTagEntry openTag,
      @Nullable HtmlTagEntry closeTag,
      ErrorReporter errorReporter) {
    if (openTag == null && closeTag == null) {
      return true;
    }
    if (openTag == null && closeTag != null) {
      errorReporter.report(closeTag.getSourceLocation(), UNEXPECTED_CLOSE_TAG);
      return false;
    }
    if (openTag != null && closeTag == null) {
      errorReporter.report(openTag.getSourceLocation(), OPEN_TAG_NOT_CLOSED);
      return false;
    }
    if (openTag.hasTagName() != closeTag.hasTagName()) {
      return tryMatchCommonPrefix(openTag, closeTag, errorReporter);
    }
    // Now both tags should be either tag names or conditional branches.
    if (openTag.hasTagName()) {
      return matchOrError(openTag.getTagName(), closeTag.getTagName(), errorReporter);
    } else {
      List<ConditionalBranches.ConditionalBranch> openBranches =
          openTag.getBranches().getBranches();
      List<ConditionalBranches.ConditionalBranch> closeBranches =
          closeTag.getBranches().getBranches();
      // For the following cases, we report error at the source location for the first tag in
      // the conditional branch.
      SourceLocation location = closeTag.getSourceLocation();
      if (openBranches.size() != closeBranches.size()) {
        errorReporter.report(location, MISMATCH_TAG);
        return false;
      }
      for (int i = 0; i < openBranches.size(); ++i) {
        ConditionalBranches.ConditionalBranch openBranch = openBranches.get(i);
        ConditionalBranches.ConditionalBranch closeBranch = closeBranches.get(i);
        if (!openBranch.condition().equals(closeBranch.condition())) {
          errorReporter.report(location, MISMATCH_TAG);
          return false;
        }
        ArrayDeque<HtmlTagEntry> openStack = openBranch.deque();
        ArrayDeque<HtmlTagEntry> closeQueue = closeBranch.deque();
        if (!matchOrError(openStack, closeQueue, errorReporter)) {
          return false;
        }
      }
      return true;
    }
  }

  /**
   * Pops all optional tags in the openStack that can be popped by the current close tag. Returns
   * the top of the stack after popping.
   */
  private static HtmlTagEntry popOptionalTagInStack(
      ArrayDeque<HtmlTagEntry> openStack, TagName closeTag) {
    HtmlTagEntry openTag = openStack.peekFirst();
    while (openTag != null) {
      if (openTag.hasTagName()
          && !openTag.getTagName().equals(closeTag)
          && TagName.checkOptionalTagShouldBePopped(openTag.getTagName(), closeTag)) {
        openStack.pollFirst();
        openTag = openStack.peekFirst();
        continue;
      } else if (!openTag.hasTagName()) {
        // For Conditional branches, we also need to pop optional tags.
        openTag.getBranches().popOptionalTags(closeTag);
        if (openTag.getBranches().isEmpty()) {
          openStack.pollFirst();
          openTag = openStack.peekFirst();
          continue;
        }
      }
      // At this point we should break.
      break;
    }
    return openTag;
  }

  /**
   * Try to match a close tag with a stack of open tags, and report errors accordingly.
   *
   * <p>Return false if openStack is empty or we cannot find a common prefix for the current close
   * tag. Notably returning true does not mean we find a match for the current close tag. When we
   * definitely know there is a mismatch and report an error for that, we still return true. The
   * return value is used by {@code StrictHtmlValidationPass} to decide whether we should add the
   * closeTag to the queue.
   */
  static boolean tryMatchCloseTag(
      ArrayDeque<HtmlTagEntry> openStack, TagName closeTag, ErrorReporter errorReporter) {
    // Pop out every optional tags that does not match the current close tag.
    HtmlTagEntry openTag = popOptionalTagInStack(openStack, closeTag);
    if (openTag == null) {
      return false;
    } else if (openTag.hasTagName()) {
      // Check if we can find a matching open tag within the current block.
      // Only pop the tag from the open stack if we find a match.
      // This way we do not emit cascade errors for a misplace closed tag.
      if (matchOrError(openTag.getTagName(), closeTag, errorReporter)) {
        openStack.pollFirst();
      }
    } else {
      boolean matchCommonPrefix = tryMatchCommonPrefix(openStack.peekFirst(), closeTag);
      if (matchCommonPrefix && openStack.peekFirst().hasEmptyBranches()) {
        openStack.pollFirst();
      }
      if (!matchCommonPrefix) {
        return false;
      }
    }
    return true;
  }

  static boolean tryMatchCloseTag(
      ArrayDeque<HtmlTagEntry> openStack, HtmlTagEntry closeTag, ErrorReporter errorReporter) {
    if (closeTag.hasTagName()) {
      return tryMatchCloseTag(openStack, closeTag.getTagName(), errorReporter);
    }
    HtmlTagEntry openTag = openStack.peekFirst();
    if (openTag == null) {
      return false;
    } else if (openTag.hasTagName()) {
      boolean matchCommonPrefix = tryMatchCommonPrefix(closeTag, openTag.getTagName());
      if (matchCommonPrefix && closeTag.hasEmptyBranches()) {
        return true;
      }
      if (!matchCommonPrefix) {
        return false;
      }
    } else {
      if (matchOrError(openTag, closeTag, errorReporter)) {
        openStack.pollFirst();
      } else {
        return false;
      }
    }
    return true;
  }

  /**
   * Check if there is a common prefix matching for a given {@code HtmlTagEntry} and a {@code
   * TagName}. If find a common prefix, remove them from the branches. Note that this method mutates
   * the {@code HtmlTagEntry} (removing optional tags and common prefix).
   */
  static boolean tryMatchCommonPrefix(HtmlTagEntry entry, TagName tagName) {
    Preconditions.checkArgument(!entry.hasTagName());
    if (entry.getBranches().hasCommonPrefix(tagName)) {
      entry.getBranches().popAllBranches();
      return true;
    }
    return false;
  }

  private static boolean tryMatchCommonPrefix(
      HtmlTagEntry openTag, TagName closeTag, ErrorReporter errorReporter) {
    boolean matchCommonPrefix = tryMatchCommonPrefix(openTag, closeTag);
    // TODO(user): Remove this once we support partial prefix matching.
    if (matchCommonPrefix && !openTag.getBranches().isEmpty()) {
      matchCommonPrefix = false;
      errorReporter.report(openTag.getSourceLocation(), OPEN_TAG_NOT_CLOSED);
    }
    if (!matchCommonPrefix) {
      errorReporter.report(closeTag.getTagLocation(), UNEXPECTED_CLOSE_TAG);
    }
    return matchCommonPrefix;
  }

  private static boolean tryMatchCommonPrefix(
      TagName openTag, HtmlTagEntry closeTag, ErrorReporter errorReporter) {
    boolean matchCommonPrefix = tryMatchCommonPrefix(closeTag, openTag);
    // TODO(user): Remove this once we support partial prefix matching.
    if (matchCommonPrefix && !closeTag.getBranches().isEmpty()) {
      matchCommonPrefix = false;
      errorReporter.report(
          closeTag.getSourceLocation(),
          UNEXPECTED_CLOSE_TAG_WITH_EXPECTATION,
          openTag.getStaticTagNameAsLowerCase());
    }
    if (!matchCommonPrefix) {
      errorReporter.report(openTag.getTagLocation(), OPEN_TAG_NOT_CLOSED);
    }
    return matchCommonPrefix;
  }

  /** Tries to find common prefix and report errors accordingly. */
  private static boolean tryMatchCommonPrefix(
      HtmlTagEntry openTag, HtmlTagEntry closeTag, ErrorReporter errorReporter) {
    Preconditions.checkArgument(openTag.hasTagName() != closeTag.hasTagName());
    return openTag.hasTagName()
        ? tryMatchCommonPrefix(openTag.getTagName(), closeTag, errorReporter)
        : tryMatchCommonPrefix(openTag, closeTag.getTagName(), errorReporter);
  }
}
