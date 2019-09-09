load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "restricted",
    srcs = glob([
        "**/*.java",
    ]),
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
)
