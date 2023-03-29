package me.retrodaredevil.solarthing.rest.graphql.packets;

import me.retrodaredevil.solarthing.rest.graphql.packets.nodes.PacketNode;

import java.util.Collection;
import java.util.List;

public class PacketFilterMultiplexer implements PacketFilter {
	private final List<PacketFilter> packetFilterList;

	public PacketFilterMultiplexer(Collection<? extends PacketFilter> packetFilterList) {
		this.packetFilterList = List.copyOf(packetFilterList);
	}

	@Override
	public boolean keep(PacketNode<?> packetNode) {
		for (PacketFilter filter : packetFilterList) {
			if (!filter.keep(packetNode)) {
				return false;
			}
		}
		return true;
	}
}
