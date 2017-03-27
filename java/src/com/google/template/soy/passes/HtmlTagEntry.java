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
      SoyErrorKind.of("Unexpected HTML close tag. Expected: ''{0}''.");
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
   */
  public static void tryMatchOrError(
      ArrayDeque<HtmlTagEntry> openStack,
      ArrayDeque<HtmlTagEntry> closeQueue,
      ErrorReporter errorReporter) {
    while (!openStack.isEmpty() && !closeQueue.isEmpty()) {
      if (!matchOrError(openStack.pollFirst(), closeQueue.pollFirst(), errorReporter)) {
        return;
      }
    }
  }

  /**
   * A helper method that matches a list of open tags and a list of close tags. We need to compare
   * the last open tag and the first close tag one by one.
   */
  public static boolean matchOrError(
      ArrayDeque<HtmlTagEntry> openStack,
      ArrayDeque<HtmlTagEntry> closeQueue,
      ErrorReporter errorReporter) {
    while (!openStack.isEmpty() && !closeQueue.isEmpty()) {
      if (!matchOrError(openStack.pop(), closeQueue.poll(), errorReporter)) {
        return false;
      }
    }
    if (!openStack.isEmpty()) {
      errorReporter.report(openStack.peek().getSourceLocation(), OPEN_TAG_NOT_CLOSED);
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
  public static boolean matchOrError(
      TagName openTag, TagName closeTag, ErrorReporter errorReporter) {
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
            openTag.getStaticTagNameAsLowerCase().get());
      } else {
        errorReporter.report(closeTag.getTagLocation(), MISMATCH_DYNAMIC_TAG);
      }
      return false;
    }
    return true;
  }

  /** A helper method that compare two {@code HtmlTagEntry}s. */
  public static boolean matchOrError(
      HtmlTagEntry openTag, HtmlTagEntry closeTag, ErrorReporter errorReporter) {
    if (openTag.hasTagName() != closeTag.hasTagName()) {
      boolean matchCommonPrefix = false;
      // Try to match common prefixes if possible.
      if (openTag.hasTagName()) {
        TagName openTagName = openTag.getTagName();
        if (closeTag.getBranches().hasCommonPrefix(openTagName)) {
          closeTag.getBranches().popAllBranches();
          matchCommonPrefix = true;
          // TODO(user): In this case, it is still possible that these remaining elements can
          // be matched by another condition flow later in the template. However, is it really
          // worthy to support this use case?
          if (!closeTag.getBranches().isEmpty()) {
            matchCommonPrefix = false;
            errorReporter.report(
                closeTag.getSourceLocation(),
                UNEXPECTED_CLOSE_TAG_WITH_EXPECTATION,
                openTagName.getStaticTagNameAsLowerCase().get());
          }
        } else {
          errorReporter.report(openTagName.getTagLocation(), OPEN_TAG_NOT_CLOSED);
        }
      } else {
        TagName closeTagName = closeTag.getTagName();
        if (openTag.getBranches().hasCommonPrefix(closeTagName)) {
          openTag.getBranches().popAllBranches();
          matchCommonPrefix = true;
          // TODO(user): In this case, it is still possible that these remaining elements can
          // be matched by another condition flow later in the template. However, is it really
          // worthy to support this use case?
          if (!openTag.getBranches().isEmpty()) {
            matchCommonPrefix = false;
            errorReporter.report(openTag.getSourceLocation(), OPEN_TAG_NOT_CLOSED);
          }
        } else {
          errorReporter.report(closeTagName.getTagLocation(), UNEXPECTED_CLOSE_TAG);
        }
      }
      return matchCommonPrefix;
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
        if (openStack.size() != closeQueue.size()) {
          errorReporter.report(location, MISMATCH_TAG);
          return false;
        }
        while (!openStack.isEmpty() && !closeQueue.isEmpty()) {
          // Recursively compare
          if (!matchOrError(openStack.pop(), closeQueue.poll(), errorReporter)) {
            // We have already report an error recursively, so do not need to report again here.
            return false;
          }
        }
      }
      return true;
    }
  }
}
