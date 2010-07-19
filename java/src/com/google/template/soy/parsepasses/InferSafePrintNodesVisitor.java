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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.template.soy.base.IdGenerator;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.DataRefKeyNode;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.PrimitiveNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.shared.SoyGeneralOptions.SafePrintTagsInferenceLevel;
import static com.google.template.soy.shared.SoyGeneralOptions.SafePrintTagsInferenceLevel.ADVANCED;
import static com.google.template.soy.shared.SoyGeneralOptions.SafePrintTagsInferenceLevel.NONE;
import static com.google.template.soy.shared.SoyGeneralOptions.SafePrintTagsInferenceLevel.SIMPLE;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateNode.SoyDocSafePath;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Visitor for inferring safe {@code PrintNode}s and optionally adding the {@code |noAutoescape}
 * directive to each {@code PrintNode} inferred to be safe.
 *
 * <p> Inference levels:
 * <pre>
 *     SIMPLE: Infers safe print tags based on '@safe' declarations in the current template's
 *         SoyDoc (same behavior for public and private templates).
 *     ADVANCED: Infers safe print tags based on '@safe' declarations in SoyDoc of public
 *         templates. For private templates, infers safe data from the data being passed in all
 *         the calls to the private template from the rest of the Soy code (in the same compiled
 *         bundle).
 * </pre>
 *
 * <p> Also checks calls between templates. Specifically, if the callee template's SoyDoc specifies
 * '@safe' declarations, then the data being passed in the call must be known to be safe for all of
 * those declared safe data paths. Otherwise, a data-safety-mismatch error is reported.
 *
 * <p> {@link #exec} should be called on a full parse tree. The result is the list of
 * {@code PrintNode}s inferred to be safe, ordered by appearance in the source Soy code.
 *
 * <p> Precondition: All the template names in {@code TemplateNode}s must be full names (i.e. you
 * must execute {@link PrependNamespacesVisitor} before executing this visitor).
 *
 * @author Kai Huang
 */
public class InferSafePrintNodesVisitor extends AbstractSoyNodeVisitor<List<PrintNode>> {


  /** The level of safe-print-tags inference. */
  private final SafePrintTagsInferenceLevel inferenceLevel;

  /** Whether to add '|noAutoescape' to each PrintNode inferred to be safe. */
  private final boolean shouldAddNoAutoescapeDirectives;

  /** The node id generator for the parse tree. Retrieved from the root SoyFileSetNode. */
  private IdGenerator nodeIdGen;

  /** The set of inferred safe PrintNodes (built during the pass). */
  private Set<PrintNode> inferredSafePrintNodesSet;

  /** The list of inferred safe PrintNodes (result of this visitor; ordered by appearance). */
  private List<PrintNode> inferredSafePrintNodes;

  /** Map from each template's name to its node. */
  private Map<String, TemplateNode> templateNameToNodeMap;

  /** [ADVANCED inference] Built by FindPrivateTemplateCallsVisitor and used during main pass. */
  private Map<String, Set<CallNode>> privateTemplateNameToCallsMap;

  /** [ADVANCED inference] Map from CallNode to SafetyInfo for the data (+params) being passed. */
  private Map<CallNode, SafetyInfo> callToDataSafetyInfoMap;

  /** The SafetyInfo corresponding to the current template's data (during pass). */
  private SafetyInfo dataSafetyInfo;

  /** Frames to hold SafetyInfo for current in-scope local vars (during pass). */
  private Deque<Map<String, SafetyInfo>> localVarFrames;

  /** The ComputeExprSafetyInfoVisitor instance to use for the current template (during pass). */
  private ComputeExprSafetyInfoVisitor computeExprSafetyInfoVisitor;


  /**
   * @param inferenceLevel The level of safe-print-tags inference.
   * @param shouldAddNoAutoescapeDirectives Whether to add the '|noAutoescape' directive to each
   */
  public InferSafePrintNodesVisitor(
      SafePrintTagsInferenceLevel inferenceLevel, boolean shouldAddNoAutoescapeDirectives) {
    checkArgument(inferenceLevel != NONE);
    this.inferenceLevel = inferenceLevel;
    this.shouldAddNoAutoescapeDirectives = shouldAddNoAutoescapeDirectives;
  }


  @Override protected void setup() {
    inferredSafePrintNodesSet = Sets.newLinkedHashSet();
  }


  @Override protected List<PrintNode> getResult() {
    return inferredSafePrintNodes;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(SoyFileSetNode node) {

    if (shouldAddNoAutoescapeDirectives) {
      nodeIdGen = node.getNodeIdGen();
    }

    // Fill in the templateNameToTemplateMap.
    templateNameToNodeMap = Maps.newHashMap();
    for (SoyFileNode soyFile : node.getChildren()) {
      for (TemplateNode template : soyFile.getChildren()) {
        templateNameToNodeMap.put(template.getTemplateName(), template);
      }
    }

    if (inferenceLevel == SIMPLE) {
      visitSoyFileSetNodeForSimpleInference(node);
    } else {
      visitSoyFileSetNodeForAdvancedInference(node);
    }
  }


  private void visitSoyFileSetNodeForSimpleInference(SoyFileSetNode node) {

    for (SoyFileNode soyFile : node.getChildren()) {
      for (TemplateNode template : soyFile.getChildren()) {
        visit(template);
      }
    }

    inferredSafePrintNodes = ImmutableList.copyOf(inferredSafePrintNodesSet);
  }


  private void visitSoyFileSetNodeForAdvancedInference(SoyFileSetNode node) {

    callToDataSafetyInfoMap = Maps.newHashMap();

    // Initialize the set of unprocessed templates.
    Set<TemplateNode> unprocessedTemplates = Sets.newLinkedHashSet();
    for (SoyFileNode soyFile : node.getChildren()) {
      for (TemplateNode template : soyFile.getChildren()) {
        unprocessedTemplates.add(template);
      }
    }

    // Find all the calls of each private template.
    privateTemplateNameToCallsMap = (new FindPrivateTemplateCallsVisitor()).exec(node);

    // Loop repeatedly until there are no more templates that we can process.
    boolean didProcessTemplates;
    do {
      didProcessTemplates = false;

      for (TemplateNode template : ImmutableSet.copyOf(unprocessedTemplates)) {

        // First figure out whether we can process this template yet. We can process it if
        // (a) it's a public template, or
        // (b) it's a private template, but all calls of it have previously been processed.
        boolean canProcessTemplate;
        if (template.isPrivate()) {
          canProcessTemplate = true;
          Set<CallNode> calls = privateTemplateNameToCallsMap.get(template.getTemplateName());
          for (CallNode call : calls) {
            if (! callToDataSafetyInfoMap.containsKey(call)) {
              canProcessTemplate = false;
              break;
            }
          }
        } else {
          canProcessTemplate = true;
        }

        // Process the template (if we can).
        if (canProcessTemplate) {
          visit(template);
          unprocessedTemplates.remove(template);
          didProcessTemplates = true;
        }
      }

    } while (didProcessTemplates);

    // Note: There may be unprocessed templates left (e.g. if there are recursive private templates
    // or circular dependencies among private templates), but this shouldn't be an error since this
    // feature is best-effort only and there will be cases we can't handle.

    // Build the inferred safe print nodes list (ordered by appeareance of the nodes in the tree).
    inferredSafePrintNodes = Lists.newArrayListWithCapacity(inferredSafePrintNodesSet.size());
    for (PrintNode printNode : (new GetAllPrintNodesVisitor()).exec(node)) {
      if (inferredSafePrintNodesSet.contains(printNode)) {
        inferredSafePrintNodes.add(printNode);
      }
    }
  }


  @Override protected void visitInternal(TemplateNode node) {

    if (inferenceLevel == SIMPLE) {
      // SIMPLE inference: Safe data paths always come from SoyDoc.
      dataSafetyInfo = SafetyInfo.createFromSoyDocSafePaths(node.getSoyDocSafePaths());

    } else {
      // ADVANCED inference: For public templates, safe data paths come from SoyDoc; for private
      // templates, safe data paths are inferred by merging safe data paths from all calls.
      if (! node.isPrivate()) {
        dataSafetyInfo = SafetyInfo.createFromSoyDocSafePaths(node.getSoyDocSafePaths());
      } else {
        Set<CallNode> calls = privateTemplateNameToCallsMap.get(node.getTemplateName());
        List<SafetyInfo> callDataSafetyInfos = Lists.newArrayList();
        for (CallNode call : calls) {
          callDataSafetyInfos.add(callToDataSafetyInfoMap.get(call));
        }
        dataSafetyInfo = SafetyInfo.merge(callDataSafetyInfos);
      }
    }

    localVarFrames = new ArrayDeque<Map<String, SafetyInfo>>();

    computeExprSafetyInfoVisitor = new ComputeExprSafetyInfoVisitor(dataSafetyInfo, localVarFrames);

    visitChildren(node);
  }


  @Override protected void visitInternal(PrintNode node) {

    if (computeExprSafetyInfoVisitor.exec(node.getExpr()).isSafe()) {

      inferredSafePrintNodesSet.add(node);

      if (shouldAddNoAutoescapeDirectives) {
        boolean hasNoAutoescapeOrIdDirective = false;
        for (PrintDirectiveNode directive : node.getChildren()) {
          if (directive.getName().equals("|id") || directive.getName().equals("|noAutoescape")) {
            hasNoAutoescapeOrIdDirective = true;
            break;
          }
        }
        if (! hasNoAutoescapeOrIdDirective) {
          node.addChild(new PrintDirectiveNode(nodeIdGen.genStringId(), "|noAutoescape", ""));
        }
      }
    }
  }


  @Override protected void visitInternal(ForeachNonemptyNode node) {

    SafetyInfo foreachVarSafetyInfo = computeExprSafetyInfoVisitor.exec(node.getDataRef());

    Map<String, SafetyInfo> newLocalVarFrame = Maps.newHashMap();
    newLocalVarFrame.put(node.getLocalVarName(), foreachVarSafetyInfo);
    localVarFrames.push(newLocalVarFrame);
    visitChildren(node);
    localVarFrames.pop();
  }


  @Override protected void visitInternal(ForNode node) {

    SafetyInfo forVarSafetyInfo = SafetyInfo.SAFE_LEAF_INSTANCE;

    Map<String, SafetyInfo> newLocalVarFrame = Maps.newHashMap();
    newLocalVarFrame.put(node.getLocalVarName(), forVarSafetyInfo);
    localVarFrames.push(newLocalVarFrame);
    visitChildren(node);
    localVarFrames.pop();
  }


  @Override protected void visitInternal(CallNode node) {

    // Compute the SafetyInfo of the passed data from the 'data' attribute.
    SafetyInfo callDataSafetyInfo = (node.isPassingAllData()) ?
        dataSafetyInfo : computeExprSafetyInfoVisitor.exec(node.getDataRef());

    // Put SafetyInfo for 'param' tags (may replace some parts of the passed data's SafetyInfo).
    if (node.numChildren() > 0) {
      // If there are additional params, clone the original data so we don't modify it.
      callDataSafetyInfo = SafetyInfo.clone(callDataSafetyInfo);
    }
    for (CallParamNode param : node.getChildren()) {
      if (param instanceof CallParamValueNode) {
        SafetyInfo paramValueSafetyInfo =
            computeExprSafetyInfoVisitor.exec(((CallParamValueNode) param).getValueExpr());
        callDataSafetyInfo.putSubinfo(param.getKey(), paramValueSafetyInfo);
      } else {
        // For CallParamContentNode, the param value is assumed to be a safe string because it's the
        // result of template rendering.
        callDataSafetyInfo.putSubinfo(param.getKey(), SafetyInfo.SAFE_LEAF_INSTANCE);
      }
    }

    // Check that the call data's SafetyInfo contains the callee template's declared safe paths.
    TemplateNode callee = templateNameToNodeMap.get(node.getCalleeName());
    List<SoyDocSafePath> unmatchedSoyDocSafePaths =
        callDataSafetyInfo.findUnmatchedSoyDocSafePaths(callee.getSoyDocSafePaths());
    if (unmatchedSoyDocSafePaths.size() > 0) {
      StringBuilder unmatchedSoyDocSafePathsSb = new StringBuilder();
      for (SoyDocSafePath unmatchedSoyDocSafePath : unmatchedSoyDocSafePaths) {
        if (unmatchedSoyDocSafePathsSb.length() > 0) {
          unmatchedSoyDocSafePathsSb.append(", ");
        }
        unmatchedSoyDocSafePathsSb.append(Joiner.on('.').join(unmatchedSoyDocSafePath.path));
      }
      throw new SoySyntaxException(
          "Data safety mismatch for " + node.getTagString() + ": " +
          "the callee template " + callee.getTemplateName() + " expects safe data paths [" +
          unmatchedSoyDocSafePathsSb.toString() +
          "], but these paths are not known to be safe in the data being passed.");
    }

    if (inferenceLevel == ADVANCED) {
      // ADVANCED inference: Add the call data's SafetyInfo to the map. This info will be used later
      // when we process the callee template.
      callToDataSafetyInfoMap.put(node, callDataSafetyInfo);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(SoyNode node) {
    // Nothing to do for non-parent nodes not handled above.
  }


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
    localVarFrames.push(Maps.<String, SafetyInfo>newHashMap());
    visitChildren(node);
    localVarFrames.pop();
  }


  // -----------------------------------------------------------------------------------------------
  // Helper passes.


  /**
   * Private helper for InferSafePrintNodesVisitor to build a map from each private template's name
   * to its set of calls.
   */
  private static class FindPrivateTemplateCallsVisitor
      extends AbstractSoyNodeVisitor<Map<String, Set<CallNode>>> {

    private Map<String, Set<CallNode>> privateTemplateNameToCallsMap;

    @Override protected Map<String, Set<CallNode>> getResult() {
      return privateTemplateNameToCallsMap;
    }

    // ------ Implementations for concrete classes. ------

    @Override protected void visitInternal(SoyFileSetNode node) {

      // Find all the private templates and initialize the result map.
      privateTemplateNameToCallsMap = Maps.newLinkedHashMap();
      for (SoyFileNode soyFile : node.getChildren()) {
        for (TemplateNode template : soyFile.getChildren()) {
          if (template.isPrivate()) {
            privateTemplateNameToCallsMap.put(
                template.getTemplateName(), Sets.<CallNode>newLinkedHashSet());
          }
        }
      }

      visitChildren(node);
    }

    @Override protected void visitInternal(CallNode node) {

      // If this is a call of a private template, add it to the result map.
      Set<CallNode> calls = privateTemplateNameToCallsMap.get(node.getCalleeName());
      if (calls != null) {
        calls.add(node);
      }

      visitChildren(node);
    }

    // ------ Implementations for interfaces. ------

    @Override protected void visitInternal(SoyNode node) {
      // Nothing to do for non-parent nodes not handled above.
    }

    @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
      visitChildren(node);
    }
  }


  /**
   * Private helper for InferSafePrintNodesVisitor to get a list of all the PrintNodes in the tree,
   * ordered by appearance in the source Soy code.
   */
  private static class GetAllPrintNodesVisitor extends AbstractSoyNodeVisitor<List<PrintNode>> {

    private List<PrintNode> allPrintNodes;

    @Override protected void setup() {
      allPrintNodes = Lists.newArrayList();
    }

    @Override protected List<PrintNode> getResult() {
      return allPrintNodes;
    }

    @Override protected void visitInternal(PrintNode node) {
      allPrintNodes.add(node);
    }

    @Override protected void visitInternal(SoyNode node) {
      // Nothing to do for non-parent nodes not handled above.
    }

    @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
      visitChildren(node);
    }
  }


  /**
   * Private helper for InferSafePrintNodesVisitor to compute the SafetyInfo corresponding to the
   * result of a given expression.
   */
  private static class ComputeExprSafetyInfoVisitor extends AbstractExprNodeVisitor<SafetyInfo> {

    private static final Set<String> SAFE_FUNCTION_NAMES = ImmutableSet.of(
        "isFirst", "isLast", "index", "hasData", "length", "round", "floor", "ceiling", "min",
        "max", "randomInt");

    private final SafetyInfo dataSafetyInfo;

    private final Deque<Map<String, SafetyInfo>> localVarFrames;

    /** Stack of partial results of subtrees. */
    private Deque<SafetyInfo> resultStack;

    public ComputeExprSafetyInfoVisitor(
        SafetyInfo dataSafetyInfo, Deque<Map<String, SafetyInfo>> localVarFrames) {
      this.dataSafetyInfo = dataSafetyInfo;
      this.localVarFrames = localVarFrames;
    }

    @Override public SafetyInfo exec(ExprNode node) {
      return (node != null) ? super.exec(node) : SafetyInfo.EMPTY_INSTANCE;
    }

    @Override protected void setup() {
      resultStack = new ArrayDeque<SafetyInfo>();
    }

    @Override protected SafetyInfo getResult() {
      return resultStack.peek();
    }

    // ------ Implementations for concrete classes. ------

    @Override protected void visitInternal(ExprRootNode<? extends ExprNode> node) {
      visitChildren(node);
    }

    @Override protected void visitInternal(DataRefNode node) {

      String firstKey = ((DataRefKeyNode) node.getChild(0)).getKey();

      // First check local vars.
      for (Map<String, SafetyInfo> localVarFrame : localVarFrames) {
        SafetyInfo safetyInfo = localVarFrame.get(firstKey);
        if (safetyInfo != null) {
          resultStack.push(safetyInfo);
          return;
        }
      }

      // If got here, this is a data ref (not local var).
      resultStack.push(dataSafetyInfo.getSubinfo(node.getChildren()));
    }

    @Override protected void visitInternal(GlobalNode node) {
      // Assume unsafe.
      resultStack.push(SafetyInfo.EMPTY_INSTANCE);
    }

    @Override protected void visitInternal(PlusOpNode node) {

      for (ExprNode operand : node.getChildren()) {
        visit(operand);
        SafetyInfo operandSafetyInfo = resultStack.pop();
        if (! operandSafetyInfo.isSafe()) {
          resultStack.push(SafetyInfo.EMPTY_INSTANCE);
          return;
        }
      }

      resultStack.push(SafetyInfo.SAFE_LEAF_INSTANCE);
    }

    @Override protected void visitInternal(ConditionalOpNode node) {

      // Note: Only need to check the second and third operands.
      List<ExprNode> operands = node.getChildren();
      for (int i = 1; i <= 2; i++) {
        visit(operands.get(i));
        SafetyInfo operandSafetyInfo = resultStack.pop();
        if (! operandSafetyInfo.isSafe()) {
          resultStack.push(SafetyInfo.EMPTY_INSTANCE);
          return;
        }
      }

      resultStack.push(SafetyInfo.SAFE_LEAF_INSTANCE);
    }

    @Override protected void visitInternal(FunctionNode node) {

      // For now, we just check for a few basic function names, and assume the rest are unsafe.
      // TODO: Figure out how to better handle function return values.
      if (SAFE_FUNCTION_NAMES.contains(node.getFunctionName())) {
        resultStack.push(SafetyInfo.SAFE_LEAF_INSTANCE);
      } else {
        resultStack.push(SafetyInfo.EMPTY_INSTANCE);
      }
    }

    // ------ Implementations for interfaces. ------

    @Override protected void visitInternal(OperatorNode node) {
      // Safe for all mathematical and logical operators. The only exceptions are PlusOpNode and
      // ConditionalOpNode, which are specifically handled above.
      resultStack.push(SafetyInfo.SAFE_LEAF_INSTANCE);
    }

    @Override protected void visitInternal(PrimitiveNode node) {
      resultStack.push(SafetyInfo.SAFE_LEAF_INSTANCE);
    }
  }

}
