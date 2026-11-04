// Source: src/BugTracker.Web/Search/IBugSearch.cs
package btnet.Search;

import btnet.runtime.data.DataSet;
import btnet.runtime.security.IIdentity;

/**
 * Provides full text based search of bugs
 */
public interface IBugSearch {
    /**
     * Re-index all bugs.
     * Warning: This is a CPU, Database and network intensive operation
     */
    void IndexAll();

    /**
     * Index of re-index the bug matching the specified id
     * @param bugId The id of the bug to index
     */
    void IndexBug(int bugId);

    /**
     * Search for bugs based on the specified input text and security settings
     * @param searchText The user entered search text
     * @param identity The current user
     * @return A dataset containing the search results
     */
    DataSet Search(String searchText, IIdentity identity);
}
// PORT STATUS: confidence=high todos=0
