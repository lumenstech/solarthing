package me.retrodaredevil.solarthing;

import me.retrodaredevil.solarthing.annotations.UtilityClass;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.time.Duration;

@UtilityClass
public final class SolarThingConstants {
	private SolarThingConstants(){ throw new UnsupportedOperationException(); }

	public static final String STATUS_DATABASE = "solarthing";
	public static final String EVENT_DATABASE = "solarthing_events";
	public static final String OPEN_DATABASE = "solarthing_open";
	public static final String CLOSED_DATABASE = "solarthing_closed";
	public static final String CACHE_DATABASE = "solarthing_cache";
	public static final String ALTER_DATABASE = "solarthing_alter";

	// for documentation on markers: https://logging.apache.org/log4j/2.0/manual/filters.html#MarkerFilter
	/**
	 * This is a marker used to put something in the "summary" log file. This log file is used for events that don't happen
	 * frequently. (SolarThing program is starting, a command is requested, etc)
	 */
	public static final Marker SUMMARY_MARKER = MarkerFactory.getMarker("SUMMARY");

	/**
	 * This can be used to make sure something doesn't go to the console. This is useful for debugs that aren't important
	 */
	public static final Marker NO_CONSOLE = MarkerFactory.getMarker("NO_CONSOLE");

	/**
	 * This can be used to stop certain messages from being logged to GELF.
	 * This is useful when you want to give information to the average user, but don't want to pollute remote logs.
	 */
	public static final Marker NO_REMOTE = MarkerFactory.getMarker("NO_REMOTE");


	/** Duration for how long to query back in time to get only the latest packets*/
	public static final Duration LATEST_PACKETS_DURATION = Duration.ofMinutes(12);

	public static final Duration STANDARD_MAX_TIME_DISTANCE = Duration.ofMinutes(8);
	public static final Duration STANDARD_MASTER_ID_IGNORE_DISTANCE = Duration.ofMinutes(4);

	public static final Duration SHORT_MAX_TIME_DISTANCE = Duration.ofMinutes(4);
	public static final Duration SHORT_MASTER_ID_IGNORE_DISTANCE = Duration.ofMinutes(2);

	// We avoid exit codes 1-127 because the JVM may use those
	// We avoid exit codes 128-192 because those exit codes may be used by kill signals

	/** The exit code that is used when SolarThing has invalid config */
	public static final int EXIT_CODE_INVALID_CONFIG = 195;
	/** The exit code that is used when SolarThing has invalid CLI options */
	public static final int EXIT_CODE_INVALID_OPTIONS = 196;
	/** The exit code that is used when SolarThing crashes */
	public static final int EXIT_CODE_CRASH = 197;
	public static final int EXIT_CODE_INTERRUPTED = 198;
	public static final int EXIT_CODE_FAIL = 199;
	public static final int EXIT_CODE_NOT_IMPLEMENTED = 200;
	/** The exit code used when some sort of configuration needs to be migrated*/
	public static final int EXIT_CODE_MIGRATE = 201;

	// https://www.freedesktop.org/software/systemd/man/systemd.service.html#RestartForceExitStatus=

	/** The exit code that is used when SolarThing crashes because of a {@link NoClassDefFoundError} */
	public static final int EXIT_CODE_RESTART_NEEDED_JAR_UPDATED = 210;
	/** Used for errors that are uncommon and that are typically fixed by relaunching. These errors are typically erorrs that occur during startup of SolarThing*/
	public static final int EXIT_CODE_RESTART_NEEDED_UNCOMMON_ERROR = 211;
	/** Not used yet but we have this defined in solarthing.service.template */
	@Deprecated
	public static final int EXIT_CODE_RESTART_NEEDED_UNUSED_2 = 212;
	/** Used only by bash scripts to download/launch SolarThing. This is not defined in the legacy template, but will be defined in the newer one.*/
	@Deprecated
	public static final int EXIT_CODE_DOWNLOAD_FAILED = 213;

	public static final class Links {
		private Links() { throw new UnsupportedOperationException(); }
		public static final String ISSUES = "https://github.com/wildmountainfarms/solarthing/issues";
	}
}
