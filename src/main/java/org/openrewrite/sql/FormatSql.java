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

import java.util.Optional;

import javax.annotation.Nullable;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.style.IntelliJ;
import org.openrewrite.java.style.TabsAndIndentsStyle;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import com.github.vertical_blank.sqlformatter.SqlFormatter;
import com.github.vertical_blank.sqlformatter.languages.Dialect;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class FormatSql extends Recipe {
    @Option(displayName = "SQL dialect to be used to format SQL snippets.", description = "Check out https://github.com/vertical-blank/sql-formatter#dialect for supported dialects.", valid = {"sql", "mariadb", "mysql", "postgresql", "db2", "plsql", "n1ql", "redshift", "spark", "tsql"}, example = "postgresql", required = false)
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
        return "Checks whether a text block may contain SQL, and if so, formats the text accordingly.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesJavaVersion<>(15);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal lit, ExecutionContext ctx) {
                J.Literal literal = super.visitLiteral(lit, ctx);
                if (isTextBlock(literal)) {
                    String originalValue = (String) literal.getValue();
                    if (new SqlDetector().isSql(originalValue)) {
                        String formatted = SqlFormatter.of(sqlDialect).format(originalValue);
                        TabsAndIndentsStyle style = getCursor().firstEnclosingOrThrow(SourceFile.class)
                                .getStyle(TabsAndIndentsStyle.class);
                        String indented = indent(literal.getValueSource(), formatted, style);
                        if (!originalValue.equals(formatted)) {
                            return literal
                                    .withValue(formatted)
                                    .withValueSource(String.format("\"\"\"%s\"\"\"", indented));
                        }
                    }
                }

                return literal;
            }

            private boolean isTextBlock(J.Literal l) {
                return TypeUtils.isString(l.getType()) &&
                        l.getValueSource() != null &&
                        l.getValueSource().startsWith("\"\"\"");
            }
        };
    }

    private static String indent(String valueSource, String formatted, @Nullable TabsAndIndentsStyle style) {
        TabsAndIndentsStyle tabsAndIndentsStyle = Optional
                .ofNullable(style)
                .orElse(IntelliJ.tabsAndIndents());
        boolean useTab = tabsAndIndentsStyle.getUseTabCharacter();
        int tabSize = tabsAndIndentsStyle.getTabSize();

        String indentation = getIndents(valueSource, useTab, tabSize);

        // preserve trailing spaces
        String indented = formatted.replace(" \n", "\\s\n");
        // handle preceding indentation
        indented = indented.replace("\n", "\n" + indentation);

        // add first line
        indented = "\n" + indentation + indented;

        // add last line to ensure the closing delimiter is in a new line to manage
        // indentation & remove theformatted
        // need to escape ending quote in the content
        boolean isEndsWithNewLine = valueSource.endsWith("\n");
        if (!isEndsWithNewLine) {
            indented = indented + "\\\n" + indentation;
        }
        return indented;
    }

    private static String getIndents(String concatenation, boolean useTabCharacter, int tabSize) {
        int[] tabAndSpaceCounts = shortestPrefixAfterNewline(concatenation, tabSize);
        int tabCount = tabAndSpaceCounts[0];
        int spaceCount = tabAndSpaceCounts[1];
        if (useTabCharacter) {
            return StringUtils.repeat("\t", tabCount) +
                    StringUtils.repeat(" ", spaceCount);
        } else {
            // replace tab with spaces if the style is using spaces
            return StringUtils.repeat(" ", tabCount * tabSize + spaceCount);
        }
    }

    /**
     * @param concatenation a string to present concatenation context
     * @param tabSize       from autoDetect
     * @return an int array of size 2, 1st value is tab count, 2nd value is space
     * count
     */
    private static int[] shortestPrefixAfterNewline(String concatenation, int tabSize) {
        int shortest = Integer.MAX_VALUE;
        int[] shortestPair = new int[]{0, 0};
        int tabCount = 0;
        int spaceCount = 0;

        boolean afterNewline = false;
        for (int i = 0; i < concatenation.length(); i++) {
            char c = concatenation.charAt(i);
            if (c != ' ' && c != '\t' && afterNewline) {
                if ((spaceCount + tabCount * tabSize) < shortest) {
                    shortest = spaceCount + tabCount;
                    shortestPair[0] = tabCount;
                    shortestPair[1] = spaceCount;
                }
                afterNewline = false;
            }

            if (c == '\n') {
                afterNewline = true;
                spaceCount = 0;
                tabCount = 0;
            } else if (c == ' ') {
                if (afterNewline) {
                    spaceCount++;
                }
            } else if (c == '\t') {
                if (afterNewline) {
                    tabCount++;
                }
            } else {
                afterNewline = false;
                spaceCount = 0;
                tabCount = 0;
            }
        }

        if ((spaceCount + tabCount > 0) && ((spaceCount + tabCount) < shortest)) {
            shortestPair[0] = tabCount;
            shortestPair[1] = spaceCount;
        }

        return shortestPair;
    }
}
