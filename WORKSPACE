##
# Copyright 2013 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
##

workspace(name = "com_google_closure_templates")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

RULES_JVM_EXTERNAL_TAG = "3.3"

RULES_JVM_EXTERNAL_SHA = "d85951a92c0908c80bd8551002d66cb23c3434409c814179c0ff026b53544dab"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@rules_jvm_external//:specs.bzl", "maven")

SERVER_URLS = [
    "https://mirror.bazel.build/repo1.maven.org/maven2",
    "https://repo1.maven.org/maven2",
]

maven_install(
    artifacts = [
        "args4j:args4j:2.0.23",
        "com.google.code.findbugs:jsr305:3.0.2",
        "com.google.code.gson:gson:2.7",
        "com.google.common.html.types:types:1.0.7",
        "com.google.errorprone:error_prone_annotations:2.4.0",
        "com.google.escapevelocity:escapevelocity:0.9.1",
        "com.google.guava:guava:29.0-jre",
        maven.artifact(
            "com.google.guava",
            "guava-testlib",
            "29.0-jre",
            testonly = True
        ),
        "com.google.inject.extensions:guice-multibindings:4.1.0",
        "com.google.inject:guice:4.1.0",
        maven.artifact(
            "com.google.truth",
            "truth",
            "1.0.1",
            testonly = True,
        ),
        maven.artifact(
            "com.google.truth.extensions",
            "truth-java8-extension",
            "1.0.1",
            testonly = True
        ),
        "com.ibm.icu:icu4j:57.1",
        "javax.inject:javax.inject:1",
        maven.artifact(
            "junit",
            "junit",
            "4.13",
            testonly = True,
        ),
        "net.java.dev.javacc:javacc:6.1.2",
        "org.apache.ant:ant:1.10.7",
        "org.json:json:20160212",
        maven.artifact(
            "org.mozilla",
            "rhino",
            "1.7.11",
            testonly = True,
        ),
        "org.ow2.asm:asm:7.0",
        "org.ow2.asm:asm-commons:7.0",
        "org.ow2.asm:asm-tree:7.0",
        "org.ow2.asm:asm-util:7.0",
    ],
    maven_install_json = "//:maven_install.json",
    override_targets = {
        "com.google.auto.value:auto-value": "@com_google_auto_value_auto_value",
        "com.google.auto.value:auto-value-annotations": "@com_google_auto_value_auto_value_annotations",
        "com.google.protobuf:protobuf-java": "@com_google_protobuf//:protobuf_java",
    },
    repositories = SERVER_URLS,
    strict_visibility = True,
)

load("@maven//:defs.bzl", "pinned_maven_install")

pinned_maven_install()

jvm_maven_import_external(
    name = "com_google_auto_value_auto_value",
    artifact = "com.google.auto.value:auto-value:1.7.4",
    artifact_sha256 = "8320edb037b62d45bc05ae4e1e21941255ef489e950519ef14d636d66870da64",
    extra_build_file_content = """
java_plugin(
    name = "AutoAnnotationProcessor",
    output_licenses = ["unencumbered"],
    processor_class = "com.google.auto.value.processor.AutoAnnotationProcessor",
    tags = ["annotation=com.google.auto.value.AutoAnnotation;genclass=${package}.AutoAnnotation_${outerclasses}${classname}_${methodname}"],
    deps = [":processor"],
)

java_plugin(
    name = "AutoOneOfProcessor",
    output_licenses = ["unencumbered"],
    processor_class = "com.google.auto.value.processor.AutoOneOfProcessor",
    tags = ["annotation=com.google.auto.value.AutoValue;genclass=${package}.AutoOneOf_${outerclasses}${classname}"],
    deps = [":processor"],
)

java_plugin(
    name = "AutoValueProcessor",
    output_licenses = ["unencumbered"],
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    tags = ["annotation=com.google.auto.value.AutoValue;genclass=${package}.AutoValue_${outerclasses}${classname}"],
    deps = [":processor"],
)

java_plugin(
    name = "MemoizedValidator",
    output_licenses = ["unencumbered"],
    processor_class = "com.google.auto.value.extension.memoized.processor.MemoizedValidator",
    deps = [":processor"],
)

java_library(
    name = "com_google_auto_value_auto_value",
    exported_plugins = [
        ":AutoAnnotationProcessor",
        ":AutoOneOfProcessor",
        ":AutoValueProcessor",
        ":MemoizedValidator",
    ],
    exports = ["@com_google_auto_value_auto_value_annotations"],
)
""",
    generated_rule_name = "processor",
    server_urls = SERVER_URLS,
    exports = ["@com_google_auto_value_auto_value_annotations"],
)

# This isn't part of the maven_install above so we can set a custom visibility.
jvm_maven_import_external(
    name = "com_google_auto_value_auto_value_annotations",
    artifact = "com.google.auto.value:auto-value-annotations:1.7.4",
    artifact_sha256 = "fedd59b0b4986c342f6ab2d182f2a4ee9fceb2c7e2d5bdc4dc764c92394a23d3",
    default_visibility = [
        "@com_google_auto_value_auto_value//:__pkg__",
        "@maven//:__pkg__",
    ],
    neverlink = True,
    server_urls = SERVER_URLS,
)

# Apache 2.0
http_archive(
    name = "rules_java",
    sha256 = "52423cb07384572ab60ef1132b0c7ded3a25c421036176c0273873ec82f5d2b2",
    urls = [
        "https://github.com/bazelbuild/rules_java/releases/download/0.1.0/rules_java-0.1.0.tar.gz",
    ],
)

load("@rules_java//java:repositories.bzl", "rules_java_dependencies", "rules_java_toolchains")

rules_java_dependencies()

rules_java_toolchains()

# Apache 2.0
http_archive(
    name = "rules_proto",
    sha256 = "57001a3b33ec690a175cdf0698243431ef27233017b9bed23f96d44b9c98242f",
    strip_prefix = "rules_proto-9cd4f8f1ede19d81c6d48910429fe96776e567b1",
    urls = [
        "https://github.com/bazelbuild/rules_proto/archive/9cd4f8f1ede19d81c6d48910429fe96776e567b1.tar.gz",
    ],
)

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")

rules_proto_dependencies()

rules_proto_toolchains()
