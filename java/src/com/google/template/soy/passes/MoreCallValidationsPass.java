/*
 * Copyright 2020 Google Inc.
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Streams.stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.TemplateContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.internal.BuiltinMethod;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TagName;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.types.SanitizedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.TemplateType.Parameter;
import com.google.template.soy.types.TemplateType.ParameterKind;
import com.google.template.soy.types.UnknownType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Catch-all pass that validates various things about template calls. Currently upgrades template
 * types from the "named template" placeholder type to proper types. Validates Element Calls.
 */
@RunAfter(ResolveExpressionTypesPass.class)
final class MoreCallValidationsPass implements CompilerFileSetPass {

  private static final SoyErrorKind ELEMENT_CALL_TO_HTML_TEMPLATE =
      SoyErrorKind.of(
          "Expected a template with kind 'html<?>' that is completely bound or only has html"
              + " parameters, but found `{0}`.");

  private static final SoyErrorKind ONLY_STRICT_HTML_TEMPLATES_ALLOWED =
      SoyErrorKind.of(
          "Only strict HTML templates are allowed in expressions, but template `{0}` was not"
              + " strict HTML.");

  private static final SoyErrorKind ILLEGAL_USE =
      SoyErrorKind.of("''legacyDynamicTag'' may only be used to name an HTML tag.");

  private static final SoyErrorKind NEED_WRAP =
      SoyErrorKind.of("A dynamic tag name should be wrapped in the ''legacyDynamicTag'' function.");

  private static final SoyErrorKind ONLY_ONE_CLOSE_TAG =
      SoyErrorKind.of("Element calls require exactly one close tag.");

  private static final SoyErrorKind NON_STATIC_ATTRIBUTE_NAME =
      SoyErrorKind.of("Element call attribute names must be static.");

  private static final SoyErrorKind NEGATIVE_ATTRIBUTE =
      SoyErrorKind.of("Callee template does not allow attribute ''{0}''.");

  private static final SoyErrorKind PARAM_AS_ATTRIBUTE =
      SoyErrorKind.of("Param ''{0}'' may not be set as an attribute.");

  private static final SoyErrorKind NO_SUCH_ATTRIBUTE =
      SoyErrorKind.of("Unrecognized attribute.{0}", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind MISUSED_AT_ATTRIBUTE =
      SoyErrorKind.of("Attributes with a leading @ should not have values.");

  private static final SoyErrorKind BAD_ATTRIBUTE_NAME =
      SoyErrorKind.of("Element attribute names must be lower hyphen case.");

  private static final SoyErrorKind NO_ATTRIBUTE_VALUE =
      SoyErrorKind.of("Element call attributes must have values.");

  private static final SoyErrorKind NO_ATTRIBUTES_ON_SLOT =
      SoyErrorKind.of(
          "<parameter> elements cannot have attributes except slot, which must have a static"
              + " value.");

  private static final SoyErrorKind SLOTS_ONLY_ONE_CLOSE_TAG =
      SoyErrorKind.of("<parameter> elements cannot have more than one close tag.");

  private static final SoyErrorKind SLOTS_ONLY_DIRECT_DESCENDENTS_OF_TEMPLATE_CALL =
      SoyErrorKind.of("<parameter> elements can only be direct descendents of template calls.");

  private static final SoyErrorKind DUPLICATE_PARAM = SoyErrorKind.of("Duplicate param ''{0}''.");

  private static final SoyErrorKind PASSES_UNUSED_PARAM =
      SoyErrorKind.of(
          "''{0}'' is not a declared parameter of {1} or any indirect callee.{2}",
          StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind MISSING_PARAM =
      SoyErrorKind.of("Call missing required param ''{0}''.");

  private static final SoyErrorKind MISSING_ATTRIBUTE =
      SoyErrorKind.of("Call missing required attribute ''{0}''.");

  private static final SoyErrorKind ONLY_SLOTS_ALLOWED =
      SoyErrorKind.of("Element calls require all children to be <parameter> elements.");

  private final ErrorReporter errorReporter;
  private final boolean elementFunctionsWereRewritten;

  MoreCallValidationsPass(ErrorReporter errorReporter, boolean elementFunctionsWereRewritten) {
    this.errorReporter = errorReporter;
    this.elementFunctionsWereRewritten = elementFunctionsWereRewritten;
  }

  @Override
  public Result run(ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator) {
    for (SoyFileNode file : sourceFiles) {
      checkTemplateLiteralsUsedInExpr(file);
      if (elementFunctionsWereRewritten) {
        handleDynamicTagAndCheckForLegacyDynamicTags(file);
      }
    }
    return Result.CONTINUE;
  }

  // Returns all child ExprNodes, omitting the "modifies" expression of TemplateBasicNodes.
  private static Stream<ExprNode> nonModifiesExprs(ExprHolderNode exprHolderNode) {
    if (exprHolderNode instanceof TemplateBasicNode) {
      TemplateBasicNode templateBasicNode = (TemplateBasicNode) exprHolderNode;
      Stream<ExprNode> stream = Stream.<ExprNode>builder().build();
      for (ExprRootNode rootNode : exprHolderNode.getExprList()) {
        if (rootNode != templateBasicNode.getModifiesExpr()) {
          stream = Stream.concat(stream, SoyTreeUtils.allNodesOfType(rootNode, ExprNode.class));
        }
      }
      return stream;
    }
    return SoyTreeUtils.allNodesOfType(exprHolderNode, ExprNode.class);
  }

  private void checkTemplateLiteralsUsedInExpr(SoyFileNode file) {
    SoyTreeUtils.allNodesOfType(file, SoyNode.ExprHolderNode.class)
        .forEach(
            exprHolder ->
                nonModifiesExprs(exprHolder)
                    .filter(
                        exprNode ->
                            exprNode.getKind() == Kind.TEMPLATE_LITERAL_NODE
                                && !((TemplateLiteralNode) exprNode).isStaticCall())
                    .map(TemplateLiteralNode.class::cast)
                    .forEach(
                        templateNode ->
                            stream(SoyTypes.getTypeTraverser(templateNode.getType(), null))
                                .filter(t -> t.getKind() == SoyType.Kind.TEMPLATE)
                                .map(TemplateType.class::cast)
                                .filter(
                                    templateType ->
                                        templateType
                                                .getContentKind()
                                                .getSanitizedContentKind()
                                                .isHtml()
                                            && !templateType.isStrictHtml())
                                .forEach(
                                    templateType ->
                                        // Only report errors for template literal nodes, to avoid
                                        // reporting errors multiple times (ie., once for everywhere
                                        // the 'named' template type has propagated in the
                                        // expression tree).
                                        // TODO(b/180151169) Is this check necessary?
                                        errorReporter.report(
                                            templateNode.getSourceLocation(),
                                            ONLY_STRICT_HTML_TEMPLATES_ALLOWED,
                                            templateNode.getResolvedName()))));
  }

  private void handleDynamicTagAndCheckForLegacyDynamicTags(SoyFileNode file) {
    Set<FunctionNode> correctlyPlaced = new HashSet<>();
    Set<HtmlTagNode> allowedSlots = new HashSet<>();
    SoyTreeUtils.allNodesOfType(file, HtmlTagNode.class)
        .filter(tag -> !tag.getTagName().isStatic())
        .forEach(
            tagNode -> {
              SoyType type = tagNode.getTagName().getDynamicTagName().getExpr().getType();
              if (type.isAssignableFromStrict(StringType.getInstance())) {
                handleDynamicTag(tagNode, correctlyPlaced);
              } else if (!(type instanceof TemplateType)
                  || !(((TemplateType) type).getContentKind()
                      instanceof TemplateContentKind.ElementContentKind)) {
                errorReporter.report(
                    tagNode.getSourceLocation(),
                    ELEMENT_CALL_TO_HTML_TEMPLATE,
                    tagNode.getTagName().getDynamicTagName().getExpr().getType());
              } else {
                validateTemplateCall((HtmlOpenTagNode) tagNode, allowedSlots::add);
              }
            });

    // No other uses of legacyDynamicTag are allowed.
    SoyTreeUtils.allFunctionInvocations(file, BuiltinFunction.LEGACY_DYNAMIC_TAG)
        .filter(fn -> !correctlyPlaced.contains(fn))
        .forEach(fn -> errorReporter.report(fn.getSourceLocation(), ILLEGAL_USE));

    SoyTreeUtils.allNodesOfType(file, HtmlOpenTagNode.class)
        .filter((tag) -> tag.isSlot() && !allowedSlots.contains(tag))
        .forEach(
            tagNode ->
                errorReporter.report(
                    tagNode.getSourceLocation(), SLOTS_ONLY_DIRECT_DESCENDENTS_OF_TEMPLATE_CALL));
  }

  private void validateTemplateCall(HtmlOpenTagNode openTagNode, Consumer<HtmlTagNode> consumer) {
    if (openTagNode.getTaggedPairs().size() > 1) {
      errorReporter.report(openTagNode.getSourceLocation(), ONLY_ONE_CLOSE_TAG);
    }
    Set<String> seenSlots = new HashSet<>();
    Set<String> seenAttributes = new HashSet<>();

    TemplateType templateType =
        (TemplateType) openTagNode.getTagName().getDynamicTagName().getExpr().getType();

    boolean hasAllAttributes = templateType.getAllowExtraAttributes();
    ImmutableSet<String> reservedAttributes = templateType.getReservedAttributes();
    ImmutableMap<String, Parameter> allParamsByAttrName =
        templateType.getParameters().stream()
            .collect(toImmutableMap(p -> Parameter.paramToAttrName(p.getName()), p -> p));

    SoyTreeUtils.getAllNodesOfType(openTagNode, HtmlAttributeNode.class)
        .forEach(
            a ->
                validateAttribute(
                    a,
                    seenAttributes::add,
                    allParamsByAttrName,
                    hasAllAttributes,
                    reservedAttributes));

    HtmlTagNode closeTag = Iterables.getFirst(openTagNode.getTaggedPairs(), openTagNode);
    if (!openTagNode.isSelfClosing()) {
      // Hande slots
      SoyNode next = SoyTreeUtils.nextSibling(openTagNode);
      ImmutableList<Parameter> htmlTypeParams =
          templateType.getParameters().stream()
              .filter(
                  p ->
                      SoyTypes.makeNullish(SanitizedType.HtmlType.getInstance())
                          .isAssignableFromStrict(p.getType()))
              .collect(toImmutableList());
      boolean childIsSlot = next instanceof HtmlOpenTagNode && ((HtmlOpenTagNode) next).isSlot();
      if (!childIsSlot && htmlTypeParams.size() == 1) {
        // Mark the single html typed param as set.
        seenSlots.add(htmlTypeParams.get(0).getName());
      } else {
        while (next != closeTag) {
          if (next == null) {
            break;
          }
          next =
              consumeSlot(
                  next,
                  openTagNode.getTagName().getDynamicTagName().getExpr(),
                  openTagNode.getSourceLocation(),
                  seenSlots::add,
                  consumer);
        }
      }
    }
    templateType.getParameters().stream()
        .filter(Parameter::isRequired)
        .forEach(
            p -> {
              if (p.getKind() == ParameterKind.ATTRIBUTE) {
                if (!seenAttributes.contains(p.getName())) {
                  errorReporter.report(
                      openTagNode.getSourceLocation(),
                      MISSING_ATTRIBUTE,
                      Parameter.paramToAttrName(p.getName()));
                }
              } else if (!seenSlots.contains(p.getName())) {
                // Note that <{tpl(a: 'foo')} /> is rewritten to <{tpl.bind(record(a: 'foo'))} />
                // in ResolveTemplateFunctionsPass. So any params that are correctly set won't be
                // in the resulting template type and won't need to be in `seenSlots` to validate
                // correctly here.
                errorReporter.report(openTagNode.getSourceLocation(), MISSING_PARAM, p.getName());
              }
            });
  }

  private void validateAttribute(
      HtmlAttributeNode attr,
      Function<String, Boolean> addAttr,
      ImmutableMap<String, Parameter> allParamsByAttrName,
      boolean hasAllAttributes,
      ImmutableSet<String> reservedAttributes) {
    String name = attr.getStaticKey();
    SourceLocation loc = attr.getChild(0).getSourceLocation();
    if (name == null) {
      if (attr.numChildren() != 1) {
        errorReporter.report(loc, NON_STATIC_ATTRIBUTE_NAME);
      }
      return;
    }

    boolean isSoyAttr = name.startsWith("@");
    if (isSoyAttr) {
      name = name.substring(1);
    }
    if (Parameter.isValidAttrName(name)) {
      String paramName = Parameter.attrToParamName(name);
      // This is a synthetically created IfCond Node created by somethin akin to @class="Hello"
      if (((attr.getParent() instanceof IfCondNode
                  && !attr.getParent().getSourceLocation().isKnown())
              || attr.getParent() instanceof HtmlOpenTagNode)
          && !addAttr.apply(paramName)) {
        errorReporter.report(loc, DUPLICATE_PARAM, name);
        return;
      } else if (!hasAllAttributes) {
        Parameter param = allParamsByAttrName.get(name);
        if (param == null) {
          String didYouMeanMessage =
              SoyErrors.getDidYouMeanMessage(
                  allParamsByAttrName.entrySet().stream()
                      .filter(e -> e.getValue().getKind() == ParameterKind.ATTRIBUTE)
                      .map(Map.Entry::getKey)
                      .collect(Collectors.toList()),
                  name);
          errorReporter.report(loc, NO_SUCH_ATTRIBUTE, didYouMeanMessage);
          return;
        } else if (param.getKind() != ParameterKind.ATTRIBUTE) {
          errorReporter.report(loc, PARAM_AS_ATTRIBUTE, param.getName());
          return;
        }
      } else if (reservedAttributes.contains(name)) {
        errorReporter.report(loc, NEGATIVE_ATTRIBUTE, name);
        return;
      }
    } else {
      errorReporter.report(loc, BAD_ATTRIBUTE_NAME);
      return;
    }

    if (!attr.hasValue() && !isSoyAttr && allParamsByAttrName.containsKey(name)) {
      errorReporter.report(attr.getSourceLocation(), NO_ATTRIBUTE_VALUE);
    }
    if (attr.hasValue() && isSoyAttr) {
      errorReporter.report(attr.getSourceLocation(), MISUSED_AT_ATTRIBUTE);
    }
  }

  private SoyNode consumeSlot(
      SoyNode startNode,
      ExprNode template,
      SourceLocation templateLocation,
      Function<String, Boolean> addParam,
      Consumer<HtmlTagNode> consumer) {
    if (!(startNode instanceof HtmlOpenTagNode) || !((HtmlOpenTagNode) startNode).isSlot()) {
      errorReporter.report(startNode.getSourceLocation(), ONLY_SLOTS_ALLOWED);
      return null;
    }
    HtmlOpenTagNode nextOpenTag = (HtmlOpenTagNode) startNode;
    if (nextOpenTag.numChildren() != 2) {
      errorReporter.report(nextOpenTag.getSourceLocation(), NO_ATTRIBUTES_ON_SLOT);
      return null;
    }
    if (nextOpenTag.getTaggedPairs().size() > 1) {
      errorReporter.report(nextOpenTag.getSourceLocation(), SLOTS_ONLY_ONE_CLOSE_TAG);
      return null;
    }
    HtmlAttributeNode attributeNode = (HtmlAttributeNode) nextOpenTag.getChild(1);
    if (!attributeNode.hasValue()
        || attributeNode.getStaticKey() == null
        || !attributeNode.getStaticKey().equals("slot")
        || attributeNode.getStaticContent() == null) {
      errorReporter.report(attributeNode.getSourceLocation(), NO_ATTRIBUTES_ON_SLOT);
      return null;
    }
    TemplateType templateType = (TemplateType) template.getType();
    boolean containsParam =
        templateType.getParameters().stream()
            .anyMatch(p -> p.getName().equals(attributeNode.getStaticContent()));
    if (!containsParam) {
      errorReporter.report(
          templateLocation,
          PASSES_UNUSED_PARAM,
          attributeNode.getStaticContent(),
          template.toSourceString(),
          SoyErrors.getDidYouMeanMessage(
              templateType.getParameters().stream()
                  .map(Parameter::getName)
                  .collect(toImmutableList()),
              attributeNode.getStaticContent()));
    }
    if (!addParam.apply(attributeNode.getStaticContent())) {
      errorReporter.report(templateLocation, DUPLICATE_PARAM, attributeNode.getStaticContent());
    }
    HtmlTagNode closeTag = nextOpenTag.getTaggedPairs().get(0);
    consumer.accept(nextOpenTag);
    return SoyTreeUtils.nextSibling(closeTag);
  }

  private void handleDynamicTag(HtmlTagNode tagNode, Set<FunctionNode> correctlyPlaced) {
    TagName name = tagNode.getTagName();
    PrintNode printNode = name.getDynamicTagName();
    ExprNode exprNode = printNode.getExpr().getRoot();
    if (exprNode.getKind() == Kind.FUNCTION_NODE) {
      if (((FunctionNode) exprNode).isResolved()
          && ((FunctionNode) exprNode).getSoyFunction() == BuiltinFunction.LEGACY_DYNAMIC_TAG) {
        FunctionNode functionNode = (FunctionNode) exprNode;
        if (functionNode.numChildren() == 1) {
          printNode.getExpr().clearChildren();
          printNode.getExpr().addChild(functionNode.getChild(0));
        }
        correctlyPlaced.add(functionNode);
      }
    } else if (!tagNode.getTagName().isTemplateCall()) {
      if (printNode.getExpr().getType() == UnknownType.getInstance()) {
        if (exprNode instanceof MethodCallNode
            && ((MethodCallNode) exprNode).isMethodResolved()
            && ((MethodCallNode) exprNode).getSoyMethod() == BuiltinMethod.BIND) {
          // Bind method + unknown type indicates an error already reported here.
          return;
        }
        // Same for template literal node. This is a Soy element in the root of an element.
        if (exprNode instanceof TemplateLiteralNode) {
          return;
        }
      }
      errorReporter.report(printNode.getExpr().getSourceLocation(), NEED_WRAP);
    }
  }
}
