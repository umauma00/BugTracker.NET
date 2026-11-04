package btnet.runtime;

/**
 * Stands for .NET {@code System.Globalization.CultureInfo} (RULEBOOK §2A "Dates, numbers, culture", §3).
 * Only two data sets exist: en-US ({@link #EN_US}, also {@link #CurrentCulture}) and the invariant culture.
 * Date/time pattern data is package-private and consumed by {@link DotNetFormat} and {@link DateTimes}.
 */
public final class CultureInfo {

    static final String[] DAY_NAMES = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
    static final String[] ABBR_DAY_NAMES = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    static final String[] MONTH_NAMES = {"January", "February", "March", "April", "May", "June", "July",
        "August", "September", "October", "November", "December"};
    static final String[] ABBR_MONTH_NAMES = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep",
        "Oct", "Nov", "Dec"};

    /** en-US, the culture of the whole application (Web.config has no globalization element). */
    public static final CultureInfo EN_US = new CultureInfo("en-US", false, 0);

    /** .NET {@code CultureInfo.InvariantCulture}. */
    public static final CultureInfo InvariantCulture = new CultureInfo("", true, 0);

    /** .NET {@code CultureInfo.CurrentCulture} = en-US (RULEBOOK §2A). */
    public static final CultureInfo CurrentCulture = EN_US;

    /** .NET {@code CultureInfo.CurrentUICulture} = en-US. */
    public static final CultureInfo CurrentUICulture = EN_US;

    private final String name;
    private final boolean invariant;
    private final NumberFormatInfo numberFormat;

    final String shortDatePattern;
    final String longDatePattern;
    final String shortTimePattern;
    final String longTimePattern;
    final String monthDayPattern;
    final String yearMonthPattern;
    final String amDesignator = "AM";
    final String pmDesignator = "PM";
    final String dateSeparator = "/";
    final String timeSeparator = ":";

    private CultureInfo(String name, boolean invariant, int unused) {
        this.name = name;
        this.invariant = invariant;
        this.numberFormat = new NumberFormatInfo(invariant ? "¤" : "$");
        if (invariant) {
            shortDatePattern = "MM/dd/yyyy";
            longDatePattern = "dddd, dd MMMM yyyy";
            shortTimePattern = "HH:mm";
            longTimePattern = "HH:mm:ss";
            monthDayPattern = "MMMM dd";
            yearMonthPattern = "yyyy MMMM";
        } else {
            shortDatePattern = "M/d/yyyy";
            longDatePattern = "dddd, MMMM d, yyyy";
            shortTimePattern = "h:mm tt";
            longTimePattern = "h:mm:ss tt";
            monthDayPattern = "MMMM d";
            yearMonthPattern = "MMMM yyyy";
        }
    }

    /**
     * .NET {@code new CultureInfo(name)}. {@code ""} gives invariant data; every other name gives en-US data
     * (the only culture the application runs under, e.g. {@code edit_task.aspx.cs:436}
     * {@code new CultureInfo(Thread.CurrentThread.CurrentCulture.Name, true)}).
     * TODO(port): cultures other than en-US/invariant are not modelled; they format as en-US.
     */
    public CultureInfo(String name) {
        this(checkName(name), name != null && name.isEmpty(), 0);
    }

    /** .NET {@code new CultureInfo(name, useUserOverride)}; user overrides do not exist here. */
    public CultureInfo(String name, boolean useUserOverride) {
        this(name);
    }

    private static String checkName(String name) {
        if (name == null) {
            throw new ArgumentNullException("name");
        }
        return name;
    }

    /** .NET {@code Name}. */
    public String Name() {
        return name;
    }

    /** .NET {@code NumberFormat}. */
    public NumberFormatInfo NumberFormat() {
        return numberFormat;
    }

    boolean isInvariant() {
        return invariant;
    }

    @Override
    public String toString() {
        return name;
    }
}
