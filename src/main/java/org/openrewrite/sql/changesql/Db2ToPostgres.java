
import org.openrewrite.Option;
import org.openrewrite.Recipe;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper=false)
public class Db2ToPostgres extends Recipe {


    @Option(displayName="Db2 keyword needs to be replaced with Postgres Keywords", 
            description="Db2 keyword needs to be replaced with Postgres Keywords",
            example="FETCH FIRST `String` ROWS ONLY",
            required=false)
    String db2Keyword;

    @Option(displayName="Postgres keyword needs to be replaced with Postgres Keywords", 
            description="Postgres keyword needs to be replaced with Postgres Keywords",
            example="LIMIT `String`",
            required=false)
    String postgresKeyword;


    @Override
    public String getDisplayName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getDescription() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}