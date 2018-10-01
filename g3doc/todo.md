# TODO

This file is meant to track technical debt issues in the Soy codebase. This
isn't meant to track bugs or feature requests, but rather long term design
issues.

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
