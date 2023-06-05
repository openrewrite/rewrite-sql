/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.sql.table.DatabaseColumnsUsed;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.yaml.Assertions.yaml;

@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
class FindSqlTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindSql());
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "This will be SELECTed by the heuristic but not parse as SQL",
      "The heuristic won't match this at all"
    })
    void notSql(String maybeSql) {
        rewriteRun(
          text(maybeSql)
        );
    }

    @Test
    void select() {
        rewriteRun(
          spec -> spec.dataTable(DatabaseColumnsUsed.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              DatabaseColumnsUsed.Row row = rows.get(0);
              assertThat(row.getOperation()).isEqualTo(DatabaseColumnsUsed.Operation.SELECT);
              assertThat(row.getTable()).isEqualTo("recipe_run_repository");
              assertThat(row.getColumn()).isEqualTo("repository_origin");
          }),
          text(
            // language=sql
            """
              SELECT distinct(repository_origin)
              FROM recipe_run_repository
              WHERE run_id = :recipeRunId
              AND :dataTable = ANY (data_tables)
              """,
            spec -> spec
              .path("select.sql")
              .after(a -> {
                  assertThat(a).startsWith("~~>");
                  return a;
              })
          )
        );
    }

    @Test
    void selectInJava() {
        rewriteRun(
          spec -> spec.dataTable(DatabaseColumnsUsed.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              DatabaseColumnsUsed.Row row = rows.get(0);
              assertThat(row.getOperation()).isEqualTo(DatabaseColumnsUsed.Operation.SELECT);
              assertThat(row.getTable()).isEqualTo("recipe_run_repository");
              assertThat(row.getColumn()).isEqualTo("repository_origin");
          }).cycles(1).expectedCyclesThatMakeChanges(1),
          java(
            """
              class Test {
                String aSelect = \"""
                  SELECT distinct(repository_origin)
                  FROM recipe_run_repository
                  WHERE run_id = :recipeRunId
                  AND :dataTable = ANY (data_tables)
                \""";
              }
              """,
            spec -> spec.after(a -> {
                assertThat(a).contains("/*~~>*/");
                return a;
            })
          )
        );
    }

    @Test
    void selectInYaml() {
        rewriteRun(
          spec -> spec.dataTable(DatabaseColumnsUsed.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              DatabaseColumnsUsed.Row row = rows.get(0);
              assertThat(row.getOperation()).isEqualTo(DatabaseColumnsUsed.Operation.SELECT);
              assertThat(row.getTable()).isEqualTo("recipe_run_repository");
              assertThat(row.getColumn()).isEqualTo("repository_origin");
          }).cycles(1).expectedCyclesThatMakeChanges(1),
          yaml(
            """
              query: >
                  SELECT distinct(repository_origin)
                  FROM recipe_run_repository
                  WHERE run_id = :recipeRunId
                  AND :dataTable = ANY (data_tables)
              """,
            spec -> spec.after(a -> {
                assertThat(a).contains("~~>");
                return a;
            })
          )
        );
    }

    @Test
    void update() {
        rewriteRun(
          spec -> spec.dataTable(DatabaseColumnsUsed.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              DatabaseColumnsUsed.Row row = rows.get(0);
              assertThat(row.getOperation()).isEqualTo(DatabaseColumnsUsed.Operation.UPDATE);
              assertThat(row.getTable()).isEqualTo("commit");
              assertThat(row.getColumn()).isEqualTo("state");
          }),
          text(
            // language=sql
            """
              UPDATE commit
              SET state = 'CANCELED'
              WHERE state IN ('QUEUED', 'ORPHANED', 'PROCESSING')
              AND commit_id = :commitId
              """,
            spec -> spec
              .path("update.sql")
              .after(a -> {
                  assertThat(a).startsWith("~~>");
                  return a;
              })
          )
        );
    }

    @Test
    void delete() {
        rewriteRun(
          spec -> spec.dataTable(DatabaseColumnsUsed.Row.class, rows -> {
              assertThat(rows).hasSize(1);
              DatabaseColumnsUsed.Row row = rows.get(0);
              assertThat(row.getOperation()).isEqualTo(DatabaseColumnsUsed.Operation.DELETE);
              assertThat(row.getTable()).isEqualTo("access_token");
              assertThat(row.getColumn()).isNull();
          }),
          text(
            // language=sql
            """
              DELETE FROM access_token
              WHERE email = :email
              """,
            spec -> spec
              .path("delete.sql")
              .after(a -> {
                  assertThat(a).startsWith("~~>");
                  return a;
              })
          )
        );
    }
}
