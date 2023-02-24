plugins {
    id("org.openrewrite.build.recipe-library") version "1.8.1"
}

group = "org.openrewrite.recipe"
description = "Add SQL query processing capability to OpenRewrite."

dependencies {
    implementation("com.github.jsqlparser:jsqlparser:latest.release")
    implementation("org.openrewrite:rewrite-core:latest.integration")
    implementation("org.openrewrite:rewrite-java:latest.integration")
    implementation("org.openrewrite:rewrite-yaml:latest.integration")

    testImplementation("org.openrewrite:rewrite-test:latest.integration")
    testRuntimeOnly("org.openrewrite:rewrite-java-17:latest.integration")
}
