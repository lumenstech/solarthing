package me.retrodaredevil.solarthing.config.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import me.retrodaredevil.io.IOBundle;
import me.retrodaredevil.io.modbus.*;
import me.retrodaredevil.solarthing.annotations.JsonExplicit;
import me.retrodaredevil.solarthing.program.ConfigUtil;
import me.retrodaredevil.solarthing.program.receiver.PzemShuntPacketListUpdater;
import me.retrodaredevil.solarthing.solar.pzem.PzemShuntReadTable;
import me.retrodaredevil.solarthing.solar.pzem.modbus.PzemShuntModbusSlaveRead;

import java.nio.file.Path;

@JsonTypeName("pzem")
@JsonExplicit
public class PzemShuntDataRequester implements DataRequester {
	private final Path ioBundleFile;
	private final int dataId;
	private final int modbusAddress;

	@JsonCreator
	public PzemShuntDataRequester(
			@JsonProperty(value = "io", required = true) Path ioBundleFile,
			@JsonProperty(value = "data_id", required = true) int dataId,
			@JsonProperty(value = "modbus", required = true) int modbusAddress) {
		this.ioBundleFile = ioBundleFile;
		this.dataId = dataId;
		this.modbusAddress = modbusAddress;
	}

	@Override
	public DataRequesterResult create(RequestObject requestObject) {
		final IOBundle ioBundle = ConfigUtil.createIOBundle(ioBundleFile, PzemShuntReadTable.SERIAL_CONFIG);
		ModbusSlaveBus bus = new IOModbusSlaveBus(ioBundle, new RtuDataEncoder());
		ModbusSlave slave = new ImmutableAddressModbusSlave(modbusAddress, bus);
		return DataRequesterResult.builder()
				.statusPacketListReceiver(new PzemShuntPacketListUpdater(dataId, modbusAddress, new PzemShuntModbusSlaveRead(slave)))
				.build();
	}
}
