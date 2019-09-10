load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "base",
    srcs = glob([
        "**/*.java",
    ]),
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
    deps = [
        "@com_google_guava_guava",
    ],
)
