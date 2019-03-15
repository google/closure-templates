# Templates


<!--#include file="commands-blurb-include.md"-->

This chapter describes the template commands.

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
{template <TEMPLATE_NAME> visibility="<private/public>" autoescape="strict" kind="html" stricthtml="true" requirecss="<NAMESPACE>.<CSS_ELEMENT>" cssbase="<NAMESPACE>.<CSS_BASE>"}
  ...
{/template}
```

The template name must be a dot followed by an identifier because it is a
partial name relative to the file's namespace. For example, if the file's
namespace is `ui.settings.login` and the template's partial name is
`.newPassword`, then the full template name is `ui.settings.login.newPassword`.

These are the `template` tag's attributes:

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

*   `autoescape`: Optional. Affects the encoding of `print` commands and is set
    according to these rules:

    *   If there is an `autoescape` value specified in the `template` tag, use
        that value.
    *   Else default to [strict autoescaping](../dev/security.md#strict).

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
  {@param <PARAM_NAME>: <PARAM_TYPE>}
  {@param? <PARAM_NAME>: <PARAM_TYPE>}
  ...
{/template}
```

`{@param}` declares a template parameter. Parameter declarations must come first
in the template, preceding all other template content.

The first syntax form above (`@param`) declares a required parameter that must
be provided whenever you call the template. The second form (`@param?`) declares
an optional parameter, which does not always have to be provided. Within a
template, an optional parameter declared as `{@param? <PARAM_NAME>:
<PARAM_TYPE>}` has a type of `<PARAM_TYPE>|null`.

See the [types reference](types) for instructions on how to declare types.

## @inject {#inject}

Syntax:

```soy
{template <TEMPLATE_NAME>}
  {@inject <PARAM_NAME>: <PARAM_TYPE>}
  ...
{/template}
```

`{@inject}` declares an [injected template parameter](../concepts/ij-data.md).
The syntax is identical to the [@param](#param) syntax with the exception of the
keyword.

See the [types reference](types) for instructions on how to declare types.

### Doc comments for params {#doc-comments}

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

### Parameter type expressions {#param-type}

See the [type expression reference](types) for the types that can appear in a
parameter declaration.
