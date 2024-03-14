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
package org.openrewrite.sql.search;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.test.SourceSpecs.text;

class FindFunctionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindFunction("nvl"));
    }

    @DocumentExample
    @Test
    void findFunction() {
        rewriteRun(
          text(
            """
              select nvl(a, b) from table where id = 1
              """,
            """
              ~~>select nvl(a, b) from table where id = 1
              """,
            spec -> spec.path("q.sql")
          )
        );
    }

    @Test
    void noMatch() {
        rewriteRun(
          text(
            """
              select coalesce(a, b) from table where id = 1
              """,
            spec -> spec.path("q.sql")
          )
        );
    }
}
