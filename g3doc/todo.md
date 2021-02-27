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

## Eliminate Tofu

Pros:

*   deleted code,
*   fewer configurations to support,
*   soy feature development is accelerating, so this will pay dividends going
    forward

Cons:

*   we may not be able to delete as much code as we would hope due to how this
    works in the optimizer


## Move af_soy_library to `third_party/java/builddefs` and rename to `soy_library`

A prerequisite for maybe bazel-ifying it

pros:

*   a shorter name,
*   no longer a 'labs' filename,
*   moved to a package without real build rules which should make presubmits run
    faster

Cons:

*   none really except the work, which is fairly automatable.

## goog.module

Fully enable goog.module support for our JavaScript backends. We have done a lot
of work here but we can't actually enabled it yet because doing so will add name
conflicts. This is because the JavaScript generator doesn't keep track of all
the symbols it is going to generate and so can't avoid conflicts. This is
actually already a problem with our `goog.provide` support but it becomes much
worse with `goog.module` since we need to generate more symbols at the top
level.

There are also more classic difficulties like legacy projects that break when
they use `goog.module` code, or projects using per-library translations since
the jscompiiler doesn't support per-library translations with goog.module. (it
isn't clear if this is intentional or not).

The fix will be first detect all the symbols we need to generate and then assign
names, mangling as needed to avoid conflicts. To do this we will probably need
to enhance the `CodeChunk` API and eliminate `JsCodeBuilder`, see next item.

## Elimninate JsCodeBuilder

The JavaScript backends use two strategies for building their code.

*   CodeChunk. A set of structured APIs that can generate code and report on
    things like what `goog.require` statements it needs
*   JsCodeBuilder. A simple enhanced StringBuilder API

At various points we build up CodeChunk objects and then serialize them into the
code builder, the problem is that this eliminates the structured data and also
means we need to be able to serialize everything 'too soon' which means we need
to select symbol names for temporaries before we know all the code we are going
to generate. (see previous item)


## Eliminate the SoyData type hierarchy

There is a half finished migration of `SoyData` -> `SoyValue` this should be
completed and the various SoyData classes should be deleted. We should also try
to delete the public use of internal soy collection objects (`SoyEasyList`,
`SoyEasyDict`...).
