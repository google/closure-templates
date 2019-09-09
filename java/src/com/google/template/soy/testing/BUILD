load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "testing",
    testonly = True,
    srcs = glob([
        "**/*.java",
    ]),
    visibility = [
        "//java/tests/com/google/template/soy:__subpackages__",
    ],
)
