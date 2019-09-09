load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "basetree",
    srcs = glob([
        "**/*.java",
    ]),
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
    deps = [
        "//java/src/com/google/template/soy/base",
        "@com_google_guava_guava",
    ],
)
