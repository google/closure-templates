/*
 * Copyright 2017 Google Inc.
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.passes.IndirectParamsCalculator.IndirectParamsInfo;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateMetadata.Parameter;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnionType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * This compiler pass runs several checks on {@code CallNode}s.
 *
 * <ul>
 *   <li>Checks that calling arguments match the declared parameter types of the called template.
 *   <li>Checks missing, unused or duplicate parameters.
 *   <li>Checks strict-html templates can only call other strict-html templates from an html
 *       context.
 * </ul>
 *
 * <p>In addition to checking that static types match and flagging errors, this visitor also stores
 * a set of TemplateParam object in each {@code CallNode} for all the params that require runtime
 * checking.
 *
 * <p>Note: This pass requires that the ResolveExpressionTypesPass has already been run.
 */
final class CheckTemplateCallsPass extends CompilerFileSetPass {

  static final SoyErrorKind ARGUMENT_TYPE_MISMATCH =
      SoyErrorKind.of(
          "Type mismatch on param {0}: expected: {1}, actual: {2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind DUPLICATE_PARAM = SoyErrorKind.of("Duplicate param ''{0}''.");
  private static final SoyErrorKind PASSES_UNUSED_PARAM =
      SoyErrorKind.of(
          "''{0}'' is not a declared parameter of {1} or any indirect callee.{2}",
          StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind MISSING_PARAM = SoyErrorKind.of("Call missing required {0}.");
  private static final SoyErrorKind PASSING_PROTOBUF_FROM_STRICT_TO_NON_STRICT =
      SoyErrorKind.of("Passing protobuf {0} of type {1} to an untyped template is not allowed.");
  private static final SoyErrorKind STRICT_HTML =
      SoyErrorKind.of(
          "Found call to non stricthtml template. Strict HTML template "
              + "can only call other strict HTML templates from an HTML context.");

  /** The error reporter that is used in this compiler pass. */
  private final ErrorReporter errorReporter;

  private static final ImmutableTable<SoyType, SoyType, BuiltinFunction>
      AVAILABLE_CALL_SITE_COERCIONS =
          new ImmutableTable.Builder<SoyType, SoyType, BuiltinFunction>()
              .put(IntType.getInstance(), FloatType.getInstance(), BuiltinFunction.TO_FLOAT)
              .build();

  CheckTemplateCallsPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public Result run(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, TemplateRegistry registry) {
    CheckCallsHelper helper = new CheckCallsHelper(registry);
    for (SoyFileNode file : sourceFiles) {
      for (TemplateNode template : file.getChildren()) {
        for (CallBasicNode callNode :
            SoyTreeUtils.getAllNodesOfType(template, CallBasicNode.class)) {
          helper.checkCall(template, callNode);
        }
        for (CallDelegateNode callNode :
            SoyTreeUtils.getAllNodesOfType(template, CallDelegateNode.class)) {
          helper.checkCall(template, callNode);
        }
      }
    }

    return Result.CONTINUE;
  }

  private final class CheckCallsHelper {

    /** Registry of all templates in the Soy tree. */
    private final TemplateRegistry templateRegistry;

    /** Map of all template parameters, both explicit and implicit, organized by template. */
    private final Map<TemplateMetadata, TemplateParamTypes> paramTypesMap = new HashMap<>();

    CheckCallsHelper(TemplateRegistry registry) {
      this.templateRegistry = registry;
    }

    void checkCall(TemplateNode callerTemplate, CallBasicNode node) {
      TemplateMetadata callee = templateRegistry.getBasicTemplateOrElement(node.getCalleeName());
      if (callee != null) {
        Predicate<String> paramsToRuntimeCheck = checkCallParamTypes(callerTemplate, node, callee);
        node.setParamsToRuntimeCheck(paramsToRuntimeCheck);
        checkCallParamNames(node, callee);
        checkPassesUnusedParams(node, callee);
      }
      checkStrictHtml(callerTemplate, node, callee);
    }

    void checkCall(TemplateNode callerTemplate, CallDelegateNode node) {
      ImmutableMap.Builder<String, Predicate<String>> paramsToCheckByTemplate =
          ImmutableMap.builder();
      ImmutableList<TemplateMetadata> potentialCallees =
          templateRegistry
              .getDelTemplateSelector()
              .delTemplateNameToValues()
              .get(node.getDelCalleeName());
      for (TemplateMetadata delTemplate : potentialCallees) {
        Predicate<String> params = checkCallParamTypes(callerTemplate, node, delTemplate);
        paramsToCheckByTemplate.put(delTemplate.getTemplateName(), params);
        checkCallParamNames(node, delTemplate);
        // We don't call checkPassesUnusedParams here because we might not know all delegates.
      }
      node.setParamsToRuntimeCheck(paramsToCheckByTemplate.build());
      // NOTE: we only need to check one of them.  If there is more than one of them and they have
      // different content kinds of stricthtml settings then the CheckDelegatesPass will flag that
      // as an error independently.
      if (!potentialCallees.isEmpty()) {
        checkStrictHtml(callerTemplate, node, potentialCallees.get(0));
      }
    }

    /**
     * Returns the subset of {@link TemplateNode#getParams() callee params} that require runtime
     * type checking.
     */
    private Predicate<String> checkCallParamTypes(
        TemplateNode callerTemplate, CallNode call, TemplateMetadata callee) {
      TemplateParamTypes calleeParamTypes = getTemplateParamTypes(callee);
      // Explicit params being passed by the CallNode
      Set<String> explicitParams = new HashSet<>();
      // The set of params that need runtime type checking at template call time. We start this with
      // all the params of the callee and remove each param that is statically verified.
      Set<String> paramNamesToRuntimeCheck = new HashSet<>(calleeParamTypes.params.keySet());
      // indirect params should be checked at their callsites, not this one.
      paramNamesToRuntimeCheck.removeAll(calleeParamTypes.indirectParamNames);

      // First check all the {param} blocks of the caller to make sure that the types match.
      for (CallParamNode callerParam : call.getChildren()) {
        String paramName = callerParam.getKey().identifier();
        // The types of the parameters. If this is an explicitly declared parameter,
        // then this collection will have only one member; If it's an implicit
        // parameter then this may contain multiple types. Note we don't use
        // a union here because the rules are a bit different than the normal rules
        // for assigning union types.
        // It's possible that the set may be empty, because all of the callees
        // are external. In that case there's nothing we can do, so we don't
        // report anything.
        Collection<SoyType> declaredParamTypes = calleeParamTypes.params.get(paramName);

        // Type of the param value. May be null if the param is a v1 expression.
        SoyType argType = null;
        if (callerParam.getKind() == SoyNode.Kind.CALL_PARAM_VALUE_NODE) {
          CallParamValueNode node = (CallParamValueNode) callerParam;
          ExprNode expr = node.getExpr();
          if (expr != null) {
            argType = maybeCoerceType(node, expr.getType(), declaredParamTypes);
          }
        } else if (callerParam.getKind() == SoyNode.Kind.CALL_PARAM_CONTENT_NODE) {
          SanitizedContentKind contentKind = ((CallParamContentNode) callerParam).getContentKind();
          argType =
              contentKind == null
                  ? StringType.getInstance()
                  : SanitizedType.getTypeForContentKind(contentKind);
        } else {
          throw new AssertionError(); // CallParamNode shouldn't have any other kind of child
        }

        // If the param is a v1 expression (so argType == null) we can't check anything, or if the
        // calculated type is an error type (because we already reported a type error for this
        // expression) don't check whether it matches the formal param.
        if (argType != null && argType.getKind() != SoyType.Kind.ERROR) {
          boolean staticTypeSafe = true;
          for (SoyType formalType : declaredParamTypes) {
            staticTypeSafe &=
                checkArgumentAgainstParamType(
                    callerParam.getSourceLocation(),
                    paramName,
                    argType,
                    formalType,
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
      if (call.isPassingData()) {
        if (call.isPassingAllData()) {
          // Check indirect params that are passed via data="all".
          // We only need to check explicit params of calling template here.
          for (TemplateParam callerParam : callerTemplate.getParams()) {
            String paramName = callerParam.name();

            // The parameter is explicitly overridden with another value, which we
            // already checked.
            if (explicitParams.contains(paramName)) {
              continue;
            }

            Collection<SoyType> declaredParamTypes = calleeParamTypes.params.get(paramName);
            boolean staticTypeSafe = true;
            for (SoyType formalType : declaredParamTypes) {
              staticTypeSafe &=
                  checkArgumentAgainstParamType(
                      call.getSourceLocation(),
                      paramName,
                      callerParam.type(),
                      formalType,
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

      return ImmutableSet.copyOf(paramNamesToRuntimeCheck)::contains;
    }

    /**
     * For int values passed into template param float, perform automatic type coercion from the
     * call param value to the template param type.
     *
     * <p>Supported coercions:
     *
     * <ul>
     *   <li>int -> float
     *   <li>int -> float|null
     * </ul>
     *
     * @param paramNode Node containing the call param value
     * @param argType Type of the call param value
     * @param declaredTypes Types accepted by the template param
     * @return the new coerced type
     */
    @CheckReturnValue
    private SoyType maybeCoerceType(
        CallParamValueNode paramNode, SoyType argType, Collection<SoyType> declaredTypes) {
      if (AVAILABLE_CALL_SITE_COERCIONS.row(argType).isEmpty()) {
        return argType;
      }
      for (SoyType formalType : declaredTypes) {
        if (formalType.isAssignableFrom(argType)) {
          return argType; // template already accepts value, no need to coerce
        }
      }
      for (SoyType coercionTargetType : AVAILABLE_CALL_SITE_COERCIONS.row(argType).keySet()) {
        BuiltinFunction function = null;
        for (SoyType formalType : declaredTypes) {
          if (!formalType.isAssignableFrom(coercionTargetType)) {
            continue;
          }
          if (function == null) {
            function = AVAILABLE_CALL_SITE_COERCIONS.get(argType, coercionTargetType);
          } else {
            // This is actually a bad state that shouldn't happen because there should only be one
            // coercing function.
            function = null;
            break;
          }
        }
        if (function == null) {
          continue;
        }
        ExprRootNode root = paramNode.getExpr();

        // create a node to wrap param in coercion
        FunctionNode newParam =
            new FunctionNode(
                Identifier.create(function.getName(), root.getRoot().getSourceLocation()),
                function,
                root.getRoot().getSourceLocation());
        newParam.setType(coercionTargetType);

        newParam.addChild(root.getRoot());
        root.addChild(newParam);
        return coercionTargetType;
      }
      return argType;
    }

    /**
     * Check that the argument passed to the template is compatible with the template parameter
     * type.
     *
     * @param location The location to report a type check error.
     * @param paramName the name of the parameter.
     * @param argType The type of the value being passed.
     * @param formalType The type of the parameter.
     * @param calleeParams metadata about the callee parameters
     * @return true if runtime type checks can be elided for this param
     */
    private boolean checkArgumentAgainstParamType(
        SourceLocation location,
        String paramName,
        SoyType argType,
        SoyType formalType,
        TemplateParamTypes calleeParams) {
      if (formalType.getKind() == SoyType.Kind.ANY) {
        // Special rules for unknown / any
        if (argType.getKind() == SoyType.Kind.PROTO) {
          errorReporter.report(
              location, PASSING_PROTOBUF_FROM_STRICT_TO_NON_STRICT, paramName, argType);
        }
      } else if (argType.getKind() == SoyType.Kind.UNKNOWN
          // TODO(b/69048281): consider allowing passing `?`-typed values to `map`-typed params.
          // As long as there are two map types, `map` cannot be assigned <em>to</em> the unknown
          // type; the jssrc backend wouldn't know what code to generate for bracket access on a
          // `?`-typed value. However, assigning `map` <em>from</em> the unknown type is possible
          // and perhaps useful, since it is equivalent to a runtime type assertion. During the
          // legacy_object_map to map migration, it is disallowed in order to catch bugs in the
          // migration tool (e.g. upgrading a callee's param to `map` without inserting
          // `legacyObjectMapToMap` calls in the caller). But it may be useful in order to work with
          // recursive map-like structures such as JSON.
          && SoyTypes.tryRemoveNull(formalType).getKind() != Kind.MAP
          // ve and ve_data usage is limited to prevent abuse, so don't allow the unknown type to be
          // upgraded to the ve or ve_data types.
          && SoyTypes.tryRemoveNull(formalType).getKind() != Kind.VE
          && SoyTypes.tryRemoveNull(formalType).getKind() != Kind.VE_DATA) {
        // Special rules for unknown / any
        //
        // This check disabled: We now allow maps created from protos to be passed
        // to a function accepting a proto, this makes migration easier.
        // (See GenJsCodeVisitor.genParamTypeChecks).
        // TODO(user): Re-enable at some future date?
        // if (formalType.getKind() == SoyType.Kind.PROTO || SoyType.Kind.PROTO_ENUM) {
        //   reportProtoArgumentTypeMismatch(call, paramName, formalType, argType);
        // }
      } else {
        if (!formalType.isAssignableFrom(argType)) {
          if (calleeParams.isIndirect(paramName)
              && argType.getKind() == SoyType.Kind.UNION
              && ((UnionType) argType).isNullable()
              && SoyTypes.makeNullable(formalType).isAssignableFrom(argType)) {
            // Special case for indirect params: Allow a nullable type to be assigned
            // to a non-nullable type if the non-nullable type is an indirect parameter type.
            // The reason is because without flow analysis, we can't know whether or not
            // there are if-statements preventing null from being passed as an indirect
            // param, so we assume all indirect params are optional.
            return false;
          }
          errorReporter.report(location, ARGUMENT_TYPE_MISMATCH, paramName, formalType, argType);
        }
      }
      return true;
    }

    /**
     * Get the parameter types for a callee.
     *
     * @param callee The template being called.
     * @return The set of template parameters, both explicit and implicit.
     */
    private TemplateParamTypes getTemplateParamTypes(TemplateMetadata callee) {
      TemplateParamTypes paramTypes = paramTypesMap.get(callee);
      if (paramTypes == null) {
        paramTypes = new TemplateParamTypes();

        // Store all of the explicitly declared param types
        for (Parameter param : callee.getParameters()) {
          paramTypes.params.put(param.getName(), param.getType());
        }

        // Store indirect params where there's no conflict with explicit params.
        // Note that we don't check here whether the explicit type and the implicit
        // types are in agreement - that will be done when it's this template's
        // turn to be analyzed as a caller.
        IndirectParamsInfo ipi =
            new IndirectParamsCalculator(templateRegistry).calculateIndirectParams(callee);
        for (String indirectParamName : ipi.indirectParamTypes.keySet()) {
          if (paramTypes.params.containsKey(indirectParamName)) {
            continue;
          }
          paramTypes.params.putAll(
              indirectParamName, ipi.indirectParamTypes.get(indirectParamName));
          paramTypes.indirectParamNames.add(indirectParamName);
        }

        // Save the param types map
        paramTypesMap.put(callee, paramTypes);
      }
      return paramTypes;
    }

    /**
     * Helper method that reports an error if a strict html template calls a non-strict html
     * template from HTML context.
     */
    private void checkStrictHtml(
        TemplateNode callerTemplate, CallNode caller, @Nullable TemplateMetadata callee) {
      // We should only check strict html if 1) the current template
      // sets stricthtml to true, and 2) the current call node is in HTML context.
      // Then we report an error if the callee is HTML but is not a strict HTML template.
      if (callerTemplate.isStrictHtml()
          && caller.getIsPcData()
          && callee != null
          && callee.getContentKind() == SanitizedContentKind.HTML
          && !callee.isStrictHtml()) {
        errorReporter.report(caller.getSourceLocation(), STRICT_HTML);
      }
    }

    /**
     * Helper method that reports an error if:
     *
     * <ul>
     *   <li>There are duplicate params for the caller.
     *   <li>Required parameters in callee template are not presented in the caller.
     * </ul>
     */
    private void checkCallParamNames(CallNode caller, TemplateMetadata callee) {
      if (callee != null) {
        // Get param keys passed by caller.
        Set<String> callerParamKeys = Sets.newHashSet();
        for (CallParamNode callerParam : caller.getChildren()) {
          boolean isUnique = callerParamKeys.add(callerParam.getKey().identifier());
          if (!isUnique) {
            // Found a duplicate param.
            errorReporter.report(
                callerParam.getKey().location(),
                DUPLICATE_PARAM,
                callerParam.getKey().identifier());
          }
        }
        // If all the data keys being passed are listed using 'param' commands, then check that all
        // required params of the callee are included.
        if (!caller.isPassingData()) {
          // Check param keys required by callee.
          List<String> missingParamKeys = Lists.newArrayListWithCapacity(2);
          for (Parameter calleeParam : callee.getParameters()) {
            if (calleeParam.isRequired() && !callerParamKeys.contains(calleeParam.getName())) {
              missingParamKeys.add(calleeParam.getName());
            }
          }
          // Report errors.
          if (!missingParamKeys.isEmpty()) {
            String errorMsgEnd =
                (missingParamKeys.size() == 1)
                    ? "param '" + missingParamKeys.get(0) + "'"
                    : "params " + missingParamKeys;
            errorReporter.report(caller.getSourceLocation(), MISSING_PARAM, errorMsgEnd);
          }
        }
      }
    }

    /** Reports error if unused params are passed to a template. */
    private void checkPassesUnusedParams(CallNode caller, TemplateMetadata callee) {
      if (caller.numChildren() == 0) {
        return;
      }
      Set<String> paramNames = Sets.newHashSet();
      for (Parameter param : callee.getParameters()) {
        paramNames.add(param.getName());
      }
      IndirectParamsInfo ipi = null; // Compute only if necessary.
      for (CallParamNode callerParam : caller.getChildren()) {
        String paramName = callerParam.getKey().identifier();
        if (paramNames.contains(paramName)) {
          continue;
        }
        if (ipi == null) {
          ipi = new IndirectParamsCalculator(templateRegistry).calculateIndirectParams(callee);
          // If the callee has unknown indirect params then we can't validate that this isn't one
          // of them. So just give up.
          if (ipi.mayHaveIndirectParamsInExternalCalls
              || ipi.mayHaveIndirectParamsInExternalDelCalls) {
            return;
          }
        }
        if (!ipi.indirectParams.containsKey(paramName)) {
          Set<String> allParams =
              ImmutableSet.<String>builder()
                  .addAll(paramNames)
                  .addAll(ipi.indirectParams.keySet())
                  .build();
          errorReporter.report(
              callerParam.getKey().location(),
              PASSES_UNUSED_PARAM,
              paramName,
              callee.getTemplateName(),
              SoyErrors.getDidYouMeanMessage(allParams, paramName));
        }
      }
    }

    private class TemplateParamTypes {
      public final Multimap<String, SoyType> params = HashMultimap.create();
      public final Set<String> indirectParamNames = new HashSet<>();

      public boolean isIndirect(String paramName) {
        return indirectParamNames.contains(paramName);
      }
    }
  }
}
