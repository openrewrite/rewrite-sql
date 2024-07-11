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
import org.openrewrite.marker.SearchResult;
import org.openrewrite.sql.table.DatabaseFunctions;
import org.openrewrite.sql.table.DatabaseQueries;

import java.util.concurrent.atomic.AtomicReference;

import static org.openrewrite.sql.trait.Traits.sql;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindFunction extends Recipe {
    transient DatabaseQueries databaseQueries = new DatabaseQueries(this);
    transient DatabaseFunctions databaseFunctions = new DatabaseFunctions(this);

    @Option(displayName = "Function name",
            description = "The name of the function to find, case insensitive.",
            example = "nvl")
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
        return sql().asVisitor((sql, ctx) -> {
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            sql.visitSql(new ExpressionDeParser() {
                @Override
                public void visit(Function function) {
                    if (StringUtils.matchesGlob(function.getName(), functionName)) {
                        databaseQueries.insertRow(ctx, new DatabaseQueries.Row(
                                sql.getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString(),
                                sql.getString()
                        ));
                        databaseFunctions.insertRow(ctx, new DatabaseFunctions.Row(
                                sql.getCursor().firstEnclosingOrThrow(SourceFile.class).getSourcePath().toString(),
                                function.getName().toLowerCase(),
                                sql.getString()
                        ));
                        found.set(true);
                    }
                    super.visit(function);
                }
            });
            return found.get() ? SearchResult.found(sql.getTree()) : sql.getTree();
        });
    }
}
