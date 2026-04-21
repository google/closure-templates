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
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.CallableExpr.ParamsStyle;
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
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.templatecall.TemplateCallMetadata;
import com.google.template.soy.templatecall.TemplateCallMetadata.TemplateCall;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.TemplateType;
import java.util.List;
import java.util.Optional;

/** Provides a serializable proto containing descriptions of template calls. */
@RunAfter({
  // Required to use getCalleeName()
  ResolveTemplateNamesPass.class,
  // Required to use allowedToInvokeAsFunction() to identify short form template calls
  ResolveExpressionTypesPass.class,
})
final class TemplateCallMetadataPass implements CompilerFileSetPass {

  private ErrorReporter errorReporter;

  TemplateCallMetadataPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator nodeIdGen) {
    if (errorReporter.hasErrors()) {
      return Result.CONTINUE;
    }
    for (SoyFileNode file : sourceFiles) {
      for (TemplateNode template : file.getTemplates()) {

        // Handle Soy Element Composition
        List<TemplateCallMetadata.TemplateCall> shortFormCalls =
            SoyTreeUtils.getAllNodesOfType(template, PrintNode.class).stream()
                .map(TemplateCallMetadataPass::parseShortFormTemplateCallAndElementComposition)
                .flatMap(Streams::stream)
                .collect(toImmutableList());

        template.setTemplateCallMetadata(
            TemplateCallMetadata.Template.newBuilder()
                .setName(getTemplateName(template))
                .setModname(nullToEmpty(template.getModName()))
                .addAllCalls(
                    SoyTreeUtils.getAllNodesOfType(template, CallNode.class).stream()
                        .map(TemplateCallMetadataPass::calculateTemplateCall)
                        .collect(toImmutableList()))
                .addAllCalls(shortFormCalls)
                .build());
      }
    }
    return Result.CONTINUE;
  }

  private String getTemplateName(TemplateNode template) {
    if (!(template instanceof TemplateBasicNode basicNode)) {
      return template.getTemplateName();
    }
    if (basicNode.getModifiesExpr() != null
        && basicNode.getModifiesExpr().getRoot() instanceof TemplateLiteralNode literal) {
      return literal.getResolvedName();
    }
    return template.getTemplateName();
  }

  /**
   * Parses short form and element compostion template calls.
   *
   * <p>Short form template calls follow the form {@code {template(key: value, ...)}}.
   *
   * <p>More information on element composition can be found at
   * go/soy/reference/element-composition.
   *
   * <p>There is currently no documentation for short form template calls.
   *
   * @param printNode Node that may contain a template call
   * @return template call node if provided node references a template call; else empty Optional
   */
  private static Optional<TemplateCallMetadata.TemplateCall>
      parseShortFormTemplateCallAndElementComposition(PrintNode printNode) {
    if (printNode.getExpr().getRoot() instanceof FunctionNode fnNode) {

      if (!fnNode.allowedToInvokeAsFunction()
          || fnNode.getParamsStyle() == ParamsStyle.POSITIONAL) {
        return Optional.empty();
      }
      if (fnNode.getNameExpr() instanceof TemplateLiteralNode templateLiteralNode) {
        TemplateCallMetadata.TemplateCall.Builder templateCall =
            TemplateCallMetadata.TemplateCall.newBuilder()
                .addAllParamArgs(getFunctionParams(fnNode.getParamNames(), fnNode.getParams()));
        return Optional.of(
            templateCall.setDestTemplateName(templateLiteralNode.getResolvedName()).build());
      }

      TemplateType templateType = SoyTypes.getTemplateType(fnNode.getNameExpr().getType());
      if (templateType == null) {
        return Optional.empty();
      }
      TemplateCallMetadata.TemplateCall.Builder templateCall =
          TemplateCallMetadata.TemplateCall.newBuilder()
              .addAllParamArgs(getFunctionParams(fnNode.getParamNames(), fnNode.getParams()));
      return resolveVarRefTemplateCall((VarRefNode) fnNode.getNameExpr(), templateCall);
    }
    return resolveTemplateReference(printNode.getExpr().getRoot());
  }

  private static TemplateCallMetadata.TemplateCall calculateTemplateCall(
      CallNode templateCallNode) {

    ImmutableList<TemplateCallMetadata.ParamArg> callParamArgs =
        templateCallNode.getChildren().stream()
            .map(TemplateCallMetadataPass::createParamArg)
            .collect(toImmutableList());

    // Check to see if the template call is constructed using a bind statement.
    if (templateCallNode instanceof CallBasicNode callBasicNode) {
      ExprNode exprNode = callBasicNode.getCalleeExpr().getRoot();
      if (isBindStatement(exprNode) || exprNode instanceof VarRefNode) {
        Optional<TemplateCallMetadata.TemplateCall> boundTemplateCall =
            resolveTemplateReference(exprNode);
        if (boundTemplateCall.isPresent()) {
          return TemplateCallMetadata.TemplateCall.newBuilder(boundTemplateCall.get())
              .addAllParamArgs(callParamArgs)
              .build();
        }
      }
    }
    TemplateCallMetadata.TemplateCall.Builder templateCallMetaData =
        TemplateCallMetadata.TemplateCall.newBuilder()
            .setDestTemplateName(getDestTemplateName(templateCallNode))
            .setIsDelcall(templateCallNode.getKind() == SoyNode.Kind.CALL_DELEGATE_NODE)
            .setIsModifiableCall(isModifiableCall(templateCallNode))
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

  private static boolean isModifiableCall(CallNode call) {
    if (!(call instanceof CallBasicNode callBasicNode)) {
      return false;
    }
    if (!(callBasicNode.getCalleeExpr().getRoot().getType() instanceof TemplateType templateType)) {
      return false;
    }
    return templateType.isModifiable();
  }

  /**
   * Resolves an exprNode to its corresponding TemplateCall representation, empty if exprNode does
   * not correspond to a template.
   *
   * @param exprNode An exprNode that represents a template reference.
   */
  private static Optional<TemplateCallMetadata.TemplateCall> resolveTemplateReference(
      ExprNode exprNode) {
    if (exprNode instanceof TemplateLiteralNode templateLiteralNode) {
      // This can determine that the template is called, but not its param args.
      // TODO(b/179912526): can this syntax specify param args? if so, add corresponding test case
      String destTemplateName = templateLiteralNode.getResolvedName();
      return Optional.of(
          TemplateCallMetadata.TemplateCall.newBuilder()
              .setDestTemplateName(destTemplateName)
              .build());
    } else if (exprNode instanceof VarRefNode varRefNode) {
      return resolveVarRefTemplateCall(varRefNode, TemplateCall.newBuilder());
    } else if (!(exprNode instanceof MethodCallNode bind
        && bind.getMethodName().identifier().equals("bind"))) {
      return Optional.empty();
    }

    MethodCallNode bind = (MethodCallNode) exprNode;
    // Get the template or variable reference that calls bind.
    ExprNode bindCaller = bind.getChild(0);
    TemplateCall.Builder templateCall =
        TemplateCall.newBuilder()
            .addAllParamArgs(getBoundTemplateParams(bind))
            // TODO(b/179912526): when / if needed, handle soy element composition + delcalls here
            .setIsDelcall(false);
    if (bindCaller instanceof VarRefNode varRefNode) {
      return resolveVarRefTemplateCall(varRefNode, templateCall);
    }
    if (bindCaller instanceof TemplateLiteralNode templateLiteralNode) {
      String destTemplateName = templateLiteralNode.getResolvedName();
      return Optional.of(templateCall.setDestTemplateName(destTemplateName).build());
    }
    return Optional.empty();
  }

  /**
   * Resolves a varRefNode, that respresents a template call, back to its source value.
   *
   * @param varRefNode the variable reference used in the template call.
   * @param templateCallInfo TemplateCall builder that contains information about the template Call
   * @return a TemplateCall with all of the params bound from the source param or template to the
   *     call to the variable reference. If the varRefNode's source value is not a template literal
   *     or param of type template, the optional will be empty.
   */
  private static Optional<TemplateCallMetadata.TemplateCall> resolveVarRefTemplateCall(
      VarRefNode varRefNode, TemplateCallMetadata.TemplateCall.Builder templateCallInfo) {
    if (templateCallInfo.getDestTemplateName().isEmpty()) {
      templateCallInfo.setDestTemplateName(varRefNode.getName());
    }
    VarDefn varDefn = varRefNode.getDefnDecl();

    if (varDefn.kind() == VarDefn.Kind.PARAM
        && (varDefn.type().getKind() == SoyType.Kind.TEMPLATE_TYPE
            || varDefn.type().getKind() == SoyType.Kind.TEMPLATE)) {
      return Optional.of(templateCallInfo.setSourceParam(varDefn.name()).build());
    } else if (varDefn instanceof LocalVar localVar) {
      LocalVarNode localVarDefn = localVar.declaringNode();
      if (localVarDefn instanceof LetValueNode letValueNode) {
        ExprNode letExpression = letValueNode.getExpr().getRoot();
        if (letExpression instanceof TemplateLiteralNode templateLiteralNode) {
          return Optional.of(
              templateCallInfo.setSourceTemplate(templateLiteralNode.getResolvedName()).build());
        } else if (letExpression instanceof MethodCallNode bind
            && bind.getMethodName().identifier().equals("bind")) {
          templateCallInfo.addAllParamArgs(getBoundTemplateParams(bind));
          ExprNode bindCaller = bind.getChild(0);
          if (bindCaller instanceof TemplateLiteralNode templateLiteralNode) {
            return Optional.of(
                templateCallInfo.setSourceTemplate(templateLiteralNode.getResolvedName()).build());
          } else if (bindCaller instanceof VarRefNode varRefNodeBind) {
            return resolveVarRefTemplateCall(varRefNodeBind, templateCallInfo);
          }
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Creates a proto representation of a param provided to a template call.
   *
   * @param callParamNode a param passed to a template call.
   * @return a ParamArg with the same key as the param and the VarRefInfo or TemplateCall pertaining
   *     to its value, if applicable.
   */
  private static TemplateCallMetadata.ParamArg createParamArg(CallParamNode callParamNode) {
    // only shorthand param ref syntax is supported
    if (!(callParamNode instanceof CallParamValueNode callParamValueNode)) {
      return TemplateCallMetadata.ParamArg.newBuilder()
          .setKey(callParamNode.getKey().identifier())
          .build();
    }
    ExprNode possibleParamRefExpr = callParamValueNode.getExpr().getRoot();
    return resolveParam(callParamNode.getKey().identifier(), possibleParamRefExpr);
  }

  /**
   * Builds a paramArg based on the key and valueExpr provided.
   *
   * @param key the key of a param passed to a template call.
   * @param valueExpr the value of param passed to a template call.
   * @return a ParamArg containing the key and the template call or varRefInfo corresponding to
   *     valueExpr.
   */
  private static TemplateCallMetadata.ParamArg resolveParam(String key, ExprNode valueExpr) {
    TemplateCallMetadata.ParamArg.Builder paramArg =
        TemplateCallMetadata.ParamArg.newBuilder().setKey(key);
    TemplateCallMetadata.VarRefInfo varRefInfo =
        resolveLocalVarRefToParamRef(valueExpr, TemplateCallMetadata.VarRefInfo.newBuilder());
    if (varRefInfo.getHeaderParam().isEmpty()) {
      Optional<TemplateCallMetadata.TemplateCall> boundTemplate =
          resolveTemplateReference(valueExpr);
      if (boundTemplate.isPresent()) {
        return paramArg.setBoundTemplate(boundTemplate.get()).build();
      }
    }
    return paramArg.setVarRef(varRefInfo).build();
  }

  /**
   * Returns ParamArgs containing keys and their corresponding values.
   *
   * @param keys the keys of params passed to a function call.
   * @param values the values of params passed to a function call
   */
  private static ImmutableList<TemplateCallMetadata.ParamArg> getFunctionParams(
      List<Identifier> keys, List<ExprNode> values) {
    List<TemplateCallMetadata.ParamArg> callParamArgs = newArrayList();
    if (keys.size() != values.size()) {
      throw new IllegalArgumentException("Both keys and values must be the same size");
    }
    for (int i = 0; i < keys.size(); i++) {
      callParamArgs.add(resolveParam(keys.get(i).identifier(), values.get(i)));
    }
    return ImmutableList.copyOf(callParamArgs);
  }

  /**
   * Resolves all params of a MethodCallNode, that corresponds to a template bind statement.
   *
   * @param bind a template bind statement.
   * @return a list of ParamArgs that correspond to params bound to the template.
   */
  private static ImmutableList<TemplateCallMetadata.ParamArg> getBoundTemplateParams(
      MethodCallNode bind) {
    ExprNode possibleBindParams = bind.getParam(0);
    if (!(possibleBindParams instanceof RecordLiteralNode recordLiteralNode)) {
      return ImmutableList.of();
    }
    List<ExprNode> bindExprs = recordLiteralNode.getChildren();
    return getFunctionParams(recordLiteralNode.getKeys(), bindExprs);
  }

  /**
   * Resolves a local variable back to the param it was derived from, if applicable.
   *
   * @param varExpr node that describes a variable expression.
   * @param varRefInfo a builder for the VarRefInfo proto that contains information about varExpr.
   */
  private static TemplateCallMetadata.VarRefInfo resolveLocalVarRefToParamRef(
      ExprNode varExpr, TemplateCallMetadata.VarRefInfo.Builder varRefInfo) {
    if (varExpr instanceof VarRefNode varRefNode) {
      VarDefn possibleParamRefDef = varRefNode.getDefnDecl();
      if (possibleParamRefDef.kind() == VarDefn.Kind.PARAM) {
        varRefInfo.setHeaderParam(possibleParamRefDef.name());
        return varRefInfo.build();
      } else if (possibleParamRefDef instanceof LocalVar localVar) {
        LocalVarNode varRefNodeLocal = localVar.declaringNode();
        // The LocalVarNode point to a for loop local variable
        if (varRefNodeLocal instanceof ForNonemptyNode forNonemptyNode) {
          varRefInfo.setUsesListIndex(true);
          return resolveLocalVarRefToParamRef(forNonemptyNode.getExpr().getRoot(), varRefInfo);
        } else if (varRefNodeLocal instanceof LetValueNode letValueNode) {
          // The LocalVarNode is a let statement
          return resolveLocalVarRefToParamRef(letValueNode.getExpr().getRoot(), varRefInfo);
        }
      }
    } else if (varExpr instanceof ItemAccessNode itemAccessNode) {
      varRefInfo.setUsesListIndex(true);
      return resolveLocalVarRefToParamRef(itemAccessNode.getBaseExprChild(), varRefInfo);
    } else if (varExpr instanceof FieldAccessNode fieldAccessNode) {
      varRefInfo.setDataAccessAlias(fieldAccessNode.getFieldName());
      return resolveLocalVarRefToParamRef(fieldAccessNode.getBaseExprChild(), varRefInfo);
    } else if (varExpr instanceof FunctionNode functionNode) {
      if (functionNode.getFunctionName().equals("checkNotNull")) {
        // this function accepts 1 expr and does not meaningfully change its value
        return resolveLocalVarRefToParamRef(functionNode.getParam(0), varRefInfo);
      }
    }

    return varRefInfo.build();
  }

  /**
   * Returns {@code true} if exprNode is a template bind statement.
   *
   * <p>Example: template.bind(record(key: value, ...))
   *
   * @param exprNode Node that describes an expression.
   */
  private static boolean isBindStatement(ExprNode exprNode) {
    return exprNode.getKind() == Kind.METHOD_CALL_NODE
        && ((MethodCallNode) exprNode).getMethodName().identifier().equals("bind");
  }

  private static String getDestTemplateName(CallNode callNode) {
    return switch (callNode) {
      case CallBasicNode basicNode ->
          basicNode.isStaticCall()
              ? basicNode.getCalleeName()
              : basicNode.getCalleeExpr().toSourceString();
      case CallDelegateNode delegateNode -> delegateNode.getDelCalleeName();
      default -> throw new IllegalStateException("Unknown CallNode kind: " + callNode.getKind());
    };
  }
}
