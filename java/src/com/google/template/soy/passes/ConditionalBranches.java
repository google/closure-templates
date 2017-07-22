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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.soytree.TagName;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * An abstract representation of an {@code IfNode} or a {@code SwitchNode} in a Soy template.
 *
 * <p>This class is used for {@code StrictHtmlValidationPass} so that we can validate if a template
 * with control flow is a valid HTML.
 */
final class ConditionalBranches {

  /**
   * Inner class that represents a conditional branch. Note that it is possible to have nested
   * control flows, and each {@HtmlTagEntry} might contain another {@code CondtionalBranch}.
   */
  @AutoValue
  abstract static class ConditionalBranch {
    static ConditionalBranch create(Condition condition, ArrayDeque<HtmlTagEntry> deque) {
      return new AutoValue_ConditionalBranches_ConditionalBranch(condition, deque);
    }

    abstract Condition condition();

    abstract ArrayDeque<HtmlTagEntry> deque();
  }

  private final List<ConditionalBranch> branches = new ArrayList<>();

  ConditionalBranches() {}

  ConditionalBranches(ConditionalBranches branches) {
    this();
    addAll(branches);
  }

  void clear() {
    branches.clear();
  }

  List<ConditionalBranch> getBranches() {
    removeEmptyDeque();
    return branches;
  }

  /** Return source location for the first entry in all branches. */
  SourceLocation getSourceLocation() {
    removeEmptyDeque();
    if (branches.isEmpty()) {
      return SourceLocation.UNKNOWN;
    }
    ConditionalBranch branch = branches.get(0);
    // removeEmptyDeque guarantees that the deque is not empty.
    return branch.deque().peekFirst().getSourceLocation();
  }

  @Override
  public String toString() {
    return branches.toString();
  }

  private void removeEmptyDeque() {
    // Remove the empty deque if necessary.
    for (Iterator<ConditionalBranch> it = branches.iterator(); it.hasNext(); ) {
      ConditionalBranch branch = it.next();
      // Recursively remove empty branches within each deque.
      for (Iterator<HtmlTagEntry> it2 = branch.deque().iterator(); it2.hasNext(); ) {
        HtmlTagEntry entry = it2.next();
        if (entry.getBranches() != null) {
          if (entry.getBranches().isEmpty()) {
            it2.remove();
          }
        }
      }
      if (branch.deque().isEmpty()) {
        it.remove();
      }
    }
  }

  /**
   * Checks if the list of branches contains a "default" conditional branch (i.e., a {@code
   * IfElseNode} or a {@code SwitchDefaultNode} at the end of the list).
   *
   * <p>If this is true, we will try to match {@code TagName} for all branches.
   */
  private boolean hasDefaultCond() {
    if (branches.isEmpty()) {
      return false;
    }
    return Iterables.getLast(branches).condition().isDefaultCond();
  }

  /** Checks if all branches contain the given {@TagName} at the head of their open stacks. */
  boolean hasCommonPrefix(TagName tag) {
    if (!hasDefaultCond()) {
      return false;
    }
    for (ConditionalBranch branch : branches) {
      if (branch.deque().isEmpty()) {
        return false;
      }
      HtmlTagEntry entry = branch.deque().peek();
      // Remove optional tags that do not match the desired tag.
      while (entry.hasTagName()
          && !entry.getTagName().equals(tag)
          && TagName.checkOptionalTagShouldBePopped(entry.getTagName(), tag)) {
        branch.deque().poll();
        entry = branch.deque().peek();
        if (entry == null) {
          return false;
        }
      }
      if (entry.hasTagName()) {
        if (!entry.getTagName().equals(tag)) {
          return false;
        }
      } else {
        // Recursively search
        if (!entry.getBranches().hasCommonPrefix(tag)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Remove a common {@code TagName} from all branches. Note that we assume that each branch
   * contains the {@code TagName}. You will need to explicitly call {@code hasCommonPrefix} before
   * calling this method.
   */
  void popAllBranches() {
    for (ConditionalBranch branch : branches) {
      HtmlTagEntry entry = branch.deque().peek();
      if (entry.hasTagName()) {
        branch.deque().pop();
      } else {
        entry.getBranches().popAllBranches();
      }
    }
    removeEmptyDeque();
  }

  /**
   * Removes optional tags from all branches. This method should only be called for removing the
   * optional open tags from the top of the open tag stack.
   */
  void popOptionalTags(@Nullable TagName closeTag) {
    for (ConditionalBranch branch : branches) {
      HtmlTagEntry.popOptionalTags(branch.deque(), closeTag);
    }
    removeEmptyDeque();
  }

  boolean isEmpty() {
    removeEmptyDeque();
    return branches.isEmpty();
  }

  void add(Condition condition, ArrayDeque<HtmlTagEntry> deque) {
    checkArgument(
        !condition.equals(Condition.getEmptyCondition()),
        "Cannot add an empty condition into a branch. This should never happen.");
    checkState(
        !hasDefaultCond(),
        "Cannot add a new branch since the current ConditionalBranches already contains "
            + "a default condition.");
    ArrayDeque<HtmlTagEntry> newDeque = new ArrayDeque<>();
    newDeque.addAll(deque);
    branches.add(ConditionalBranch.create(condition.copy(), newDeque));
  }

  void addAll(ConditionalBranches branches) {
    checkState(
        !hasDefaultCond(),
        "Cannot add a new branch since the current ConditionalBranches already contain "
            + "a default condition.");
    // Always make deep copy so that the branches will not be accidentally changed.
    for (ConditionalBranch branch : branches.branches) {
      ArrayDeque<HtmlTagEntry> deque = branch.deque();
      ArrayDeque<HtmlTagEntry> newDeque = new ArrayDeque<>();
      newDeque.addAll(deque);
      this.branches.add(ConditionalBranch.create(branch.condition().copy(), newDeque));
    }
  }
}
