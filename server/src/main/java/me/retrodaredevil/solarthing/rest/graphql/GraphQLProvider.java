package me.retrodaredevil.solarthing.rest.graphql;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.leangen.graphql.GraphQLSchemaGenerator;
import io.leangen.graphql.generator.mapping.common.NonNullMapper;
import io.leangen.graphql.metadata.strategy.query.ResolverBuilder;
import io.leangen.graphql.metadata.strategy.value.jackson.JacksonValueMapperFactory;
import me.retrodaredevil.solarthing.annotations.NotNull;
import me.retrodaredevil.solarthing.config.databases.implementations.CouchDbDatabaseSettings;
import me.retrodaredevil.solarthing.packets.collection.DefaultInstanceOptions;
import me.retrodaredevil.solarthing.rest.cache.CacheController;
import me.retrodaredevil.solarthing.rest.graphql.service.SolarThingGraphQLAlterService;
import me.retrodaredevil.solarthing.rest.graphql.service.SolarThingGraphQLBatteryRecordService;
import me.retrodaredevil.solarthing.rest.graphql.service.SolarThingGraphQLDailyService;
import me.retrodaredevil.solarthing.rest.graphql.service.SolarThingGraphQLFXService;
import me.retrodaredevil.solarthing.rest.graphql.service.SolarThingGraphQLLongTermService;
import me.retrodaredevil.solarthing.rest.graphql.service.SolarThingGraphQLService;
import me.retrodaredevil.solarthing.rest.graphql.service.SolarThingGraphQLSolcastService;
import me.retrodaredevil.solarthing.rest.graphql.service.web.DefaultDatabaseProvider;
import me.retrodaredevil.solarthing.rest.graphql.service.web.SolarThingAdminService;
import me.retrodaredevil.solarthing.rest.graphql.solcast.SolcastConfig;
import me.retrodaredevil.solarthing.util.JacksonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
public class GraphQLProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLProvider.class);

	private final CouchDbDatabaseSettings couchDbDatabaseSettings;
	private final DefaultInstanceOptions defaultInstanceOptions;
	private final CacheController cacheController;

	@Value("${solarthing.config.solcast_file:config/solcast.json}")
	private File solcastFile;

	private GraphQL graphQL;

	public GraphQLProvider(CouchDbDatabaseSettings couchDbDatabaseSettings, DefaultInstanceOptions defaultInstanceOptions, CacheController cacheController) {
		this.couchDbDatabaseSettings = couchDbDatabaseSettings;
		this.defaultInstanceOptions = defaultInstanceOptions;
		this.cacheController = cacheController;
	}


	@Deprecated(forRemoval = true)
	static void updateNonNull() throws NoSuchFieldException, IllegalAccessException {
		// more info here: https://github.com/leangen/graphql-spqr/issues/334
		Field field = NonNullMapper.class.getDeclaredField("COMMON_NON_NULL_ANNOTATIONS");
		field.setAccessible(true);

		Field modifiersField = Field.class.getDeclaredField("modifiers");
		modifiersField.setAccessible(true);
		modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

		String[] nonNullAnnotations = (String[]) field.get(null);
		String[] newAnnotations = Arrays.copyOf(nonNullAnnotations, nonNullAnnotations.length + 1);
		newAnnotations[newAnnotations.length - 1] = NotNull.class.getName();
		field.set(null, newAnnotations);
	}
	@SuppressWarnings("unchecked")
	static void updateNonNull(NonNullMapper nonNullMapper) {
		final Field field;
		try {
			field = NonNullMapper.class.getDeclaredField("nonNullAnnotations");
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
		field.setAccessible(true);
		Set<Class<? extends Annotation>> nonNullAnnotations;
		try {
			nonNullAnnotations = (Set<Class<? extends Annotation>>) field.get(nonNullMapper);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		Set<Class<? extends Annotation>> newAnnotations = new HashSet<>(nonNullAnnotations);
		newAnnotations.add(NotNull.class);
		try {
			field.set(nonNullMapper, newAnnotations);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@PostConstruct
	public void init() {
		ObjectMapper objectMapper = JacksonUtil.defaultMapper();

		SolcastConfig solcastConfig = null;
		try {
			solcastConfig = objectMapper.readValue(solcastFile, SolcastConfig.class);
		} catch (JsonParseException | JsonMappingException e) {
			throw new RuntimeException("Bad solcast JSON!", e);
		} catch (IOException e) {
			LOGGER.debug("No solcast config! Not using solcast!");
		}
		if (solcastConfig == null) {
			solcastConfig = new SolcastConfig(Collections.emptyMap());
		}

		GraphQLSchema schema = createGraphQLSchemaGenerator(objectMapper, couchDbDatabaseSettings, defaultInstanceOptions, solcastConfig, cacheController).generate();

		this.graphQL = GraphQL.newGraphQL(schema)
				.defaultDataFetcherExceptionHandler(new SolarThingExceptionHandler())
				.build();
	}

	static GraphQLSchemaGenerator createGraphQLSchemaGenerator(ObjectMapper objectMapper, CouchDbDatabaseSettings couchDbDatabaseSettings, DefaultInstanceOptions defaultInstanceOptions, @NotNull SolcastConfig solcastConfig, CacheController cacheController) {
		JacksonValueMapperFactory jacksonValueMapperFactory = JacksonValueMapperFactory.builder()
				.withPrototype(objectMapper)
				.build();
		ResolverBuilder resolverBuilder = new SolarThingAnnotatedResolverBuilder();
		SimpleQueryHandler simpleQueryHandler = new SimpleQueryHandler(defaultInstanceOptions, couchDbDatabaseSettings, objectMapper);
		ZoneId zoneId = ZoneId.systemDefault(); // In the future, we could make this customizable, but like, bro just make sure your system time is correct
		LOGGER.debug("Using timezone: " + zoneId);
		return new GraphQLSchemaGenerator()
				.withBasePackages("me.retrodaredevil.solarthing")
				.withOperationsFromSingleton(new SolarThingGraphQLService(simpleQueryHandler))
				.withOperationsFromSingleton(new SolarThingGraphQLDailyService(simpleQueryHandler, zoneId, cacheController))
				.withOperationsFromSingleton(new SolarThingGraphQLBatteryRecordService(simpleQueryHandler, cacheController))
				.withOperationsFromSingleton(new SolarThingGraphQLLongTermService(cacheController, zoneId))
				.withOperationsFromSingleton(new SolarThingGraphQLMetaService(simpleQueryHandler))
				.withOperationsFromSingleton(new SolarThingGraphQLExtensions())
				.withOperationsFromSingleton(new SolarThingGraphQLFXService(simpleQueryHandler))
				.withOperationsFromSingleton(new SolarThingGraphQLSolcastService(solcastConfig, zoneId, cacheController))
				.withOperationsFromSingleton(new SolarThingGraphQLAlterService(simpleQueryHandler))
				.withOperationsFromSingleton(new SolarThingAdminService(new DefaultDatabaseProvider(couchDbDatabaseSettings, objectMapper)))
				.withTypeMappers((config, defaults) -> defaults.modify(NonNullMapper.class, GraphQLProvider::updateNonNull))
				.withTypeInfoGenerator(new SolarThingTypeInfoGenerator())
				.withValueMapperFactory(jacksonValueMapperFactory)
				.withResolverBuilders(resolverBuilder)
				.withNestedResolverBuilders(
						resolverBuilder,
						new JacksonResolverBuilder().withObjectMapper(objectMapper),
						new SolarThingResolverBuilder()
				);
	}


	@Bean
	public GraphQL graphQL() {
		return graphQL;
	}

}
