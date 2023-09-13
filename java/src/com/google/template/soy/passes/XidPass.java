/*
 * Copyright 2018 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/** Handles xid calls involving global nodes by rewriting them to be string literals. */
@RunAfter(RewriteGlobalsPass.class)
public final class XidPass implements CompilerFilePass {
  private static final SoyErrorKind STRING_OR_GLOBAL_REQUIRED =
      SoyErrorKind.of(
          "Argument to function ''xid'' must be a string literal or a (possibly) "
              + "dotted identifier.");

  private final ErrorReporter reporter;
  private final boolean replaceXidNodes;

  XidPass(
      boolean replaceXidNodes,
      ErrorReporter reporter) {
    this.reporter = reporter;
    this.replaceXidNodes = replaceXidNodes;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.allFunctionInvocations(file, BuiltinFunction.XID)
        .forEach(
            fn -> {
              if (fn.numChildren() != 1) {
                // if it isn't == 1, then an error has already been reported, move along.
                return;
              }
              ExprNode child = fn.getChild(0);
              switch (child.getKind()) {
                case GLOBAL_NODE:
                  GlobalNode global = (GlobalNode) child;
                  String xid = global.getName();
                  boolean isControllerOrModelXid = isControllerOrModelXid(xid);
                  fn.replaceChild(
                      0,
                      new StringNode(
                          xid,
                          QuoteStyle.SINGLE,
                          global.getSourceLocation(),
                          isControllerOrModelXid && replaceXidNodes));
                  break;
                case VAR_REF_NODE:
                case FIELD_ACCESS_NODE:
                  // There can be collisions between the xid arg and certain in-scope symbols. Turn
                  // any VarRef (with possibly dotted field access) back into a string. Also, these
                  // var refs didn't get global alias expanded so do that here too.
                  String source = child.toSourceString();
                  if (!BaseUtils.isDottedIdentifier(source) || source.startsWith("$")) {
                    reporter.report(child.getSourceLocation(), STRING_OR_GLOBAL_REQUIRED);
                    break;
                  }
                  String expanded =
                      file.resolveAlias(Identifier.create(source, SourceLocation.UNKNOWN))
                          .identifier();
                  isControllerOrModelXid = isControllerOrModelXid(expanded);
                  fn.replaceChild(
                      0,
                      new StringNode(
                          expanded,
                          QuoteStyle.SINGLE,
                          child.getSourceLocation(),
                          isControllerOrModelXid && replaceXidNodes));
                  break;
                case STRING_NODE:
                  break;
                default:
                  reporter.report(child.getSourceLocation(), STRING_OR_GLOBAL_REQUIRED);
              }
            });
  }
}
