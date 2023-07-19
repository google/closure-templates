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

RULES_JVM_EXTERNAL_TAG = "5.3"
RULES_JVM_EXTERNAL_SHA ="d31e369b854322ca5098ea12c69d7175ded971435e55c18dd9dd5f29cc5249ac"

http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    sha256 = RULES_JVM_EXTERNAL_SHA,
    url = "https://github.com/bazelbuild/rules_jvm_external/releases/download/%s/rules_jvm_external-%s.tar.gz" % (RULES_JVM_EXTERNAL_TAG, RULES_JVM_EXTERNAL_TAG)
)

load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")

rules_jvm_external_deps()

load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")

rules_jvm_external_setup()

load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@rules_jvm_external//:specs.bzl", "maven")

SERVER_URLS = [
    "https://mirror.bazel.build/repo1.maven.org/maven2",
    "https://repo1.maven.org/maven2",
]

maven_install(
    artifacts = [
        "args4j:args4j:2.33",
        "com.google.code.findbugs:jsr305:3.0.2",
        "com.google.code.gson:gson:2.10.1",
        "com.google.common.html.types:types:1.0.8",
        "com.google.errorprone:error_prone_annotations:2.20.0",
        "com.google.escapevelocity:escapevelocity:1.1",
        "com.google.flogger:flogger:0.7.4",
        "com.google.flogger:flogger-system-backend:0.7.4",
        "com.google.flogger:google-extensions:0.7.4",
        "com.google.guava:guava:32.1.1-jre",
        maven.artifact(
            "com.google.guava",
            "guava-testlib",
            "31.1-jre",
            testonly = True
        ),
        "com.google.inject:guice:7.0.0",
        maven.artifact(
            "com.google.truth",
            "truth",
            "1.1.5",
            testonly = True,
        ),
        maven.artifact(
            "com.google.truth.extensions",
            "truth-java8-extension",
            "1.1.5",
            testonly = True
        ),
        "com.ibm.icu:icu4j:73.2",
        "javax.inject:javax.inject:1",
        maven.artifact(
            "junit",
            "junit",
            "4.13.2",
            testonly = True,
        ),
        "net.java.dev.javacc:javacc:6.1.2",
        "org.apache.ant:ant:1.10.13",
        "org.json:json:20230618",
        "org.ow2.asm:asm:9.5",
        "org.ow2.asm:asm-commons:9.5",
        "org.ow2.asm:asm-tree:9.5",
        "org.ow2.asm:asm-util:9.5",
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
    artifact = "com.google.auto.value:auto-value:1.10.2",
    artifact_sha256 = "276ba82816fab66ff057e94a599c4bbdd3ab70700602b540ea17ecfe82a2986a",
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
    artifact = "com.google.auto.value:auto-value-annotations:1.10.2",
    artifact_sha256 = "3f3b7edfaf7fbbd88642f7bd5b09487b8dcf2b9e5f3a19f1eb7b3e53f20f14ba",
    default_visibility = [
        "@com_google_auto_value_auto_value//:__pkg__",
        "@maven//:__pkg__",
    ],
    neverlink = True,
    server_urls = SERVER_URLS,
)

http_archive(
    name = "rules_java",
    urls = [
        "https://github.com/bazelbuild/rules_java/releases/download/6.2.2/rules_java-6.2.2.tar.gz",
    ],
    sha256 = "847527aa7f74712e0a63af2670ba3ddc04e8ea3d8930a7947c17aebfb29d5294",
)
load("@rules_java//java:repositories.bzl", "rules_java_dependencies", "rules_java_toolchains")
rules_java_dependencies()
rules_java_toolchains()

http_archive(
    name = "rules_proto",
    sha256 = "dc3fb206a2cb3441b485eb1e423165b231235a1ea9b031b4433cf7bc1fa460dd",
    strip_prefix = "rules_proto-5.3.0-21.7",
    urls = [
        "https://github.com/bazelbuild/rules_proto/archive/refs/tags/5.3.0-21.7.tar.gz",
    ],
)
load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")
rules_proto_dependencies()
rules_proto_toolchains()
