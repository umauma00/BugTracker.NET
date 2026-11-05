// Source: src/BugTracker.Web/btnet/whatsnew.cs
/*
Copyright 2002-2011 Corey Trager
Distributed under the terms of the GNU General Public License
*/

package btnet;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;

import btnet.runtime.Convert;
import btnet.runtime.DateTimes;
import btnet.runtime.security.IIdentity;
import btnet.runtime.web.HttpApplicationState;
import btnet.runtime.web.HttpRuntime;

public class WhatsNew
{

	// inventory: src/BugTracker.Web/btnet/whatsnew.cs L17
	private static final Object mylock = new Object();
	// inventory: src/BugTracker.Web/btnet/whatsnew.cs L18
	// BUG(port): prev_seconds is read and written outside mylock — repro: two concurrent add_news calls within the same second can store the same seconds value
	private static volatile long prev_seconds = 0;
	public static final long ten_million = 10000000;

	public static void add_news(int bugid, String desc, String action, IIdentity identity)
	{

		if (Objects.equals(Util.get_setting("EnableWhatsNewPage", "0"), "1"))
		{

			long seconds = DateTimes.ticks(LocalDateTime.now()) / ten_million;
			// BUG(port): dedupe only compares with the previous value, so a third add_news in the same second yields seconds S, S+1, S (duplicate, non-monotonic; whatsnew.aspx:77 'seconds > since' then skips the later item) — repro: EnableWhatsNewPage=1, three add_news calls within one second on one thread
			if (seconds == prev_seconds)
			{
				seconds++; // prevent dupes, even if we have to lie.
			}
			prev_seconds = seconds;

			BugNews bn = new BugNews();
			bn.seconds = seconds;
			bn.seconds_string = Convert.ToString(seconds);
			bn.bugid = Convert.ToString(bugid);
			bn.desc = desc;
			bn.action = action;
			bn.who = identity.Name();

			// inventory: src/BugTracker.Web/btnet/whatsnew.cs L42
			synchronized (mylock)
			{
				// inventory: src/BugTracker.Web/btnet/whatsnew.cs L44
				HttpApplicationState app = (HttpApplicationState) HttpRuntime.Cache().get("Application");
				// inventory: src/BugTracker.Web/btnet/whatsnew.cs L45
				@SuppressWarnings("unchecked")
				ArrayList<BugNews> list = (ArrayList<BugNews>) app.get("whatsnew");

				// create the list if necessary
				if (list == null)
				{
					// inventory: src/BugTracker.Web/btnet/whatsnew.cs L51
					list = new ArrayList<BugNews>();
					app.set("whatsnew", list);
				}

				// Add the newest item
				list.add(bn);

				// Trim the old items
				int max = Convert.ToInt32(Util.get_setting("WhatsNewMaxItemsCount", "200"));
				while (list.size() > max)
				{
					list.remove(0);
				}

			}

		}

	}

	// second top-level type of whatsnew.cs (RULEBOOK §4)
	public static class BugNews
	{
		public long seconds;
		public String seconds_string;
		public String bugid;
		public String desc;
		public String action;
		public String who;
	}

}

// PORT STATUS: confidence=high todos=0
