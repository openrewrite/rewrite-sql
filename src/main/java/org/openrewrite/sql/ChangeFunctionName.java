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

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.sf.jsqlparser.expression.Function;
import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.sql.internal.ChangeTrackingExpressionDeParser;
import org.openrewrite.sql.table.DatabaseQueries;
import org.openrewrite.sql.trait.SqlQuery;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeFunctionName extends Recipe {
    transient DatabaseQueries databaseQueries = new DatabaseQueries(this);

    @Option(displayName = "Old function name",
            description = "The name of the function to find, case insensitive.")
    String oldFunctionName;

    @Option(displayName = "New function name",
            description = "The new name to use. This will match the casing of " +
                          "the original method when a replacement is made.")
    String newFunctionName;

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
                        .map(q -> q.mapSql(new ChangeTrackingExpressionDeParser() {
                            @Override
                            public void visit(Function function) {
                                if (StringUtils.matchesGlob(function.getName(), oldFunctionName)) {
                                    databaseQueries.insertRow(ctx, new DatabaseQueries.Row(
                                            getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString(),
                                            q.getSql()
                                    ));
                                    trackChange(() -> {
                                        function.setName(newFunctionName);
                                        super.visit(function);
                                    });
                                } else {
                                    super.visit(function);
                                }
                            }
                        }))
                        .orSuccess(super.preVisit(tree, ctx));
            }
        };
    }
}
