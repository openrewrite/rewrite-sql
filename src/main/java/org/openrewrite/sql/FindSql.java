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

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.sql.table.DatabaseColumnsUsed;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

public class FindSql extends Recipe {
    transient DatabaseColumnsUsed used = new DatabaseColumnsUsed(this);

    @Override
    public String getDisplayName() {
        return "Find SQL in code and resource files";
    }

    @Override
    public String getDescription() {
        return "Find SQL in code (e.g. in string literals) and in resources like those ending with `.sql`.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return Applicability.or(
                Applicability.not(new PlainTextVisitor<ExecutionContext>() {
                    @Override
                    public PlainText visitText(PlainText text, ExecutionContext executionContext) {
                        return SearchResult.found(text);
                    }
                }),
                new HasSourcePath<>("**/*.sql")
        );
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            final SqlDetector detector = new SqlDetector();

            @Override
            public Tree visitSourceFile(SourceFile sourceFile, ExecutionContext ctx) {
                Tree t = sourceFile;

                t = new PlainTextVisitor<ExecutionContext>() {
                    @Override
                    public PlainText visitText(PlainText text, ExecutionContext ctx) {
                        return find(ctx, getCursor(), text.getText());
                    }
                }.visit(t, ctx);

                t = new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Literal visitLiteral(J.Literal literal, ExecutionContext executionContext) {
                        return literal.getValue() instanceof String ?
                                find(ctx, getCursor(), (String) literal.getValue()) :
                                literal;
                    }
                }.visit(t, ctx);

                t = new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext executionContext) {
                        return find(ctx, getCursor(), scalar.getValue());
                    }
                }.visit(t, ctx);

                return t;
            }

            private <T extends Tree> T find(ExecutionContext ctx, Cursor cursor, String text) {
                //noinspection unchecked
                return (T) cursor
                        .getPathAsStream(v -> v instanceof SourceFile)
                        .findFirst()
                        .map(SourceFile.class::cast)
                        .map(sourceFile -> {
                            Tree t = cursor.getValue();
                            for (DatabaseColumnsUsed.Row row : detector.rows(sourceFile, text)) {
                                used.insertRow(ctx, row);
                                t = SearchResult.found(t);
                            }
                            return t;
                        })
                        .orElseGet(cursor::getValue);
            }
        };
    }
}
