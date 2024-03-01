/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.sql.trait;

import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;
import static org.openrewrite.test.SourceSpecs.text;

public class SqlQueryTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new TreeVisitor<>() {
            @Override
            public Tree preVisit(Tree tree, ExecutionContext executionContext) {
                return SqlQuery.viewOf(getCursor())
                  .map((SqlQuery q) -> {
                      q.mapSql(new ExpressionDeParser());
//                      assertThat(q.mapSql(new ExpressionDeParser()))
//                        .isEqualTo("select * from table where id = 1");
                      return SearchResult.found(tree);
                  })
                  .orSuccess(tree);
            }
        })).cycles(1).expectedCyclesThatMakeChanges(1);
    }

    @Test
    void probablyButNotActuallySql() {
        rewriteRun(
          text(
            """
              delete
              """,
            """
              ~~>delete
              """
          )
        );
    }

    @Test
    void javaLiteral() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String sql = "select * from table where id = 1";
              }
              """,
            """
              class Test {
                  String sql = /*~~>*/"select * from table where id = 1";
              }
              """
          )
        );
    }

    @Test
    void javaBinaryExpression() {
        rewriteRun(
          //language=java
          java(
            """
              class Test {
                  String sql = "select * from table " +
                    "where id = 1";
              }
              """,
            """
              class Test {
                  String sql = /*~~>*/"select * from table " +
                    "where id = 1";
              }
              """
          )
        );
    }

    @Test
    void sqlFile() {
        rewriteRun(
          text(
            """
              select * from table where id = 1
              """,
            """
              ~~>select * from table where id = 1
              """,
            spec -> spec.path("q.sql")
          )
        );
    }
}
