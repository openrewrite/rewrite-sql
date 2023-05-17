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

import static org.openrewrite.Tree.randomId;

import javax.annotation.Nullable;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.HasJavaVersion;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import com.github.vertical_blank.sqlformatter.SqlFormatter;
import com.github.vertical_blank.sqlformatter.languages.Dialect;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class FormatSql extends Recipe {
    @Option(displayName = "SQL dialect to be used to format SQL snippets.", description = "", example = "PostgreSql", required = false)
    @Nullable
    String sqlDialect;

    public FormatSql() {
        sqlDialect = Dialect.StandardSql.name();
    }

    public FormatSql(String sqlDialect) {
        this.sqlDialect = sqlDialect;
    }

    @Override
    public String getDisplayName() {
        return "Format SQL in String Text Blocks";
    }

    @Override
    public String getDescription() {
        return "...";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new HasJavaVersion("17", true).getVisitor();
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                final SqlDetector detector = new SqlDetector();

                if (isTextBlock(binary)) {
                    J.Literal literal = (J.Literal) ((Expression) binary);
                    String value = literal.getValue().toString();

                    if (detector.isSql(value)) {
                        String formatted = SqlFormatter.of(Dialect.valueOf(sqlDialect)).format(value);
                        return new J.Literal(randomId(), binary.getPrefix(), Markers.EMPTY, value,
                                String.format("\"\"\"%s\"\"\"", formatted), null, JavaType.Primitive.String);
                    }
                }

                return binary;
            }

            private static boolean isTextBlock(Expression expr) {
                if (expr instanceof J.Literal) {
                    J.Literal l = (J.Literal) expr;

                    return TypeUtils.isString(l.getType()) &&
                            l.getValueSource() != null &&
                            l.getValueSource().startsWith("\"\"\"");
                }
                return false;
            }
        };
    }
}
