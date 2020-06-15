/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.invocationbuilders.passes;

import static com.google.template.soy.invocationbuilders.passes.InvocationBuilderTypeUtils.upcastTypesForIndirectParams;
import static com.google.template.soy.shared.internal.gencode.JavaGenerationUtils.makeUpperCamelCase;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.invocationbuilders.javatypes.JavaType;
import com.google.template.soy.invocationbuilders.passes.SoyFileNodeTransformer.ParamStatus;
import com.google.template.soy.passes.IndirectParamsCalculator;
import com.google.template.soy.passes.IndirectParamsCalculator.IndirectParamsInfo;
import com.google.template.soy.shared.internal.gencode.JavaGenerationUtils;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateMetadata.Parameter;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.Visibility;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts {@link SoyFileNode} into an intermediate data structure used to generate Java source
 * code as well as reports about the generated API.
 *
 * <p>This class performs all the validation checks such as duplicate template and parameter name
 * detection, unhandled parameter types, and more.
 */
public class SoyFileNodeTransformer {
  public static final ImmutableList<String> RESERVED_IDENTIFIERS = ImmutableList.of("Builder");

  /** The transformed {@link SoyFileNode}. */
  @AutoValue
  public abstract static class FileInfo {

    abstract SoyFileNode fileNode();

    abstract Path soyFilePath();

    /**
     * Returns the fully qualified name of the outer class created to hold the SoyTemplate
     * implementations.
     */
    public abstract String fqClassName();

    public abstract ImmutableList<TemplateInfo> templates();

    /** Returns whether all templates in the file are fully handled. */
    public boolean complete() {
      return templates().stream().allMatch(TemplateInfo::complete);
    }

    public String soyFileName() {
      return soyFilePath().getFileName().toString();
    }

    public String packageName() {
      String className = fqClassName();
      return className.substring(0, className.lastIndexOf("."));
    }

    /** Returns just the last token of the FQ classname. */
    public String className() {
      String className = fqClassName();
      return className.substring(className.lastIndexOf(".") + 1);
    }

    public TemplateInfo findTemplate(TemplateNode node) {
      return templates().stream().filter(t -> t.template().equals(node)).findFirst().get();
    }

    public Set<String> getProtoTypes(SoyTypeRegistry typeRegistry) {
      return JavaGenerationUtils.getProtoTypes(fileNode(), typeRegistry);
    }
  }

  /** Status categories for {@link TemplateInfo}. */
  public enum TemplateStatus {
    HANDLED,
    NAME_COLLISION,
  }

  /** The transformed {@link TemplateNode}. */
  @AutoValue
  public abstract static class TemplateInfo {

    static TemplateInfo error(TemplateNode template, TemplateStatus status) {
      return new AutoValue_SoyFileNodeTransformer_TemplateInfo(
          generateTemplateClassName(template), ImmutableList.of(), status, template);
    }

    /** Returns the fully qualified name of the generated SoyTemplate implementation. */
    public abstract String fqClassName();

    public abstract ImmutableList<ParamInfo> params();

    public abstract TemplateStatus status();

    protected abstract TemplateNode template();

    public boolean complete() {
      return status() == TemplateStatus.HANDLED
          && params().stream()
              .allMatch(
                  p ->
                      p.status() == ParamStatus.HANDLED
                          || p.status() == ParamStatus.JAVA_INCOMPATIBLE);
    }

    public String templateName() {
      return template().getTemplateName();
    }

    public String soyDocDesc() {
      return template().getSoyDocDesc();
    }

    public String templateNameForUserMsgs() {
      return template().getTemplateNameForUserMsgs();
    }

    /** Returns just the last token of the FQ classname. */
    public String className() {
      String className = fqClassName();
      return className.substring(className.lastIndexOf(".") + 1);
    }
  }

  /** Status categories for {@link ParamInfo}. */
  public enum ParamStatus {
    HANDLED,
    NAME_COLLISION,
    INDIRECT_INCOMPATIBLE_TYPES,
    INDIRECT_PROTO,
    UNHANDLED_TYPE,
    JAVA_INCOMPATIBLE
  }

  /** Status categories for {@link TemplateInfo} related to future setters. */
  public enum ParamFutureStatus {
    HANDLED,
    NAME_COLLISION,
    UNHANDLED
  }

  /** The transformed {@link TemplateParam}. */
  @AutoValue
  public abstract static class ParamInfo {
    static ParamInfo of(TemplateParam param, ParamStatus status) {
      return of(
          Parameter.fromParam(param), status, false, param.isInjected(), ParamFutureStatus.HANDLED);
    }

    static ParamInfo of(TemplateParam param, ParamStatus status, boolean indirect) {
      return of(
          Parameter.fromParam(param),
          status,
          indirect,
          param.isInjected(),
          ParamFutureStatus.HANDLED);
    }

    static ParamInfo of(Parameter param, ParamStatus status, boolean indirect) {
      return of(param, status, indirect, false, ParamFutureStatus.HANDLED);
    }

    static ParamInfo of(
        Parameter param,
        ParamStatus status,
        boolean indirect,
        boolean injected,
        ParamFutureStatus futureStatus) {
      return new AutoValue_SoyFileNodeTransformer_ParamInfo(
          param, status, indirect, injected, futureStatus);
    }

    private int uniqueSerial = 0;

    public abstract Parameter param();

    public abstract ParamStatus status();

    public abstract boolean indirect();

    public abstract boolean injected();

    public abstract ParamFutureStatus futureStatus();

    public String name() {
      return param().getName();
    }

    public String constantFieldName() {
      String baseName = BaseUtils.convertToUpperUnderscore(name());
      return uniqueSerial == 0 ? baseName : baseName + "__" + uniqueSerial;
    }

    public void updateConstantFieldName() {
      uniqueSerial++;
    }

    public String setterName() {
      return "set" + makeUpperCamelCase(name());
    }

    public String adderName() {
      return "add" + makeUpperCamelCase(name());
    }

    public String futureSetterName() {
      Preconditions.checkState(futureStatus() == ParamFutureStatus.HANDLED);
      return "set" + makeUpperCamelCase(name()) + "Future";
    }

    public SoyType type() {
      return param().getType();
    }

    public Iterable<JavaType> futureTypes() {
      Preconditions.checkState(futureStatus() == ParamFutureStatus.HANDLED);
      return javaTypes();
    }

    public List<JavaType> javaTypes() {
      return InvocationBuilderTypeUtils.getJavaTypes(type());
    }

    public boolean required() {
      return param().isRequired();
    }

    public boolean requiredAndNotIndirect() {
      return required() && !indirect();
    }
  }

  private final String javaPackage;

  public SoyFileNodeTransformer(String javaPackage, TemplateRegistry templateRegistry) {
    this.javaPackage = javaPackage;
  }

  public FileInfo transform(SoyFileNode node) {
    Path path = Paths.get(node.getFilePath());
    String fqClassName = javaPackage + "." + convertSoyFileNameToJavaClassName(node);
    List<TemplateInfo> templates = new ArrayList<>();

    Set<String> uniqueTemplateClassNames = new HashSet<>();
    for (TemplateNode template : node.getTemplates()) {
      if (template.getVisibility() == Visibility.PUBLIC
          && template.getKind() != SoyNode.Kind.TEMPLATE_DELEGATE_NODE) {

        String templateClassName = generateTemplateClassName(template);
        if (!RESERVED_IDENTIFIERS.contains(templateClassName)
            && uniqueTemplateClassNames.add(templateClassName)) {
          templates.add(transform(template, fqClassName + "." + templateClassName));
        } else {
          templates.add(TemplateInfo.error(template, TemplateStatus.NAME_COLLISION));
        }
      }
    }

    return new AutoValue_SoyFileNodeTransformer_FileInfo(
        node, path, fqClassName, ImmutableList.copyOf(templates));
  }

  private TemplateInfo transform(TemplateNode template, String className) {
    List<ParamInfo> params = getAllParams(template);
    return new AutoValue_SoyFileNodeTransformer_TemplateInfo(
        className, ImmutableList.copyOf(params), TemplateStatus.HANDLED, template);
  }

  private List<ParamInfo> getAllParams(TemplateNode template) {
    Map<String, ParamInfo> params = new LinkedHashMap<>();

    for (TemplateParam param : template.getAllParams()) {
      params.put(param.name(), ParamInfo.of(param, ParamStatus.HANDLED));
    }

    addIndirectParams(template, params);
    updateParamStatuses(params);
    updateParamFutureStatuses(params);

    return ImmutableList.copyOf(params.values());
  }

  private void addIndirectParams(TemplateNode template, Map<String, ParamInfo> params) {
    Set<String> directParamNames = ImmutableSet.copyOf(params.keySet());

    IndirectParamsInfo idi =
        new IndirectParamsCalculator(template.getParent().getTemplateRegistry())
            .calculateIndirectParams(
                TemplateMetadata.asTemplateType(TemplateMetadata.fromTemplate(template)));

    for (Map.Entry<String, Parameter> entry : idi.indirectParams.entrySet()) {
      String paramName = entry.getKey();
      if (directParamNames.contains(paramName)) {
        // if the param is direct, just use that definition of the type preferably
        continue;
      }
      Parameter param = entry.getValue();

      // Combine the types from all parameters, direct and indirect, with the same name.
      Optional<SoyType> superType =
          upcastTypesForIndirectParams(idi.indirectParamTypes.get(paramName));

      // If we can't combine all those types into a single supported type then fail.

      if (!superType.isPresent()) {
        params.put(paramName, ParamInfo.of(param, ParamStatus.INDIRECT_INCOMPATIBLE_TYPES, true));
        continue;
      } else if (hasProtoDep(superType.get())) {
        // Temporarily skip any indirect params with proto dependencies since they can cause java
        // build errors.
        params.put(paramName, ParamInfo.of(param, ParamStatus.INDIRECT_PROTO, true));
        continue;
      }

      // Create a new indirect parameter.
      params.put(
          paramName,
          ParamInfo.of(
              param.toBuilder()
                  .setType(superType.get())
                  .setDescription(
                      modifyIndirectDesc(
                          param.getDescription(), idi.paramKeyToCalleesMultimap.get(paramName)))
                  .build(),
              ParamStatus.HANDLED,
              true));
    }
  }

  private static boolean hasProtoDep(SoyType type) {
    return Streams.stream(SoyTypes.getTypeTraverser(type, null))
        .anyMatch(t -> t.getKind() == Kind.PROTO || t.getKind() == Kind.PROTO_ENUM);
  }

  private static void updateParamStatuses(Map<String, ParamInfo> params) {
    HashSet<String> setterNames = new HashSet<>();
    for (Map.Entry<String, ParamInfo> entry : params.entrySet()) {
      String paramName = entry.getKey();
      ParamInfo param = entry.getValue();

      if (InvocationBuilderTypeUtils.isJavaIncompatible(param.type())) {
        changeParamStatus(params, paramName, ParamStatus.JAVA_INCOMPATIBLE);
        continue;
      }

      String setterName = getParamSetterSuffix(paramName);
      if (RESERVED_IDENTIFIERS.contains(paramName) || !setterNames.add(setterName)) {
        changeParamStatus(params, paramName, ParamStatus.NAME_COLLISION);
        continue;
      }

      if (param.javaTypes().isEmpty()) {
        changeParamStatus(params, paramName, ParamStatus.UNHANDLED_TYPE);
      }
    }
  }

  private static void updateParamFutureStatuses(Map<String, ParamInfo> params) {
    Set<String> allSetters =
        params.values().stream().map(ParamInfo::setterName).collect(Collectors.toSet());

    for (Map.Entry<String, ParamInfo> entry : params.entrySet()) {
      String paramName = entry.getKey();
      ParamInfo param = entry.getValue();

      if (param.status() != ParamStatus.HANDLED) {
        continue;
      }

      // Guard against properties p1 and p1_future.
      if (allSetters.contains(param.futureSetterName())) {
        changeParamFutureStatus(params, paramName, ParamFutureStatus.NAME_COLLISION);
        continue;
      }

      List<JavaType> javaTypes = param.javaTypes();
      // For now only support Future methods for properties that do not create overloaded methods
      // (because of generic type erasure).
      if (javaTypes.size() == 1 && javaTypes.get(0).isGenericsTypeSupported()) {
        changeParamFutureStatus(params, paramName, ParamFutureStatus.HANDLED);
      } else {
        changeParamFutureStatus(params, paramName, ParamFutureStatus.UNHANDLED);
      }
    }
  }

  private static void changeParamFutureStatus(
      Map<String, ParamInfo> params, String paramName, ParamFutureStatus futureStatus) {
    ParamInfo previous = params.get(paramName);
    params.put(
        paramName,
        ParamInfo.of(
            previous.param(),
            previous.status(),
            previous.indirect(),
            previous.injected(),
            futureStatus));
  }

  private static void changeParamStatus(
      Map<String, ParamInfo> params, String paramName, ParamStatus newStatus) {
    ParamInfo previous = params.get(paramName);
    params.put(
        paramName,
        ParamInfo.of(
            previous.param(),
            newStatus,
            previous.indirect(),
            previous.injected(),
            previous.futureStatus()));
  }

  private static String modifyIndirectDesc(
      String description, Collection<TemplateMetadata> callees) {
    StringBuilder sb = new StringBuilder();
    if (description != null) {
      sb.append(description);
      sb.append(" ");
    }
    sb.append("[From template");
    if (callees.size() > 1) {
      sb.append("s");
    }
    sb.append(": ");
    Joiner.on(", ")
        .appendTo(sb, callees.stream().map(TemplateMetadata::getTemplateName).sorted().iterator());
    sb.append("]");
    return sb.toString();
  }

  private static String convertSoyFileNameToJavaClassName(SoyFileNode soyFile) {
    String fileName = soyFile.getFileName();
    if (fileName == null) {
      throw new IllegalArgumentException(
          "Trying to generate Java class name based on Soy file name, but Soy file name was"
              + " not provided.");
    }
    if (Ascii.toLowerCase(fileName).endsWith(".soy")) {
      fileName = fileName.substring(0, fileName.length() - 4);
    }
    return makeUpperCamelCase(fileName) + "Templates";
  }

  private static String generateTemplateClassName(TemplateNode template) {
    String namespacedTemplateName = template.getTemplateName();
    String templateName =
        namespacedTemplateName.substring(namespacedTemplateName.lastIndexOf('.') + 1);

    // Convert the template name to upper camel case (stripping non-alphanumeric characters),  (e.g.
    // template "foo" -> "Foo").
    return makeUpperCamelCase(templateName);
  }

  private static String getParamSetterSuffix(String paramName) {
    return makeUpperCamelCase(paramName);
  }
}
