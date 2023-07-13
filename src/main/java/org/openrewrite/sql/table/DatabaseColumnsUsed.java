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
package org.openrewrite.sql.table;

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;
import org.openrewrite.internal.lang.Nullable;

public class DatabaseColumnsUsed extends DataTable<DatabaseColumnsUsed.Row> {

    public DatabaseColumnsUsed(Recipe recipe) {
        super(recipe,
                "Database columns used",
                "Shows which database columns are read/written by a SQL statement.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The path to the source file.")
        String sourcePath;

        @Column(displayName = "Line number",
                description = "The line number the SQL operation appears at")
        int lineNumber;

        @Column(displayName = "Operation",
                description = "Whether the column is read, written, etc.")
        Operation operation;

        @Column(displayName = "Table",
                description = "The table name.")
        String table;

        @Column(displayName = "Column",
                description = "The column name. In the case of a DELETE, column will be null.")
        @Nullable
        String column;
    }

    public enum Operation {
        SELECT,
        UPDATE,
        INSERT,
        DELETE
    }
}
