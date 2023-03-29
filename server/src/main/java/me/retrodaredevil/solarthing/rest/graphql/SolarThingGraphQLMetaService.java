package me.retrodaredevil.solarthing.rest.graphql;

import io.leangen.graphql.annotations.GraphQLContext;
import io.leangen.graphql.annotations.GraphQLQuery;
import me.retrodaredevil.solarthing.annotations.Nullable;
import me.retrodaredevil.solarthing.rest.graphql.packets.nodes.PacketNode;
import me.retrodaredevil.solarthing.rest.graphql.packets.nodes.SimplePacketNode;
import me.retrodaredevil.solarthing.type.closed.meta.DeviceInfoPacket;
import me.retrodaredevil.solarthing.type.closed.meta.MetaDatabase;
import me.retrodaredevil.solarthing.type.closed.meta.TargetedMetaPacket;
import me.retrodaredevil.solarthing.misc.common.DataIdentifiable;
import me.retrodaredevil.solarthing.misc.common.meta.DataMetaPacket;
import me.retrodaredevil.solarthing.misc.weather.TemperaturePacket;

public class SolarThingGraphQLMetaService {
	private final SimpleQueryHandler simpleQueryHandler;

	public SolarThingGraphQLMetaService(SimpleQueryHandler simpleQueryHandler) {
		this.simpleQueryHandler = simpleQueryHandler;
	}
	@GraphQLQuery(name = "meta")
	public @Nullable DataMetaPacket getMetaTemperaturePacket(@GraphQLContext PacketNode<TemperaturePacket> packetNode){
		return getMeta(packetNode);
	}

	private @Nullable DataMetaPacket getMeta(PacketNode<? extends DataIdentifiable> packetNode) {
		int fragmentId = packetNode.getFragmentId();
		int dataId = packetNode.getPacket().getDataId();
		MetaDatabase metaDatabase = simpleQueryHandler.queryMeta();
		for (TargetedMetaPacket targetedMetaPacket : metaDatabase.getMeta(packetNode.getDateMillis(), fragmentId)) {
			if (targetedMetaPacket instanceof DataMetaPacket dataMetaPacket) {
				if (dataMetaPacket.getDataId() == dataId) {
					return dataMetaPacket;
				}
			}
		}
		return null;
	}

	@GraphQLQuery(name = "fragmentDeviceInfo")
	public @Nullable DeviceInfoPacket getFragmentDeviceInfo(@GraphQLContext SimplePacketNode packetNode){
		int fragmentId = packetNode.getFragmentId();
		MetaDatabase metaDatabase = simpleQueryHandler.queryMeta();
		for (TargetedMetaPacket targetedMetaPacket : metaDatabase.getMeta(packetNode.getDateMillis(), fragmentId)) {
			if (targetedMetaPacket instanceof DeviceInfoPacket) {
				return (DeviceInfoPacket) targetedMetaPacket;
			}
		}
		return null;
	}
}
