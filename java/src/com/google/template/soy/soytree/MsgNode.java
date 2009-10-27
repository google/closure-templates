/*
 * Copyright 2008 Google Inc.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.SoyNode.SoyStatementNode;

import java.util.List;
import java.util.Map;


/**
 * Node representing a 'msg' statement/block. Every child of a MsgNode must either be a RawTextNode
 * or a MsgPlaceholderNode.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class MsgNode extends AbstractParentSoyCommandNode<SoyNode> implements SoyStatementNode {


  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("msg",
          new Attribute("meaning", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("desc", Attribute.ALLOW_ALL_VALUES,
                        Attribute.NO_DEFAULT_VALUE_BECAUSE_REQUIRED),
          new Attribute("hidden", Attribute.BOOLEAN_VALUES, "false"));


  /** We don't support different content types. Always provide this value to the TC. */
  private static final String DEFAULT_CONTENT_TYPE = "text/html";


  /** The meaning string if set, otherwise null (usually null). */
  private final String meaning;

  /** The description string for translators. */
  private final String desc;

  /** Whether the message should be added as 'hidden' in the TC. */
  private final boolean isHidden;

  /** The generated map from placeholder name to representative node, or null if not generated. */
  private Map<String, MsgPlaceholderNode> phNameToRepNodeMap = null;

  /** The generated map from placeholder node to placeholder name, or null if not generated. */
  private Map<MsgPlaceholderNode, String> phNodeToNameMap = null;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public MsgNode(String id, String commandText) throws SoySyntaxException {
    super(id, "msg", commandText);

    Map<String, String> attributes = ATTRIBUTES_PARSER.parse(commandText);
    meaning = attributes.get("meaning");
    desc = attributes.get("desc");
    isHidden = attributes.get("hidden").equals("true");
  }


  /** Returns the meaning string if set, otherwise null (usually null). */
  public String getMeaning() {
    return meaning;
  }

  /** Returns the description string for translators. */
  public String getDesc() {
    return desc;
  }

  /** Returns whether the message should be added as 'hidden' in the TC. */
  public boolean isHidden() {
    return isHidden;
  }

  /** Returns the content type for the TC. */
  public String getContentType() {
    return DEFAULT_CONTENT_TYPE;
  }


  /**
   * Gets the representative placeholder node for a given placeholder name.
   * @param placeholderName The placeholder name.
   * @return The representative placeholder node for the given placeholder name.
   */
  public MsgPlaceholderNode getPlaceholderNode(String placeholderName) {

    if (phNameToRepNodeMap == null) {
      genPlaceholderNamesHelper();
    }
    return phNameToRepNodeMap.get(placeholderName);
  }


  /**
   * Gets the placeholder name for a given placeholder node.
   * @param placeholderNode The placeholder node.
   * @return The placeholder name for the given placeholder node.
   */
  public String getPlaceholderName(MsgPlaceholderNode placeholderNode) {

    if (phNodeToNameMap == null) {
      genPlaceholderNamesHelper();
    }
    return phNodeToNameMap.get(placeholderNode);
  }


  /**
   * Private helper to generate the placeholder names (with number suffixes as necessary).
   */
  private void genPlaceholderNamesHelper() {

    // ------ Step 1: Determine representative nodes and build preliminary map ------
    //
    // Sepcifically, we are building basePhNameToRepNodeMap and nonRepNodeToRepNodeMap.
    //
    // If there are multiple MsgPlaceholderNodes in the message that are exactly the same, then the
    // first such node encountered becomes the "representative node" for the group ("RepNode" in
    // variable names). The rest of the same nodes are non-representative ("nonRepNode").
    //
    // The basePhNameToRepNodeMap is a map from base placeholder name to the list of
    // representative nodes (not exactly same) that all generate the same placeholder name. If we
    // encounter a non-representative node, then we insert it into nonRepNodeToRepNodeMap, mapping
    // it to its corresponding representative node.
    //
    // The basePhNameToRepNodeMap is preliminary because some of the final placeholder names will
    // be the base placeholder names plus some unique suffix.

    Map<String, List<MsgPlaceholderNode>> basePhNameToRepNodeMap = Maps.newHashMap();
    Map<MsgPlaceholderNode, MsgPlaceholderNode> nonRepNodeToRepNodeMap = Maps.newHashMap();

    for (SoyNode child : getChildren()) {
      if (!(child instanceof MsgPlaceholderNode)) {
        continue;
      }
      MsgPlaceholderNode phNode = (MsgPlaceholderNode) child;

      String basePhName = phNode.genBasePlaceholderName();
      if (!basePhNameToRepNodeMap.containsKey(basePhName)) {
        // Case 1: First occurrence of this base placeholder name.
        basePhNameToRepNodeMap.put(basePhName, Lists.newArrayList(phNode));
      } else {
        List<MsgPlaceholderNode> nodesWithSameBasePhName = basePhNameToRepNodeMap.get(basePhName);
        boolean isNewPlaceholder = true;
        for (MsgPlaceholderNode other : nodesWithSameBasePhName) {
          if (phNode.isSamePlaceholderAs(other)) {
            // Case 2: Exactly same as another node we've seen.
            nonRepNodeToRepNodeMap.put(phNode, other);
            isNewPlaceholder = false;
            break;
          }
        }
        if (isNewPlaceholder) {
          // Case 3: New representative node that has the same base placeholder name as another
          // node we've seen, but is not the same placeholder.
          nodesWithSameBasePhName.add(phNode);
        }
      }
    }

    // ------ Step 2: Build final map of placeholder name to representative node ------
    //
    // The final map of phNameToRepNodeMap must be a one-to-one mapping. If a base placeholder name
    // only maps to one representative node, then we simply put that same mapping into the final
    // map. But if a base placeholder name maps to multiple nodes, we must append number suffixes
    // ("_1", "_2", etc) to make the placeholder names unique.
    //
    // Note: We must be careful that, while appending number suffixes, we don't generate a new
    // placeholder name that is the same as an existing base placeholder name.

    phNameToRepNodeMap = Maps.newHashMap();

    for (Map.Entry<String, List<MsgPlaceholderNode>> entry : basePhNameToRepNodeMap.entrySet()) {
      String basePhName = entry.getKey();
      List<MsgPlaceholderNode> nodesWithSameBasePhName = entry.getValue();

      if (nodesWithSameBasePhName.size() == 1) {
        // Case 1: Only one node generates this base placeholder name. Simple.
        phNameToRepNodeMap.put(basePhName, nodesWithSameBasePhName.get(0));
      } else {
        // Case 2: Multiple nodes generate this base placeholder name. Need number suffixes.
        int nextSuffix = 1;
        for (MsgPlaceholderNode repNode : nodesWithSameBasePhName) {
          String newPhName;
          do {
            newPhName = basePhName + "_" + nextSuffix;
            ++nextSuffix;
          } while (basePhNameToRepNodeMap.containsKey(newPhName));
          phNameToRepNodeMap.put(newPhName, repNode);
        }
      }
    }

    // ------ Step 3: Build map of every node to its placeholder name ------

    phNodeToNameMap = Maps.newHashMap();

    // Reverse the map of placeholders to representative nodes.
    for (Map.Entry<String, MsgPlaceholderNode> entry : phNameToRepNodeMap.entrySet()) {
      phNodeToNameMap.put(entry.getValue(), entry.getKey());
    }

    // Add mappings for the non-representative nodes.
    for (Map.Entry<MsgPlaceholderNode, MsgPlaceholderNode> entry :
         nonRepNodeToRepNodeMap.entrySet()) {
      phNodeToNameMap.put(entry.getKey(), phNodeToNameMap.get(entry.getValue()));
    }
  }

}
