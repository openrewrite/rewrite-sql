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

import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.GitProvenance;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.sql.table.DatabaseColumnsUsed;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import static org.openrewrite.internal.StringUtils.countOccurrences;

public class FindSql extends Recipe {
    transient DatabaseColumnsUsed used = new DatabaseColumnsUsed(this);

    @Override
    public String getDisplayName() {
        return "Find SQL in code and resource files";
    }

    @Override
    public String getDescription() {
        return "Find SQL in code (e.g. in string literals) and in resources like those ending with `.sql`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(Preconditions.or(
                Preconditions.not(new PlainTextVisitor<ExecutionContext>() {
                    @Override
                    public PlainText visitText(PlainText text, ExecutionContext ctx) {
                        return SearchResult.found(text);
                    }
                }),
                new HasSourcePath<>("**/*.sql")
        ), new TreeVisitor<Tree, ExecutionContext>() {
            final SqlDetector detector = new SqlDetector();

            @Override
            public @Nullable Tree preVisit(Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    stopAfterPreVisit();
                    tree = new PlainTextVisitor<ExecutionContext>() {
                        @Override
                        public PlainText visitText(PlainText text, ExecutionContext ctx) {
                            return find(ctx, 1, getCursor(), text.getText());
                        }
                    }.visit(tree, ctx);

                    tree = new JavaIsoVisitor<ExecutionContext>() {
                        int lineNumber = 1;
                        @Override
                        public Space visitSpace(Space space, Space.Location loc, ExecutionContext executionContext) {
                            lineNumber += countLines(space);
                            return space;
                        }

                        @Override
                        public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                            visitSpace(literal.getPrefix(), Space.Location.LITERAL_PREFIX, ctx);
                            if(literal.getValue() instanceof String) {
                                literal = find(ctx, lineNumber, getCursor(), (String) literal.getValue());
                                assert literal.getValue() != null;
                                lineNumber += countLines(literal.getValue().toString());
                            }
                            return literal;
                        }
                    }.visit(tree, ctx);

                tree = new YamlIsoVisitor<ExecutionContext>() {
                    int lineNumber = 1;
                    @Override
                    public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
                        lineNumber += countLines(scalar.getPrefix());
                        Yaml.Scalar s = find(ctx, lineNumber, getCursor(), scalar.getValue());
                        lineNumber += countLines(s.getValue());
                        return s;
                    }

                    @Override
                    public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext executionContext) {
                        lineNumber += countLines(documents.getPrefix());
                        return super.visitDocuments(documents, executionContext);
                    }

                    @Override
                    public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext executionContext) {
                        lineNumber += countLines(document.getPrefix());
                        return super.visitDocument(document, executionContext);
                    }

                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext executionContext) {
                        lineNumber += countLines(entry.getPrefix());
                        lineNumber += countLines(entry.getBeforeMappingValueIndicator());
                        return super.visitMappingEntry(entry, executionContext);
                    }

                    @Override
                    public Yaml.Sequence.Entry visitSequenceEntry(Yaml.Sequence.Entry entry, ExecutionContext executionContext) {
                        lineNumber += countLines(entry.getPrefix());
                        return super.visitSequenceEntry(entry, executionContext);
                    }

                    @Override
                    public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext executionContext) {
                        lineNumber += countLines(mapping.getPrefix());
                        lineNumber += countLines(mapping.getOpeningBracePrefix());
                        Yaml.Mapping m = super.visitMapping(mapping, executionContext);
                        lineNumber += countLines(m.getClosingBracePrefix());
                        return m;
                    }

                    @Override
                    public Yaml.Sequence visitSequence(Yaml.Sequence sequence, ExecutionContext executionContext) {
                        lineNumber += countLines(sequence.getPrefix());
                        lineNumber += countLines(sequence.getOpeningBracketPrefix());
                        Yaml.Sequence s = super.visitSequence(sequence, executionContext);
                        lineNumber += countLines(s.getOpeningBracketPrefix());
                        return s;
                    }

                    @Override
                    public Yaml visitAlias(Yaml.Alias alias, ExecutionContext executionContext) {
                        lineNumber += countLines(alias.getPrefix());
                        return super.visitAlias(alias, executionContext);
                    }

                    @Override
                    public Yaml visitAnchor(Yaml.Anchor anchor, ExecutionContext executionContext) {
                        lineNumber += countLines(anchor.getPrefix());
                        return super.visitAnchor(anchor, executionContext);
                    }
                }.visit(tree, ctx);

                }
                return tree;
            }

            private <T extends Tree> T find(ExecutionContext ctx, int lineNumber, Cursor cursor, String text) {
                //noinspection unchecked
                return (T) cursor
                        .getPathAsStream(SourceFile.class::isInstance)
                        .findFirst()
                        .map(SourceFile.class::cast)
                        .map(sourceFile -> {
                            Tree t = cursor.getValue();
                            String commitHash = sourceFile.getMarkers().findFirst(GitProvenance.class)
                                    .map(GitProvenance::getChange)
                                    .orElse(null);
                            for (DatabaseColumnsUsed.Row row : detector.rows(sourceFile, commitHash, lineNumber, text)) {
                                used.insertRow(ctx, row);
                                t = SearchResult.found(t);
                            }
                            return t;
                        })
                        .orElseGet(cursor::getValue);
            }
        });
    }

    private static int countLines(@Nullable String s) {
        if(s == null) {
            return 0;
        }
        return countOccurrences(s, "\n");
    }
    private static int countLines(Space space) {
        int n = countOccurrences(space.getWhitespace(), "\n");
        for (Comment comment : space.getComments()) {
            if(comment instanceof TextComment) {
                TextComment textComment = (TextComment) comment;
                n += countOccurrences(textComment.getText(), "\n");
            } else if(comment instanceof Javadoc.DocComment) {
                Javadoc.DocComment docComment = (Javadoc.DocComment) comment;
                n += countOccurrences(docComment.toString(), "\n");
            }
            n += countOccurrences(comment.getSuffix(), "\n");
        }
        return n;
    }
}
