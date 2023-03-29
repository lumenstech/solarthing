package me.retrodaredevil.solarthing.rest.graphql.service;

import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLQuery;
import me.retrodaredevil.solarthing.annotations.NotNull;
import me.retrodaredevil.solarthing.annotations.Nullable;
import me.retrodaredevil.solarthing.type.cache.packets.IdentificationCacheDataPacket;
import me.retrodaredevil.solarthing.type.cache.packets.data.ChargeControllerAccumulationDataCache;
import me.retrodaredevil.solarthing.rest.cache.CacheController;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static me.retrodaredevil.solarthing.rest.graphql.service.SchemaConstants.*;

public class SolarThingGraphQLLongTermService {

	private final CacheController cacheController;
	private final ZoneId zoneId;

	public SolarThingGraphQLLongTermService(CacheController cacheController, ZoneId zoneId) {
		this.cacheController = cacheController;
		this.zoneId = zoneId;
	}

	@GraphQLQuery
	public SolarThingLongTermQuery queryLongTermMillis(
			@GraphQLArgument(name = "from", description = DESCRIPTION_FROM) long from, @GraphQLArgument(name = "to", description = DESCRIPTION_TO) long to,
			@GraphQLArgument(name = "sourceId", description = DESCRIPTION_OPTIONAL_SOURCE) @Nullable String sourceId){
		return new SolarThingLongTermQuery(sourceId, from, to);
	}
	@GraphQLQuery
	public SolarThingLongTermQuery queryLongTermMonth(
			@GraphQLArgument(name = "year") int year,
			@GraphQLArgument(name = "month") Month month,
			@GraphQLArgument(name = "sourceId", description = DESCRIPTION_OPTIONAL_SOURCE) @Nullable String sourceId){
		YearMonth yearMonth = YearMonth.of(year, month);
		LocalDate start = LocalDate.of(year, month, 1);
		LocalDate end = yearMonth.atEndOfMonth();
		return queryLongTermMillis(
				start.atStartOfDay(zoneId).toInstant().toEpochMilli(),
				end.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1,
				sourceId
		);
	}
	public class SolarThingLongTermQuery {
		private final String sourceId;
		private final long startMillis;
		private final long endMillis;

		public SolarThingLongTermQuery(String sourceId, long startMillis, long endMillis) {
			this.sourceId = sourceId;
			this.startMillis = startMillis;
			this.endMillis = endMillis;
		}

		@GraphQLQuery
		public @NotNull List<@NotNull IdentificationCacheDataPacket<ChargeControllerAccumulationDataCache>> chargeControllerAccumulationRaw(){
			return cacheController.getChargeControllerAccumulation(sourceId, startMillis, endMillis);
		}
		@GraphQLQuery
		public @Nullable IdentificationCacheDataPacket<ChargeControllerAccumulationDataCache> chargeControllerAccumulation() {
			List<IdentificationCacheDataPacket<ChargeControllerAccumulationDataCache>> raw = chargeControllerAccumulationRaw();
			if (raw.isEmpty()) {
				return null;
			}
			IdentificationCacheDataPacket<ChargeControllerAccumulationDataCache> result = null;
			for (IdentificationCacheDataPacket<ChargeControllerAccumulationDataCache> packet : raw) {
				if (result == null) {
					result = packet;
				} else {
					result = result.combine(packet);
				}
			}
			return result;
		}
		// TODO percent
		// TODO total

		public float acUseTimeHours() {
			throw new UnsupportedOperationException("TODO");
		}
		public float fxInverterKWH() {
			throw new UnsupportedOperationException("TODO");
		}
		public float fxChargerKWH() {
			throw new UnsupportedOperationException("TODO");
		}
		public float fxBuyKWH() {
			throw new UnsupportedOperationException("TODO");
		}
		public float fxSellKWH() {
			throw new UnsupportedOperationException("TODO");
		}
	}
}
