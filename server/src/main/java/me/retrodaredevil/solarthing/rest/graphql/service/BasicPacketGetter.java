package me.retrodaredevil.solarthing.rest.graphql.service;

import me.retrodaredevil.solarthing.annotations.NotNull;
import me.retrodaredevil.solarthing.rest.graphql.packets.PacketFilter;
import me.retrodaredevil.solarthing.rest.graphql.packets.nodes.PacketNode;
import me.retrodaredevil.solarthing.rest.graphql.packets.PacketUtil;
import me.retrodaredevil.solarthing.packets.collection.FragmentedPacketGroup;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class BasicPacketGetter implements PacketGetter {
	private final List<? extends FragmentedPacketGroup> packets;
	private final PacketFilter packetFilter;

	public BasicPacketGetter(List<? extends FragmentedPacketGroup> packets, PacketFilter packetFilter) {
		requireNonNull(this.packets = packets);
		requireNonNull(this.packetFilter = packetFilter);
	}
	@Override
	public <T> @NotNull List<@NotNull PacketNode<T>> getPackets(Class<T> clazz) {
		return PacketUtil.convertPackets(packets, clazz, packetFilter);
	}
}
