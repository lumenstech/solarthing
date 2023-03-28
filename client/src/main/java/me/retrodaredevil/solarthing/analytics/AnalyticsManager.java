package me.retrodaredevil.solarthing.analytics;

import com.brsanthu.googleanalytics.GoogleAnalytics;
import com.brsanthu.googleanalytics.GoogleAnalyticsConfig;
import com.brsanthu.googleanalytics.request.DefaultRequest;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.retrodaredevil.solarthing.SolarThingConstants;
import me.retrodaredevil.solarthing.config.options.ProgramType;
import me.retrodaredevil.solarthing.program.JarUtil;
import me.retrodaredevil.solarthing.util.JacksonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class AnalyticsManager {
	/*
	For future self setting this up again: in the View Settings in Google Analytics, you must have "Bot Filtering" turned off.
	 */
	private static final ObjectMapper MAPPER = JacksonUtil.defaultMapper();
	private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsManager.class);
	private final GoogleAnalytics googleAnalytics;

	public AnalyticsManager(boolean isEnabled, Path dataDirectory) {
		if (isEnabled) {
			LOGGER.info(SolarThingConstants.SUMMARY_MARKER, "Google Analytics is ENABLED!");
			Path file = dataDirectory.resolve("analytics_data.json");
			AnalyticsData analyticsData = null;
			try {
				analyticsData = MAPPER.readValue(Files.newInputStream(file), AnalyticsData.class);
			} catch (IOException e) {
				LOGGER.debug(SolarThingConstants.NO_CONSOLE, "Couldn't read analytics data, but that's OK!", e);
			}
			if (analyticsData == null) {
				analyticsData = new AnalyticsData(UUID.randomUUID());
				LOGGER.info(SolarThingConstants.SUMMARY_MARKER, "Generated a new Analytics UUID");
				try {
					MAPPER.writeValue(Files.newOutputStream(file), analyticsData);
				} catch (IOException e) {
					LOGGER.warn(SolarThingConstants.SUMMARY_MARKER, "Couldn't save analytics data!", e);
				}
			}
			final String clientId = analyticsData.uuid.toString();
			LOGGER.debug("Using Analytics UUID: " + clientId);
			googleAnalytics = GoogleAnalytics.builder()
					.withConfig(new GoogleAnalyticsConfig()
							.setThreadTimeoutSecs(5)
					)
					.withDefaultRequest(new DefaultRequest()
							.applicationName("solarthing-program")
							.applicationVersion("V-UNKNOWN")
							.clientId(clientId)
					)
					.withTrackingId("UA-70767765-2")
					.build();
		} else {
			// TODO when the --validate argument is present, this is always printed. Think about if we want to let the user know what the actual configuration is
			LOGGER.info(SolarThingConstants.SUMMARY_MARKER, "Google Analytics is disabled");
			googleAnalytics = null;
		}
	}

	public void sendStartUp(ProgramType programType) {
		if (googleAnalytics != null) {
			LOGGER.info("Sending program type to Google Analytics");
			googleAnalytics.screenView()
					.screenName(programType.getName())
					.sendAsync();
			googleAnalytics.event()
					.eventCategory("startup")
					.eventAction(programType.getName())
					.eventLabel(JarUtil.getJarFileName()) // so we know what version they're running
					.sendAsync();
		}
	}
	public void sendMateStatus(String data, int uptimeHours) {
		if (googleAnalytics != null) {
			LOGGER.info("Sending Mate status to Google Analytics. data=" + data + " uptime hours=" + uptimeHours);
			googleAnalytics.event()
					.eventCategory("status")
					.eventAction("mate")
					.eventLabel(data)
					.eventValue(uptimeHours)
					.sendAsync();
		}
	}
	public void sendRoverStatus(String data, int uptimeHours) {
		if (googleAnalytics != null) {
			LOGGER.info("Sending Rover status to Google Analytics. data=" + data + " uptime hours=" + uptimeHours);
			googleAnalytics.event()
					.eventCategory("status")
					.eventAction("rover")
					.eventLabel(data)
					.eventValue(uptimeHours)
					.sendAsync();
		}
	}
	private static final class AnalyticsData {
		@JsonProperty("uuid")
		private final UUID uuid;

		@JsonCreator
		private AnalyticsData(@JsonProperty(value = "uuid", required = true) UUID uuid) {
			this.uuid = uuid;
		}
	}
}
