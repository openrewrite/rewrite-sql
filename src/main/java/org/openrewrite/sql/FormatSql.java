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

import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesJavaVersion;
import org.openrewrite.java.style.IntelliJ;
import org.openrewrite.java.style.TabsAndIndentsStyle;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import com.github.vertical_blank.sqlformatter.SqlFormatter;
import com.github.vertical_blank.sqlformatter.core.FormatConfig;
import com.github.vertical_blank.sqlformatter.core.FormatConfig.FormatConfigBuilder;
import com.github.vertical_blank.sqlformatter.languages.Dialect;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class FormatSql extends Recipe {
    @Option(displayName = "SQL dialect to be used to format SQL snippets.",
            description = "Check out https://github.com/vertical-blank/sql-formatter#dialect for supported dialects.",
            valid = {"sql", "mariadb", "mysql", "postgresql", "db2", "plsql", "n1ql", "redshift", "spark", "tsql"},
            example = "postgresql",
            required = false)
    @Nullable
    String sqlDialect;

    @Option(displayName = "Characters used for indentation.", description = "Defaults to two spaces.", example = "    ", required = false)
    @Nullable
    String indent;

    @Option(displayName = "Maximum length to treat inline block as one line.", description = "Defaults to 50.", example = "100", required = false)
    @Nullable
    Integer maxColumnLength;

    @Option(displayName = "Converts keywords to uppercase.", description = "Defaults to false (not safe to use when SQL dialect has case-sensitive identifiers).", example = "true", required = false)
    @Nullable
    Boolean uppercase;

    public FormatSql() {
        this(Dialect.StandardSql.name());
    }

    public FormatSql(String sqlDialect) {
        this(sqlDialect, null, null, null);
    }

    public FormatSql(String sqlDialect, String indent, Integer maxColumnLength, Boolean uppercase) {
        this.sqlDialect = sqlDialect;
        this.indent = indent;
        this.maxColumnLength = maxColumnLength;
        this.uppercase = uppercase;
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
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesJavaVersion<>(15),
                new SqlTextBlockFormatVisitor(sqlDialect, indent, maxColumnLength, uppercase));
    }
}

class SqlTextBlockFormatVisitor extends JavaIsoVisitor<ExecutionContext> {
    private final SqlDetector sqlDetector = new SqlDetector();
    private final FormatConfig config;
    private final SqlFormatter.Formatter sqlFormatter;

    SqlTextBlockFormatVisitor(
            String sqlDialect,
            String indent,
            Integer maxColumnLength,
            Boolean uppercase
    ) {
        FormatConfigBuilder builder = FormatConfig.builder();
        if (indent != null) {
            builder = builder.indent(indent);
        }
        if (uppercase != null) {
            builder = builder.uppercase(uppercase);
        }
        if (maxColumnLength != null) {
            builder = builder.maxColumnLength(maxColumnLength);
        }
        this.config = builder.build();
        this.sqlFormatter = SqlFormatter.of(sqlDialect);
    }

    @Override
    public J.Literal visitLiteral(J.Literal lit, ExecutionContext ctx) {
        J.Literal literal = super.visitLiteral(lit, ctx);
        if (isTextBlock(literal)) {
            String originalValue = (String) literal.getValue();
            if (sqlDetector.isSql(originalValue)) {
                String formatted = sqlFormatter.format(originalValue, config);
                if (!originalValue.equals(formatted)) {
                    TabsAndIndentsStyle style = getCursor().firstEnclosingOrThrow(SourceFile.class)
                            .getStyle(TabsAndIndentsStyle.class);
                    String indented = Indenter.indent(literal.getValueSource(), formatted, style);
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
}

class Indenter {

    public static String indent(String valueSource, String formatted, @Nullable TabsAndIndentsStyle style) {
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
