package dao.model;

import java.util.Locale;

public class TimestampFormatterTimeSinceEnglish implements TimestampFormatter {
	/**
	 * Generates a textual label describing how long ago timestamp was,
	 * compared with the current system time
	 * @param timestamp the UNIX time in milliseconds to compare to
	 * @return a String-based timestamp descriptor
	 */
	@Override
	public String format(long timestamp) {
		boolean zh = Locale.getDefault().getLanguage().equals("zh");
		long current = System.currentTimeMillis();
		// Small device/server clock differences should not label new comments as future.
        if (timestamp > current + 30000) return zh ? "未来" : "in the future";

		long secondsAgo = Math.max(0, (current - timestamp)/1000);
		if (secondsAgo < 5) return zh ? "刚刚" : "right now";
		else if (secondsAgo < 60) return zh
				? String.format(Locale.ROOT, "%d 秒前", secondsAgo)
				: String.format(Locale.ROOT, "%d seconds ago", secondsAgo);

		long minutesAgo = secondsAgo / 60;
		if (minutesAgo == 1) return zh ? "1 分钟前" : "a minute ago";
		if (minutesAgo < 60) return zh
				? String.format(Locale.ROOT, "%d 分钟前", minutesAgo)
				: String.format(Locale.ROOT, "%d minutes ago", minutesAgo);

		long hoursAgo = minutesAgo / 60;
		if (hoursAgo == 1) return zh ? "1 小时前" : "an hour ago";
		if (hoursAgo < 24) return zh
				? String.format(Locale.ROOT, "%d 小时前", hoursAgo)
				: String.format(Locale.ROOT, "%d hours ago", hoursAgo);

		long daysAgo = hoursAgo / 24;
		if (daysAgo == 1) return zh ? "1 天前" : "a day ago";
		if (daysAgo < 7) return zh
				? String.format(Locale.ROOT, "%d 天前", daysAgo)
				: String.format(Locale.ROOT, "%d days ago", daysAgo);

		long weeksAgo = daysAgo / 7;
		if (weeksAgo == 1) return zh ? "1 周前" : "a week ago";
		if (weeksAgo == 2) return zh ? "2 周前" : "a fortnight ago";

		return zh ? "一段时间前" : "a while ago";
	}
}
