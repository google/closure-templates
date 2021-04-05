/*
 * Copyright 2021 Google Inc.
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

import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Lists.newArrayList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.FieldAccessNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.ItemAccessNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.RecordLiteralNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.templatecall.TemplateCallMetadata;
import java.util.List;
import java.util.Optional;

/** Provides a serializable proto containing descriptions of template calls. */
@RunAfter(ResolveTemplateNamesPass.class) // Required to use getCalleeName()
public final class TemplateCallMetadataPass implements CompilerFileSetPass {

  TemplateCallMetadataPass() {}

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator nodeIdGen) {
    for (SoyFileNode file : sourceFiles) {
      for (TemplateNode template : file.getTemplates()) {
        // Handle Soy Element Composition
        List<TemplateCallMetadata.TemplateCall> callsFromTags =
            SoyTreeUtils.getAllNodesOfType(template, HtmlTagNode.class).stream()
                .map(TemplateCallMetadataPass::calculateTemplateCallInfoFromTag)
                .flatMap(Streams::stream)
                .collect(toImmutableList());

        template.addTemplateCallMetadata(
            TemplateCallMetadata.Template.newBuilder()
                .setName(template.getTemplateName())
                .setDelpackage(nullToEmpty(template.getDelPackageName()))
                .addAllCalls(
                    SoyTreeUtils.getAllNodesOfType(template, CallNode.class).stream()
                        .map(TemplateCallMetadataPass::calculateTemplateCall)
                        .collect(toImmutableList()))
                .addAllCalls(callsFromTags)
                .build());
      }
    }
    return Result.CONTINUE;
  }

  /**
   * Parses template calls which are declared via tag syntax.
   *
   * <p>This logic only infers static calls where the binding is produced by the framework. This
   * handles framework-provided Wiz Component template calls which use Soy element composition. A
   * more complex solution may be needed for dynamic calls.
   *
   * @param tagNode The node that may reference a template call
   * @return template call node
   */
  private static Optional<TemplateCallMetadata.TemplateCall> calculateTemplateCallInfoFromTag(
      HtmlTagNode tagNode) {
    TagName tagName = tagNode.getTagName();
    if (tagNode.getTagName().isStatic()) {
      return Optional.empty();
    }
    ExprNode tagExprNode = tagName.getDynamicTagName().getExpr().getRoot();
    if (tagExprNode instanceof TemplateLiteralNode) {
      // can determine that the template is called, but not its param args
      // TODO(b/179912526): can this syntax specify param args? if so, add corresponding test case
      String destTemplateName = ((TemplateLiteralNode) tagExprNode).getResolvedName();
      return Optional.of(
          TemplateCallMetadata.TemplateCall.newBuilder()
              .setDestTemplateName(destTemplateName)
              .build());
    }

    if (!(tagExprNode.getKind() == ExprNode.Kind.METHOD_CALL_NODE
        && ((MethodCallNode) tagExprNode).getMethodName().identifier().equals("bind"))) {
      return Optional.empty();
    }

    MethodCallNode bind = (MethodCallNode) tagExprNode;
    if (bind.getChild(0).getKind() != ExprNode.Kind.TEMPLATE_LITERAL_NODE) {
      return Optional.empty();
    }
    RecordLiteralNode recordLiteralNode = (RecordLiteralNode) bind.getParams().get(0);
    List<ExprNode> bindExprs = recordLiteralNode.getChildren();
    List<TemplateCallMetadata.ParamArg> callParamArgs = newArrayList();
    for (int i = 0; i < bindExprs.size(); i++) {
      callParamArgs.add(
          TemplateCallMetadata.ParamArg.newBuilder()
              .setKey(recordLiteralNode.getKey(i).identifier())
              .setVarRef(
                  resolveLocalVarRefToParamRef(
                      bindExprs.get(i), TemplateCallMetadata.VarRefInfo.newBuilder()))
              .build());
    }
    String destTemplateName = ((TemplateLiteralNode) bind.getChild(0)).getResolvedName();
    return Optional.of(
        TemplateCallMetadata.TemplateCall.newBuilder()
            .setDestTemplateName(destTemplateName)
            // TODO(b/179912526): when / if needed, handle soy element composition + delcalls here
            .setIsDelcall(false)
            .addAllParamArgs(callParamArgs)
            .build());
  }

  private static TemplateCallMetadata.TemplateCall calculateTemplateCall(
      CallNode templateCallNode) {

    ImmutableList<TemplateCallMetadata.ParamArg> callParamArgs =
        templateCallNode.getChildren().stream()
            .map(TemplateCallMetadataPass::createParamArg)
            .collect(toImmutableList());
    TemplateCallMetadata.TemplateCall.Builder templateCallMetaData =
        TemplateCallMetadata.TemplateCall.newBuilder()
            .setDestTemplateName(getDestTemplateName(templateCallNode))
            .setIsDelcall(templateCallNode.getKind() == SoyNode.Kind.CALL_DELEGATE_NODE)
            .addAllParamArgs(callParamArgs);
    if (templateCallNode.isPassingAllData()) {
      templateCallMetaData.setIsPassingAllData(true);
    } else if (templateCallNode.getDataExpr() != null) {
      templateCallMetaData.setDataArg(
          resolveLocalVarRefToParamRef(
              templateCallNode.getDataExpr().getRoot(),
              TemplateCallMetadata.VarRefInfo.newBuilder()));
    }
    return templateCallMetaData.build();
  }

  /**
   * Given a CallParamNode return its ParamArg proto representation.
   *
   * @param callParamNode Node that describes a variable referenced in a template call
   * @return A ParamArg containing the VarRefInfo pertaining to its value, if applicable.
   */
  private static TemplateCallMetadata.ParamArg createParamArg(CallParamNode callParamNode) {
    // only shorthand param ref syntax is supported
    TemplateCallMetadata.ParamArg.Builder paramArg =
        TemplateCallMetadata.ParamArg.newBuilder().setKey(callParamNode.getKey().identifier());
    if (!(callParamNode instanceof CallParamValueNode)) {
      return paramArg.build();
    }
    ExprNode possibleParamRefExpr = ((CallParamValueNode) callParamNode).getExpr().getRoot();
    return paramArg
        .setVarRef(
            resolveLocalVarRefToParamRef(
                possibleParamRefExpr, TemplateCallMetadata.VarRefInfo.newBuilder()))
        .build();
  }

  /**
   * Given a ExprNode and a VarRefInfo Builder return a VarRefInfo that resolves a localVar to the
   * Param it was derived from, if applicable.
   *
   * @param varExpr Node that describes a variable expression.
   * @param varRefInfo A builder for the VarRefInfo proto that contains information about varExpr.
   * @return A VarRefInfo that contains relates varExpr to the param reference it was derived from,
   *     if applicable
   */
  private static TemplateCallMetadata.VarRefInfo resolveLocalVarRefToParamRef(
      ExprNode varExpr, TemplateCallMetadata.VarRefInfo.Builder varRefInfo) {
    if (varExpr.getKind() == Kind.VAR_REF_NODE) {
      VarDefn possibleParamRefDef = ((VarRefNode) varExpr).getDefnDecl();
      if (possibleParamRefDef.kind() == VarDefn.Kind.PARAM) {
        varRefInfo.setHeaderParam(possibleParamRefDef.name());
        return varRefInfo.build();
      } else if (possibleParamRefDef.kind() == VarDefn.Kind.LOCAL_VAR) {
        LocalVarNode varRefNode = ((LocalVar) possibleParamRefDef).declaringNode();
        // The LocalVarNode point to a for loop local variable
        if (varRefNode.getKind() == SoyNode.Kind.FOR_NONEMPTY_NODE) {
          varRefInfo.setUsesListIndex(true);
          return resolveLocalVarRefToParamRef(
              ((ForNonemptyNode) varRefNode).getExpr().getRoot(), varRefInfo);
        } else if (varRefNode instanceof LetValueNode) {
          // The LocalVarNode is a let statement
          return resolveLocalVarRefToParamRef(
              ((LetValueNode) varRefNode).getExpr().getRoot(), varRefInfo);
        }
      }
    } else if (varExpr.getKind() == Kind.ITEM_ACCESS_NODE) {
      varRefInfo.setUsesListIndex(true);
      return resolveLocalVarRefToParamRef(
          ((ItemAccessNode) varExpr).getBaseExprChild(), varRefInfo);
    } else if (varExpr.getKind() == Kind.FIELD_ACCESS_NODE) {
      FieldAccessNode fieldAccessNode = ((FieldAccessNode) varExpr);
      varRefInfo.setDataAccessAlias(fieldAccessNode.getFieldName());
      return resolveLocalVarRefToParamRef(fieldAccessNode.getBaseExprChild(), varRefInfo);
    } else if (varExpr.getKind() == Kind.FUNCTION_NODE) {
      if ("checkNotNull".equals(((FunctionNode) varExpr).getFunctionName())) {
        // this function accepts 1 expr and does not meaningfully change its value
        return resolveLocalVarRefToParamRef(((FunctionNode) varExpr).getChild(0), varRefInfo);
      }
    }

    return varRefInfo.build();
  }

  private static String getDestTemplateName(CallNode callNode) {
    switch (callNode.getKind()) {
      case CALL_BASIC_NODE:
        CallBasicNode basicNode = ((CallBasicNode) callNode);
        return basicNode.isStaticCall()
            ? basicNode.getCalleeName()
            : basicNode.getCalleeExpr().toSourceString();
      case CALL_DELEGATE_NODE:
        return ((CallDelegateNode) callNode).getDelCalleeName();
      default:
        throw new IllegalStateException("Unknown CallNode kind");
    }
  }
}
