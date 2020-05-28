*   `requirecss`: takes a list of css namespaces (dotted identifiers). This is
    used to add `@requirecss` annotations in the generated javascript. Also, if
    there is no `cssbase` attribute, the first `requirecss` namespace can be
    used for autoprefixing in [css function](functions.md#css) calls.

*   `cssbase`: takes a single css namespace (dotted identifier). This is used
    for autoprefixing in [css function](functions.md#css) calls.

*   `requirecsspath` takes a list of global or relative paths for css files
    (this can be either GSS or SASS). This does NOT have any autoprefix
    behavior. Use of cssbase is required to autoprefix.
