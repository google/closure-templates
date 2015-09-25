# TODO

This file is meant to track technical debt issues in the Soy codebase. This
isn't meant to track bugs or feature requests, but rather long term design
issues.

## Improve consistency of backends by moving passes into the PassManager

Each Soy backend (jssrc, pysrc, tofu, jbcsrc) should try to run the same set of
compiler passes. The current technique is to move shared compiler passes into
`PassManager` and expose backend specific configuration requirements via
explicit setters. This is superior to the historical approach where each backend
has just decided _not_ to run certain passes (backends should have to opt-out
instead of opt-in to consistency).

Work on this task consists mostly of encapsulating compiler functionality in
`PassManager` and pulling it out of `SoyFileSet` and updating unit tests that
were relying on particular passes not running.

## Remove `SyntaxVersion` whenever possible

There are 2 aspects of `SyntaxVersion` _declared_ and _inferred_ syntax version,
we should try to eliminate everything possible. It is likely that we will for a
while still need a way for legacy templates to get V1 behavior (more on this
[below](#v1_language_features), but all other uses for `SyntaxVersion` should be
able to be eliminated.

### Eliminate V2_3

2.3 changes the type checking on the boolean logical operators to result in
`bool` types expressions instead of `?` types. The issue here is that different
backends implement the logical operators differently. Specifically, jssrc uses
the JS `||` operator to implement Soy `or`; this means that the soy expression
`$a or $b` compiles to `opt_data['a'] || opt_data['a']` in jssrc but it compiles
to something like `a.coerceToBoolean() || b.coerceToBoolean()` in Tofu and
jbcsrc.

The new type checks in 2.3 try to make this issue more obvious. So to eliminate
this I think we need to either

*   change the jssrc code generation to something like `!!opt_data['a'] ||
    !!opt_data['a']` to force boolean coercions
*   just allow js and everything else to be different

### Eliminate V2_4

There isn't much to this since no additional language checks are enabled. But
you are _inferred_ to 2.4 if you use `{@inject ...}` parameters instead of `$ij`
params.

We should consider moving everything to use the same syntax for ijs.

## Eliminate legacy language features {#v1_language_features}

Soy has grown a rather large array of legacy compatibility options that don't
have much reason to exist anymore.

A lot of these are related. For example, adding parameter declarations should
probably happen _after_ fixing expression syntax since unparseable expressions
hide data references.

### Eliminate support for files without `{namespace ...}` declarations

In Soy v1, all template declarations were fully qualified and there were no
`{namespace ...}` declarations. We should be able to infer namespaces for all
such files.

### Eliminate support for templates that don't declare parameters

`{@param ...}` tags allow Soy authors to declare parameters that provide a [type]
(https://developers.google.com/closure/templates/docs/commands#parameter-type-expressions)
for that parameter. v1 Soy templates do not have to declare parameters in SoyDoc
or `{@param ...}` tags. This will convert all templates that do not already
declare parameters to instead look like:

```soy
{template .foo}
  {@param myVar: ?}

  {$myVar}
{/template}
```

The default type for these parameter declarations will be `?`, the unknown type.
It would be possible for us to infer the type in certain circumstances, but that
would increase the complexity of the tool significantly.

### Eliminate v1 expression syntax

Unparseable expressions are currently interpreted as v1 expressions and are only
supported in the jssrc backend. We should be able to write tools to help migrate
most of expressions. Here are the major incompatibilities.

*   `&&`, `||` and `!` -> `and`, `or` and `not`
*   String literals using double quotes (use single quotes)
*   translate unknown function references to soy plugins. Probably should wait
    for work on [better plugin support](#better_plugins)
*   eliminate unknown global references (maybe not worht it?)

## Eliminate legacy compiler options

*   --shouldGenerateJsdoc eliminate the flag, there is no reason not to always
    do this.
*   --isUsingIjData flip to true, delete flag
*   --codeStyle remove flag, it is dead
*   --shouldProvideRequireSoyNamespaces and
    --should_provide_require_js_functions These should be eliminated with a
    reasonable default selected. This will have implications for v1 templates
    that are currently missing namespaces.
    *   This step's goal is to consolidate all Soy files to use the same option
        for providing the namespace at the Soy file level and at the template
        level. This will eliminate one of the biggest problems that user face
        with incompatible templates across large code bases.
    *   AI: Add a namespaces tag to all files
    *   AI: Eliminate namespace collisions across soy files (mknichel has some
        scripts)
    *   AI: Flip the provide namespace flag on for all projects.
    *   AI: Remove the options to change this since it won't matter once we
        provide both namespaces and functions
*   --allowv1syntax This should be mostly redundant now that v1 templates need
    the `deprecatedV1="true"` attribute. But eliminating it will be a little
    tricky due to the way 'declaredSyntaxVersion' is used when configuring the
    compiler.

## Improve the plugin apis {#better_plugins}

Implementing a soy plugin is pretty easy (implement `SoyFunction`) but the
registration mechanism is strange (pass a guice module class name to the
compiler) and works against our goals of removing/reducing the guice dependency
from the compiler. Finally, it is required that you implement support for all
backends in one `SoyFunction` subtype. This seems unnecessary.

Proposals:

*   Use the standard ServiceLocator apis to register plugins. Then services can
    just add `@AutoService(SoyJsSrcFunction.class)` to their plugin functions.
    This is the same mechanism that Java annotation processors use.
*   Allow functions to declare dependencies on `ij` variables. Currently plugins
    that run in `Tofu` can rely on being in the same guice injector as the
    application in order to get access application data. This is sort of
    convenient (it often causes weird scoping issues). Instead we could just
    allow plugins to access this kind of data via `$ij` params.
*   Allow backend specific implementations to be implemented in different
    classes
*   Expand the set of built in plugins. There are a lot of reasonable default
    plugins that many teams reimplement (`sqrt`, `listContains`)

## Eliminate the SoyData type hierarchy

There is a half finished migration of `SoyData` -> `SoyValue` this should be
completed and the various SoyData classes should be deleted. We should also try
to delete the public use of internal soy collection objects (`SoyEasyList`,
`SoyEasyDict`...).
