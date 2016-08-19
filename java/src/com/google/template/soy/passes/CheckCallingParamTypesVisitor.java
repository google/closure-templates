/*
 * Copyright 2014 Google Inc.
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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.passes.FindIndirectParamsVisitor.IndirectParamsInfo;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.HeaderParam;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.soytree.defn.TemplateParam.DeclLoc;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.SanitizedType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.proto.SoyProtoType;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Checks that calling arguments match the declared parameter types of the called template.
 * Doesn't check for missing parameters; that is done by {@link CheckCallsVisitor}.
 *
 * <p>In addition to checking that static types match and flagging errors, this
 * visitor also stores a set of TemplateParam object in each CallNode for all the
 * params that require runtime checking.
 *
 * Note: This pass requires that the ResolveExpressionTypesVisitor has already
 * been run.
 *
 */
final class CheckCallingParamTypesVisitor extends AbstractSoyNodeVisitor<Void> {

  private static final SoyErrorKind ARGUMENT_TYPE_MISMATCH =
      SoyErrorKind.of("Type mismatch on param {0}: expected: {1}, actual: {2}.");
  private static final SoyErrorKind PASSING_PROTOBUF_FROM_STRICT_TO_NON_STRICT =
      SoyErrorKind.of("Passing protobuf {0} of type {1} to non-strict template not allowed.");

  /** Registry of all templates in the Soy tree. */
  private final TemplateRegistry templateRegistry;

  /** The current template being checked. */
  private TemplateNode callerTemplate;

  /** Map of all template parameters, both explicit and implicit, organized by template. */
  private final Map<TemplateNode, TemplateParamTypes> paramTypesMap = new HashMap<>();
  private final ErrorReporter errorReporter;

  CheckCallingParamTypesVisitor(TemplateRegistry registry, ErrorReporter errorReporter) {
    this.templateRegistry = registry;
    this.errorReporter = errorReporter;
  }

  @Override protected void visitCallBasicNode(CallBasicNode node) {
    TemplateNode callee = templateRegistry.getBasicTemplate(node.getCalleeName());
    if (callee != null) {
      Set<TemplateParam> paramsToRuntimeCheck = checkCallParamTypes(node, callee);
      node.setParamsToRuntimeCheck(paramsToRuntimeCheck);
    }
    visitChildren(node);
  }

  @Override protected void visitCallDelegateNode(CallDelegateNode node) {
    ImmutableMap.Builder<TemplateDelegateNode, ImmutableList<TemplateParam>>
        paramsToCheckByTemplate = ImmutableMap.builder();
    ImmutableMultimap<String, TemplateDelegateNode> delTemplateNameToValues =
        templateRegistry.getDelTemplateSelector().delTemplateNameToValues();
    for (TemplateDelegateNode delTemplate : delTemplateNameToValues.get(node.getDelCalleeName())) {
      Set<TemplateParam> params = checkCallParamTypes(node, delTemplate);
      paramsToCheckByTemplate.put(delTemplate, ImmutableList.copyOf(params));
    }
    node.setParamsToRuntimeCheck(paramsToCheckByTemplate.build());

    visitChildren(node);
  }

  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

  @Override protected void visitTemplateNode(TemplateNode node) {
    callerTemplate = node;
    visitChildren(node);
    callerTemplate = null;
  }

  /**
   * Returns the subset of {@link TemplateNode#getParams() callee params} that require runtime type
   * checking.
   */
  private Set<TemplateParam> checkCallParamTypes(CallNode call, TemplateNode callee) {
    TemplateParamTypes calleeParamTypes = getTemplateParamTypes(callee);
    // Explicit params being passed by the CallNode
    Set<String> explicitParams = new HashSet<>();
    // The set of params the need runtime type checking at template call time. We start this with
    // all the params of the callee and remove each param that is statically verified.
    Set<String> paramNamesToRuntimeCheck = new HashSet<>(calleeParamTypes.params.keySet());
    // indirect params should be checked at their callsites, not this one.
    paramNamesToRuntimeCheck.removeAll(calleeParamTypes.indirectParamNames);

    // First check all the {param} blocks of the caller to make sure that the types match.
    for (CallParamNode callerParam : call.getChildren()) {
      SoyType argType = null;
      if (callerParam.getKind() == SoyNode.Kind.CALL_PARAM_VALUE_NODE) {
        ExprNode expr = ((CallParamValueNode) callerParam).getValueExprUnion().getExpr();
        if (expr != null) {
          argType = expr.getType();
        }
      } else if (callerParam.getKind() == SoyNode.Kind.CALL_PARAM_CONTENT_NODE) {
        ContentKind contentKind = ((CallParamContentNode) callerParam).getContentKind();
        argType = contentKind == null
            ? StringType.getInstance()
            : SanitizedType.getTypeForContentKind(contentKind);
      }
      String paramName = callerParam.getKey();
      if (argType != null) {
        // The types of the parameters. If this is an explicitly declared parameter,
        // then this collection will have only one member; If it's an implicit
        // parameter then this may contain multiple types. Note we don't use
        // a union here because the rules are a bit different than the normal rules
        // for assigning union types.
        // It's possible that the set may be empty, because all of the callees
        // are external. In that case there's nothing we can do, so we don't
        // report anything.
        Collection<SoyType> declaredParamTypes = calleeParamTypes.params.get(paramName);
        boolean staticTypeSafe = true;
        for (SoyType formalType : declaredParamTypes) {
          staticTypeSafe &= checkArgumentAgainstParamType(
              callerParam.getSourceLocation(), paramName, argType, formalType,
              calleeParamTypes);
        }
        if (staticTypeSafe) {
          paramNamesToRuntimeCheck.remove(paramName);
        }
      }

      explicitParams.add(paramName);
    }

    // If the caller is passing data via data="all" then we look for matching static param
    // declarations in the callers template and see if there are type errors there.
    if (call.dataAttribute().isPassingData()) {
      if (call.dataAttribute().isPassingAllData() && callerTemplate.getParams() != null) {
        // Check indirect params that are passed via data="all".
        // We only need to check explicit params of calling template here.
        for (TemplateParam callerParam : callerTemplate.getParams()) {
          if (!(callerParam instanceof HeaderParam)) {
            continue;
          }
          String paramName = callerParam.name();

          // The parameter is explicitly overridden with another value, which we
          // already checked.
          if (explicitParams.contains(paramName)) {
            continue;
          }

          Collection<SoyType> declaredParamTypes = calleeParamTypes.params.get(paramName);
          boolean staticTypeSafe = true;
          for (SoyType formalType : declaredParamTypes) {
            staticTypeSafe &= checkArgumentAgainstParamType(
                call.getSourceLocation(), paramName, callerParam.type(), formalType,
                calleeParamTypes);
          }
          if (staticTypeSafe) {
            paramNamesToRuntimeCheck.remove(paramName);
          }
        }
      } else {
        // TODO: Check if the fields of the type of data arg can be assigned to the params.
        // This is possible for some types, and never allowed for other types.
      }
    }
    // We track the set as names above and transform to TemplateParams here because the above loops
    // are over the {param}s of the caller and TemplateParams of the callers template, so all we
    // have are the names of the parameters.  To convert them to a TemplateParam of the callee we
    // need to match the names and it is easier to do that as one pass at the end instead of
    // iteratively throughout.
    Set<TemplateParam> paramsToRuntimeCheck = new HashSet<>();
    for (TemplateParam param : callee.getParams()) {
      if (paramNamesToRuntimeCheck.remove(param.name())) {
        paramsToRuntimeCheck.add(param);
      }
    }
    // sanity check
    Preconditions.checkState(paramNamesToRuntimeCheck.isEmpty(),
        "Unexpected callee params %s", paramNamesToRuntimeCheck);
    return paramsToRuntimeCheck;
  }


  /**
   * Check that the argument passed to the template is compatible with the template
   * parameter type.
   * @param location The location to report a type check error.
   * @param paramName the name of the parameter.
   * @param argType The type of the value being passed.
   * @param formalType The type of the parameter.
   * @param calleeParams metadata about the callee parameters
   * @return true if runtime type checks can be elided for this param
   */
  private boolean checkArgumentAgainstParamType(
      SourceLocation location, String paramName, SoyType argType, SoyType formalType,
      TemplateParamTypes calleeParams) {
    if (!calleeParams.isStrictlyTyped
        && formalType.getKind() == SoyType.Kind.UNKNOWN ||
        formalType.getKind() == SoyType.Kind.ANY) {
      // Special rules for unknown / any
      if (argType instanceof SoyProtoType) {
        errorReporter.report(
            location,
            PASSING_PROTOBUF_FROM_STRICT_TO_NON_STRICT,
            paramName,
            argType);
      }
    } else if (argType.getKind() == SoyType.Kind.UNKNOWN) {
      // Special rules for unknown / any
      //
      // This check disabled: We now allow maps created from protos to be passed
      // to a function accepting a proto, this makes migration easier.
      // (See GenJsCodeVisitor.genParamTypeChecks).
      // TODO(user): Re-enable at some future date?
      // if (formalType instanceof SoyProtoType) {
      //   reportProtoArgumentTypeMismatch(call, paramName, formalType, argType);
      // }
    } else {
      if (!formalType.isAssignableFrom(argType)) {
        if (calleeParams.isIndirect(paramName)
            && argType.getKind() == SoyType.Kind.UNION
            && ((UnionType) argType).isNullable()) {
          if (SoyTypes.makeNullable(formalType).isAssignableFrom(argType)) {
            // Special case for indirect params: Allow a nullable type to be assigned
            // to a non-nullable type if the non-nullable type is an indirect parameter type.
            // The reason is because without flow analysis, we can't know whether or not
            // there are if-statements preventing null from being passed as an indirect
            // param, so we assume all indirect params are optional.
            return false;
          }
        }
        errorReporter.report(location, ARGUMENT_TYPE_MISMATCH, paramName, formalType, argType);
      }
    }
    return true;
  }

  /**
   * Get the parameter types for a callee.
   * @param node The template being called.
   * @return The set of template parameters, both explicit and implicit.
   */
  private TemplateParamTypes getTemplateParamTypes(TemplateNode node) {
    TemplateParamTypes paramTypes = paramTypesMap.get(node);
    if (paramTypes == null) {
      paramTypes = new TemplateParamTypes();

      // Store all of the explicitly declared param types
      if (node.getParams() != null) {
        for (TemplateParam param : node.getParams()) {
          if (param.declLoc() == DeclLoc.SOY_DOC) {
            paramTypes.isStrictlyTyped = false;
          }
          Preconditions.checkNotNull(param.type());
          paramTypes.params.put(param.name(), param.type());
        }
      }

      // Store indirect params where there's no conflict with explicit params.
      // Note that we don't check here whether the explicit type and the implicit
      // types are in agreement - that will be done when it's this template's
      // turn to be analyzed as a caller.
      IndirectParamsInfo ipi = new FindIndirectParamsVisitor(templateRegistry).exec(node);
      for (String indirectParamName: ipi.indirectParamTypes.keySet()) {
        if (paramTypes.params.containsKey(indirectParamName)) {
          continue;
        }
        paramTypes.params.putAll(indirectParamName, ipi.indirectParamTypes.get(indirectParamName));
        paramTypes.indirectParamNames.add(indirectParamName);
      }

      // Save the param types map
      paramTypesMap.put(node, paramTypes);
    }
    return paramTypes;
  }


  private static class TemplateParamTypes {
    public boolean isStrictlyTyped = true;
    public final Multimap<String, SoyType> params = HashMultimap.create();
    public final Set<String> indirectParamNames = new HashSet<>();

    public boolean isIndirect(String paramName) {
      return indirectParamNames.contains(paramName);
    }
  }
}
