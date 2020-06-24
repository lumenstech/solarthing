package me.retrodaredevil.solarthing.graphql.packets;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.retrodaredevil.solarthing.annotations.JsonExplicit;
import me.retrodaredevil.solarthing.annotations.Nullable;

import me.retrodaredevil.solarthing.annotations.NotNull;

import static java.util.Objects.requireNonNull;

@JsonExplicit
public final class PacketNode<T> implements SimplePacketNode {
	private final T packet;
	private final long dateMillis;
	private final String sourceId;
	private final int fragmentId;

	public PacketNode(@NotNull T packet, long dateMillis, @NotNull String sourceId, int fragmentId) {
		requireNonNull(this.packet = packet);
		this.dateMillis = dateMillis;
		requireNonNull(this.sourceId = sourceId);
		this.fragmentId = fragmentId;
	}

	@Override
	public @NotNull T getPacket() {
		return packet;
	}

	@Override
	public long getDateMillis() {
		return dateMillis;
	}

	@Override
	public @NotNull String getSourceId() {
		return sourceId;
	}

	@Override
	public int getFragmentId() {
		return fragmentId;
	}

	/**
	 * The GraphQL datasource takes all numeric values, so if you don't want it to automatically take the fragmentId and use it,
	 * you can call this instead to get a string representation of it.
	 * @return String representation of fragmentId
	 */
	@JsonProperty("fragmentIdString")
	public @NotNull String getFragmentIdString() {
		return "" + getFragmentId();
	}

	@Override
	public String toString() {
		return "PacketNode(" +
				"packet=" + packet +
				", dateMillis=" + dateMillis +
				", sourceId='" + sourceId + '\'' +
				", fragmentId=" + fragmentId +
				')';
	}
}
