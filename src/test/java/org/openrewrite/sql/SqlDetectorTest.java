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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
