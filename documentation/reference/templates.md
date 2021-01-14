# Templates


[TOC]

## template {#template}

Syntax (basic form):

```soy
{template <TEMPLATE_NAME>}
  ...
{/template}
```

With all optional attributes:

```soy
{template <TEMPLATE_NAME> visibility="<private/public>" kind="html" stricthtml="true" requirecsspath="./foo" requirecss="<NAMESPACE>.<CSS_ELEMENT>" cssbase="<NAMESPACE>.<CSS_BASE>"}
  ...
{/template}
```

**Note:** Earlier versions of Soy required template names to start with a dot
(`.`). This is no longer required and any existing dots are ignored by the Soy
compiler. In particular, `{template foo}` and `{template .foo}` both define the
same template, and `{call foo}` and `{call .foo}` both call the same template.
{@paragraph #leading-dot}

These are the `template` tag's attributes:
{@paragraph #leading-dot}

*   `visibility`: Optional. Default `public`. Set this to `private` if you want
    this to be a private template. A private template can be called by other
    templates in the same file. The various backends also take efforts to
    prevent direct calls to that template from the host language.

    *   In Java (SoySauce and Tofu), a runtime exception will be thrown if you
        try to render a private template.
    *   In Javascript, private templates will be annotated with `@private` which
        will be enforced by the JsCompiler.
    *   In Python, private templates will generate Python functions that start
        with `__` to discourage calls outside of the file.

*   `kind`: Optional. Default `html`. See the security guide for other
    [content kinds](../dev/security.md#content_kinds).

<!--#include file="common-attributes-include.md"-->

*   `stricthtml`: Optional. Default `true`. Configures
    [strict html support](html)


*   `whitespace`: Optional. Default `join`. Configures the whitespace joining
    algorithm to use. See the [Line Joining](textual-commands##line-joining)
    documentation for details on the default `join` algorithm. Set this to
    `preserve` if you want to preserve all whitespace characters that are found
    inside the current template. This behavior is similar to what can be
    observed when using the [{literal}](textual-commands#literal) command.

    For example, consider the following input file:

    ```soy
     {namespace example}

     {template .article whitespace="preserve"}
     {@param title: string}
     {@param body: string}
     <article>
       <h1>{$title}</h1>
       <p>
         {$body}
       </p>
     </article>
     {/template}
    ```

    The output in this case will look like the following (assuming "Hello Soy!"
    and "Article content." are being passed as values for `{title}` and `{body}`
    parameters respectively):

    ```html
     <article>
       <h1>Hello Soy!</h1>
       <p>
         Article content.
       </p>
     </article>
    ```

## @param {#param}

Syntax:

```soy
{template <TEMPLATE_NAME>}
  {@param <PARAM_NAME>: <PARAM_TYPE>} /** A required param. */
  {@param <PARAM_NAME>:= <PARAM_DEFAULT>} /** A default param with an inferred type. */
  {@param <PARAM_NAME>: <PARAM_TYPE> = <PARAM_DEFAULT>} /** A default param with an explicit type. */
  {@param? <PARAM_NAME>: <PARAM_TYPE>} /** An optional param. */
  ...
{/template}
```

`{@param}` declares a template parameter. Parameter declarations must come first
in the template, preceding all other template content.

See the [types reference](types) for instructions on how to declare types.

### Required params

The first syntax form above (`@param`) declares a required parameter that must
be provided whenever you call the template.

### Default params

Default parameters declare a parameter which does not always have to be
provided. When not provided, a default parameter's value is set to the given
default value.

A default parameter can either infer its type from the type of the default value
or use an explicitly declared type (this improves readability if the type of the
default value isn't obvious or if the template is part of a public API). An
explicit type can also be used to widen the type inferred from the default value
to allow a broader set of values. For example:

```soy
{@param name: string|null = null}
```

The inferred type is `null` but this also allows the parameter to accept
`string`s.

The default value can only be a compile-time constant expression. It cannot
reference any other parameters or call [non-pure](../dev/plugins#pure) Soy
functions.

NOTE: Default parameters don't support content kind types like `html`, `uri` or
`attributes`.

### Optional params

The `@param?` syntax declares an optional parameter, which does not always have
to be provided. Within a template, an optional parameter declared as `{@param?
<PARAM_NAME>: <PARAM_TYPE>}` has a type of `<PARAM_TYPE>|null`.

Default parameters are usually better than optional parameters, except when you
need a `null` value if the parameter is unset.

## @inject {#inject}

Syntax:

```soy
{template <TEMPLATE_NAME>}
  {@inject <PARAM_NAME>: <PARAM_TYPE>}
  ...
{/template}
```

`{@inject}` declares an [injected template parameter](../concepts/ij-data.md).
The syntax is identical to the [required param](#param) syntax with the
exception of the keyword.

See the [types reference](types) for instructions on how to declare types.

## Doc comments for params {#doc-comments}

Doc comments for parameters may appear before or after the parameter
declaration. If before, the comment must be on the preceding line; if after, the
comment must start on the same line as the parameter declaration.

Example:

```soy
{template .example}
  /** A leading doc comment. */
  {@param name: string}
  {@param? height: int} /** A trailing doc comment. */
  ...
{/template}
```

## Parameter type expressions {#param-type}

See the [type expression reference](types) for the types that can appear in a
parameter declaration.
