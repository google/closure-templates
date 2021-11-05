## The Soy File Parser

This package and `SoyFileParser.jj` in particular contain the main entry point
for turning Soy files into abstract syntax trees (or `SoyFileNode` objects, more
specifically).

The best resources for javacc are:

1. [The JavaCC FAQ](http://www.engr.mun.ca/~theo/JavaCC-FAQ/javacc-faq-moz.htm)
1. [The main JavaCC site](https://javacc.org/)

### Complexities

Soy has a fairly complex grammar, this is due to a number of reasons.

1.  Users are allowed to write arbitrary textual content outside of soy
    commands.
1.  We implement html style 'line joining' of textual content.
1.  C++ style end of line comments (e.g. `// ...`), are difficult to parse
    since similar constructs are very common in uri schemes (e.g.
    `http://...`). Within templates, we only interpret `// ...` as a comment if
    it is preceded by whitespace.
1.  Whitespace is only important after the header.

### LOOKAHEAD

`LOOKAHEAD` is a powerful JavaCC feature that allows you to make dynamic choices
in the parser by looking ahead into the token stream. Additionally you can try
matching full BNF expressions against the stream in order to see if they match.

NOTE: there are a lot of `LOOKAHEAD(1)` calls in the parser. These are no-op
LOOKAHEADs that disable ambiguity warnings. JavaCC will issue warnings if there
is more than one way a given set of tokens could be matched. The default
behavior is greedy and a `LOOKAHEAD(1)` just means that greedy matching is what
we want.

### Lexical States

Lexical States are a mechanism for modifying the behavior of the tokenizer. This
is useful if a given sequence of characters has different meanings in different
contexts. In the soy compiler we have a large number of states to handle this:

#### DEFAULT

Used for parsing `{namespace`, `{alias`, and `{delpackage` commands.

#### IN_MULTILINE_COMMENT

Used for parsing multiline non-SoyDoc comments (e.g. `/*...*/`).

#### IN_SOYDOC

Used for parsing multiline SoyDoc comments (e.g. `/**...*/`). The contents are
collected into a `SPECIAL_TOKEN` so that they can be optionally consumed by the parser.

#### IN_CMD_TAG

Used for parsing the contents of soy commands (e.g. `{template ...}`). This is
used for soy tags that need to parse tokens that are keywords in `EXPR`
(e.g. `and`, `null`, `false`, etc.) as regular identifiers.

#### EXPR

Used for parsing soy expressions (e.g. `$foo['bar']`) as well as type
expressions.

#### IN_STRING

Used for parsing soy strings. May not contain a newline.

#### IN_ATTRIBUTE_VALUE

Much like `IN_MULTILINE_COMMENT`, this is used for parsing attribute values in
command tags. e.g. `{template foo kind="js"}`. This allows us to handle
arbitrary characters as string contents, much like how html attributes work (for
example, newlines are allowed).

#### TEMPLATE_DEFAULT

This is the main state for parsing template contents. The reason we don't
just use `DEFAULT` is due to how end-of-line comments are handled.

The problem is that in Soy files people often write things like `<a
href="//foo.com/resource">` but Soy allows `//` as a comment syntax. To handle
this we require that for `//` to be interpreted as a comment, it must be
preceded by whitespace.

#### IN_LITERAL_BLOCK

This is used for collecting all content with a `{literal}....{/literal}`
sequence as raw text.

### File Structure

-   Delpackage declaration (optional)
-   Namespace declaration
-   Alias declarations (optional)
-   Template declarations
    -   Template header
    -   `@param` and `@inject` declarations
    -   Template body

### Type Declarations

Types are declared in template params. All type tokens are parsed in the
TYPE_EXPR lexical state.
