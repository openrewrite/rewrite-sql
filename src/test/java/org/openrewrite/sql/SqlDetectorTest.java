package org.openrewrite.sql;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlDetectorTest {
    
    SqlDetector classUnderTest = new SqlDetector();

    @ParameterizedTest
    @ValueSource(strings = {
      "UPDATE tab SET x = y",
      "DELETE FROM table_name WHERE condition = true;"
    })
    void test_isSql(String maybeSql){
        assertThat(classUnderTest.isSql(maybeSql)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "This will be SELECTed by the heuristic but not parse as SQL",
      "The heuristic won't match this at all"
    })
    void test_isNotSql(String maybeSql){
        assertThat(classUnderTest.isSql(maybeSql)).isFalse();
    }

    @Disabled("dealt with later")
    @ParameterizedTest
    @ValueSource(strings = {
      "Truncate tab",
      "DROP FUNCTION func CASCADE"
    })
    void test_isExoticSql(String maybeSql){
        assertThat(classUnderTest.isSql(maybeSql)).isTrue();
    }


}
