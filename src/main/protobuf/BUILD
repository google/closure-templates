load("@rules_java//java:defs.bzl", "java_proto_library")
load("@rules_proto//proto:defs.bzl", "proto_library")

proto_library(
    name = "conformance_proto",
    srcs = [
        "conformance.proto",
    ],
)

java_proto_library(
    name = "conformance_java_proto",
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
    deps = [
        ":conformance_proto",
    ],
)

proto_library(
    name = "css_metadata_proto",
    srcs = [
        "css_metadata.proto",
    ],
)

java_proto_library(
    name = "css_metadata_java_proto",
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
    deps = [
        ":css_metadata_proto",
    ],
)

proto_library(
    name = "logging_config_proto",
    srcs = [
        "logging_config.proto",
    ],
)

java_proto_library(
    name = "logging_config_java_proto",
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
    deps = [
        ":logging_config_proto",
    ],
)

proto_library(
    name = "template_metadata_proto",
    srcs = [
        "template_metadata.proto",
    ],
)

java_proto_library(
    name = "template_metadata_java_proto",
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
    deps = [
        ":template_metadata_proto",
    ],
)
