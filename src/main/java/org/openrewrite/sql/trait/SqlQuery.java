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

import lombok.Value;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;
import net.sf.jsqlparser.util.deparser.StatementDeParser;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.trait.Literal;
import org.openrewrite.java.tree.J;
import org.openrewrite.sql.internal.ChangeTrackingExpressionDeParser;
import org.openrewrite.text.PlainText;
import org.openrewrite.trait.SimpleTraitMatcher;
import org.openrewrite.trait.Trait;

import java.util.regex.Pattern;

import static org.openrewrite.java.trait.Traits.literal;

@Value
public class SqlQuery implements Trait<Tree> {
    Cursor cursor;

    public String getString() {
        Object value = cursor.getValue();
        if (value instanceof J.Literal) {
            return literal().get(cursor)
                    .map(Literal::getString)
                    .orElseThrow(() -> new IllegalStateException("Should have only matched on string literals"));
        } else if (value instanceof PlainText) {
            return ((PlainText) value).getText();
        }
        throw new UnsupportedOperationException("Implement SQL extraction from tree type " + value.getClass().getName());
    }

    public Statement getStatement() {
        try {
            return CCJSqlParserUtil.parse(getString());
        } catch (JSQLParserException e) {
            throw new IllegalStateException("Unexpected SQL parsing error since parsing was " +
                                            "validated prior to the creation of the trait.");
        }
    }

    public Tree visitSql(ExpressionDeParser map) {
        try {
            StringBuilder sb = new StringBuilder();

            SelectDeParser selectDeParser = new SelectDeParser(map, sb);
            map.setSelectVisitor(selectDeParser);
            map.setBuffer(sb);
            StatementDeParser statementDeParser = new StatementDeParser(map, selectDeParser, sb);

            getStatement().accept(statementDeParser);
            return updateSql(sb.toString(), map);
        } catch (Throwable t) {
            // this is invalid sql
            return getTree();
        }
    }

    private Tree updateSql(String sql, ExpressionDeParser deparser) {
        if (deparser instanceof ChangeTrackingExpressionDeParser) {
            sql = ChangeTrackingExpressionDeParser.applyChange(getString(), sql);
        }
        Tree tree = getTree();
        if (tree instanceof PlainText) {
            return ((PlainText) tree).withText(sql);
        } else if (tree instanceof J.Literal) {
            J.Literal literal = (J.Literal) tree;
            return literal
                    .withValue(sql)
                    .withValueSource("\"" + sql + "\"");
        }
        return tree;
    }

    public static class Matcher extends SimpleTraitMatcher<SqlQuery> {
        private static final Pattern SIMPLE_SQL_HEURISTIC = Pattern.compile("SELECT|UPDATE|DELETE|INSERT",
                Pattern.CASE_INSENSITIVE);

        @Override
        protected @Nullable SqlQuery test(Cursor cursor) {
            String sql = null;
            Object value = cursor.getValue();
            if (value instanceof J.Literal) {
                J.Literal literal = (J.Literal) value;
                if (probablySql(literal.getValue())) {
                    sql = literal.getValue().toString();
                }
            } else if (value instanceof PlainText) {
                PlainText plainText = (PlainText) value;
                if (probablySql(plainText.getText())) {
                    sql = plainText.getText();
                }
            }
            if (sql != null) {
                try {
                    CCJSqlParserUtil.parse(sql);
                    return new SqlQuery(cursor);
                } catch (JSQLParserException ignored) {
                }
            }
            return null;
        }

        static boolean probablySql(@Nullable Object maybeSql) {
            return maybeSql != null && SIMPLE_SQL_HEURISTIC.matcher(maybeSql.toString()).find();
        }
    }
}
