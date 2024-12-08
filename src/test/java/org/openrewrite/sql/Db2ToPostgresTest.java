package org.openrewrite.sql;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import static org.openrewrite.test.SourceSpecs.text;

public class Db2ToPostgresTest implements RewriteTest{

    @Override
    public void defaults(RecipeSpec spec) {
        // spec.recipe(new Db2ToPostgres()); // TODO Create class that extends `Recipe` and overrides `getVisitor()`
    }

    @Test
    void changeFetchFirstOperatorDb2ToPostgres() {
        rewriteRun(
          // language=SQL
          text(
            "SELECT * FROM my_table WHERE my_column = 1 FETCH FIRST 10 ROWS ONLY;",
            "SELECT * FROM my_table WHERE my_column = 1 LIMIT 10;",
            spec -> spec.path("src/main/resources/db2.sql")
            )
        );
    }

    @Test
    void changeMinusOperatorDb2ToPostgres() {
        rewriteRun(
          // language=SQL
          text(
            "SELECT col1 FROM t1 MINUS SELECT col1 FROM t2;",
            "SELECT col1 FROM t1 EXCEPT SELECT col1 FROM t2;",
            spec -> spec.path("src/main/resources/db2.sql")
            )
        );
    }

    @Test
    void changeTableSpaceKeywordDb2ToPostgres() {
        rewriteRun(
          // language=SQL
          text(
            "CREATE TABLE products (product_no INTEGER NOT NULL,name VARCHAR(30) NOT NULL,price INTEGER CONSTRAINT positive_price CHECK (price > 0),
                        CONSTRAINT pk_prod_no
                        PRIMARY KEY (product_no)
                        ) DATA CAPTURE NONE IN
                        mydataspace INDEX IN myindexspace
                        ;",
            "CREATE TABLE products (product_no INTEGER NOT NULL,name TEXT NOT NULL, price INTEGER CONSTRAINT positive_price CHECK (price > 0),
                        CONSTRAINT pk_prod_no
                        PRIMARY KEY (product_no) USING
                        INDEX TABLESPACE myindexspace
                        ) TABLESPACE mydataspace
                        ;",
            spec -> spec.path("src/main/resources/db2.sql")
            )
        );
    }

    @Test
    void changeCLOBDataTypeDb2ToPostgres() {
        rewriteRun(
          // language=SQL
          text(
            "CREATE TABLE orders (notes CLOB(1M));",
            "CREATE TABLE orders (notes TEXT(1M));",
            spec -> spec.path("src/main/resources/db2.sql")
            )
        );
    }


}
