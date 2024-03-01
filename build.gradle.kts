plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Add SQL query processing capability to OpenRewrite."

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))

    // 4.8 broke `FormatSqlTest > complex_query_postgresql`
    implementation("com.github.jsqlparser:jsqlparser:4.7")

    implementation("com.github.vertical-blank:sql-formatter:2.0.+")

    implementation("org.openrewrite:rewrite-core")
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-xml")
    implementation("org.openrewrite:rewrite-yaml")

    implementation("org.openrewrite.meta:rewrite-analysis:${rewriteVersion}")

    testImplementation("org.openrewrite:rewrite-test")
    testRuntimeOnly("org.openrewrite:rewrite-java-17")
}
