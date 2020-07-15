"""Useful macros for the soy codebase internal usage."""

def gen_javacc(name, srcs, outs, visibility = None, compatible_with = None):
    native.genrule(
        name = name,
        srcs = srcs,
        outs = outs,
        cmd = "$(location %s) -OUTPUT_DIRECTORY=$(@D) $(SRCS)" % (
            "//builddefs:javacc"
        ),
        compatible_with = compatible_with,
        visibility = visibility,
        tools = [
            "//builddefs:javacc",
        ],
    )
