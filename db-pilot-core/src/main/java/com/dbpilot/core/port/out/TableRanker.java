package com.dbpilot.core.port.out;

import com.dbpilot.core.model.RankedTable;
import com.dbpilot.core.model.TableMetadata;
import com.dbpilot.core.model.UserHabit;

import java.util.List;

/**
 * Outbound port for ranking tables by relevance to user intent.
 *
 * <p>Implements the triple-layer ranking algorithm:</p>
 * <ol>
 *   <li><strong>Semantic Match:</strong> Vector/embedding search for tables matching user intent.</li>
 *   <li><strong>Relation Expansion:</strong> Automatically include tables with FK links to semantic matches.</li>
 *   <li><strong>Frequency Weighting:</strong> Prioritize tables frequently used by the specific user.</li>
 * </ol>
 *
 * <p>Final score formula: {@code α × semantic + β × relation + γ × frequency}</p>
 *
 * @author DB-Pilot
 */
public interface TableRanker {

    /**
     * Ranks all candidate tables by relevance to the user's natural language intent.
     *
     * @param userIntent  the natural language query/intent from the user
     * @param allTables   all available tables in the database
     * @param userHabits  the user's historical query habits (for frequency weighting)
     * @param maxResults  maximum number of tables to return
     * @return ranked list of tables, sorted by descending score
     */
    List<RankedTable> rank(String userIntent,
                           List<TableMetadata> allTables,
                           List<UserHabit> userHabits,
                           int maxResults);
}
