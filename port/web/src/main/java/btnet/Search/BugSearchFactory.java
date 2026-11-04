// Source: src/BugTracker.Web/Search/BugSearchFactory.cs
package btnet.Search;

import btnet.Util;
import btnet.runtime.ConfigurationErrorsException;
import btnet.runtime.Strings;
import btnet.runtime.net.Uri;
import btnet.runtime.search.ElasticClient;

public final class BugSearchFactory {
    private BugSearchFactory() {}

    public static IBugSearch CreateBugSearch() {
        var uriString = Util.get_setting("SearchServerURI", "");
        if (Strings.IsNullOrEmpty(uriString)) {
            throw new ConfigurationErrorsException("SearchServerURI is missing. This application setting is required in order to use search");
        }

        var node = new Uri(uriString);

        // NEST ConnectionSettings(node, defaultIndex: "btnet") + new ElasticClient(settings) -> runtime ElasticClient (RULEBOOK §2A search row)
        var client = new ElasticClient(node, "btnet");
        return new BugSearch(client);
    }
}

// PORT STATUS: confidence=high todos=0
