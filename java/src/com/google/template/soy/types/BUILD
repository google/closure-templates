load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "types",
    srcs = glob([
        "**/*.java",
    ]),
    visibility = [
        "//java/src/com/google/template/soy:__subpackages__",
    ],
    deps = [
        "//java/src/com/google/template/soy/base",
        "//java/src/com/google/template/soy/error",
        "//java/src/com/google/template/soy/internal/proto",
        "//src/main/protobuf:template_metadata_java_proto",
        "@com_google_auto_value",
        "@com_google_code_findbugs_jsr305",
        "@com_google_common_html_types",
        "@com_google_errorprone_error_prone_annotations",
        "@com_google_guava_guava",
        "@com_google_protobuf//:protobuf_java",
        "@javax_annotation_jsr250_api",
    ],
)
