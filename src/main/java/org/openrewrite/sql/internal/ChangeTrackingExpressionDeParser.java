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
package org.openrewrite.sql.internal;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;

@RequiredArgsConstructor
public class ChangeTrackingExpressionDeParser extends ExpressionDeParser {
    private static final String CHANGE_MARKER = "※";

    public static String applyChange(String original, String changed) {
        // TODO this is a naive implementation that doesn't handle the case where
        // the original and changed have different casing
        return changed.replace(CHANGE_MARKER, "");
    }

    public void trackChange(Runnable r) {
        buffer.append(CHANGE_MARKER);
        r.run();
        buffer.append(CHANGE_MARKER);
    }
}
