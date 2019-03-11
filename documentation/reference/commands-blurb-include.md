Commands are instructions you give the compiler to create templates and add
custom logic to templates. Commands are anything inside `Closure Templates` tags,
delimited by braces (`{}`). The syntax for commands depends on the specific
command.

Within the body of a template, any text within `Closure Templates` tags is parsed
and understood by the compiler, while anything outside of `Closure Templates` tags
is raw text that is output which can be modified by `Closure Templates`'s [textual
commands](textual-commands.md).
