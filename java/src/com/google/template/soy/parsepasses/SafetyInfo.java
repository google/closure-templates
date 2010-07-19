/*
 * Copyright 2010 Google Inc.
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

package com.google.template.soy.parsepasses;

import com.google.common.base.Joiner;
import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.DataRefIndexNode;
import com.google.template.soy.exprtree.DataRefKeyNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.TemplateNode.SoyDocSafePath;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Helper class for InferSafePrintNodesVisitor to represent info about the safe data paths in some
 * corresponding Soy data. Can be used for internal or leaf data.
 *
 * Important: Mutable. Only for use by InferSafePrintNodesVisitor.
 *
 * Conceptually, each SafetyInfo object keeps track of a list of safe data paths, except that the
 * data is kept in tree form. Each SafetyInfo object is a node that contains info on:
 * + whether the data associated directly with this SafetyInfo is safe (i.e. data path ""),
 * + subdata that are safe (via references to other SafetyInfo objects).
 * In this way, SafetyInfo objects may form a tree that mirrors the Soy data tree, but only
 * including the portions that are safe.
 *
 * For example, if the data tree is
 *     root
 *       +--boo (safe)
 *       +--foo
 *       |    +--xxx (unsafe)
 *       |    +--yyy (safe)
 *       +--goo (unsafe)
 * then the safe data paths would be
 *     boo
 *     foo.yyy
 * and the corresponding SafetyInfo would be
 *     root (isSafe=false)
 *       +--boo (isSafe=true)
 *       +--foo (isSafe=false)
 *            +--yyy (isSafe=true)
 *
 * An additional feature is that if all subdata have the same safety info, then the key '*' can be
 * used to map to a common SafetyInfo object corresponding to subdata at any key (a feature usually
 * used for list data).
 *
 * @author Kai Huang
 */
class SafetyInfo {


  /** An instance that doesn't have any safe parts. */
  public static final SafetyInfo EMPTY_INSTANCE = new SafetyInfo();

  /** An instance appropriate for safe leaf data (this node is safe, but has no children). */
  public static final SafetyInfo SAFE_LEAF_INSTANCE;
  static {
    SafetyInfo tempSafetyInfo = new SafetyInfo();
    tempSafetyInfo.isSafe = true;
    SAFE_LEAF_INSTANCE = tempSafetyInfo;
  }


  /** Whether this node corresponds to safe data. */
  private boolean isSafe;

  /** SafetyInfo corresponding to subdata at any key (usually used for list data), or null if not
   *  applicable. Nonnull if and only if nonstarSubinfos is empty. */
  private SafetyInfo starSubinfo;

  /** Map from keys to the SafetyInfo objects corresponding to the subdata at each key. May not
   *  contain all data keys since some keys map to subdata without any safe parts. Nonempty if and
   *  only if starSubinfo is null. */
  private Map<String, SafetyInfo> nonstarSubinfos;


  private SafetyInfo() {
    this.isSafe = false;
    this.starSubinfo = null;
    this.nonstarSubinfos = Maps.newHashMap();
  }


  /**
   * Creates a SafetyInfo object corresponding to the data for a template, given the list of safe
   * paths found in the template's SoyDoc.
   */
  public static SafetyInfo createFromSoyDocSafePaths(List<SoyDocSafePath> soyDocSafePaths) {

    if (soyDocSafePaths == null || soyDocSafePaths.size() == 0) {
      return EMPTY_INSTANCE;
    }

    SafetyInfo dataSafetyInfo = new SafetyInfo();
    for (SoyDocSafePath soyDocSafePath : soyDocSafePaths) {
      dataSafetyInfo.addSafePathHelper(soyDocSafePath.path);
    }
    return dataSafetyInfo;
  }


  /**
   * Private helper for createForPublicTemplate().
   */
  private void addSafePathHelper(List<String> safePath) {

    if (safePath.size() == 0) {
      this.isSafe = true;
      return;
    }

    String firstKey = safePath.get(0);

    SafetyInfo subinfo;
    if (firstKey.equals("*")) {
      subinfo = starSubinfo;
      if (subinfo == null) {
        subinfo = new SafetyInfo();
        starSubinfo = subinfo;
      }
    } else {
      subinfo = nonstarSubinfos.get(firstKey);
      if (subinfo == null) {
        subinfo = new SafetyInfo();
        nonstarSubinfos.put(firstKey, subinfo);
      }
    }

    if (starSubinfo != null && nonstarSubinfos.size() > 0) {
      throw new SoySyntaxException(
          "Safe data path collision: must not have both '*' and non-'*' keys at same point.");
    }

    subinfo.addSafePathHelper(safePath.subList(1, safePath.size()));
  }


  /**
   * Creates a deep clone of the given SafetyInfo object.
   */
  public static SafetyInfo clone(SafetyInfo safetyInfo) {

    SafetyInfo clonedInfo = new SafetyInfo();
    clonedInfo.isSafe = safetyInfo.isSafe;
    if (safetyInfo.starSubinfo != null) {
      clonedInfo.starSubinfo = clone(safetyInfo.starSubinfo);
    } else {
      for (String key : safetyInfo.nonstarSubinfos.keySet()) {
        clonedInfo.nonstarSubinfos.put(key, clone(safetyInfo.nonstarSubinfos.get(key)));
      }
    }
    return clonedInfo;
  }


  /**
   * Merges the given SafetyInfo objects into a new SafetyInfo object. A data path is only safe in
   * the merged SafetyInfo if it is safe in all the input SafetyInfos.
   */
  public static SafetyInfo merge(Collection<SafetyInfo> safetyInfos) {

    // Special cases.
    if (safetyInfos.size() == 0) {
      return EMPTY_INSTANCE;
    }
    if (safetyInfos.size() == 1) {
      return clone(Iterators.getOnlyElement(safetyInfos.iterator()));
    }

    SafetyInfo mergedInfo = new SafetyInfo();

    // Determine the isSafe value on the mergedInfo.
    boolean mergedIsSafe = true;
    for (SafetyInfo safetyInfo : safetyInfos) {
      if (! safetyInfo.isSafe) {
        mergedIsSafe = false;
        break;
      }
    }
    mergedInfo.isSafe = mergedIsSafe;

    // Determine the merged keys at the current level.
    boolean isStarMergedKey = true;
    Set<String> nonstarMergedKeys = null;
    for (SafetyInfo safetyInfo : safetyInfos) {
      if (safetyInfo.starSubinfo != null) {
        continue;  // doesn't subtract from merged keys
      }
      if (isStarMergedKey) {
        isStarMergedKey = false;
        nonstarMergedKeys = Sets.newHashSet(safetyInfo.nonstarSubinfos.keySet());
      } else {
        nonstarMergedKeys =
            Sets.intersection(nonstarMergedKeys, safetyInfo.nonstarSubinfos.keySet());
      }
    }

    // For each merged key, merge subinfos and put the new merged subinfo.
    if (isStarMergedKey) {
      List<SafetyInfo> subinfosToMerge = Lists.newArrayList();
      for (SafetyInfo safetyInfo : safetyInfos) {
        subinfosToMerge.add(safetyInfo.starSubinfo);
      }
      mergedInfo.starSubinfo = merge(subinfosToMerge);
    } else {
      for (String mergedKey : nonstarMergedKeys) {
        List<SafetyInfo> subinfosToMerge = Lists.newArrayList();
        for (SafetyInfo safetyInfo : safetyInfos) {
          // Important: Must use getSubinfo() and not nonstarSubinfos.get() to retrieve subinfo
          // because some safetyInfos may have '*' key at this level while others have non-'*' key.
          subinfosToMerge.add(safetyInfo.getSubinfo(mergedKey));
        }
        mergedInfo.putSubinfo(mergedKey, merge(subinfosToMerge));
      }
    }

    return mergedInfo;
  }


  /**
   * Returns whether the data corresponding directly to this SafetyInfo object is safe.
   */
  public boolean isSafe() {
    return this.isSafe;
  }


  /**
   * Sets the SafetyInfo corresponding to the data for the given key. May replace existing info.
   */
  public void putSubinfo(String key, SafetyInfo subinfo) {

    // We don't expect this method to be used with '*' key.
    checkArgument(! key.equals("*"));

    nonstarSubinfos.put(key, subinfo);

    // If there is starSubinfo, we have to clear it.
    if (starSubinfo != null) {
      starSubinfo = null;
    }
  }


  /**
   * Gets the SafetyInfo corresponding to the data for the given key.
   */
  public SafetyInfo getSubinfo(String key) {

    // We don't expect this method to be used with '*' key.
    checkArgument(! key.equals("*"));

    if (starSubinfo != null) {
      return starSubinfo;
    } else {
      return (nonstarSubinfos.containsKey(key)) ? nonstarSubinfos.get(key) : EMPTY_INSTANCE;
    }
  }


  /**
   * Gets the SafetyInfo corresponding to the data at the given reference path.
   */
  public SafetyInfo getSubinfo(List<ExprNode> keyNodes) {

    if (keyNodes.size() == 0) {
      return this;
    }

    if (starSubinfo != null) {
      return starSubinfo.getSubinfo(keyNodes.subList(1, keyNodes.size()));
    }

    // ------ Regular case. ------
    ExprNode firstKeyNode = keyNodes.get(0);
    String firstKey;
    if (firstKeyNode instanceof DataRefKeyNode) {
      firstKey = ((DataRefKeyNode) firstKeyNode).getKey();
    } else if (firstKeyNode instanceof DataRefIndexNode) {
      firstKey = Integer.toString(((DataRefIndexNode) firstKeyNode).getIndex());
    } else {
      // firstKeyNode is an ExprNode.
      if (firstKeyNode instanceof StringNode) {
        firstKey = ((StringNode) firstKeyNode).getValue();
      } else if (firstKeyNode instanceof IntegerNode) {
        firstKey = Integer.toString(((IntegerNode) firstKeyNode).getValue());
      } else {
        firstKey = null;
      }
    }

    if (firstKey != null && nonstarSubinfos.containsKey(firstKey)) {
      SafetyInfo subinfo = nonstarSubinfos.get(firstKey);
      return subinfo.getSubinfo(keyNodes.subList(1, keyNodes.size()));
    } else {
      // Assume unsafe.
      return EMPTY_INSTANCE;
    }
  }


  /**
   * Returns a string representation of the list of safe paths, e.g.
   *     ["", "boo.xxx", "foo"]
   */
  @Override public String toString() {

    List<List<String>> safePaths = getSafePathsHelper();

    List<String> safePathStrs = Lists.newArrayListWithCapacity(safePaths.size());
    for (List<String> safePath : safePaths) {
      safePathStrs.add(Joiner.on('.').join(safePath));
    }
    Collections.sort(safePathStrs);
    return safePathStrs.toString();
  }


  /**
   * Private helper for toString().
   * Note: Result is not sorted.
   */
  private List<List<String>> getSafePathsHelper() {

    List<List<String>> safePaths = Lists.newArrayList();

    if (this.isSafe) {
      safePaths.add(ImmutableList.of(""));
    }

    if (starSubinfo != null) {
      for (List<String> safeSubpath : starSubinfo.getSafePathsHelper()) {
        List<String> safePath = Lists.newArrayListWithCapacity(safeSubpath.size() + 1);
        safePath.add("*");
        safePath.addAll(safeSubpath);
        safePaths.add(safePath);
      }
    } else {
      for (String key : nonstarSubinfos.keySet()) {
        SafetyInfo subinfo = nonstarSubinfos.get(key);
        for (List<String> safeSubpath : subinfo.getSafePathsHelper()) {
          List<String> safePath = Lists.newArrayListWithCapacity(safeSubpath.size() + 1);
          safePath.add(key);
          safePath.addAll(safeSubpath);
          safePaths.add(safePath);
        }
      }
    }

    return safePaths;
  }


  /**
   * Checks whether this SafetyInfo contains each of the given SoyDoc safe data paths, and returns
   * the list of SoyDoc safe paths that this SafetyInfo doesn't contain. If this SafetyInfo contains
   * all the given safe paths, then the returned list will be empty.
   */
  public List<SoyDocSafePath> findUnmatchedSoyDocSafePaths(List<SoyDocSafePath> soyDocSafePaths) {

    List<SoyDocSafePath> unmatchedSoyDocSafePaths = Lists.newLinkedList();
    for (SoyDocSafePath soyDocSafePath : soyDocSafePaths) {
      if (! containsSafePath(soyDocSafePath.path)) {
        unmatchedSoyDocSafePaths.add(soyDocSafePath);
      }
    }
    return unmatchedSoyDocSafePaths;
  }


  /**
   * Private helper for containsAllSoyDocSafePaths() to check whether this SafetyInfo contains a
   * given safe path.
   */
  private boolean containsSafePath(List<String> safePath) {

    if (safePath.size() == 0) {
      return isSafe;
    }

    if (starSubinfo != null) {
      return starSubinfo.containsSafePath(safePath.subList(1, safePath.size()));
    } else {
      // Note: safePath's first key might be '*', but this code still works for that case.
      SafetyInfo subinfo = nonstarSubinfos.get(safePath.get(0));
      return subinfo != null && subinfo.containsSafePath(safePath.subList(1, safePath.size()));
    }
  }

}
