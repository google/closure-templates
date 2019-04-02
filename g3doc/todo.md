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

Details: http://doc/1OeJalQXTz5ZHXU0gvpgFv6dgobJmzuy9gU1Ekl-j4jQ

## Eliminate soy_js build rules in favor of af_soy_library

We have already done a lot of work here, e.g. we added support to genjsdeps and
xaze so that teams can theoretically migrate

However, there is a major issue with translations. `soy_js` (by default) uses
jscompiler for translations which means that jscompiler calculates message ids
(specifically it doesn't set `--goog_messages_are_external`). `af_soy_library`
uses a custom message extraction and id algorithm. This means that by switching
to `af_soy_library` you will (without intervention):

*   break all translations
*   break message extraction

It is somewhat manageable to fix this on a project by project basis if you are
knowledgable about translations, but there is actually no way to do it without
at least temporarily breaking translations.

To actually enable migrations we need to make things easier

*   add a way to migrate translations between ids in the translation console:
    b/123764883
*   change soy to output some hint to the jscompiler that we want to migrate IDs
*   change jcompiler to actually interpret this hint
*   change `soy_js` to output extra xmb files as a side effect of the build to
    make sure extraction won't break
*   wait some amount of time for all projects to extract and dump translations
    (according to TC there is no build horizon like concept that we could rely
    on..., but we can manually trigger extractions for projects we don't own so
    we could theoretically do this ourselves)
*   change the default of `goog_messages_are_external` to be always true

If we do all that then we still need to change how messages are extracted.
`af_soy_library` requires a custom rule `af_soy_genmsg` to do message
extraction, so when migrating a project we would also have to add
`af_soy_genmsg` rules. Luckily, these rules have been retrofitted to use a blaze
aspect, so you just need to point them at a `js_binary` rule. In theory we could
change the jscompiler to just extract these 'external' messages, but currently
the jscompiler always adds a synthetic meaning attribute which breaks message
merging.

If we somehow did all of that, then we could migrate `soy_js` rules to
`af_soy_library`.

Pros:

*   Makes features easier to add since there are fewer configurations. For
    example:
    *   would make it possible to generate es6 modules
    *   would make it possible for soy itself to add a file based import syntax
    *   improve message sharing, right now some projects (like gmail) are paying
        for every message to be translated twice due to this issue
*   unlock other improvements in the ecosystem (e.g. if b/123764883 is fixed,
    then jscompiler can remove the `tc_project_id` flag which would improve
    message sharing across projects)
*   Reduced support burden

Cons:

*   This is a truly monumental amount of work.
*   af_soy_library brings in extra deps for js only projects
*   even after all this users still need extra build rules (`af_soy_genmsg`)
    that are easy to forget.

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
