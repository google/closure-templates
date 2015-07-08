/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jbcsrc.BytecodeUtils.constant;

import com.google.common.base.Optional;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CaseOrDefaultNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

import org.objectweb.asm.Label;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A helper for compiling {@link MsgNode messages}
 */
final class MsgCompiler {

  /**
   * A helper interface that allows the MsgCompiler to interact with the SoyNodeCompiler in a 
   * limited way.
   */
  interface SoyNodeToStringCompiler {
    /**
     * Compiles the expression to a {@link String} valued expression.
     *
     * <p>If the node requires detach logic, it should use the given label as the reattach point.
     */
    Expression compileToString(ExprRootNode node, Label reattachPoint);

    /**
     * Compiles the expression to an {@code IntegerData} valued expression.
     *
     * <p>If the node requires detach logic, it should use the given label as the reattach point.
     */
    Expression compileToInt(ExprRootNode node, Label reattachPoint);

    /**
     * Compiles the print node to a {@link String} valued expression.
     * 
     * <p>If the node requires detach logic, it should use the given label as the reattach point.
     */
    Expression compileToString(PrintNode node, Label reattachPoint);

    /** 
     * Compiles the given CallNode to a statement that writes the result into the given appendable.
     * 
     * <p>The statement is guaranteed to be written to a location with a stack depth of zero.
     */
    Statement compileToBuffer(CallNode call, AppendableExpression appendable);

    /** 
     * Compiles the given MsgHtmlTagNode to a statement that writes the result into the given
     * appendable.
     * 
     * <p>The statement is guaranteed to be written to a location with a stack depth of zero.
     */
    Statement compileToBuffer(MsgHtmlTagNode htmlTagNode, AppendableExpression appendable);
  }

  private final Expression thisVar;
  private final DetachState detachState;
  private final VariableSet variables;
  private final VariableLookup variableLookup;
  private final AppendableExpression appendableExpression;
  private final SoyNodeToStringCompiler soyNodeCompiler;

  MsgCompiler(Expression thisVar,
      DetachState detachState,
      VariableSet variables,
      VariableLookup variableLookup,
      AppendableExpression appendableExpression,
      SoyNodeToStringCompiler soyNodeCompiler) {
    this.thisVar = checkNotNull(thisVar);
    this.detachState = checkNotNull(detachState);
    this.variables = checkNotNull(variables);
    this.variableLookup = checkNotNull(variableLookup);
    this.appendableExpression = checkNotNull(appendableExpression);
    this.soyNodeCompiler = checkNotNull(soyNodeCompiler);
  }

  /**
   * Compiles the given {@link MsgNode} to a statement with the given escaping directives applied.
   *
   * <p>The returned statement must be written to a location with a stack depth of zero.
   * 
   * @param id The computed msg id
   * @param msg The msg node
   * @param escapingDirectives The set of escaping directives to apply.
   */
  Statement compileMessage(long id, MsgNode msg, List<String> escapingDirectives) {
    Expression soyMsg = variableLookup.getRenderContext()
        .invoke(MethodRef.RENDER_CONTEXT_GET_SOY_MSG, constant(id));
    Statement printMsg;
    if (msg.isRawTextMsg()) {
      // Simplest case, just a static string translation
      printMsg = handleBasicTranslation(escapingDirectives, soyMsg);
    } else {
      // String translation + placeholders
      printMsg = handleTranslationWithPlaceholders(msg, escapingDirectives, soyMsg);
    }
    return Statement.concat(
        printMsg.withSourceLocation(msg.getSourceLocation()),
        detachState.detachLimited(appendableExpression));
  }

  /**
   * Handles a translation consisting of a single raw text node.
   */
  private Statement handleBasicTranslation(List<String> escapingDirectives, Expression soyMsg) {
    // optimize for simple constant translations (very common)
    // this becomes: renderContext.getSoyMessge(<id>).getParts().get(o).getRawText()
    SoyExpression text = SoyExpression.forString(
        soyMsg.invoke(MethodRef.SOY_MSG_GET_PARTS)
            .invoke(MethodRef.LIST_GET, constant(0))
            .cast(SoyMsgRawTextPart.class)
            .invoke(MethodRef.SOY_MSG_RAW_TEXT_PART_GET_RAW_TEXT));
    for (String directive : escapingDirectives) {
      text = text.applyPrintDirective(variableLookup.getRenderContext(), directive);
    }
    return appendableExpression.appendString(text.coerceToString()).toStatement();
  }

  /**
   * Handles a translation consisting of raw text with placeholders.
   */
  private Statement handleTranslationWithPlaceholders(
      MsgNode msg,
      List<String> escapingDirectives,
      Expression soyMsg) {
    // We need to render placeholders into a buffer and then pack them into a map to pass to
    // Runtime.renderSoyMsgWithPlaceholders.
    Expression placeholderMap = variables.getMsgPlaceholderMapField().accessor(thisVar);
    Map<String, Statement> placeholderNameToPutStatement = new LinkedHashMap<>();
    putPlaceholdersIntoMap(placeholderMap, msg, placeholderNameToPutStatement);
    // sanity check
    checkState(!placeholderNameToPutStatement.isEmpty());
    variables.setMsgPlaceholderMapMinSize(placeholderNameToPutStatement.size());
    Statement populateMap = Statement.concat(placeholderNameToPutStatement.values());
    Statement clearMap = placeholderMap.invokeVoid(MethodRef.LINKED_HASH_MAP_CLEAR);
    Statement render;
    if (escapingDirectives.isEmpty()) {
      render = MethodRef.RUNTIME_RENDER_SOY_MSG_WITH_PLACEHOLDERS.invokeVoid(soyMsg,
          placeholderMap, appendableExpression);
    } else {
      // render into the handy buffer we already have!
      Statement renderToBuffer = MethodRef.RUNTIME_RENDER_SOY_MSG_WITH_PLACEHOLDERS.invokeVoid(
          soyMsg, placeholderMap, tempBuffer());
      // N.B. the type here is always 'string'
      SoyExpression value = SoyExpression.forString(
          tempBuffer().invoke(MethodRef.ADVISING_STRING_BUILDER_GET_AND_CLEAR));
      for (String directive : escapingDirectives) {
        value = value.applyPrintDirective(variableLookup.getRenderContext(), directive);
      }
      render =
          Statement.concat(
              renderToBuffer,
              appendableExpression.appendString(value.coerceToString()).toStatement());
    }
    Statement detach = detachState.detachLimited(appendableExpression);
    return Statement.concat(populateMap, render, clearMap, detach)
        .withSourceLocation(msg.getSourceLocation());
  }

  /**
   * Adds a {@link Statement} to {@link Map#put} every msg placeholder, plural variable and select
   * case value into {@code mapExpression}
   */
  private void putPlaceholdersIntoMap(Expression mapExpression, ParentSoyNode<?> msg,
      Map<String, Statement> placeholderNameToPutStatement) {
    for (SoyNode child : msg.getChildren()) {
      String varName;
      boolean addPlaceholder = true;
      if (child instanceof MsgSubstUnitNode) {
        // Every placeholder may appear multiple times in overall {msg} if it is duplicated in
        // multiple cases of {plural} or {select} statements.  However, when this happens the
        // compiler ensures that the placeholders are identical when calculating placeholder names.
        // So as long as two MsgSubstUnitNodes have the same placeholder we can just use the first
        // one for generating the placeholder code.  This is the same strategy that jssrc uses.
        varName = ((MsgSubstUnitNode) child).getBaseVarName();
        if (placeholderNameToPutStatement.containsKey(varName)) {
          addPlaceholder = false;
        }
      } else {
        // must be a RawTextNode or a MsgPluralRemainderNode, these don't need placeholders
        continue;
      }
      if (child instanceof MsgPluralNode) {
        MsgPluralNode plural = (MsgPluralNode) child;
        if (addPlaceholder) {
          Label reattachPoint = new Label();
          Expression value = soyNodeCompiler.compileToInt(plural.getExpr(), reattachPoint);
          placeholderNameToPutStatement.put(varName,
              putToMap(mapExpression, varName, value).labelStart(reattachPoint));
        }
        // Recursively visit plural cases
        for (CaseOrDefaultNode caseOrDefault : plural.getChildren()) {
          putPlaceholdersIntoMap(mapExpression, caseOrDefault, placeholderNameToPutStatement);
        }
      } else if (child instanceof MsgSelectNode) {
        MsgSelectNode select = (MsgSelectNode) child;
        if (addPlaceholder) {
          Label reattachPoint = new Label();
          Expression value = soyNodeCompiler.compileToString(select.getExpr(), reattachPoint);
          placeholderNameToPutStatement.put(varName,
              putToMap(mapExpression, varName, value).labelStart(reattachPoint));
        }
        // Recursively visit select cases
        for (CaseOrDefaultNode caseOrDefault : select.getChildren()) {
          putPlaceholdersIntoMap(mapExpression, caseOrDefault, placeholderNameToPutStatement);
        }
      } else if (child instanceof MsgPlaceholderNode) {
        if (addPlaceholder) {
          placeholderNameToPutStatement.put(varName,
              putPlaceholderIntoMap(mapExpression, (MsgPlaceholderNode) child));
        }
      } else {
        throw new AssertionError("unexpected child: " + child);
      }
    }
  }

  private Statement putPlaceholderIntoMap(Expression mapExpression, MsgPlaceholderNode placeholder)
      throws AssertionError {
    StandaloneNode initialNode = placeholder.getChild(0);
    String mapKey = placeholder.getBaseVarName();
    Statement putEntyInMap;
    if (initialNode instanceof MsgHtmlTagNode) {
      putEntyInMap =
          addHtmlTagNodeToPlaceholderMap(mapExpression, mapKey, (MsgHtmlTagNode) initialNode);
    } else if (initialNode instanceof CallNode) {
      putEntyInMap = addCallNodeToPlaceholderMap(mapExpression, mapKey, (CallNode) initialNode);
    } else if (initialNode instanceof PrintNode) {
      putEntyInMap = addPrintNodeToPlaceholderMap(mapExpression, mapKey, (PrintNode) initialNode);
    } else {
      // the AST for MsgNodes guarantee that these are the only options
      throw new AssertionError();
    }
    return putEntyInMap;
  }

  /**
   * Returns a statement that adds the content of the node to the map.
   * 
   * @param mapExpression The map to put the new entry in
   * @param mapKey The map key
   * @param htmlTagNode The node
   */
  private Statement addHtmlTagNodeToPlaceholderMap(
      Expression mapExpression, String mapKey, MsgHtmlTagNode htmlTagNode) {
    Optional<String> rawText = tryGetRawTextContent(htmlTagNode);
    if (rawText.isPresent()) {
      return mapExpression
          .invoke(MethodRef.LINKED_HASH_MAP_PUT, constant(mapKey), constant(rawText.get()))
          .toStatement();
    } else {
      Statement renderIntoBuffer = soyNodeCompiler.compileToBuffer(htmlTagNode, tempBuffer());
      Statement putBuffer = putBufferIntoMapForPlaceholder(mapExpression, mapKey);
      return Statement.concat(renderIntoBuffer, putBuffer);
    }
  }

  /**
   * Returns a statement that adds the content rendered by the call to the map.
   * 
   * @param mapExpression The map to put the new entry in
   * @param mapKey The map key
   * @param callNode The node
   */
  private Statement addCallNodeToPlaceholderMap(
      Expression mapExpression, String mapKey, CallNode callNode) {
    Statement renderIntoBuffer = soyNodeCompiler.compileToBuffer(callNode, tempBuffer());
    Statement putBuffer = putBufferIntoMapForPlaceholder(mapExpression, mapKey);
    return Statement.concat(renderIntoBuffer, putBuffer);
  }

  /**
   * Returns a statement that adds the content rendered by the call to the map.
   *
   * @param mapExpression The map to put the new entry in
   * @param mapKey The map key
   * @param printNode The node
   */
  private Statement addPrintNodeToPlaceholderMap(
      Expression mapExpression, String mapKey, PrintNode printNode) {
    // This is much like the escaping path of visitPrintNode but somewhat simpler because our
    // ultimate target is a string rather than putting bytes on the output stream.
    Label reattachPoint = new Label();
    Expression compileToString = soyNodeCompiler.compileToString(printNode, reattachPoint);
    return putToMap(mapExpression, mapKey, compileToString).labelStart(reattachPoint);
  }

  private Statement putToMap(Expression mapExpression, String mapKey, Expression valueExpression) {
    return mapExpression
        .invoke(MethodRef.LINKED_HASH_MAP_PUT,
            constant(mapKey),
            valueExpression)
        .toStatement();
  }

  private AppendableExpression tempBuffer() {
    return AppendableExpression.forStringBuilder(variables.getTempBufferField().accessor(thisVar));
  }

  private Statement putBufferIntoMapForPlaceholder(Expression mapExpression, String mapKey) {
    return mapExpression
        .invoke(MethodRef.LINKED_HASH_MAP_PUT,
            constant(mapKey),
            tempBuffer().invoke(MethodRef.ADVISING_STRING_BUILDER_GET_AND_CLEAR))
        .toStatement();
  }

  private Optional<String> tryGetRawTextContent(ParentSoyNode<?> initialNode) {
    if (initialNode.numChildren() == 1 && initialNode.getChild(0) instanceof RawTextNode) {
      return Optional.of(((RawTextNode) initialNode.getChild(0)).getRawText());
    }
    return Optional.absent();
  }
}
