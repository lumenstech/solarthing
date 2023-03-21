package me.retrodaredevil.solarthing.rest.graphql.service;

import me.retrodaredevil.solarthing.annotations.NotNull;
import me.retrodaredevil.solarthing.rest.graphql.packets.nodes.PacketNode;

import java.util.List;

public interface PacketGetter {
	/**
	 *
	 * @return A mutable list of {@link PacketNode}s.
	 */
	<T> @NotNull List<@NotNull PacketNode<T>> getPackets(Class<T> clazz);
}
