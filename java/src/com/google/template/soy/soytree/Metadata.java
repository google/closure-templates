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

package com.google.template.soy.soytree;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.types.DelegatingSoyTypeRegistry;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.NamedType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.TemplateType.TemplateKind;
import com.google.template.soy.types.UnionType;
import com.google.template.soy.types.UnknownType;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Utility methods for {@link FileSetMetadata}, {@link FileMetadata}, {@link TemplateMetadata}, etc.
 */
public final class Metadata {

  public static final FileSetMetadata EMPTY_FILESET =
      new AutoValue_Metadata_DepsFileSetMetadata(
          ImmutableList.of(), ParseContext.of(ErrorReporter.exploding(), null));

  private static final SoyErrorKind DUPLICATE_TEMPLATES =
      SoyErrorKind.of("Template/element ''{0}'' already defined at {1}.");
  private static final SoyErrorKind DUPLICATE_DELEGATE_TEMPLATES_IN_MOD =
      SoyErrorKind.of(
          "Delegate/Modifies template ''{0}'' already defined in mod {1}: {2}",
          StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind TEMPLATE_OR_ELEMENT_AND_DELTEMPLATE_WITH_SAME_NAME =
      SoyErrorKind.of("Found deltemplate {0} with the same name as a template/element at {1}.");

  private Metadata() {}

  /**
   * Builds a PartialFileSetMetadata for the AST being compiled and compilation deps. This can be
   * called any time during compilation since it only depends on the parse passes.
   */
  public static PartialFileSetMetadata partialMetadataForAst(
      PartialFileSetMetadata deps, ImmutableList<SoyFileNode> ast) {
    return new AstPartialFileSetMetadata(deps, ast);
  }

  /** Builds a FileSetMetadata for deps from compilation units. */
  public static FileSetMetadata metadataForDeps(
      List<CompilationUnitAndKind> compilationUnits,
      ErrorReporter errorReporter,
      SoyTypeRegistry typeRegistry) {
    return new AutoValue_Metadata_DepsFileSetMetadata(
        ImmutableList.copyOf(compilationUnits),
        ParseContext.of(
            errorReporter, new NamedTypeResolving(typeRegistry, compilationUnits, errorReporter)));
  }

  /**
   * Builds a FileSetMetadata for the AST being compiled and compilation deps. This should only be
   * called late in the compiler when most type information has been calculated.
   */
  public static FileSetMetadata metadataForAst(
      FileSetMetadata deps,
      List<SoyFileNode> ast,
      ErrorReporter errorReporter,
      SoyTypeRegistry typeRegistry) {
    return new AstFileSetMetadata(deps, ast, ParseContext.of(errorReporter, typeRegistry));
  }

  /** Look up possible targets for a call. */
  public static ImmutableList<TemplateType> getTemplates(
      FileSetMetadata fileSetMetadata, CallNode node) {
    if (node instanceof CallBasicNode) {
      SoyType calleeType = ((CallBasicNode) node).getCalleeExpr().getType();
      if (calleeType == null) {
        return ImmutableList.of();
      }
      if (calleeType.getKind() == SoyType.Kind.TEMPLATE) {
        return ImmutableList.of((TemplateType) calleeType);
      } else if (calleeType.getKind() == SoyType.Kind.UNION) {
        ImmutableList.Builder<TemplateType> signatures = ImmutableList.builder();
        for (SoyType member : ((UnionType) calleeType).getMembers()) {
          // Rely on CheckTemplateCallsPass to catch this with nice error messages.
          Preconditions.checkState(member.getKind() == SoyType.Kind.TEMPLATE);
          signatures.add((TemplateType) member);
        }
        return signatures.build();
      } else if (calleeType.getKind() == SoyType.Kind.UNKNOWN) {
        // We may end up with UNKNOWN here for external calls.
        return ImmutableList.of();
      } else {
        // Rely on previous passes to catch this with nice error messages.
        throw new IllegalStateException(
            "Unexpected type in call: " + calleeType.getClass() + " - " + node.toSourceString());
      }
    } else {
      String calleeName = ((CallDelegateNode) node).getDelCalleeName();
      return fileSetMetadata
          .getDelTemplateSelector()
          .delTemplateNameToValues()
          .get(calleeName)
          .stream()
          .map(TemplateMetadata::getTemplateType)
          .collect(toImmutableList());
    }
  }

  /**
   * Gets the content kind that a call results in. If used with delegate calls, the delegate
   * templates must use strict autoescaping. This relies on the fact that all delegate calls must
   * have the same kind when using strict autoescaping. This is enforced by CheckDelegatesPass.
   *
   * @param node The {@link CallBasicNode} or {@link CallDelegateNode}.
   * @return The kind of content that the call results in.
   */
  public static Optional<SanitizedContentKind> getCallContentKind(
      FileSetMetadata fileSetMetadata, CallNode node) {
    ImmutableList<TemplateType> templateNodes = getTemplates(fileSetMetadata, node);
    // For per-file compilation, we may not have any of the delegate templates in the compilation
    // unit.
    if (!templateNodes.isEmpty()) {
      return Optional.of(templateNodes.get(0).getContentKind().getSanitizedContentKind());
    }
    // The template node may be null if the template is being compiled in isolation.
    return Optional.empty();
  }

  /** Parameter bean. */
  @AutoValue
  abstract static class ParseContext {
    static ParseContext of(ErrorReporter errorReporter, SoyTypeRegistry typeRegistry) {
      return new AutoValue_Metadata_ParseContext(errorReporter, typeRegistry);
    }

    abstract ErrorReporter errorReporter();

    @Nullable
    abstract SoyTypeRegistry typeRegistry();
  }

  /** FileSetMetadata for deps. */
  @AutoValue
  abstract static class DepsFileSetMetadata implements FileSetMetadata {

    abstract ImmutableList<CompilationUnitAndKind> units();

    abstract ParseContext context();

    @Memoized
    protected ImmutableMap<SourceLogicalPath, FileMetadata> fileIndex() {
      ImmutableMap.Builder<SourceLogicalPath, FileMetadata> builder = ImmutableMap.builder();
      units()
          .forEach(
              u ->
                  u.compilationUnit()
                      .getFileList()
                      .forEach(
                          f ->
                              builder.put(
                                  SourceFilePath.create(f).asLogicalPath(),
                                  new AutoValue_Metadata_DepsFileMetadata(
                                      f, u.fileKind(), context()))));
      return builder.buildOrThrow();
    }

    @Memoized
    protected ImmutableMap<String, TemplateMetadata> templateIndex() {
      Map<String, TemplateMetadata> builder = new LinkedHashMap<>();
      getAllTemplates()
          .forEach(
              t -> {
                TemplateMetadata previous = builder.put(t.getTemplateName(), t);
                warnNameCollision(context().errorReporter(), previous, t);
              });
      return ImmutableMap.copyOf(builder);
    }

    @Override
    public TemplateMetadata getTemplate(String templateFqn) {
      return templateIndex().get(templateFqn);
    }

    @Override
    @Memoized
    public DelTemplateSelector<TemplateMetadata> getDelTemplateSelector() {
      return buildDelTemplateSelector(getAllTemplates(), context().errorReporter(), this);
    }

    @Override
    public FileMetadata getFile(SourceLogicalPath path) {
      return fileIndex().get(path);
    }

    @Override
    public ImmutableCollection<? extends FileMetadata> getAllFiles() {
      return fileIndex().values();
    }

    @Override
    @Memoized
    public ImmutableCollection<TemplateMetadata> getAllTemplates() {
      return getAllFiles().stream()
          .flatMap(f -> f.getTemplates().stream())
          .collect(toImmutableList());
    }
  }

  /** PartialFileSetMetadata for AST under compilation. */
  private static class AstPartialFileSetMetadata implements PartialFileSetMetadata {

    private final ImmutableMap<SourceLogicalPath, PartialFileMetadata> fullFileIndex;

    /** ASTs are mutable so we need to copy all data in the constructor. */
    public AstPartialFileSetMetadata(PartialFileSetMetadata deps, List<SoyFileNode> ast) {
      Map<SourceLogicalPath, PartialFileMetadata> fullFileIndexBuilder = new LinkedHashMap<>();
      for (PartialFileMetadata depFile : deps.getAllPartialFiles()) {
        fullFileIndexBuilder.put(depFile.getPath().asLogicalPath(), depFile);
      }
      ast.forEach(
          f ->
              fullFileIndexBuilder.put(
                  f.getFilePath().asLogicalPath(), new AstPartialFileMetadata(f)));
      fullFileIndex = ImmutableMap.copyOf(fullFileIndexBuilder);
    }

    @Override
    public PartialFileMetadata getPartialFile(SourceLogicalPath path) {
      return fullFileIndex.get(path);
    }

    @Override
    public ImmutableCollection<? extends PartialFileMetadata> getAllPartialFiles() {
      return fullFileIndex.values();
    }
  }

  /** FileSetMetadata for AST under compilation. */
  private static class AstFileSetMetadata implements FileSetMetadata {

    private final ParseContext context;
    // The following three fields contain all files/templates from both AST and deps.
    private final ImmutableMap<SourceLogicalPath, FileMetadata> fullFileIndex;
    @LazyInit private ImmutableMap<String, TemplateMetadata> lazyFullTemplateIndex;
    @LazyInit private ImmutableList<TemplateMetadata> lazyAllTemplatesWithCollisions;
    @LazyInit private DelTemplateSelector<TemplateMetadata> delTemplateSelector;

    /** ASTs are mutable so we need to copy all data in the constructor. */
    public AstFileSetMetadata(FileSetMetadata deps, List<SoyFileNode> ast, ParseContext context) {
      this.context = context;

      Map<SourceLogicalPath, FileMetadata> fullFileIndexBuilder = new LinkedHashMap<>();
      for (FileMetadata depFile : deps.getAllFiles()) {
        fullFileIndexBuilder.put(depFile.getPath().asLogicalPath(), depFile);
      }
      ast.forEach(
          f -> {
            FileMetadata astMetadata = new AstFileMetadata(f);
            // Put AST file at end of iteration order.
            FileMetadata protoMetadata =
                fullFileIndexBuilder.remove(f.getFilePath().asLogicalPath());
            fullFileIndexBuilder.put(f.getFilePath().asLogicalPath(), astMetadata);
            // Several unit tests require this behavior. It might be needed by some direct users
            // of SoyFileSet.
            if (protoMetadata != null
                && protoMetadata.getNamespace().equals(astMetadata.getNamespace())) {
              fullFileIndexBuilder.put(
                  f.getFilePath().asLogicalPath(), merge(astMetadata, protoMetadata));
            }
          });
      fullFileIndex = ImmutableMap.copyOf(fullFileIndexBuilder);
    }

    private ImmutableMap<String, TemplateMetadata> templateIndex() {
      ImmutableMap<String, TemplateMetadata> tmp = lazyFullTemplateIndex;
      ImmutableList.Builder<TemplateMetadata> allBuilder = ImmutableList.builder();
      if (tmp == null) {
        Map<String, TemplateMetadata> builder = new LinkedHashMap<>();
        fullFileIndex
            .values()
            .forEach(
                f ->
                    f.getTemplates()
                        .forEach(
                            t -> {
                              allBuilder.add(t);
                              TemplateMetadata previous = builder.put(t.getTemplateName(), t);
                              // Avoid duplicate warnings between deps and AST versions.
                              if (!(f instanceof DepsFileMetadata)) {
                                warnNameCollision(context.errorReporter(), previous, t);
                              }
                            }));
        tmp = ImmutableMap.copyOf(builder);
        lazyFullTemplateIndex = tmp;
        lazyAllTemplatesWithCollisions = allBuilder.build();
      }
      return tmp;
    }

    @Override
    public TemplateMetadata getTemplate(String templateFqn) {
      return templateIndex().get(templateFqn);
    }

    @Override
    public DelTemplateSelector<TemplateMetadata> getDelTemplateSelector() {
      DelTemplateSelector<TemplateMetadata> tmp = delTemplateSelector;
      if (tmp == null) {
        tmp = buildDelTemplateSelector(getAllTemplates(), context.errorReporter(), this);
        delTemplateSelector = tmp;
      }
      return tmp;
    }

    @Override
    public FileMetadata getFile(SourceLogicalPath path) {
      return fullFileIndex.get(path);
    }

    @Override
    public ImmutableCollection<? extends FileMetadata> getAllFiles() {
      return fullFileIndex.values();
    }

    @Override
    public ImmutableCollection<TemplateMetadata> getAllTemplates() {
      templateIndex(); // init lazyAllTemplatesWithCollisions
      return lazyAllTemplatesWithCollisions;
    }
  }

  abstract static class AbstractFileMetadata implements FileMetadata {

    protected abstract ImmutableMap<String, ? extends Constant> constantIndex();

    protected abstract ImmutableListMultimap<String, ? extends Extern> externIndex();

    protected abstract ImmutableMap<String, TemplateMetadata> templateIndex();

    protected abstract ImmutableMap<String, ? extends TypeDef> typeDefIndex();

    @Override
    public Constant getConstant(String name) {
      return constantIndex().get(name);
    }

    @Override
    public TemplateMetadata getTemplate(String name) {
      return templateIndex().get(name);
    }

    @Override
    public final ImmutableCollection<? extends Constant> getConstants() {
      return constantIndex().values();
    }

    @Override
    public final ImmutableSet<String> getTemplateNames() {
      return templateIndex().keySet();
    }

    @Override
    public final ImmutableSet<String> getConstantNames() {
      return constantIndex().keySet();
    }

    @Override
    public Collection<? extends Extern> getExterns() {
      return externIndex().values();
    }

    @Override
    public List<? extends Extern> getExterns(String name) {
      return externIndex().get(name);
    }

    @Override
    public Set<String> getExternNames() {
      return externIndex().keySet();
    }

    @Override
    public TypeDef getTypeDef(String name) {
      return typeDefIndex().get(name);
    }

    @Override
    public Collection<? extends TypeDef> getTypeDefs() {
      return typeDefIndex().values();
    }

    @Override
    public Set<String> getTypeDefNames() {
      return typeDefIndex().keySet();
    }
  }

  /** FileMetadata for deps. */
  @AutoValue
  abstract static class DepsFileMetadata extends AbstractFileMetadata {

    protected abstract SoyFileP proto();

    protected abstract SoyFileKind kind();

    protected abstract ParseContext context();

    @Override
    @Memoized
    public SourceFilePath getPath() {
      return SourceFilePath.create(proto());
    }

    @Memoized
    @Override
    protected ImmutableMap<String, ConstantImpl> constantIndex() {
      return proto().getConstantsList().stream()
          .collect(
              toImmutableMap(
                  ConstantP::getName,
                  c ->
                      ConstantImpl.of(
                          c.getName(),
                          TemplateMetadataSerializer.fromProto(
                              c.getType(),
                              context().typeRegistry(),
                              getPath(),
                              context().errorReporter())),
                  (t1, t2) -> t1) /* Will be reported as error elsewhere. */);
    }

    @Memoized
    @Override
    public ImmutableList<TemplateMetadata> getTemplates() {
      return proto().getTemplateList().stream()
          .map(
              t ->
                  TemplateMetadataSerializer.metadataFromProto(
                      proto(),
                      t,
                      kind(),
                      context().typeRegistry(),
                      getPath(),
                      context().errorReporter()))
          .collect(toImmutableList());
    }

    @Memoized
    @Override
    protected ImmutableListMultimap<String, ? extends Extern> externIndex() {
      return proto().getExternsList().stream()
          .collect(
              toImmutableListMultimap(
                  ExternP::getName,
                  e ->
                      ExternImpl.of(
                          e.getName(),
                          (FunctionType)
                              TemplateMetadataSerializer.fromProto(
                                  SoyTypeP.newBuilder().setFunction(e.getSignature()).build(),
                                  context().typeRegistry(),
                                  getPath(),
                                  context().errorReporter()))));
    }

    @Override
    public SoyFileKind getSoyFileKind() {
      return kind();
    }

    @Memoized
    @Override
    protected ImmutableMap<String, TemplateMetadata> templateIndex() {
      return getTemplates().stream()
          .collect(
              toImmutableMap(
                  t -> {
                    String name = t.getTemplateName();
                    int index = name.lastIndexOf('.');
                    return index >= 0 ? name.substring(index + 1) : name;
                  },
                  t -> t,
                  (t1, t2) -> t1) /* Will be reported as error elsewhere. */);
    }

    @Memoized
    @Override
    protected ImmutableMap<String, TypeDef> typeDefIndex() {
      return proto().getTypeDefsList().stream()
          .collect(
              toImmutableMap(
                  TypeDefP::getName,
                  c ->
                      typeDefOf(
                          c.getName(),
                          c.getExported(),
                          NamedType.create(
                              c.getName(),
                              proto().getNamespace(),
                              TemplateMetadataSerializer.fromProto(
                                  c.getType(),
                                  context().typeRegistry(),
                                  getPath(),
                                  context().errorReporter()))),
                  (t1, t2) -> t1) /* Will be reported as error elsewhere. */);
    }

    @Override
    public String getNamespace() {
      return proto().getNamespace();
    }
  }

  /** PartialFileMetadata for AST under compilation. */
  private static final class AstPartialFileMetadata implements PartialFileMetadata {

    private final SourceFilePath path;
    private final String namespace;
    private final ImmutableSet<String> templateNames;
    private final ImmutableSet<String> constantNames;
    private final ImmutableSet<String> externNames;
    private final ImmutableSet<String> typeDefNames;

    /** ASTs are mutable so we need to copy all data in the constructor. */
    public AstPartialFileMetadata(SoyFileNode ast) {
      this.path = ast.getFilePath();
      this.namespace = ast.getNamespace();
      this.templateNames =
          ast.getTemplates().stream()
              .map(TemplateNode::getPartialTemplateName)
              .collect(toImmutableSet());
      this.constantNames =
          ast.getConstants().stream()
              .filter(ConstNode::isExported)
              .map(c -> c.getVar().name())
              .collect(toImmutableSet());
      this.typeDefNames =
          ast.getTypeDefs().stream().map(TypeDefNode::getName).collect(toImmutableSet());
      this.externNames =
          ast.getExterns().stream()
              .filter(ExternNode::isExported)
              .map(e -> e.getIdentifier().identifier())
              .collect(toImmutableSet());
    }

    @Override
    public SourceFilePath getPath() {
      return path;
    }

    @Override
    public String getNamespace() {
      return namespace;
    }

    @Override
    public ImmutableSet<String> getTemplateNames() {
      return templateNames;
    }

    @Override
    public ImmutableSet<String> getConstantNames() {
      return constantNames;
    }

    @Override
    public ImmutableSet<String> getExternNames() {
      return externNames;
    }

    @Override
    public ImmutableSet<String> getTypeDefNames() {
      return typeDefNames;
    }
  }

  public static FileMetadata forAst(SoyFileNode node) {
    return new AstFileMetadata(node);
  }

  /** FileMetadata for AST under compilation. */
  private static final class AstFileMetadata extends AbstractFileMetadata {

    private final SourceFilePath path;
    private final String namespace;
    private final ImmutableMap<String, ConstantImpl> constantIndex;
    private final ImmutableMap<String, TypeDef> typeDefIndex;
    private final ImmutableList<TemplateMetadata> allTemplates;
    private final ImmutableMap<String, TemplateMetadata> templateIndex;
    private final ImmutableListMultimap<String, ExternImpl> externIndex;

    /** ASTs are mutable so we need to copy all data in the constructor. */
    public AstFileMetadata(SoyFileNode ast) {
      this.path = ast.getFilePath();
      this.namespace = ast.getNamespace();
      this.constantIndex =
          ast.getConstants().stream()
              .filter(ConstNode::isExported)
              .collect(
                  toImmutableMap(
                      c -> c.getVar().name(),
                      c ->
                          ConstantImpl.of(
                              c.getVar().name(),
                              // Type will not be set if type checking is off.
                              c.getVar().typeOrDefault(UnknownType.getInstance())),
                      (t1, t2) -> t1 /* Will be reported as error elsewhere. */));
      this.typeDefIndex =
          ast.getTypeDefs().stream()
              .collect(
                  toImmutableMap(
                      TypeDefNode::getName,
                      c -> typeDefOf(c.getName(), c.isExported(), c.asNamedType()),
                      (t1, t2) -> t1 /* Will be reported as error elsewhere. */));
      this.externIndex =
          ast.getExterns().stream()
              .filter(ExternNode::isExported)
              .collect(
                  toImmutableListMultimap(
                      e -> e.getIdentifier().identifier(),
                      e -> ExternImpl.of(e.getIdentifier().identifier(), e.getType())));

      ImmutableList.Builder<TemplateMetadata> templates = ImmutableList.builder();
      Map<String, TemplateMetadata> index = new LinkedHashMap<>();
      ast.getTemplates()
          .forEach(
              t -> {
                TemplateMetadata metadata = TemplateMetadata.fromTemplate(t);
                templates.add(metadata);
                // Duplicates reported elsewhere.
                index.putIfAbsent(t.getPartialTemplateName(), metadata);
              });
      this.allTemplates = templates.build();
      this.templateIndex = ImmutableMap.copyOf(index);
    }

    @Override
    public ImmutableList<TemplateMetadata> getTemplates() {
      return allTemplates;
    }

    @Override
    protected ImmutableMap<String, ConstantImpl> constantIndex() {
      return constantIndex;
    }

    @Override
    protected ImmutableMap<String, ? extends TypeDef> typeDefIndex() {
      return typeDefIndex;
    }

    @Override
    protected ImmutableListMultimap<String, ? extends Extern> externIndex() {
      return externIndex;
    }

    @Override
    protected ImmutableMap<String, TemplateMetadata> templateIndex() {
      return templateIndex;
    }

    @Override
    public SourceFilePath getPath() {
      return path;
    }

    @Override
    public String getNamespace() {
      return namespace;
    }

    @Override
    public SoyFileKind getSoyFileKind() {
      return SoyFileKind.SRC;
    }
  }

  @AutoValue
  abstract static class ConstantImpl implements FileMetadata.Constant {

    private static ConstantImpl of(String name, SoyType type) {
      return new AutoValue_Metadata_ConstantImpl(name, type);
    }

    @Override
    public abstract String getName();

    @Override
    public abstract SoyType getType();
  }

  public static FileMetadata.TypeDef typeDefOf(String name, boolean exported, NamedType type) {
    return new AutoValue_Metadata_TypeDefImpl(name, exported, type);
  }

  @AutoValue
  abstract static class TypeDefImpl implements FileMetadata.TypeDef {
    @Override
    public abstract String getName();

    @Override
    public abstract boolean isExported();

    @Override
    public abstract NamedType getType();
  }

  @AutoValue
  abstract static class ExternImpl implements FileMetadata.Extern {

    private static ExternImpl of(String name, FunctionType signature) {
      return new AutoValue_Metadata_ExternImpl(name, signature);
    }

    @Override
    public abstract String getName();

    @Override
    public abstract FunctionType getSignature();
  }

  private static FileMetadata merge(FileMetadata primary, FileMetadata secondary) {
    return new MergedFileMetadata(primary, secondary);
  }

  /**
   * FileMetadata that overlays an AST FileMetadata on a dep FileMetadata. Some compiler use cases
   * require this (?).
   */
  private static class MergedFileMetadata extends AbstractFileMetadata {

    private final FileMetadata primary;
    private final ImmutableMap<String, Constant> constantIndex;
    private final ImmutableMap<String, TypeDef> typeDefIndex;
    private final ImmutableMap<String, TemplateMetadata> templateIndex;

    public MergedFileMetadata(FileMetadata primary, FileMetadata secondary) {
      this.primary = primary;

      Map<String, Constant> constants = new LinkedHashMap<>();
      secondary.getConstants().forEach(c -> constants.put(c.getName(), c));
      primary.getConstants().forEach(c -> constants.put(c.getName(), c));
      constantIndex = ImmutableMap.copyOf(constants);

      Map<String, TypeDef> typeDefs = new LinkedHashMap<>();
      secondary.getTypeDefs().forEach(c -> typeDefs.put(c.getName(), c));
      primary.getTypeDefs().forEach(c -> typeDefs.put(c.getName(), c));
      typeDefIndex = ImmutableMap.copyOf(typeDefs);

      Map<String, TemplateMetadata> templates = new LinkedHashMap<>();
      secondary.getTemplates().forEach(t -> templates.put(t.getTemplateName(), t));
      primary.getTemplates().forEach(t -> templates.put(t.getTemplateName(), t));
      templateIndex = ImmutableMap.copyOf(templates);
    }

    @Override
    protected ImmutableMap<String, Constant> constantIndex() {
      return constantIndex;
    }

    @Override
    protected ImmutableListMultimap<String, ? extends Extern> externIndex() {
      return ((AbstractFileMetadata) primary).externIndex();
    }

    @Override
    public ImmutableCollection<TemplateMetadata> getTemplates() {
      // Don't report any duplicates with merged since one file overwrites the other.
      return templateIndex.values();
    }

    @Override
    protected ImmutableMap<String, TemplateMetadata> templateIndex() {
      return templateIndex;
    }

    @Override
    protected ImmutableMap<String, ? extends TypeDef> typeDefIndex() {
      return typeDefIndex;
    }

    @Override
    public SourceFilePath getPath() {
      return primary.getPath();
    }

    @Override
    public String getNamespace() {
      return primary.getNamespace();
    }

    @Override
    public SoyFileKind getSoyFileKind() {
      return primary.getSoyFileKind();
    }
  }

  private static DelTemplateSelector<TemplateMetadata> buildDelTemplateSelector(
      Collection<TemplateMetadata> allTemplates,
      ErrorReporter errorReporter,
      FileSetMetadata fileSetMetadata) {
    DelTemplateSelector.Builder<TemplateMetadata> builder = new DelTemplateSelector.Builder<>();

    allTemplates.forEach(
        template -> {
          if (template.getDelTemplateName() != null) {
            String delTemplateName =
                !template.getTemplateType().getLegacyDeltemplateNamespace().isEmpty()
                    ? template.getTemplateType().getLegacyDeltemplateNamespace()
                    : template.getDelTemplateName();
            addDeltemplate(delTemplateName, template, errorReporter, fileSetMetadata, builder);
          }
        });
    return builder.build();
  }

  private static void addDeltemplate(
      String delTemplateName,
      TemplateMetadata template,
      ErrorReporter errorReporter,
      FileSetMetadata fileSetMetadata,
      DelTemplateSelector.Builder<TemplateMetadata> builder) {
    String modName = template.getModName();
    String variant = template.getDelTemplateVariant();
    TemplateMetadata previous;
    if (modName == null) {
      // default delegate
      previous = builder.addDefault(delTemplateName, variant, template);
    } else {
      previous = builder.add(delTemplateName, modName, variant, template);
      if (previous != null) {
        errorReporter.report(
            template.getSourceLocation(),
            DUPLICATE_DELEGATE_TEMPLATES_IN_MOD,
            delTemplateName,
            modName,
            previous.getSourceLocation());
      }
    }

    TemplateMetadata nameCollision = fileSetMetadata.getBasicTemplateOrElement(delTemplateName);
    // getBasicTemplateOrElement() will return modifiable templates. We're only interested in name
    // collisions between deltemplates and non-modifiable templates.
    if (nameCollision != null
        && nameCollision.getDelTemplateName() == null
        && !template.getTemplateType().isModifiable()
        && !template.getTemplateType().isModifying()) {
      errorReporter.report(
          template.getSourceLocation(),
          TEMPLATE_OR_ELEMENT_AND_DELTEMPLATE_WITH_SAME_NAME,
          delTemplateName,
          nameCollision.getSourceLocation());
    }
  }

  private static void warnNameCollision(
      ErrorReporter errorReporter, TemplateMetadata previous, TemplateMetadata t) {
    if (previous != null
        && !sameFile(previous, t)
        && t.getTemplateType().getTemplateKind() != TemplateKind.DELTEMPLATE) {
      // Collisions in the same file are reported in LocalVariables.
      errorReporter.report(
          t.getSourceLocation(),
          DUPLICATE_TEMPLATES,
          t.getTemplateName(),
          previous.getSourceLocation());
    }
  }

  private static boolean sameFile(TemplateMetadata t1, TemplateMetadata t2) {
    return t1.getSourceLocation().getFileName().equals(t2.getSourceLocation().getFileName());
  }

  /** Simple tuple of un an-evaluated compilation unit containing information about dependencies. */
  @AutoValue
  public abstract static class CompilationUnitAndKind {
    public static CompilationUnitAndKind create(
        SoyFileKind fileKind, CompilationUnit compilationUnit) {
      // sanity check
      checkArgument(
          fileKind != SoyFileKind.SRC, "compilation units should only represent dependencies");
      return new AutoValue_Metadata_CompilationUnitAndKind(fileKind, compilationUnit);
    }

    abstract SoyFileKind fileKind();

    abstract CompilationUnit compilationUnit();
  }

  /**
   * A SoyTypeRegistry that can resolve named types at the point creation by looking them up from
   * the set of Soy header files provided to the compiler.
   */
  private static final class NamedTypeResolving extends DelegatingSoyTypeRegistry {

    private final ImmutableMap<String, TypeDefP> namedTypes;
    private final Map<String, NamedType> cache = new HashMap<>();
    private final ErrorReporter errorReporter;

    NamedTypeResolving(
        SoyTypeRegistry typeRegistry,
        Collection<CompilationUnitAndKind> depHeaders,
        ErrorReporter errorReporter) {
      super(typeRegistry);
      this.errorReporter = errorReporter;
      ImmutableMap.Builder<String, TypeDefP> namedTypesBuilder = ImmutableMap.builder();
      depHeaders.stream()
          .map(CompilationUnitAndKind::compilationUnit)
          .flatMap(u -> u.getFileList().stream())
          .forEach(
              f ->
                  f.getTypeDefsList()
                      .forEach(
                          t -> namedTypesBuilder.put(f.getNamespace() + "." + t.getName(), t)));
      this.namedTypes = namedTypesBuilder.buildOrThrow();
    }

    @Override
    public SoyType getOrCreateNamedType(String name, String namespace) {
      String key = namespace + "." + name;
      return cache.computeIfAbsent(
          key,
          k -> {
            TypeDefP typeDef = namedTypes.get(k);
            SoyType fullType =
                typeDef != null
                    ? TemplateMetadataSerializer.fromProto(
                        typeDef.getType(),
                        this,
                        SourceFilePath.create(namespace, namespace), // fake
                        errorReporter)
                    : UnknownType.getInstance();
            return intern(NamedType.create(name, namespace, fullType));
          });
    }
  }
}
