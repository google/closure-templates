# Comments

go/soy/reference/comments

This page describes how to comment your Soy file.

**NOTE**: No type of comment is ever rendered to Soy's output.

[TOC]

## Single line comments

`//` single line comments example:

```soy
{template .example}
  {@param exampleParam: example_source}

  // This is a single line Soy comment.
  <div>
    Example HTML content.
  </div>
{/template}
```

## Multiline comments

`/* ... */` multiline comments example:

```soy
{template .example}
  {@param exampleParam: example_source}

  /* This is a one star Soy comment. */
  <div>
    Example HTML content.
  </div>
  /*
   * This is a longer
   * multiline comment
   */
{/template}
```

## SoyDoc

`/** ... */` two star comments are special in that they're SoyDoc. SoyDoc on a
template or [parameter declaration](templates#doc-comments-for-params) is copied
to the generated code. SoyDoc is also necessary for [legacy SoyDoc parameter
declarations](deprecated#params-in-comments). Example:

```soy
/**
 * This is a SoyDoc comment, that will be copied to the generated code for this
 * template declaration.
 */
{template .example}
  /** This is a single line SoyDoc comment on a parameter declaration. */
  {@param exampleParam: example_source}

  <div>
    Example HTML content.
  </div>
{/template}
```
