package(
    default_testonly = 1,
    default_visibility = ["//template/soy:soy_internal"],
)

licenses(["notice"])  # Apache 2.0

java_library(
    name = "tests",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/common/base",
        "//java/com/google/common/testing",
        "//third_party/java/asm",
        "//third_party/java/asm:asm-commons",
        "//third_party/java/asm:asm-util",
        "//third_party/java/guice",
        "//third_party/java/jsr305_annotations",
        "//third_party/java/junit",
        "//third_party/java/truth",
        "//third_party/java_src/soy/java/com/google/template/soy/data",
        "//third_party/java_src/soy/java/com/google/template/soy/jbcsrc",
        "//third_party/java_src/soy/java/com/google/template/soy/sharedpasses:render",
    ],
)

subinclude("//java/com/google/apps/framework/testing/junit:java_individual_tests")

java_individual_tests(
    test_files = glob(["*Test.java"]),
    deps = [":tests"],
)

java_binary(
    name = "ASMifier",
    args = [
        "com.google.template.soy.jbcsrc.ExampleClass",
    ],
    main_class = "org.objectweb.asm.util.ASMifier",
    runtime_deps = [
        ":tests",
        "//third_party/java/asm:asm-util",
    ],
)

# --------------------------------------------------------------------------------------------------
# Open source files.

filegroup(
    name = "opensource_filegroup",
    testonly = 0,
    srcs = glob(
        ["*"],
        exclude = ["ExampleClass.java"],
    ),
    visibility = ["//third_party/java_src/soy/opensource:__pkg__"],
)
