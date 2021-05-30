package me.retrodaredevil.solarthing.program;

import me.retrodaredevil.solarthing.InstantType;
import me.retrodaredevil.solarthing.SolarThingConstants;
import me.retrodaredevil.solarthing.packets.Packet;
import me.retrodaredevil.solarthing.packets.handling.PacketListReceiver;
import me.retrodaredevil.solarthing.solar.renogy.rover.RoverReadTable;
import me.retrodaredevil.solarthing.solar.renogy.rover.RoverStatusPacket;
import me.retrodaredevil.solarthing.solar.renogy.rover.RoverStatusPackets;
import me.retrodaredevil.solarthing.solar.renogy.rover.RoverWriteTable;
import me.retrodaredevil.solarthing.solar.renogy.rover.special.SpecialPowerControl_E02D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RoverPacketListUpdater implements PacketListReceiver {
	private static final Logger LOGGER = LoggerFactory.getLogger(RoverPacketListUpdater.class);

	private final int number;
	private final RoverReadTable read;
	private final RoverWriteTable write;

	public RoverPacketListUpdater(int number, RoverReadTable read, RoverWriteTable write) {
		this.number = number;
		this.read = read;
		this.write = write;
	}

	@Override
	public void receive(List<Packet> packets, InstantType instantType) {
		RoverStatusPacket packet = RoverStatusPackets.createFromReadTable(number, read);
		SpecialPowerControl_E02D specialPower2 = packet.getSpecialPowerControlE02D();
		LOGGER.debug(SolarThingConstants.NO_CONSOLE, "Debugging special power control values: (Will debug all packets later)\n" +
				packet.getSpecialPowerControlE021().getFormattedInfo().replaceAll("\n", "\n\t") +
				(specialPower2 == null ? "" : "\n" + specialPower2.getFormattedInfo().replaceAll("\n", "\n\t"))
		);
		packets.add(packet);
	}
}
