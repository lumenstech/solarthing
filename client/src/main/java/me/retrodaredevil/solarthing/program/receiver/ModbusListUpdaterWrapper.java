package me.retrodaredevil.solarthing.program.receiver;

import me.retrodaredevil.io.modbus.ModbusMessage;
import me.retrodaredevil.io.modbus.ModbusRuntimeException;
import me.retrodaredevil.io.modbus.ModbusTimeoutException;
import me.retrodaredevil.io.modbus.handling.ErrorCodeException;
import me.retrodaredevil.io.modbus.handling.ParsedResponseException;
import me.retrodaredevil.io.modbus.handling.RawResponseException;
import me.retrodaredevil.solarthing.SolarThingConstants;
import me.retrodaredevil.solarthing.config.request.modbus.SuccessReporter;
import me.retrodaredevil.solarthing.io.NotInitializedIOException;
import me.retrodaredevil.solarthing.misc.error.ImmutableExceptionErrorPacket;
import me.retrodaredevil.solarthing.packets.Packet;
import me.retrodaredevil.solarthing.packets.handling.PacketListReceiver;
import me.retrodaredevil.solarthing.solar.renogy.rover.modbus.ExceptionCodeError;
import me.retrodaredevil.solarthing.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class ModbusListUpdaterWrapper implements PacketListReceiver {
	private static final Logger LOGGER = LoggerFactory.getLogger(ModbusListUpdaterWrapper.class);
	private static final String MODBUS_RUNTIME_EXCEPTION_CATCH_LOCATION_IDENTIFIER = "read.modbus";

	private final LogType logType;
	private final PacketListReceiver packetListReceiver;
	private final Runnable reloadCache;
	private final SuccessReporter successReporter;

	private final boolean isSendErrorPackets;
	private final String errorIdentifierString;

	private final Set<Feature> extraFeatures;

	private boolean hasBeenSuccessful = false;

	public ModbusListUpdaterWrapper(LogType logType, PacketListReceiver packetListReceiver, Runnable reloadCache, SuccessReporter successReporter, boolean isSendErrorPackets, String errorIdentifierString, Set<Feature> extraFeatures) {
		this.logType = logType;
		this.packetListReceiver = packetListReceiver;
		this.reloadCache = reloadCache;
		this.successReporter = successReporter;
		this.isSendErrorPackets = isSendErrorPackets;
		this.errorIdentifierString = errorIdentifierString;
		this.extraFeatures = extraFeatures;
	}
	private static String dataToSplitHex(byte[] data) {
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (byte element : data) {
			if (first) {
				first = false;
			} else {
				builder.append(' '); // separate each byte with space
			}
			builder.append(String.format("%02X", element & 0xFF));
		}
		return builder.toString();
	}

	@Override
	public void receive(List<Packet> packets) {
		final long startTimeNanos = System.nanoTime();
		try {
			reloadCache.run();
			packetListReceiver.receive(packets);
		} catch(ModbusRuntimeException e){

			if (isSendErrorPackets) {
				LOGGER.debug("Sending error packets");
				packets.add(new ImmutableExceptionErrorPacket(
						e.getClass().getName(),
						e.getMessage(),
						MODBUS_RUNTIME_EXCEPTION_CATCH_LOCATION_IDENTIFIER,
						errorIdentifierString
				));
			}
			boolean isTimeout = false;
			if (e.getCause() instanceof NotInitializedIOException) {
				isTimeout = true;
			}
			if (e instanceof ModbusTimeoutException) {
				isTimeout = true;
				// These messages will hopefully help people with problems fix it faster.
				if (hasBeenSuccessful) {
					LOGGER.debug(SolarThingConstants.NO_REMOTE, "\n\nHey! We noticed you got a ModbusTimeoutException after getting this to work.\n" +
							"This is likely a fluke and hopefully this message isn't printed a bunch of times. If it is not a fluke, you may want to check your cable.\n");
				} else {
					LOGGER.info(SolarThingConstants.NO_REMOTE, "\n\nHey! We noticed you got a ModbusTimeoutException.\n" +
							"This is likely a problem with your cable. SolarThing is communicating fine with your serial adapter, but it cannot reach the device.\n" +
							"Make sure the cable you have has the correct pinout, and feel free to open an issue at https://github.com/wildmountainfarms/solarthing/issues if you need help.\n");
				}
				if (extraFeatures.contains(Feature.DEBUG_MODBUS_TIMEOUT)) {
					LOGGER.debug("Got a modbus timeout. Message: " + e.getMessage()); // we don't need to log the stacktrace, so the exception is not logged
				} else {
					LOGGER.error("Got a modbus timeout. Message: " + e.getMessage()); // we don't need to log the stacktrace, so the exception is not logged
				}
			} else if (e instanceof ParsedResponseException) {
				// we don't need to log the stacktrace here as long as we log the message of the error
				ParsedResponseException parsedResponseException = (ParsedResponseException) e;
				ModbusMessage message = parsedResponseException.getResponse();
				String hexFunctionCode = String.format("%02X", message.getFunctionCode());
				LOGGER.info("Communication with device working well. Got this response back: function code=0x" + hexFunctionCode + " data='" + dataToSplitHex(message.getByteData()) + "' feel free to open issue at https://github.com/wildmountainfarms/solarthing/issues/");
				LOGGER.error("Modbus parsed response exception: " + e.getMessage());
				if (logType == LogType.ROVER) {
					if (e instanceof ErrorCodeException) {
						int code = ((ErrorCodeException) e).getExceptionCode();
						ExceptionCodeError error = ExceptionCodeError.fromCodeOrNull(code);
						if (error == ExceptionCodeError.READ_EXCEPTION_UNSUPPORTED_FUNCTION_CODE) {
							LOGGER.error(SolarThingConstants.SUMMARY_MARKER, "Got unsupported function code error. This should never happen.");
						} else if (error == ExceptionCodeError.READ_EXCEPTION_UNSUPPORTED_REGISTER) {
							LOGGER.info("This error is common on models of charge controllers that are unsupported by SolarThing. When you create your issue, please include the model of your charge controller.");
						} else if (error == ExceptionCodeError.READ_EXCEPTION_TOO_MANY_REGISTERS_TO_READ) {
							LOGGER.error(SolarThingConstants.SUMMARY_MARKER, "This error should never happen because we never request to read too many registers. Please report this.");
						} else if (error == ExceptionCodeError.READ_EXCEPTION_CANNOT_READ_MULTIPLE_REGISTERS) {
							LOGGER.warn(SolarThingConstants.SUMMARY_MARKER, "This error is unexpected. To attempt to fix this yourself, please visit: https://solarthing.readthedocs.io/en/latest/config/file/base-json/request/modbus/rover/bulk-request.html to disable bulk request. Please report this anyway, as it should never happen.");
						}
					}
				}
			} else if (e instanceof RawResponseException) {
				byte[] data = ((RawResponseException) e).getRawData();
				LOGGER.info("Got part of a response back. (Maybe timed out halfway through?) data='" + dataToSplitHex(data) + "' Feel free to open an issue at https://github.com/wildmountainfarms/solarthing/issues/", e);
			} else {
				LOGGER.error("Modbus exception", e);
			}

			LOGGER.trace("Tracing the modbus runtime exception", e); // good chance that we did not actually log the stacktrace, so let's put a trace here for the worst case scenario of us needing to turn this on

			if (isTimeout) {
				successReporter.reportTimeout();
			} else {
				successReporter.reportSuccessWithError();
			}
			return;
		}
		hasBeenSuccessful = true;
		successReporter.reportSuccess();
		final long readDurationNanos = System.nanoTime() - startTimeNanos;
		LOGGER.debug("took " + TimeUtil.nanosToSecondsString(readDurationNanos) + " seconds to read from device");
	}

	/**
	 * Helps this class understand what type of device is being handled. This helps with log debug information
	 */
	public enum LogType {
		ROVER,
		TRACER,
	}
	public enum Feature {
		/** A feature that allows the level of modbus exceptions to be DEBUG only if it was a timeout modbus exception*/
		DEBUG_MODBUS_TIMEOUT,
	}
}
