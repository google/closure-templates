"""Useful macros for the soy codebase internal usage."""

load("@rules_java//java:defs.bzl", "java_test")

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

def java_individual_tests(deps, jvm_flags = []):
    """Creates java_test targets for each *Test.java file in the calling package.

    Args:
      deps: a java_library containing the *Test.java files.
      jvm_flags: JVM flags to pass when running the test.
    """


    test_files = native.glob(["*Test.java"])

    test_package = native.package_name()[len("java/tests/"):].replace("/", ".")

    for test_file in test_files:
        test_name = test_file[:-len(".java")]
        java_test(
            name = test_name,
            test_class = "%s.%s" % (test_package, test_name),
            runtime_deps = deps,
            jvm_flags = jvm_flags,
        )
