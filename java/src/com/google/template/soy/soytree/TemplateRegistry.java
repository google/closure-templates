/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.TemplateDelegateNode.DelTemplateKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.Immutable;


/**
 * A registry or index of all templates in a Soy tree.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class TemplateRegistry {


  /**
   * Represents a set of delegate templates with the same key (name and variant) and same priority.
   * <p> Note: Per delegate rules, at most one of the templates in each division may be active at
   * render time.
   *
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  @Immutable
  public static class DelegateTemplateDivision {

    /** The priority value for the delegate templates in this division. */
    public final int delPriority;
    /** Map of all delegate templates in this division, by delegate package name. */
    public final Map<String, TemplateDelegateNode> delPackageNameToDelTemplateMap;

    public DelegateTemplateDivision(
        int delPriority, Map<String, TemplateDelegateNode> delPackageNameToDelTemplateMap) {
      this.delPriority = delPriority;
      this.delPackageNameToDelTemplateMap =
          Collections.unmodifiableMap(Maps.newHashMap(delPackageNameToDelTemplateMap));
    }
  }


  /**
   * Exception thrown when there's no unique highest-priority active delegate template at render
   * time.
   *
   * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
   */
  public static class DelegateTemplateConflictException extends Exception {

    public DelegateTemplateConflictException(String errorMsg) {
      super(errorMsg);
    }
  }


  /** Map from basic template name to node. */
  private final Map<String, TemplateBasicNode> basicTemplatesMap;

  /** Map from delegate template name to set of keys. */
  private final Map<String, Set<DelTemplateKey>> delTemplateNameToKeysMap;

  /** Map from delegate template key to list of DelegateTemplateDivision, where the list is in
   *  descending priority order. */
  private final Map<DelTemplateKey, List<DelegateTemplateDivision>> delTemplatesMap;


  /**
   * Constructor.
   * @param soyTree The Soy tree from which to build a template registry.
   */
  public TemplateRegistry(SoyFileSetNode soyTree) {

    // ------ Iterate through all templates to collect data. ------

    Map<String, TemplateBasicNode> tempBasicTemplatesMap = Maps.newHashMap();
    Map<String, Set<DelTemplateKey>> tempDelTemplateNameToKeysMap = Maps.newHashMap();
    Map<DelTemplateKey, Map<Integer, Map<String, TemplateDelegateNode>>> tempDelTemplatesMap =
        Maps.newHashMap();

    for (SoyFileNode soyFile : soyTree.getChildren()) {
      for (TemplateNode template : soyFile.getChildren()) {

        if (template instanceof TemplateBasicNode) {
          // Case 1: Basic template.
          tempBasicTemplatesMap.put(template.getTemplateName(), (TemplateBasicNode) template);

        } else {
          // Case 2: Delegate template.
          TemplateDelegateNode delTemplate = (TemplateDelegateNode) template;
          DelTemplateKey delTemplateKey = delTemplate.getDelTemplateKey();

          // Add to tempDelTemplateNameToKeysMap.
          String delTemplateName = delTemplate.getDelTemplateName();
          Set<DelTemplateKey> keys = tempDelTemplateNameToKeysMap.get(delTemplateName);
          if (keys == null) {
            keys = Sets.newLinkedHashSet();
            tempDelTemplateNameToKeysMap.put(delTemplateName, keys);
          }
          keys.add(delTemplateKey);

          // Add to tempDelTemplatesMap.
          int delPriority = delTemplate.getDelPriority();
          String delPackageName = delTemplate.getDelPackageName();

          Map<Integer, Map<String, TemplateDelegateNode>> tempDivisions =
              tempDelTemplatesMap.get(delTemplateKey);
          if (tempDivisions == null) {
            tempDivisions = Maps.newHashMap();
            tempDelTemplatesMap.put(delTemplateKey, tempDivisions);
          }

          Map<String, TemplateDelegateNode> tempDivision = tempDivisions.get(delPriority);
          if (tempDivision == null) {
            tempDivision = Maps.newHashMap();
            tempDivisions.put(delPriority, tempDivision);
          }

          if (tempDivision.containsKey(delPackageName)) {
            TemplateDelegateNode prevTemplate = tempDivision.get(delPackageName);
            String prevTemplateFilePath =
                prevTemplate.getNearestAncestor(SoyFileNode.class).getFilePath();
            String currTemplateFilePath =
                delTemplate.getNearestAncestor(SoyFileNode.class).getFilePath();
            String errorMsgPrefix = (delPackageName == null) ?
                "Found two default implementations" :
                "Found two implementations in the same delegate package";
            if (currTemplateFilePath != null && currTemplateFilePath.equals(prevTemplateFilePath)) {
              throw SoySyntaxException.createWithoutMetaInfo(String.format(
                  errorMsgPrefix + " for delegate template '%s', both in the file %s.",
                  delTemplateKey, currTemplateFilePath));
            } else {
              throw SoySyntaxException.createWithoutMetaInfo(String.format(
                  errorMsgPrefix + " for delegate template '%s', in files %s and %s.",
                  delTemplateKey, prevTemplateFilePath, currTemplateFilePath));
            }
          }
          tempDivision.put(delPackageName, delTemplate);
        }
      }
    }

    // ------ Build the final data structures. ------

    basicTemplatesMap = Collections.unmodifiableMap(tempBasicTemplatesMap);

    ImmutableMap.Builder<DelTemplateKey, List<DelegateTemplateDivision>> delTemplatesMapBuilder =
        ImmutableMap.builder();

    for (DelTemplateKey delTemplateKey : tempDelTemplatesMap.keySet()) {
      Map<Integer, Map<String, TemplateDelegateNode>> tempDivisions =
          tempDelTemplatesMap.get(delTemplateKey);

      ImmutableList.Builder<DelegateTemplateDivision> divisionsBuilder = ImmutableList.builder();

      // Note: List should be in decreasing priority order.
      for (int priority = TemplateNode.MAX_PRIORITY; priority >= 0; priority--) {
        if (! tempDivisions.containsKey(priority)) {
          continue;
        }
        Map<String, TemplateDelegateNode> tempDivision = tempDivisions.get(priority);
        DelegateTemplateDivision division = new DelegateTemplateDivision(priority, tempDivision);
        divisionsBuilder.add(division);
      }

      delTemplatesMapBuilder.put(delTemplateKey, divisionsBuilder.build());
    }

    delTemplatesMap = delTemplatesMapBuilder.build();

    delTemplateNameToKeysMap = Collections.unmodifiableMap(tempDelTemplateNameToKeysMap);
  }


  /**
   * Returns a map from basic template name to node.
   */
  public Map<String, TemplateBasicNode> getBasicTemplatesMap() {
    return basicTemplatesMap;
  }


  /**
   * Retrieves a basic template given the template name.
   * @param templateName The basic template name to retrieve.
   * @return The corresponding basic template, or null if the template name is not defined.
   */
  public TemplateBasicNode getBasicTemplate(String templateName) {
    return basicTemplatesMap.get(templateName);
  }


  /**
   * Returns a map from delegate template name to set of keys.
   */
  public Map<String, Set<DelTemplateKey>> getDelTemplateNameToKeysMap() {
    return delTemplateNameToKeysMap;
  }


  /**
   * Returns a map from delegate template key (name and variant) to list of
   * {@code DelegateTemplateDivision}s, where each list is sorted in descending priority order.
   */
  public Map<DelTemplateKey, List<DelegateTemplateDivision>> getDelTemplatesMap() {
    return delTemplatesMap;
  }


  /**
   * Retrieves the set of key (name and variant) strings for all variants of a given delegate
   * template name.
   * @param delTemplateName The delegate template name to retrieve.
   * @return The set of keys for all variants.
   */
  public Set<DelTemplateKey> getDelTemplateKeysForAllVariants(String delTemplateName) {
    return delTemplateNameToKeysMap.get(delTemplateName);
  }


  /**
   * Retrieves the set of {@code DelegateTemplateDivision}s for all variants of a given a delegate
   * template name.
   * @param delTemplateName The delegate template name to retrieve.
   * @return The set of {@code DelegateTemplateDivision}s for all variants.
   */
  public Set<DelegateTemplateDivision> getDelTemplateDivisionsForAllVariants(
      String delTemplateName) {

    Set<DelTemplateKey> keysForAllVariants = delTemplateNameToKeysMap.get(delTemplateName);
    if (keysForAllVariants == null) {
      return null;
    }

    Set<DelegateTemplateDivision> divisionsForAllVariants = Sets.newLinkedHashSet();
    for (DelTemplateKey delTemplateKey : keysForAllVariants) {
      divisionsForAllVariants.addAll(delTemplatesMap.get(delTemplateKey));
    }
    return divisionsForAllVariants;
  }


  /**
   * Retrieves the list of {@code DelegateTemplateDivision}s (sorted in descencing priority order)
   * given a delegate template key (name and variant).
   * @param delTemplateKey The delegate template key (name and variant) to retrieve.
   * @return The corresponding list of {@code DelegateTemplateDivision}s (sorted in descencing
   *     priority order), or null if the delegate template key is not implemented.
   */
  public List<DelegateTemplateDivision> getSortedDelTemplateDivisions(
      DelTemplateKey delTemplateKey) {
    return delTemplatesMap.get(delTemplateKey);
  }


  /**
   * Selects a delegate template based on the rendering rules, given the delegate template key (name
   * and variant) and the set of active delegate package names.
   * @param delTemplateKey The delegate template key (name and variant) to select an implementation
   *     for.
   * @param activeDelPackageNames The set of active delegate package names.
   * @return The selected delegate template, or null if there are no active implementations.
   * @throws DelegateTemplateConflictException If there are two or more active implementations with
   *     equal priority (unable to select one over the other).
   */
  public TemplateDelegateNode selectDelTemplate(
      DelTemplateKey delTemplateKey, Set<String> activeDelPackageNames)
      throws DelegateTemplateConflictException {

    List<DelegateTemplateDivision> divisions = delTemplatesMap.get(delTemplateKey);
    if (divisions == null && delTemplateKey.variant.length() > 0) {
      // Fallback to empty variant.
      divisions = delTemplatesMap.get(new DelTemplateKey(delTemplateKey.name, ""));
    }
    if (divisions == null) {
      return null;
    }

    for (DelegateTemplateDivision division : divisions) {

      TemplateDelegateNode delTemplate = null;

      for (String delPackageName : division.delPackageNameToDelTemplateMap.keySet()) {
        if (delPackageName != null && ! activeDelPackageNames.contains(delPackageName)) {
          continue;
        }

        if (delTemplate != null) {
          throw new DelegateTemplateConflictException(String.format(
              "For delegate template '%s', found two active implementations with equal" +
                  " priority in delegate packages '%s' and '%s'.",
              delTemplateKey, delTemplate.getDelPackageName(), delPackageName));
        }
        delTemplate = division.delPackageNameToDelTemplateMap.get(delPackageName);
      }

      if (delTemplate != null) {
        return delTemplate;
      }
    }

    return null;
  }

}
