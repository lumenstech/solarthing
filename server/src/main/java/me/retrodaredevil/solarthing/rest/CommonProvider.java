package me.retrodaredevil.solarthing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.retrodaredevil.solarthing.annotations.Nullable;
import me.retrodaredevil.solarthing.config.databases.DatabaseSettings;
import me.retrodaredevil.solarthing.config.databases.implementations.CouchDbDatabaseSettings;
import me.retrodaredevil.solarthing.packets.collection.DefaultInstanceOptions;
import me.retrodaredevil.solarthing.packets.instance.InstanceSourcePacket;
import me.retrodaredevil.solarthing.program.DatabaseConfig;
import me.retrodaredevil.solarthing.util.JacksonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

@Component
public class CommonProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(CommonProvider.class);

	@Value("${solarthing.config.database}")
	private File databaseFile;
	@Value("${solarthing.config.default_source:#{null}}")
	private @Nullable String defaultSourceId;
	@Value("${solarthing.config.default_fragment:#{null}}")
	private @Nullable Integer defaultFragmentId;

	private DefaultInstanceOptions defaultInstanceOptions;
	private CouchDbDatabaseSettings couchDbDatabaseSettings;


	private String getDefaultSourceId() {
		String r = defaultSourceId;
		if (r == null) {
			return InstanceSourcePacket.UNUSED_SOURCE_ID;
		}
		return r;
	}

	private int getDefaultFragmentId() {
		// The fact that we use the default DefaultInstanceOptions is not a big deal here. All new data should always have instance data,
		//   and it's unlikely that someone (WMF) will go back far enough to find data that doesn't have instance data. (The only database this can happen on
		//   is the WMF one, and I already configured this on its GraphQL program, so it's good)
		return Objects.requireNonNullElseGet(defaultFragmentId, DefaultInstanceOptions.DEFAULT_DEFAULT_INSTANCE_OPTIONS::getDefaultFragmentId);
	}

	@PostConstruct
	public void init() {
		defaultInstanceOptions = DefaultInstanceOptions.create(getDefaultSourceId(), getDefaultFragmentId());
		LOGGER.debug("Using defaultInstanceOptions=" + defaultInstanceOptions);
		LOGGER.debug("Database file: " + databaseFile.getAbsolutePath());
		LOGGER.debug("Working directory: " + new File(".").getAbsolutePath());

		ObjectMapper objectMapper = JacksonUtil.defaultMapper();
		objectMapper.getSubtypeResolver().registerSubtypes(
				DatabaseSettings.class,
				CouchDbDatabaseSettings.class
		);
		final FileInputStream reader;
		try {
			reader = new FileInputStream(databaseFile);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		final DatabaseConfig databaseConfig;
		try {
			databaseConfig = objectMapper.readValue(reader, DatabaseConfig.class);
		} catch (IOException e) {
			throw new RuntimeException("Couldn't parse data!", e);
		}
		DatabaseSettings databaseSettings = databaseConfig.getSettings();
		if(!(databaseSettings instanceof CouchDbDatabaseSettings)) {
			throw new UnsupportedOperationException("Only CouchDB is supported right now!");
		}
		couchDbDatabaseSettings = (CouchDbDatabaseSettings) databaseSettings;
	}

	@Bean
	public DefaultInstanceOptions defaultInstanceOptions() {
		return defaultInstanceOptions;
	}

	@Bean
	public CouchDbDatabaseSettings couchDbDatabaseSettings() {
		return couchDbDatabaseSettings;
	}

}
