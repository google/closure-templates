# TODO

This file is meant to track technical debt issues in the Soy codebase. This
isn't meant to track bugs or feature requests, but rather long term design
issues.

## Eliminate `V1_0` features whenever possible

The V1_0 mode in the compiler disables some common compiler checks. With the
exception of uses of the `v1Expression` function these should all be fixable.

### Eliminate support for buggy soydoc params in v1 templates

v1 allows soydoc that looks like `@param {string} foo` which is an error in 2.0.

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

### Enforce that all required parameters are passed

v1 templates don't have to pass all parameters required by their callees. This
is very errorprone and could be easily removed by marking parameters as optional
where callers don't pass them.

## Eliminate legacy compiler options

*   --shouldProvideRequireSoyNamespaces and
    --should_provide_require_js_functions These should be eliminated with a
    reasonable default selected. This will have implications for v1 templates
    that are currently missing namespaces.
    *   This step's goal is to consolidate all Soy files to use the same option
        for providing the namespace at the Soy file level and at the template
        level. This will eliminate one of the biggest problems that user face
        with incompatible templates across large code bases.
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
