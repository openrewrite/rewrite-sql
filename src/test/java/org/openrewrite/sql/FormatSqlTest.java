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

import static org.openrewrite.java.Assertions.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class FormatSqlTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FormatSql());
    }

    @DocumentExample
    @Test
    void regular() {
        rewriteRun(
          version(
            // language=java
            java(
              """
                class Test {
                    String query = \"""
                            SELECT * FROM my_table
                            WHERE
                              something = 1;\\
                            \""";
                }
                """,
              """
                class Test {
                    String query = \"""
                            SELECT
                              *
                            FROM
                              my_table
                            WHERE
                              something = 1;\\
                            \""";
                }
                """
            )
            , 15)
        );
    }

    @Test
    void test_single_line() {
        rewriteRun(
          version(
            // language=java
            java(
              """
                class Test {
                    String query = \"""
                            SELECT * FROM my_table WHERE something = 1;\""";
                }
                """,
              """
                class Test {
                    String query = \"""
                            SELECT
                              *
                            FROM
                              my_table
                            WHERE
                              something = 1;\\
                            \""";
                }
                """
            )
            , 15)
        );
    }

    @Test
    void test_complex_query() {
        rewriteRun(
          version(
            // language=java
            java(
              """
                class Test {
                    String query = \"""
                      DELETE FROM tbl_scores
                      WHERE student_id IN
                      (SELECT student_id
                      FROM
                      (SELECT student_id,
                      ROW_NUMBER() OVER(PARTITION BY student_id
                      ORDER BY student_id) AS row_num
                      FROM tbl_scores) t
                      WHERE t.row_num > 1);\""";
                }
                """,
              """
                class Test {
                    String query = \"""
                      DELETE FROM
                        tbl_scores
                      WHERE
                        student_id IN (
                          SELECT
                            student_id
                          FROM
                            (
                              SELECT
                                student_id,
                                ROW_NUMBER() OVER(
                                  PARTITION BY student_id
                                  ORDER BY
                                    student_id
                                ) AS row_num
                              FROM
                                tbl_scores
                            ) t
                          WHERE
                            t.row_num > 1
                        );\\
                      \""";
                }
              """
            )
            , 15)
        );
    }

    @Test
    void test_query_custom_config() {
        rewriteRun(
          spec -> spec.recipe(new FormatSql("sql","\t", 80, true)),
          version(
            // language=java
            java(
              """
                class Test {
                    String query = \"""
                      delete from tbl_scores
                      where t.row_num > 1 OR a in (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);\""";
                }
                """,
              """
                class Test {
                    String query = \"""
                      DELETE FROM
                      	tbl_scores
                      WHERE
                      	t.row_num > 1
                      	OR a IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);\\
                      \""";
                }
                """
            )
            , 15)
        );
    }

    @Test
    void test_insert_postgres() {
        rewriteRun(
          spec -> spec.recipe(new FormatSql("postgresql")),
          version(
            // language=java
            java(
              """
                class Test {
                    String query = \"""
                            INSERT INTO tbl_students (student_id, full_name, teacher_id, department_id)
                            VALUES
                            (1, 'Elvis', NULL, 5),
                            (2, 'Captain America', 1, 7),
                            (3, 'Star Lord', 2, 5);\\
                            \""";
                }
                """,
              """
                class Test {
                    String query = \"""
                            INSERT INTO 
                              tbl_students (student_id, full_name, teacher_id, department_id)
                            VALUES
                              (1, 'Elvis', NULL, 5),
                              (2, 'Captain America', 1, 7),
                              (3, 'Star Lord', 2, 5);\\
                            \""";
                }
                """
            )
            , 15)
        );
    }

    @Test
    void test_complex_query_postgresql() {
        rewriteRun(
          spec -> spec.recipe(new FormatSql("postgresql")),
          version(
            // language=java
            // this statement makes no sense
            java(
              """
                class Test {
                    String query = \"""
                            WITH RECURSIVE cohort AS (
                            SELECT student_id, teacher_id, full_name
                            FROM tbl_students
                            WHERE student_id = 2
                            UNION
                            SELECT e.student_id, e.teacher_id, e.full_name
                            FROM tbl_students e
                            INNER JOIN cohort s ON s.student_id = e.teacher_id)
                            SELECT *
                            FROM cohort
                            UNION ALL
                            SELECT AVG (score)
                            FROM tbl_scores
                            WHERE a != ? AND b IS NOT NULL OR c in (:values) AND d like 'Jen%'
                            GROUP BY student_id
                            HAVING COUNT(student_id)> 1
                            UNION
                            SELECT DISTINCT ON
                            student_id, COALESCE(score,1) sco
                            FROM tbl_scores
                            WHERE EXISTS( SELECT 
                            1 FROM table_2 WHERE 1 = ANY ('{1,2,3}'::int[] )) AND sco > (
                            SELECT MAX (score) FROM tbl_scores
                            FULL OUTER JOIN tbl_scores ON score.student_id = tbl_students.student_id
                            WHERE a = (SELECT SUM (i.net_total_in_cents) FILTER (WHERE status = 'paid') / 100.00 as collected
                            FROM invoices i
                            GROUP BY 1
                            ORDER BY 1 desc)
                            ) AND amount BETWEEN 8 AND 9 AND payment_date BETWEEN NOW()::timestamp AND (NOW() + interval '1 hour')
                            LIMIT 1 OFFSET 1;\\
                            \""";
                }
                """,
              """
                class Test {
                    String query = \"""
                            WITH RECURSIVE cohort AS (
                              SELECT
                                student_id,
                                teacher_id,
                                full_name
                              FROM
                                tbl_students
                              WHERE
                                student_id = 2
                              UNION
                              SELECT
                                e.student_id,
                                e.teacher_id,
                                e.full_name
                              FROM
                                tbl_students e
                                INNER JOIN cohort s ON s.student_id = e.teacher_id
                            )
                            SELECT
                              *
                            FROM
                              cohort
                            UNION ALL
                            SELECT
                              AVG (score)
                            FROM
                              tbl_scores
                            WHERE
                              a != ?
                              AND b IS NOT NULL
                              OR c in (:values)
                              AND d like 'Jen%'
                            GROUP BY
                              student_id
                            HAVING
                              COUNT(student_id) > 1
                            UNION
                            SELECT
                              DISTINCT ON student_id,
                              COALESCE(score, 1) sco
                            FROM
                              tbl_scores
                            WHERE
                              EXISTS(
                                SELECT
                                  1
                                FROM
                                  table_2
                                WHERE
                                  1 = ANY ('{1,2,3}' :: int [ ])
                              )
                              AND sco > (
                                SELECT
                                  MAX (score)
                                FROM
                                  tbl_scores
                                  FULL OUTER JOIN tbl_scores ON score.student_id = tbl_students.student_id
                                WHERE
                                  a = (
                                    SELECT
                                      SUM (i.net_total_in_cents) FILTER (
                                        WHERE
                                          status = 'paid'
                                      ) / 100.00 as collected
                                    FROM
                                      invoices i
                                    GROUP BY
                                      1
                                    ORDER BY
                                      1 desc
                                  )
                              )
                              AND amount BETWEEN 8 AND 9
                              AND payment_date BETWEEN NOW() :: timestamp
                              AND (NOW() + interval '1 hour')
                            LIMIT
                              1 OFFSET 1;\\
                            \""";
                }
                """
            )
            , 15)
        );
    }

    @Test
    void test_update_postgres() {
        rewriteRun(
          spec -> spec.recipe(new FormatSql("postgresql")),
          version(
            // language=java
            java(
              """
                class Test {
                    String query = \"""
                            UPDATE table_name
                            SET column1 = :value1,
                                column2 = :value2
                            WHERE column1 is not null
                            RETURNING *;\\
                            \""";
                }
                """,
              """
                class Test {
                    String query = \"""
                            UPDATE
                              table_name
                            SET
                              column1 = :value1,
                              column2 = :value2
                            WHERE
                              column1 is not null RETURNING *;\\
                            \""";
                }
                """
            )
            , 15)
        );
    }

    @Test
    void test_delete_mariadb() {
        rewriteRun(
          spec -> spec.recipe(new FormatSql("mariadb")),
          version(
            // language=java
            java(
              """
                class Test {
                    String query = \"""
                            delete from contacts
                            where last_name = 'Smith';\\
                            \""";
                }
                """,
              """
                class Test {
                    String query = \"""
                            delete from 
                              contacts
                            where 
                              last_name = 'Smith';\\
                            \""";
                }
                """
            )
            , 15)
        );
    }

    @Test
    void test_create_table_postgres() {
        rewriteRun(
          spec -> spec.recipe(new FormatSql("postgresql","    ", 80, true)),
          version(
            // language=java
            java(
              """
                class Test {
                    String query = \"""
                            CREATE TABLE account_roles (
                                user_id INT NOT NULL,role_id INT NOT NULL,grant_date TIMESTAMP,
                                PRIMARY KEY (user_id, role_id),FOREIGN KEY (role_id) REFERENCES roles (role_id),FOREIGN KEY (user_id)REFERENCES accounts (user_id)
                            );\\
                            \""";
                }
                """,
              """
                class Test {
                    String query = \"""
                            CREATE TABLE account_roles (
                                user_id INT NOT NULL,
                                role_id INT NOT NULL,
                                grant_date TIMESTAMP,
                                PRIMARY KEY (user_id, role_id),
                                FOREIGN KEY (role_id) REFERENCES roles (role_id),
                                FOREIGN KEY (user_id) REFERENCES accounts (user_id)
                            );\\
                            \""";
                }
                """
            )
            , 15)
        );
    }

    @Test
    void test_multi_ddl_postgres() {
        rewriteRun(
          spec -> spec.recipe(new FormatSql("postgresql","    ", 30, true)),
          version(
            // language=java
            java(
              """
                class Test {
                    String query = \"""
                            CREATE TABLE account_roles (
                                user_id INT NOT NULL,role_id INT NOT NULL,grant_date TIMESTAMP,
                                PRIMARY KEY (user_id, role_id),FOREIGN KEY (role_id) REFERENCES roles (role_id),FOREIGN KEY (user_id)REFERENCES accounts (user_id)
                            );
                            create index concurrently index_active_users
                                on users using btree(created_when,created_where,created_why) where active is true;\\
                            \""";
                }
                """,
              """
                class Test {
                    String query = \"""
                            CREATE TABLE account_roles (
                                user_id INT NOT NULL,
                                role_id INT NOT NULL,
                                grant_date TIMESTAMP,
                                PRIMARY KEY (user_id, role_id),
                                FOREIGN KEY (role_id) REFERENCES roles (role_id),
                                FOREIGN KEY (user_id) REFERENCES accounts (user_id)
                            );
                            CREATE INDEX CONCURRENTLY index_active_users ON users USING btree(
                                created_when,
                                created_where,
                                created_why
                            )
                            WHERE
                                active IS TRUE;\\
                            \""";
                }
                """
            )
            , 15)
        );
    }

    @Test
    @Disabled("lines are broken")
    void test_false_detected_sql_in_json_stays_untouched() {
        rewriteRun(
          spec -> spec.recipe(new FormatSql()),
          version(
            // language=java
            java(
              """
                class Test {
                    String query = \"""
                        {
                            \"sql\": \"CREATE Table\",
                            \"number\": \"1234-5678-9012-3456\",
                            \"obj\":
                            {
                                \"test\": \"SELECT * FROM tab\"
                                \"child\": [],
                                \"Partner\": null
                            }
                          }\\
                        \""";
                }
                """,
              """
                class Test {
                    String query = \"""
                        {
                            \"sql\": \"CREATE Table\",
                            \"number\": \"1234-5678-9012-3456\",
                            \"obj\":
                            {
                                \"test\": \"SELECT * FROM tab\"
                                \"child\": [],
                                \"Partner\": null
                            }
                          }\\
                        \""";
                }
                """
            )
            , 15)
        );
    }

    @Test
    void test_sql_in_text_embedded() {
        rewriteRun(
          spec -> spec.recipe(new FormatSql()),
          version(
            // language=java
            java(
              """
                class Test {
                    String query = \"""
                        This is 
                        just a text with a embedded CREATE VIEW view_name AS SELECT column1, column2 FROM table_name WHERE condition = null; statement
                        that should be kept
                        \""";
                }
                """,
              """
                class Test {
                    String query = \"""
                        This is just a text with a embedded CREATE VIEW view_name AS
                        SELECT
                          column1,
                          column2
                        FROM
                          table_name
                        WHERE
                          condition = null;
                        statement that should be kept\\
                        \""";
                }
                """
            )
            , 15)
        );
    }

    @Test
    @Disabled("lines are broken")
    void test_false_postive_detection() {
        rewriteRun(
          spec -> spec.recipe(new FormatSql()),
          version(
            // language=java
            java(
              """
                class Test {
                    String query = \"""
                        This is 
                        just a text with a CREATE
                        that should be kept\\
                        \""";
                }
                """,
              """
                class Test {
                    String query = \"""
                        This is 
                        just a text with a CREATE
                        that should be kept\\
                        \""";
                }
                """
            )
            , 15)
        );
    }

    @Test
    void test_no_sql_stays_untouched() {
        rewriteRun(
          spec -> spec.recipe(new FormatSql()),
          version(
            // language=java
            java(
              """
                class Test {
                    String query = \"""
                        This is 
                        just a text
                        that should be kept\\
                        \""";
                }
                """
            )
            , 15)
        );
    }
}
