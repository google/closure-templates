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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.passes.IndirectParamsCalculator.IndirectParamsInfo;
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
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UnionType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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
@RunAfter(FinalizeTemplateRegistryPass.class)
final class CheckTemplateCallsPass implements CompilerFileSetPass {

  static final SoyErrorKind ARGUMENT_TYPE_MISMATCH =
      SoyErrorKind.of(
          "Type mismatch on param {0}: expected: {1}, actual: {2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind DUPLICATE_PARAM = SoyErrorKind.of("Duplicate param ''{0}''.");
  private static final SoyErrorKind PASSES_UNUSED_PARAM =
      SoyErrorKind.of(
          "''{0}'' is not a declared parameter of {1} or any indirect callee.{2}",
          StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind MISSING_PARAM = SoyErrorKind.of("Call missing required {0}.");
  private static final SoyErrorKind STRICT_HTML =
      SoyErrorKind.of(
          "Found call to non stricthtml template. Strict HTML template "
              + "can only call other strict HTML templates from an HTML context.");
  private static final SoyErrorKind CAN_ONLY_CALL_TEMPLATE_TYPES =
      SoyErrorKind.of("'{'call'}' is only valid on template types, but found type ''{0}''.");
  private static final SoyErrorKind CANNOT_CALL_MIXED_CONTENT_TYPE =
      SoyErrorKind.of("Cannot call expressions of different content types; found {0} and {1}.");

  private static final SoyErrorKind INVALID_DATA_EXPR =
      SoyErrorKind.of("''data='' should be a record type, found ''{0}''.", StyleAllowance.NO_CAPS);

  /** The error reporter that is used in this compiler pass. */
  private final ErrorReporter errorReporter;

  private final Supplier<TemplateRegistry> templateRegistryFull;

  CheckTemplateCallsPass(
      ErrorReporter errorReporter, Supplier<TemplateRegistry> templateRegistryFull) {
    this.errorReporter = errorReporter;
    this.templateRegistryFull = templateRegistryFull;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    CheckCallsHelper helper = new CheckCallsHelper(templateRegistryFull.get());
    for (SoyFileNode file : sourceFiles) {
      for (TemplateNode template : file.getTemplates()) {
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
    private final Map<TemplateType, TemplateParamTypes> paramTypesMap = new HashMap<>();

    CheckCallsHelper(TemplateRegistry registry) {
      this.templateRegistry = registry;
    }

    void checkCall(TemplateNode callerTemplate, CallBasicNode node) {
      SoyType calleeType = node.getCalleeExpr().getType();
      if (calleeType.getKind() == SoyType.Kind.TEMPLATE) {
        checkCall(callerTemplate, node, (TemplateType) calleeType);
      } else if (calleeType.getKind() == SoyType.Kind.UNION) {
        TemplateContentKind templateContentKind = null;
        for (SoyType member : ((UnionType) calleeType).getMembers()) {
          if (member.getKind() == SoyType.Kind.TEMPLATE) {
            // Check that all members of a union type have the same content kind.
            TemplateType templateType = (TemplateType) member;
            if (templateContentKind == null) {
              templateContentKind = templateType.getContentKind();
            } else if (!templateType.getContentKind().equals(templateContentKind)) {
              errorReporter.report(
                  node.getSourceLocation(),
                  CANNOT_CALL_MIXED_CONTENT_TYPE,
                  templateContentKind,
                  templateType.getContentKind());
            }
            checkCall(callerTemplate, node, templateType);
          } else {
            errorReporter.report(
                node.getSourceLocation(), CAN_ONLY_CALL_TEMPLATE_TYPES, calleeType);
          }
        }
      } else if (calleeType.getKind() == SoyType.Kind.UNKNOWN) {
        // We may end up with UNKNOWN here for external calls.
      } else {
        errorReporter.report(node.getSourceLocation(), CAN_ONLY_CALL_TEMPLATE_TYPES, calleeType);
      }
    }

    void checkCall(TemplateNode callerTemplate, CallBasicNode node, TemplateType calleeType) {
      checkCallParamNames(node, calleeType);
      checkPassesUnusedParams(node, calleeType);
      checkStrictHtml(callerTemplate, node, calleeType);
      checkCallParamTypes(callerTemplate, node, calleeType);
    }

    void checkCall(TemplateNode callerTemplate, CallDelegateNode node) {
      ImmutableList<TemplateMetadata> potentialCallees =
          templateRegistry
              .getDelTemplateSelector()
              .delTemplateNameToValues()
              .get(node.getDelCalleeName());
      for (TemplateMetadata delTemplate : potentialCallees) {
        TemplateType delTemplateType = delTemplate.getTemplateType();
        checkCallParamTypes(callerTemplate, node, delTemplateType);
        checkCallParamNames(node, delTemplateType);
        // We don't call checkPassesUnusedParams here because we might not know all delegates.
      }
      // NOTE: we only need to check one of them.  If there is more than one of them and they have
      // different content kinds of stricthtml settings then the CheckDelegatesPass will flag that
      // as an error independently.
      if (!potentialCallees.isEmpty()) {
        checkStrictHtml(callerTemplate, node, potentialCallees.get(0).getTemplateType());
      }
    }

    /**
     * Checks that the parameters being passed have compatble types and reports errors if they do
     * not.
     */
    private void checkCallParamTypes(
        TemplateNode callerTemplate, CallNode call, TemplateType callee) {
      TemplateParamTypes calleeParamTypes = getTemplateParamTypes(callee);
      // Explicit params being passed by the CallNode
      Set<String> explicitParams = new HashSet<>();

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

        // Type of the param value.
        SoyType argType;
        if (callerParam.getKind() == SoyNode.Kind.CALL_PARAM_VALUE_NODE) {
          CallParamValueNode node = (CallParamValueNode) callerParam;
          argType =
              RuntimeTypeCoercion.maybeCoerceType(node.getExpr().getRoot(), declaredParamTypes);
        } else if (callerParam.getKind() == SoyNode.Kind.CALL_PARAM_CONTENT_NODE) {
          SanitizedContentKind contentKind = ((CallParamContentNode) callerParam).getContentKind();
          argType =
              contentKind == null
                  ? StringType.getInstance()
                  : SanitizedType.getTypeForContentKind(contentKind);
        } else {
          throw new AssertionError(); // CallParamNode shouldn't have any other kind of child
        }

        for (SoyType formalType : declaredParamTypes) {
          checkArgumentAgainstParamType(
              callerParam.getSourceLocation(), paramName, argType, formalType, calleeParamTypes);
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
            for (SoyType formalType : declaredParamTypes) {
              checkArgumentAgainstParamType(
                  call.getSourceLocation(),
                  paramName,
                  callerParam.type(),
                  formalType,
                  calleeParamTypes);
            }
          }
        } else {
          ExprNode dataExpr = call.getDataExpr();
          // TODO(b/168852179): enforce that the correct set of properties are present
          if (!SoyTypes.isKindOrUnionOfKind(dataExpr.getType(), SoyType.Kind.RECORD)
              && dataExpr.getType().getKind() != SoyType.Kind.UNKNOWN
              // We allow 'any' due to a convention in wiz components :(
              && dataExpr.getType().getKind() != SoyType.Kind.ANY) {
            errorReporter.report(
                dataExpr.getSourceLocation(), INVALID_DATA_EXPR, dataExpr.getType());
          }
        }
      }
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
     */
    private void checkArgumentAgainstParamType(
        SourceLocation location,
        String paramName,
        SoyType argType,
        SoyType formalType,
        TemplateParamTypes calleeParams) {
      // We use loose assignability because soy templates generate runtime type checking code for
      // parameter types.  So passing loosely typed values will generally be checked at runtime.
      // Our runtime type checking code isn't perfect and varies by backend.
      if (!formalType.isAssignableFromLoose(argType)) {
        if (calleeParams.isIndirect(paramName)
            && formalType.isAssignableFromLoose(SoyTypes.tryRemoveNull(argType))) {
          // Special case for indirect params: Allow a nullable type to be assigned
          // to a non-nullable type if the non-nullable type is an indirect parameter type.
          // The reason is because without flow analysis, we can't know whether or not
          // there are if-statements preventing null from being passed as an indirect
          // param, so we assume all indirect params are optional.
          return;
        }
        errorReporter.report(location, ARGUMENT_TYPE_MISMATCH, paramName, formalType, argType);
      }
    }

    /**
     * Get the parameter types for a callee.
     *
     * @param callee The template being called.
     * @return The set of template parameters, both explicit and implicit.
     */
    private TemplateParamTypes getTemplateParamTypes(TemplateType callee) {
      return paramTypesMap.computeIfAbsent(callee, this::computeTemplateParamTypes);
    }

    private TemplateParamTypes computeTemplateParamTypes(TemplateType callee) {
      TemplateParamTypes paramTypes = new TemplateParamTypes();

      // Store all of the explicitly declared param types
      for (TemplateType.Parameter param : callee.getParameters()) {
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
        paramTypes.params.putAll(indirectParamName, ipi.indirectParamTypes.get(indirectParamName));
        paramTypes.indirectParamNames.add(indirectParamName);
      }
      return paramTypes;
    }

    /**
     * Helper method that reports an error if a strict html template calls a non-strict html
     * template from HTML context.
     */
    private void checkStrictHtml(
        TemplateNode callerTemplate, CallNode caller, TemplateType callee) {
      // We should only check strict html if 1) the current template
      // sets stricthtml to true, and 2) the current call node is in HTML context.
      // Then we report an error if the callee is HTML but is not a strict HTML template.
      if (callerTemplate.isStrictHtml()
          && caller.getIsPcData()
          && (callee.getContentKind().getSanitizedContentKind().isHtml()
              && !callee.isStrictHtml())) {
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
    private void checkCallParamNames(CallNode caller, TemplateType callee) {
      // Get param keys passed by caller.
      Set<String> callerParamKeys = Sets.newHashSet();
      for (CallParamNode callerParam : caller.getChildren()) {
        boolean isUnique = callerParamKeys.add(callerParam.getKey().identifier());
        if (!isUnique) {
          // Found a duplicate param.
          errorReporter.report(
              callerParam.getKey().location(), DUPLICATE_PARAM, callerParam.getKey().identifier());
        }
      }
      // If all the data keys being passed are listed using 'param' commands, then check that all
      // required params of the callee are included.
      if (!caller.isPassingData()) {
        // Check param keys required by callee.
        List<String> missingParamKeys = Lists.newArrayListWithCapacity(2);
        for (TemplateType.Parameter calleeParam : callee.getParameters()) {
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

    /** Reports error if unused params are passed to a template. */
    private void checkPassesUnusedParams(CallNode caller, TemplateType callee) {
      if (caller.numChildren() == 0) {
        return;
      }
      Set<String> paramNames = Sets.newHashSet();
      for (TemplateType.Parameter param : callee.getParameters()) {
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
              callee.getIdentifierForDebugging(),
              SoyErrors.getDidYouMeanMessage(allParams, paramName));
        }
      }
    }
  }

  private static final class TemplateParamTypes {
    final Multimap<String, SoyType> params = HashMultimap.create();
    final Set<String> indirectParamNames = new HashSet<>();

    boolean isIndirect(String paramName) {
      return indirectParamNames.contains(paramName);
    }
  }
}
