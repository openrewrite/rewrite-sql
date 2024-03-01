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
package org.openrewrite.sql;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.sql.trait.SqlQuery;

public class ChangeFunctionName extends Recipe {

    @Override
    public String getDisplayName() {
        return "Change a SQL function name";
    }

    @Override
    public String getDescription() {
        return "When migrating between dialects, often one name can be substituted for another. " +
               "For example, Oracle's NVL function can be replaced with Postgres COALESCE.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree preVisit(Tree tree, ExecutionContext ctx) {
                return SqlQuery.viewOf(getCursor())
                        .map(q -> {
                            return tree;
                        })
                        .orSuccess(super.preVisit(tree, ctx));
            }
        };
    }
}
