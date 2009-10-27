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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.ParseException;
import com.google.template.soy.exprparse.TokenMgrError;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderNode;
import com.google.template.soy.soytree.SoyNode.ParentExprHolderNode;
import com.google.template.soy.soytree.SoyNode.SoyStatementNode;
import com.google.template.soy.soytree.SoyNode.SplitLevelTopNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Node representing a 'call' statement.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class CallNode extends AbstractParentSoyCommandNode<CallParamNode>
    implements SplitLevelTopNode<CallParamNode>, SoyStatementNode,
    ParentExprHolderNode<CallParamNode>, MsgPlaceholderNode {


  /** Pattern for a callee name not listed as an attribute name="...". */
  private static final Pattern NONATTRIBUTE_CALLEE_NAME =
      Pattern.compile("^ (?! name=\" | function=\") [.\\w]+ (?= \\s | $)", Pattern.COMMENTS);

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("call",
          new Attribute("name", Attribute.ALLOW_ALL_VALUES, null),
          new Attribute("function", Attribute.ALLOW_ALL_VALUES, null),  // V1
          new Attribute("data", Attribute.ALLOW_ALL_VALUES, null));


  /** The name of the template being called. */
  private String calleeName;

  /** Whether we're passing any part of the data (i.e. no 'data' attribute). */
  private final boolean isPassingData;

  /** Whether we're passing all of the data (i.e. data="all"). */
  private final boolean isPassingAllData;

  /** The data ref text for the subset of data to pass, or null if not applicable. */
  private final String dataRefText;

  /** The parsed data reference, or null if not applicable. */
  private final ExprRootNode<DataRefNode> dataRef;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public CallNode(String id, String commandText) throws SoySyntaxException {
    super(id, "call", commandText);

    // Handle callee name not listed as an attribute name="...".
    Matcher ncnMatcher = NONATTRIBUTE_CALLEE_NAME.matcher(commandText);
    if (ncnMatcher.find()) {
      commandText = ncnMatcher.replaceFirst("name=\"" + ncnMatcher.group() + "\"");
    }

    Map<String, String> attributes = ATTRIBUTES_PARSER.parse(commandText);

    String nameAttribute = attributes.get("name");
    String functionAttribute = attributes.get("function");
    if (nameAttribute == null && functionAttribute == null ||
        nameAttribute != null && functionAttribute != null) {
      throw new SoySyntaxException("The 'call' command text must contain attribute 'name'" +
                                   " (without attribute 'function').");
    }
    if (nameAttribute != null) {
      calleeName = nameAttribute;
    } else {
      calleeName = functionAttribute;
      maybeSetSyntaxVersion(SyntaxVersion.V1);
    }
    if (!BaseUtils.isDottedIdentifier(calleeName)) {
      throw new SoySyntaxException(
          "Invalid callee name \"" + calleeName + "\" for 'call' command.");
    }

    String dataAttribute = attributes.get("data");
    if (dataAttribute == null) {
      isPassingData = false;
      isPassingAllData = false;
      dataRefText = null;
      dataRef = null;
    } else if (dataAttribute.equals("all")) {
      isPassingData = true;
      isPassingAllData = true;
      dataRefText = null;
      dataRef = null;
    } else {
      isPassingData = true;
      isPassingAllData = false;
      dataRefText = dataAttribute;
      try {
        dataRef = (new ExpressionParser(dataRefText)).parseDataReference();
      } catch (TokenMgrError tme) {
        throw createExceptionForInvalidDataRef(tme);
      } catch (ParseException pe) {
        throw createExceptionForInvalidDataRef(pe);
      }
    }
  }


  /**
   * Private helper for the constructor.
   * @param cause The underlying exception.
   * @return The SoySyntaxException to be thrown.
   */
  private SoySyntaxException createExceptionForInvalidDataRef(Throwable cause) {
    //noinspection ThrowableInstanceNeverThrown
    return new SoySyntaxException(
        "Invalid data reference in 'call' command text \"" + getCommandText() + "\".", cause);
  }


  /**
   * Sets the full name of the template being called (must not be a partial name).
   * @param calleeName The full name of the template being called.
   */
  public void setCalleeName(String calleeName) {
    Preconditions.checkArgument(
        BaseUtils.isDottedIdentifier(calleeName) && calleeName.charAt(0) != '.');
    this.calleeName = calleeName;
  }

  /** Returns the name of the template being called. */
  public String getCalleeName() {
    return calleeName;
  }

  /** Returns whether we're passing any part of the data (i.e. no 'data' attribute). */
  public boolean isPassingData() {
    return isPassingData;
  }

  /** Returns whether we're passing all of the data (i.e. data="all"). */
  public boolean isPassingAllData() {
    return isPassingAllData;
  }

  /** Returns the data ref text for the subset of data to pass, or null if not applicable. */
  public String getDataRefText() {
    return dataRefText;
  }

  /** Returns the parsed data reference, or null if not applicable. */
  public ExprRootNode<DataRefNode> getDataRef() {
    return dataRef;
  }


  @Override public String getTagString() {
    return buildTagStringHelper(numChildren() == 0);
  }


  @Override public String toSourceString() {
    return (numChildren() == 0) ? getTagString() : super.toSourceString();
  }


  @Override public List<? extends ExprRootNode<? extends ExprNode>> getAllExprs() {
    return (dataRef != null) ? ImmutableList.of(dataRef)
                             : Collections.<ExprRootNode<? extends ExprNode>>emptyList();
  }


  @Override public String genBasePlaceholderName() {
    return "XXX";
  }


  @Override public boolean isSamePlaceholderAs(MsgPlaceholderNode other) {
    return false;
  }

}
