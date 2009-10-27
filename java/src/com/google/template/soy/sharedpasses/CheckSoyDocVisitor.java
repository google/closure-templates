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

package com.google.template.soy.sharedpasses;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.DataRefKeyNode;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.sharedpasses.FindIndirectParamsVisitor.IndirectParamsInfo;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentExprHolderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoytreeUtils;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateNode.SoyDocParam;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Visitor for checking that in each template, the parameters declared in the SoyDoc match the data
 * keys referenced in the template.
 *
 * <p> Also checks for unnecessary usages of {@code hasData()}.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Precondition: All template and callee names should be full names (i.e. you must execute
 * {@code PrependNamespacesVisitor} before executing this visitor).
 *
 * <p> Note this visitor only works for code in Soy V2 syntax.
 *
 * <p> {@link #exec} should be called on a full parse tree. There is no return value. However, a
 * {@code SoySyntaxException} is thrown if the parameters declared in some template's SoyDoc do not
 * match the data keys referenced in that template.
 *
 * @author Kai Huang
 */
public class CheckSoyDocVisitor extends AbstractSoyNodeVisitor<Void> {


  /** Whether the parse tree is guaranteed to all be in V2 syntax. */
  private final boolean isTreeAllV2;

  /** Map from template name to TemplateNode used by FindTransitiveCalleesVisitor. */
  private Map<String, TemplateNode> templateNameToNodeMap;

  /** Whether the current file specifies optional parameters "{@code @param?}" (during pass). */
  private boolean currFileHasOptParams;

  /** Whether the current template uses the function {@code hasData()} (during pass). */
  private boolean usesFunctionHasData;

  /** The data keys (not local vars) referenced in the current template (collected during pass). */
  private Set<String> dataKeys;

  /** Stack of frames containing sets of local vars currently defined (during pass). */
  private Deque<Set<String>> localVarFrames;

  /** The GetDataKeysInExprVisitor to use for expressions in the current template (during pass). */
  private GetDataKeysInExprVisitor getDataKeysInExprVisitor;


  /**
   * @param isTreeAllV2 Whether the parse tree is guaranteed to all be in V2 syntax.
   */
  public CheckSoyDocVisitor(boolean isTreeAllV2) {
    this.isTreeAllV2 = isTreeAllV2;
  }


  @Override protected void setup() {
    currFileHasOptParams = false;
    usesFunctionHasData = false;
    dataKeys = null;
    localVarFrames = null;
    getDataKeysInExprVisitor = null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  /**
   * {@inheritDoc}
   * @throws SoySyntaxException If the parameters declared in some template's SoyDoc do not match
   *     the data keys referenced in that template.
   */
  @Override protected void visitInternal(SoyFileSetNode node) {

    // Build templateNameToNodeMap.
    templateNameToNodeMap = Maps.newHashMap();
    for (SoyFileNode soyFile : node.getChildren()) {
      for (TemplateNode template : soyFile.getChildren()) {
        templateNameToNodeMap.put(template.getTemplateName(), template);
      }
    }

    // Run pass only on the Soy files that are all in V2 syntax. 
    for (SoyFileNode soyFile : node.getChildren()) {
      // First determine if Soy file is all in V2 syntax.
      boolean isFileAllV2;
      if (isTreeAllV2) {
        isFileAllV2 = true;
      } else {
        try {
          (new AssertSyntaxVersionV2Visitor()).exec(soyFile);
          isFileAllV2 = true;
        } catch (SoySyntaxException sse) {
          isFileAllV2 = false;
        }
      }
      // Run pass on Soy file if it is all in V2 syntax.
      if (isFileAllV2) {
        visit(soyFile);
      }
    }
  }


  /**
   * {@inheritDoc}
   * @throws SoySyntaxException If the parameters declared in some template's SoyDoc do not match
   *     the data keys referenced in that template.
   */
  @Override protected void visitInternal(SoyFileNode node) {

    // Set currFileHasOptParams to true if this file uses '@param?' anywhere.
    currFileHasOptParams = false;
    OUTER: for (TemplateNode template : node.getChildren()) {
      INNER: for (SoyDocParam param : template.getSoyDocParams()) {
        if (!param.isRequired) {
          // Found an optional param.
          currFileHasOptParams = true;
          break OUTER;
        }
      }
    }

    visitChildren(node);
  }


  /**
   * {@inheritDoc}
   * @throws SoySyntaxException If the parameters declared in some template's SoyDoc do not match
   *     the data keys referenced in that template.
   */
  @Override protected void visitInternal(TemplateNode node) {

    usesFunctionHasData = false;
    dataKeys = Sets.newHashSet();
    localVarFrames = new ArrayDeque<Set<String>>();
    getDataKeysInExprVisitor = new GetDataKeysInExprVisitor(dataKeys, localVarFrames);

    localVarFrames.push(Sets.<String>newHashSet());
    visitChildren(node);
    localVarFrames.pop();

    IndirectParamsInfo ipi =
        (new FindIndirectParamsVisitor(true, false, templateNameToNodeMap)).exec(node);

    List<String> unusedParams = Lists.newArrayList();
    for (SoyDocParam param : node.getSoyDocParams()) {
      if (dataKeys.contains(param.key)) {
        // Good: Declared in SoyDoc and referenced in template. We remove these from dataKeys so
        // that at the end of the for-loop, dataKeys will only contain the keys that are referenced
        // but not declared in SoyDoc.
        dataKeys.remove(param.key);
      } else if (ipi.paramKeyToCalleesMultimap.containsKey(param.key) ||
                 ipi.mayHaveExternalIndirectParams) {
        // Good: Declared in SoyDoc and either (a) used in a call that passes all data or (b) used
        // in an external call that passes all data, which may need the param (we can't verify).
      } else {
        // Bad: Declared in SoyDoc but not referenced in template.
        unusedParams.add(param.key);
      }
    }

    List<String> undeclaredDataKeys = Lists.newArrayList();
    if (dataKeys.size() > 0) {
      // Bad: Referenced in template but not declared in SoyDoc.
      undeclaredDataKeys.addAll(dataKeys);
      Collections.sort(undeclaredDataKeys);
    }

    dataKeys = null;
    getDataKeysInExprVisitor = null;

    if (undeclaredDataKeys.size() > 0) {
      throw SoytreeUtils.createSoySyntaxExceptionWithMetaInfo(
          "Found references to data keys that are not declared in SoyDoc: " + undeclaredDataKeys,
          null, node);
    }
    if (unusedParams.size() > 0) {
      throw SoytreeUtils.createSoySyntaxExceptionWithMetaInfo(
          "Found params declared in SoyDoc but not used in template: " + unusedParams, null, node);
    }

    // Check for unnecessary calls to function hasData().
    if (currFileHasOptParams && usesFunctionHasData) {
      for (SoyDocParam param : node.getSoyDocParams()) {
        if (param.isRequired) {
          throw SoytreeUtils.createSoySyntaxExceptionWithMetaInfo(
              "Unnecessary usage of hasData() since template has at least one required parameter.",
              null, node);
        }
      }
    }
  }


  @Override protected void visitInternal(IfNode node) {
    visitChildren(node);  // children will add their own local var frames
  }


  @Override protected void visitInternal(SwitchNode node) {
    visitExprHolderHelper(node);
    visitChildren(node);  // children will add their own local var frames
  }


  @Override protected void visitInternal(ForeachNode node) {
    visitExprHolderHelper(node);
    visitChildren(node);  // children will add their own local var frames
  }


  @Override protected void visitInternal(ForeachNonemptyNode node) {

    Set<String> newLocalVarFrame = Sets.newHashSet();
    newLocalVarFrame.add(node.getLocalVarName());
    localVarFrames.push(newLocalVarFrame);
    visitChildren(node);
    localVarFrames.pop();
  }


  @Override protected void visitInternal(ForNode node) {

    visitExprHolderHelper(node);

    Set<String> newLocalVarFrame = Sets.newHashSet();
    newLocalVarFrame.add(node.getLocalVarName());
    localVarFrames.push(newLocalVarFrame);
    visitChildren(node);
    localVarFrames.pop();
  }


  @Override protected void visitInternal(CallNode node) {

    if (node.isPassingAllData()) {
      // Nothing to do here, because we now use FindIndirectParamsVisitor to find all the
      // transitive callees that we pass all data to (and also to find out whether there are any
      // external transitive callees).
    } else {
      // Not passing all data.
      visitExprHolderHelper(node);
    }

    visitChildren(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(SoyNode node) {
    // Nothing to do for non-parent nodes not handled above.
  }


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
    localVarFrames.push(Sets.<String>newHashSet());
    visitChildren(node);
    localVarFrames.pop();
  }


  @Override protected void visitInternal(ExprHolderNode node) {
    visitExprHolderHelper(node);
  }


  @Override protected void visitInternal(ParentExprHolderNode<? extends SoyNode> node) {

    visitExprHolderHelper(node);

    localVarFrames.push(Sets.<String>newHashSet());
    visitChildren(node);
    localVarFrames.pop();
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  /**
   * Helper for visiting a node that holds one or more expressions. For each expression, (a) checks
   * whether it uses the function hasData() (if necessary), and (b) collects data keys referenced.
   * @param exprHolder The node holding the expressions to be visited.
   */
  private void visitExprHolderHelper(ExprHolderNode exprHolder) {

    for (ExprRootNode<? extends ExprNode> expr : exprHolder.getAllExprs()) {

      if (currFileHasOptParams && !usesFunctionHasData &&
          (new UsesFunctionHasDataVisitor()).exec(expr)) {
        usesFunctionHasData = true;
      }

      getDataKeysInExprVisitor.exec(expr);
    }
  }


  /**
   * Helper to determine whether the function {@code hasData()} is called in an expression.
   */
  private static class UsesFunctionHasDataVisitor extends AbstractExprNodeVisitor<Boolean> {

    private boolean usesFunctionHasData;

    @Override protected void setup() {
      usesFunctionHasData = false;
    }

    @Override protected Boolean getResult() {
      return usesFunctionHasData;
    }

    // ------ Implementations for concrete classes. ------

    @Override protected void visitInternal(FunctionNode node) {
      if (node.getFunctionName().equals("hasData")) {
        usesFunctionHasData = true;
      }
    }

    // ------ Implementations for interfaces. ------

    @Override protected void visitInternal(ExprNode node) {
      // Nothing to do for non-parent, non-data-ref nodes.
    }

    @Override protected void visitInternal(ParentExprNode node) {

      for (ExprNode child : node.getChildren()) {
        if (usesFunctionHasData) {
          return;  // no need to keep searching if already found a call to hasData()
        }
        visit(child);
      }
    }
  }


  /**
   * Helper for travering an expression tree and locating all the data keys (excluding local vars)
   * referenced in the expression.
   *
   * <p> {@link #exec} may be called on any expression. Any data keys referenced in the expression
   * (excluding local vars) will be added to the {@code dataKeys} set passed in to the constructor.
   * There is no return value.
   */
  private static class GetDataKeysInExprVisitor extends AbstractExprNodeVisitor<Void> {

    /** The set used to collect the data keys found. */
    private final Set<String> dataKeys;

    /** The stack of frames containing sets of local vars currently defined. */
    private final Deque<Set<String>> localVarFrames;

    /**
     * @param dataKeys The set used to collect the data keys found.
     * @param localVarFrames The stack of frames containing sets of local vars currently defined.
     */
    public GetDataKeysInExprVisitor(Set<String> dataKeys, Deque<Set<String>> localVarFrames) {
      this.dataKeys = dataKeys;
      this.localVarFrames = localVarFrames;
    }

    // ------ Implementations for concrete classes. ------

    @Override protected void visitInternal(DataRefNode node) {

      String dataKeyOrVar = ((DataRefKeyNode) node.getChild(0)).getKey();

      // Determine whether this DataRefNode references a local variable.
      boolean isLocalVar = false;
      for (Set<String> localVarFrame : localVarFrames) {
        if (localVarFrame.contains(dataKeyOrVar)) {
          isLocalVar = true;
          break;
        }
      }

      // If not local variable, add to set of data keys referenced.
      if (!isLocalVar) {
        dataKeys.add(dataKeyOrVar);
      }

      // Important: Must visit children since children may be expressions that contain data refs.
      visitChildren(node);
    }

    // ------ Implementations for interfaces. ------

    @Override protected void visitInternal(ExprNode node) {
      // Nothing to do for non-parent, non-data-ref nodes.
    }

    @Override protected void visitInternal(ParentExprNode node) {
      visitChildren(node);
    }

  }

}
