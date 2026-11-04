// Source: src/BugTracker.Web/Search/BugSearch.cs
package btnet.Search;

import btnet.DbUtil;
import btnet.SQLString;
import btnet.Util;
import btnet.runtime.Collections2;
import btnet.runtime.Convert;
import btnet.runtime.Exceptions;
import btnet.runtime.Strings;
import btnet.runtime.config.JsonConfig;
import btnet.runtime.data.DataColumn;
import btnet.runtime.data.DataRow;
import btnet.runtime.data.DataSet;
import btnet.runtime.data.DataTable;
import btnet.runtime.search.ElasticClient;
import btnet.runtime.search.Hit;
import btnet.runtime.search.SearchResponse;
import btnet.runtime.security.IIdentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A full-text bug search based on the NEST API for ElasticSearch
 */
public class BugSearch implements IBugSearch {
    private final ElasticClient _client;

    public BugSearch(ElasticClient client) {
        _client = client;
    }

    /**
     * Re-index all bugs.
     * Warning: This is a CPU, Database and network intensive operation
     */
    @Override
    public void IndexAll() {
        try {
            Util.write_to_log("started creating search index for all bugs");

            DataSet ds = DbUtil.get_dataset(new SQLString("select bg_id from bugs"));

            for (DataRow dr : ds.Tables().get(0).Rows()) {
                IndexBug(Convert.ToInt32(dr.get("bg_id")));
            }

            Util.write_to_log("Done recreating search index for all bugs");
        } catch (RuntimeException e) {
            // inventory: src/BugTracker.Web/Search/BugSearch.cs L44
            Util.write_to_log("exception building search index: " + Strings.cs(Exceptions.Message(e)));
            Util.write_to_log(Exceptions.StackTrace(e));
        }

    }

    @Override
    public DataSet Search(String searchText, IIdentity identity) {
        // inventory: src/BugTracker.Web/Search/BugSearch.cs L55
        // TODO(port): verify NEST 1.0.2 wire format
        Map<String, Object> queryString = new LinkedHashMap<>();
        queryString.put("query", searchText);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("query_string", queryString);
        Map<String, Object> highlight_ = new LinkedHashMap<>();
        highlight_.put("pre_tags", List.of("<span class='highlighted'>"));
        highlight_.put("post_tags", List.of("</span>"));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("*", new LinkedHashMap<String, Object>());
        highlight_.put("fields", fields);
        Map<String, Object> searchBody = new LinkedHashMap<>();
        searchBody.put("size", 500); //Maximum of 500 results just to avoid major performance problems
        searchBody.put("query", query);
        searchBody.put("highlight", highlight_);
        String body = JsonConfig.mapper().writeValueAsString(searchBody);
        SearchResponse response = _client.Search("btnet", body);


        DataSet results = GetSearchResultDataSet();
        DataTable resultTable = results.Tables().get(0);

        var filteredHits = GetHitsFilteredBySecurity(response, identity);

        for (var hit : filteredHits) {
            DataRow resultRow = resultTable.NewRow();

            Map<String, Object> bug = hit.Source;
            int bugId = Convert.ToInt32(bug.get("bg_id"));

            resultRow.set(ResultColumns.Color, "#ffffff");

            resultRow.set(ResultColumns.Id, bugId);
            // inventory: src/BugTracker.Web/Search/BugSearch.cs L78
            resultRow.set(ResultColumns.Description, Strings.ToStringStrict(bug.get("desc")));
            // inventory: src/BugTracker.Web/Search/BugSearch.cs L79
            if (!hit.Highlights.isEmpty()) {
                var highlight = hit.Highlights.entrySet().iterator().next();
                resultRow.set(ResultColumns.Source, highlight.getValue().Field);
                resultRow.set(ResultColumns.Text, highlight.getValue().Highlights.isEmpty() ? null : highlight.getValue().Highlights.get(0));
            }

            // inventory: src/BugTracker.Web/Search/BugSearch.cs L86
            resultRow.set(ResultColumns.Date, Strings.ToStringStrict(bug.get("bg_reported_date")));
            resultRow.set(ResultColumns.Status, Strings.ToStringStrict(bug.get("status")));
            resultRow.set(ResultColumns.Score, Convert.ToDecimal(hit.Score));

            resultTable.Rows().Add(resultRow);

        }

        return results;
    }

    private List<Hit> GetHitsFilteredBySecurity(SearchResponse response, IIdentity identity) {
        //NOTE: The search response will contain all bugs, but the current user might not have access to some of the bugs in the search response.
        //      This method filters the list of hits based on the list of bugs that the user has access to in the system.
        //      This is not an optimal solution but was considered the best approach given the current security filtering approach in bug tracker
        // (source TODO) Change this once the security approach has been redesigned.
        var sql = new SQLString("SELECT bg_id FROM bugs WHERE $ALTER_HERE");
        sql = Util.alter_sql_per_project_permissions(sql, identity);

        DataSet ds = DbUtil.get_dataset(sql);
        // inventory: src/BugTracker.Web/Search/BugSearch.cs L108
        LinkedHashSet<Integer> visibleBugIds = new LinkedHashSet<Integer>(
            ds.Tables().get(0).Rows().stream().map(d -> Convert.ToInt32(d.get("bg_id"))).collect(Collectors.toCollection(ArrayList::new)));

        // inventory: src/BugTracker.Web/Search/BugSearch.cs L110
        return response.Hits.stream().filter(h -> visibleBugIds.contains(Convert.ToInt32(h.Id))).collect(Collectors.toCollection(ArrayList::new));
    }


    /**
     * Index of re-index the bug matching the specified id
     * @param bugId The id of the bug to index
     */
    @Override
    public void IndexBug(int bugId) {
        try {

            Util.write_to_log("started updating search index");

            var sql = new SQLString("\r\nselect bg_id,\r\nisnull(bg_tags,'') tags,\r\nbg_reported_date,\r\nisnull(st_name,'') status,\r\nbg_short_desc  as [desc]\r\nfrom bugs \r\nleft outer join statuses on st_id = bg_status\r\nwhere bg_id = @bugid");

            sql = sql.AddParameterWithValue("bugid", Convert.ToString(bugId));

            DataRow bugRow = DbUtil.get_datarow(sql);

            sql = new SQLString("\r\n                select bp_id, \r\n                isnull(bp_comment_search,bp_comment) [text] ,\r\n                bp_date\r\n                from bug_posts \r\n                where bp_type <> 'update'\r\n                and bp_hidden_from_external_users = 0\r\n                and bp_bug = @bugId");
            sql.AddParameterWithValue("bugId", Convert.ToString(bugId));
            DataSet bugPosts = DbUtil.get_dataset(sql);

            IndexBug(bugRow, bugPosts.Tables().get(0));

            Util.write_to_log("done updating search index");
        } catch (RuntimeException e) {
            // inventory: src/BugTracker.Web/Search/BugSearch.cs L154
            Util.write_to_log("exception updating search index: " + Strings.cs(Exceptions.Message(e)));
            Util.write_to_log(Exceptions.StackTrace(e));
        }
    }

    private void IndexBug(DataRow bugRow, DataTable bugPosts) {
        var bugJson = ToJson(bugRow, bugPosts);
        // inventory: src/BugTracker.Web/Search/BugSearch.cs L164
        _client.Index(bugJson, "bug", Convert.ToInt64(bugRow.get("bg_id")));

    }

    /**
     * Conversts the specified bug and bug posts to a JSON reprenstation that is suitable for indexing
     * @param bugRow The bug
     * @param posts All bug posts for the specified bug
     * @return A JSON representation of the bug and it's posts
     */
    private static String ToJson(DataRow bugRow, DataTable posts) {
        //NOTE: the NEST API for ElasticSearch typically works with POCOs, but BugTracker is all dataset based currently.
        //      As a workaround for now, we need to convert the dataset representation to a JSON document that is
        //      suitable for indexing.
        Map<String, Object> bugDictionary = new LinkedHashMap<>();
        for (DataColumn column : bugRow.Table().Columns()) {
            Collections2.set(bugDictionary, column.ColumnName(), bugRow.get(column));
        }
        Collections2.set(bugDictionary, "posts", posts);
        String json = JsonConfig.mapper().writeValueAsString(bugDictionary);
        return json;
    }

    /**
     * Get a new dataset that will hold the results of a search
     * @return
     */
    private DataSet GetSearchResultDataSet() {
        DataSet result = new DataSet();
        DataTable table = result.Tables().Add();
        table.Columns().Add(ResultColumns.Color, String.class);
        table.Columns().Add(ResultColumns.Id, Integer.class);
        table.Columns().Add(ResultColumns.Description, String.class);
        table.Columns().Add(ResultColumns.Source, String.class);
        table.Columns().Add(ResultColumns.Text, String.class);
        table.Columns().Add(ResultColumns.Date, java.time.LocalDateTime.class);
        table.Columns().Add(ResultColumns.Status, String.class);
        table.Columns().Add(ResultColumns.Score, java.math.BigDecimal.class);
        return result;
    }

    private static final class ResultColumns {
        private ResultColumns() {}

        public static final String Color = "Color";
        public static final String Id = "id";
        public static final String Description = "desc";
        public static final String Source = "search_source";
        public static final String Text = "search_text";
        public static final String Date = "date";
        public static final String Status = "status";
        public static final String Score = "#SCORE";
    }
}
// PORT STATUS: confidence=high todos=1
