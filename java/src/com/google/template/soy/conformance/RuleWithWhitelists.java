/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.conformance;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * A tuple of a {@link Rule rule} and a list of whitelisted paths that are exempt from the rule.
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class RuleWithWhitelists {

  private static final Escaper MESSAGE_FORMAT =
      Escapers.builder().addEscape('{', "'{").addEscape('}', "}'").build();

  private final Rule<? extends Node> rule;
  private final ImmutableList<String> whitelistedPaths;

  private RuleWithWhitelists(Rule<? extends Node> rule, ImmutableList<String> whitelistedPaths) {
    this.rule = rule;
    this.whitelistedPaths = whitelistedPaths;
  }

  Rule<? extends Node> getRule() {
    return rule;
  }

  /** A file should be checked against a rule unless it contains one of the whitelisted paths. */
  boolean shouldCheckConformanceFor(String filePath) {
    for (String whitelistedPath : whitelistedPaths) {
      if (filePath.contains(whitelistedPath)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Constructs a list of RuleWithWhitelists objects corresponding to the given list of conformance
   * config protos.
   */
  static ImmutableList<RuleWithWhitelists> forConformanceConfigs(
      ImmutableList<ConformanceConfig> configs) {
    ImmutableList.Builder<RuleWithWhitelists> rulesBuilder = new ImmutableList.Builder<>();
    for (ConformanceConfig config : configs) {
      for (Requirement requirement : config.getRequirementList()) {
        Rule<? extends Node> rule = forRequirement(requirement);
        ImmutableList<String> whitelists = ImmutableList.copyOf(requirement.getWhitelistList());
        rulesBuilder.add(new RuleWithWhitelists(rule, whitelists));
      }
    }
    return rulesBuilder.build();
  }

  private static Rule<? extends Node> forRequirement(Requirement requirement) {
    SoyErrorKind error =
        SoyErrorKind.of(
            MESSAGE_FORMAT.escape(requirement.getErrorMessage()), StyleAllowance.values());
    switch (requirement.getRequirementTypeCase()) {
      case CUSTOM:
        return createCustomRule(requirement.getCustom().getJavaClass(), error);
      case BANNED_CSS_SELECTOR:
        Requirement.BannedCssSelector bannedCss = requirement.getBannedCssSelector();
        return new BannedCssSelector(ImmutableSet.copyOf(bannedCss.getSelectorList()), error);
      case BANNED_DIRECTIVE:
        Requirement.BannedDirective bannedDirective = requirement.getBannedDirective();
        return new BannedDirective(ImmutableSet.copyOf(bannedDirective.getDirectiveList()), error);
      case BANNED_FUNCTION:
        Requirement.BannedFunction bannedFunction = requirement.getBannedFunction();
        return new BannedFunction(ImmutableSet.copyOf(bannedFunction.getFunctionList()), error);
      case BANNED_RAW_TEXT:
        Requirement.BannedRawText bannedRawText = requirement.getBannedRawText();
        return new BannedRawText(ImmutableSet.copyOf(bannedRawText.getTextList()), error);
      case BANNED_HTML_TAG:
        Requirement.BannedHtmlTag bannedHtmlTag = requirement.getBannedHtmlTag();
        return new BannedHtmlTag(bannedHtmlTag.getTagList(), error);
      case BANNED_TEXT_EVERYWHERE_EXCEPT_COMMENTS:
        Requirement.BannedTextEverywhereExceptComments banned =
            requirement.getBannedTextEverywhereExceptComments();
        return new BannedTextEverywhereExceptComments(
            ImmutableSet.copyOf(banned.getTextList()), error);
      case REQUIREMENTTYPE_NOT_SET:
      default:
        throw new RuntimeException("requirement missing type");
    }
  }

  /**
   * Instantiates a custom conformance check from its fully-qualified class name, specified in the
   * conformance protobuf.
   *
   * <p>Custom conformance checks must extend {@link Rule}. They must also have a binary constructor
   * with {@link ErrorReporter} and {@link SoyErrorKind} parameters.
   */
  private static Rule<?> createCustomRule(String javaClass, SoyErrorKind error) {
    Class<? extends Rule<?>> customRuleClass;
    try {
      @SuppressWarnings("unchecked")
      Class<? extends Rule<?>> asSubclass =
          (Class<? extends Rule<?>>) Class.forName(javaClass).asSubclass(Rule.class);
      customRuleClass = asSubclass;
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("custom rule class " + javaClass + " not found", e);
    }
    try {
      Constructor<? extends Rule<?>> ctor = customRuleClass.getConstructor(SoyErrorKind.class);
      return ctor.newInstance(error);
    } catch (IllegalAccessException
        | InstantiationException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }
}
