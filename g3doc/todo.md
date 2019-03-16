# TODO

This file is meant to track technical debt issues in the Soy codebase. This
isn't meant to track bugs or feature requests, but rather long term design
issues.

## Eliminate print directives

we should eliminate print directives in favor of soy functions. Internally in
the compiler we could continue to use them as part of the autoescaper
strategies, but as a public api they would be deprecated.

To enable this we could provide a simple way to write a `SoySourceFunction` that
could be used via the print directive syntax. Then users could reimplement their
print directives and incrementally migrate code.

## Eliminate tofu

## Eliminate soy_js build rules

## Move af_soy_library to `third_party/java/builddefs` and rename to `soy_library`

## Eliminate the SoyData type hierarchy

There is a half finished migration of `SoyData` -> `SoyValue` this should be
completed and the various SoyData classes should be deleted. We should also try
to delete the public use of internal soy collection objects (`SoyEasyList`,
`SoyEasyDict`...).
