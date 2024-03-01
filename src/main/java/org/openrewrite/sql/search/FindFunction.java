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

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.sql.table.DatabaseFunctions;
import org.openrewrite.sql.table.DatabaseQueries;
import org.openrewrite.sql.trait.SqlQuery;

import java.util.concurrent.atomic.AtomicReference;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindFunction extends Recipe {
    transient DatabaseQueries databaseQueries = new DatabaseQueries(this);
    transient DatabaseFunctions databaseFunctions = new DatabaseFunctions(this);

    @Option(displayName = "Function name",
            description = "The name of the function to find, case insensitive.")
    String functionName;

    @Override
    public String getDisplayName() {
        return "Find SQL function";
    }

    @Override
    public String getDescription() {
        return "Find SQL functions by name.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree preVisit(Tree tree, ExecutionContext ctx) {
                return SqlQuery.viewOf(getCursor())
                        .map(q -> {
                            AtomicReference<Boolean> found = new AtomicReference<>(false);
                            q.mapSql(new ExpressionDeParser() {
                                @Override
                                public void visit(Function function) {
                                    if (StringUtils.matchesGlob(function.getName(), functionName)) {
                                        databaseQueries.insertRow(ctx, new DatabaseQueries.Row(
                                                getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString(),
                                                q.getSql()
                                        ));
                                        databaseFunctions.insertRow(ctx, new DatabaseFunctions.Row(
                                                getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString(),
                                                function.getName().toLowerCase(),
                                                q.getSql()
                                        ));
                                        found.set(true);
                                    }
                                    super.visit(function);
                                }
                            });
                            return found.get() ? SearchResult.found(tree) : tree;
                        })
                        .orSuccess(super.preVisit(tree, ctx));
            }
        };
    }
}
