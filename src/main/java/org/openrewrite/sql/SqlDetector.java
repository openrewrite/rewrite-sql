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


import lombok.EqualsAndHashCode;
import lombok.Value;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import org.jspecify.annotations.Nullable;
import org.openrewrite.SourceFile;
import org.openrewrite.sql.table.DatabaseColumnsUsed;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static org.openrewrite.PathUtils.separatorsToUnix;

public class SqlDetector {
    private static final Pattern SIMPLE_SQL_HEURISTIC = Pattern.compile("SELECT|UPDATE|DELETE|INSERT",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SIMPLE_DDL_HEURISTIC = Pattern.compile("CREATE|ALTER|DROP|TRUNCATE", Pattern.CASE_INSENSITIVE);

    public List<DatabaseColumnsUsed.Row> rows(SourceFile sourceFile, @Nullable String commitHash, int lineNumber, @Nullable String maybeSql) {
        if (!probablySql(maybeSql)) {
            return emptyList();
        }
        AtomicReference<List<DatabaseColumnsUsed.Row>> rows = new AtomicReference<>();
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(maybeSql);
        } catch (JSQLParserException e) {
            return emptyList(); // not a valid SQL statement
        }

        StatementVisitorAdapter statementVisitor = new StatementVisitorAdapter() {
            final Stack<DatabaseColumnsUsed.Operation> operation = new Stack<>();
            final Stack<String> table = new Stack<>();

            @Override
            public void visit(Select select) {
                operation.push(DatabaseColumnsUsed.Operation.SELECT);
                select.accept(new SelectVisitorAdapter() {
                    @Override
                    public void visit(PlainSelect plainSelect) {
                        if (plainSelect.getFromItem() instanceof Table) {
                            Table t = (Table) plainSelect.getFromItem();
                            table.push(t.getName());
                            for (SelectItem<?> selectItem : plainSelect.getSelectItems()) {
                                selectItem.accept(new ColumnDetector(rows, sourceFile, lineNumber, commitHash,
                                        operation.peek(), table.peek()));
                            }
                            table.pop();
                        }
                        super.visit(plainSelect);
                    }
                });
                operation.pop();
            }

            @Override
            public void visit(Update update) {
                operation.push(DatabaseColumnsUsed.Operation.UPDATE);
                Table t = update.getTable();
                table.push(t.getName());
                for (UpdateSet set : update.getUpdateSets()) {
                    for (Column column : set.getColumns()) {
                        column.accept(new ColumnDetector(rows, sourceFile, lineNumber, commitHash, operation.peek(),
                                table.peek()));
                    }
                }
                table.pop();
                operation.pop();
            }

            @Override
            public void visit(Delete delete) {
                operation.push(DatabaseColumnsUsed.Operation.DELETE);
                for (Table table : delete.getTables()) {
                    addRow(rows, new DatabaseColumnsUsed.Row(
                            separatorsToUnix(sourceFile.getSourcePath().toString()),
                            lineNumber,
                            commitHash,
                            operation.peek(),
                            table.getName(),
                            null
                    ));
                }
                if (delete.getTable() != null) {
                    addRow(rows, new DatabaseColumnsUsed.Row(
                            separatorsToUnix(sourceFile.getSourcePath().toString()),
                            lineNumber,
                            commitHash,
                            operation.peek(),
                            delete.getTable().getName(),
                            null
                    ));
                }
                operation.pop();
            }
        };

        statement.accept(statementVisitor);
        return rows.get() == null ? emptyList() : rows.get();
    }

    private boolean probablySql(@Nullable String maybeSql) {
        return maybeSql != null && SIMPLE_SQL_HEURISTIC.matcher(maybeSql).find();
    }

    private boolean probablyDdl(@Nullable String maybeDdl) {
        return maybeDdl != null && SIMPLE_DDL_HEURISTIC.matcher(maybeDdl).find();
    }

    public boolean isSql(@Nullable String maybeSql) {
        if (probablySql(maybeSql) || probablyDdl(maybeSql)) {
            try {
                for (String sql : maybeSql.split(";")) {
                    CCJSqlParserUtil.parse(sql);
                }
                return true;
            } catch (JSQLParserException e) {
                // not a valid SQL statement
            }
        }
        return false;
    }

    @Value
    @EqualsAndHashCode(callSuper = false)
    private static class ColumnDetector extends ExpressionVisitorAdapter {
        AtomicReference<List<DatabaseColumnsUsed.Row>> rows;
        SourceFile sourceFile;
        int lineNumber;

        @Nullable
        String commitHash;

        DatabaseColumnsUsed.Operation operation;
        String table;

        @Override
        public void visit(AllColumns columns) {
            addRowForColumnName(columns.toString());
        }

        @Override
        public void visit(Column column) {
            addRowForColumnName(column.getColumnName());
        }

        private void addRowForColumnName(String columnName) {
            addRow(rows, new DatabaseColumnsUsed.Row(
                    separatorsToUnix(sourceFile.getSourcePath().toString()),
                    lineNumber,
                    commitHash,
                    operation,
                    table,
                    columnName
            ));
        }
    }

    private static void addRow(AtomicReference<List<DatabaseColumnsUsed.Row>> rows, DatabaseColumnsUsed.Row row) {
        rows.getAndUpdate(acc -> {
            List<DatabaseColumnsUsed.Row> rs = acc == null ? new ArrayList<>() : acc;
            rs.add(row);
            return rs;
        });
    }
}
