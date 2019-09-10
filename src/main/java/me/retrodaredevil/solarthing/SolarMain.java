package me.retrodaredevil.solarthing;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.Cli;
import com.lexicalscope.jewel.cli.CliFactory;
import me.retrodaredevil.couchdb.CouchProperties;
import me.retrodaredevil.io.IOBundle;
import me.retrodaredevil.io.modbus.*;
import me.retrodaredevil.io.serial.JSerialIOBundle;
import me.retrodaredevil.io.serial.SerialConfig;
import me.retrodaredevil.io.serial.SerialConfigBuilder;
import me.retrodaredevil.io.serial.SerialPortException;
import me.retrodaredevil.solarthing.commands.CommandProvider;
import me.retrodaredevil.solarthing.commands.CommandProviderMultiplexer;
import me.retrodaredevil.solarthing.commands.sequence.CommandSequence;
import me.retrodaredevil.solarthing.config.JsonCouchDb;
import me.retrodaredevil.solarthing.config.options.*;
import me.retrodaredevil.solarthing.couchdb.CouchDbPacketRetriever;
import me.retrodaredevil.solarthing.couchdb.CouchDbPacketSaver;
import me.retrodaredevil.solarthing.outhouse.OuthousePacketCreator;
import me.retrodaredevil.solarthing.packets.Packet;
import me.retrodaredevil.solarthing.packets.collection.HourIntervalPacketCollectionIdGenerator;
import me.retrodaredevil.solarthing.packets.collection.PacketCollection;
import me.retrodaredevil.solarthing.packets.collection.PacketCollectionIdGenerator;
import me.retrodaredevil.solarthing.packets.collection.PacketCollections;
import me.retrodaredevil.solarthing.packets.creation.PacketProvider;
import me.retrodaredevil.solarthing.packets.creation.TextPacketCreator;
import me.retrodaredevil.solarthing.packets.handling.*;
import me.retrodaredevil.solarthing.packets.handling.implementations.FileWritePacketHandler;
import me.retrodaredevil.solarthing.packets.handling.implementations.GsonStringPacketHandler;
import me.retrodaredevil.solarthing.packets.instance.InstanceFragmentIndicatorPackets;
import me.retrodaredevil.solarthing.packets.instance.InstanceSourcePackets;
import me.retrodaredevil.solarthing.packets.security.crypto.DirectoryKeyMap;
import me.retrodaredevil.solarthing.solar.outback.MatePacketCreator49;
import me.retrodaredevil.solarthing.solar.outback.command.MateCommand;
import me.retrodaredevil.solarthing.solar.renogy.rover.*;
import me.retrodaredevil.solarthing.solar.renogy.rover.modbus.RoverModbusSlaveRead;
import me.retrodaredevil.solarthing.solar.renogy.rover.modbus.RoverModbusSlaveWrite;
import me.retrodaredevil.util.json.JsonFile;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static java.util.Objects.requireNonNull;

public final class SolarMain {
	private SolarMain(){ throw new UnsupportedOperationException(); }
	private static final SerialConfig MATE_CONFIG = new SerialConfigBuilder(19200)
		.setDataBits(8)
		.setParity(SerialConfig.Parity.NONE)
		.setStopBits(SerialConfig.StopBits.ONE)
		.setDTR(true)
		.build();
	private static final SerialConfig ROVER_CONFIG = new SerialConfigBuilder(9600)
		.setDataBits(8)
		.setParity(SerialConfig.Parity.NONE)
		.setStopBits(SerialConfig.StopBits.ONE)
		.build();
	/*
	TODO This solar command part of this file is not general at all. The command stuff was made for my specific use case and because of that, it would
	be hard for someone else to find use of this program without customizing it to their own needs.
	
	This is one thing we have to think about because while I want it to be easy for my own needs, I also want it to
	be customizable for others as well
	 */
	private static int connectMate(MateProgramOptions options) {
		PacketCollectionIdGenerator idGenerator = createIdGenerator(options.getUniqueIdsInOneHour());
		final InputStream in;
		final OutputStream output;
		if(false){ // TODO if unit test
			in = System.in;
			output = System.out;
		} else {
			final IOBundle port;
			try {
				port = JSerialIOBundle.createPort(null, MATE_CONFIG); // TODO null
			} catch (SerialPortException e) {
				e.printStackTrace();
				return 1;
			}
			in = port.getInputStream();
			output = port.getOutputStream();
		}
		final OnDataReceive onDataReceive;
		List<PacketHandler> packetHandlers = new ArrayList<>();
		if(options.isAllowCommands()) {
			System.out.println("Commands are allowed");
			List<CommandProvider<MateCommand>> commandProviders = new ArrayList<>();
			{ // InputStreamCommandProvider command_input.txt block
				InputStream fileInputStream = null;
				try {
					fileInputStream = new FileInputStream(new File("command_input.txt"));
				} catch (FileNotFoundException e) {
					System.out.println("no command input file!");
				}
				if (fileInputStream != null) {
					commandProviders.add(InputStreamCommandProvider.createFrom(fileInputStream, "command_input.txt", EnumSet.allOf(MateCommand.class)));
				}
			}
			
			final PacketHandler commandRequesterHandler; // The handler to request and get new commands to send (This may block the current thread)
			final PacketHandler commandFeedbackHandler; // The handler to handle successful command packets, usually by storing those packets somewhere (May block the current thread)
			CouchProperties couchProperties = null; // TODO null
			if (couchProperties == null) {
				commandRequesterHandler = PacketHandler.Defaults.HANDLE_NOTHING;
				commandFeedbackHandler = PacketHandler.Defaults.HANDLE_NOTHING;
			} else {
				LatestPacketHandler latestPacketHandler = new LatestPacketHandler(true);
				packetHandlers.add(latestPacketHandler);
				final CommandSequenceDataReceiver<MateCommand> commandSequenceDataReceiver;
				{
					CommandSequence<MateCommand> generatorShutOff = CommandSequences.createAuxGeneratorShutOff(latestPacketHandler::getLatestPacketCollection);
					Map<String, CommandSequence<MateCommand>> map = new HashMap<>();
					map.put("GEN OFF", generatorShutOff);
					commandSequenceDataReceiver = new CommandSequenceDataReceiver<>(map);
				}
				commandProviders.add(commandSequenceDataReceiver.getCommandProvider());
				
				commandRequesterHandler = new ThrottleFactorPacketHandler(new PrintPacketHandleExceptionWrapper(
					new CouchDbPacketRetriever(
						couchProperties,
						"commands",
						new SecurityPacketReceiver(new DirectoryKeyMap(new File("authorized")), commandSequenceDataReceiver, new DirectoryKeyMap(new File("unauthorized"))),
						true
					),
					System.err
				), 4, true); // TODO make throttle factor customizable
				commandFeedbackHandler = new CouchDbPacketSaver(couchProperties, "command_feedback");
			}
			
			Collection<MateCommand> allowedCommands = EnumSet.of(MateCommand.AUX_OFF, MateCommand.AUX_ON, MateCommand.USE, MateCommand.DROP);
			onDataReceive = new MateCommandSender(
				new CommandProviderMultiplexer<>(commandProviders),
				output,
				allowedCommands,
				new OnMateCommandSent(commandFeedbackHandler)
			);
			packetHandlers.add(commandRequesterHandler);
		} else {
			System.out.println("Commands are disabled");
			onDataReceive = OnDataReceive.Defaults.NOTHING;
		}
		
		packetHandlers.addAll(getPacketHandlers(options, "solarthing"));
		
		initReader(
			in,
			new MatePacketCreator49(options.getIgnoreCheckSum()),
			new PacketHandlerMultiplexer(packetHandlers),
			idGenerator,
			250,
			onDataReceive,
			getAdditionalPacketProvider(options)
		);
		return 0;
	}
	private static int connectRover(RoverProgramOptions options){
		List<PacketHandler> packetHandlers = getPacketHandlers(options, "solarthing");
		PacketHandler packetHandler = new PacketHandlerMultiplexer(packetHandlers);
		PacketProvider packetProvider = getAdditionalPacketProvider(options);
		
		PacketCollectionIdGenerator idGenerator = createIdGenerator(options.getUniqueIdsInOneHour());
		try(JSerialIOBundle ioBundle = JSerialIOBundle.createPort(null, ROVER_CONFIG)) { // TODO null
			ModbusSlaveBus modbus = new IOModbusSlaveBus(ioBundle, new RTUDataEncoder(300, 10));
			ModbusSlave slave = new ImmutableAddressModbusSlave(options.getModbusAddress(), modbus);
			RoverReadTable read = new RoverModbusSlaveRead(slave);
			try {
				while (!Thread.currentThread().isInterrupted()) {
					final long startTime = System.currentTimeMillis();
					final RoverStatusPacket packet;
					try {
						packet = RoverStatusPackets.createFromReadTable(read);
					} catch(ModbusRuntimeException e){
						e.printStackTrace();
						System.err.println("Modbus exception above!");
						Thread.sleep(1000);
						continue;
					}
					System.out.println(JsonFile.gson.toJson(packet));
					System.out.println(packet.getSpecialPowerControlE021().getFormattedInfo().replaceAll("\n", "\n\t"));
					System.out.println(packet.getSpecialPowerControlE02D().getFormattedInfo().replaceAll("\n", "\n\t"));
					System.out.println();
					List<Packet> packets = new ArrayList<>();
					packets.add(packet);
					packets.addAll(packetProvider.createPackets());
					PacketCollection packetCollection = PacketCollections.createFromPackets(packets, idGenerator);
					final long readDuration = System.currentTimeMillis() - startTime;
					System.out.println("took " + readDuration + "ms to read from Rover");
					final long saveStartTime = System.currentTimeMillis();
					try {
						packetHandler.handle(packetCollection, true);
					} catch (PacketHandleException e) {
						e.printUnableToHandle(System.err, "Couldn't save a renogy rover packet!");
					}
					final long saveDuration = System.currentTimeMillis() - saveStartTime;
					System.out.println("took " + saveDuration + "ms to handle packets");
					Thread.sleep(Math.max(200, 5000 - readDuration)); // allow 5 seconds to read from rover // assume saveDuration is very small
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		} catch (SerialPortException e) {
			e.printStackTrace();
			System.err.println("Unable to connect to rover");
			return 1;
		}
		return 0;
	}
	private static int connectRoverSetup(RoverSetupProgramOptions options) throws IOException{
		File dummyFile = options.getDummyFile();
		if(dummyFile != null){
			final byte[] bytes = Files.readAllBytes(dummyFile.toPath());
			String json = new String(bytes, StandardCharsets.UTF_8);
			JsonObject jsonPacket = new GsonBuilder().create().fromJson(json, JsonObject.class);
			RoverStatusPacket roverStatusPacket = RoverStatusPackets.createFromJson(jsonPacket);
			DummyRoverReadWrite readWrite = new DummyRoverReadWrite(
				roverStatusPacket,
				(fieldName, previousValue, newValue) -> System.out.println(fieldName + " changed from " + previousValue + " to " + newValue)
			);
			RoverSetupProgram.startRoverSetup(readWrite, readWrite);
		} else {
			try(JSerialIOBundle ioBundle = JSerialIOBundle.createPort(null, ROVER_CONFIG)) { // TODO null
				ModbusSlaveBus modbus = new IOModbusSlaveBus(ioBundle, new RTUDataEncoder(300, 10));
				ModbusSlave slave = new ImmutableAddressModbusSlave(options.getModbusAddress(), modbus);
				RoverReadTable read = new RoverModbusSlaveRead(slave);
				RoverWriteTable write = new RoverModbusSlaveWrite(slave);
				RoverSetupProgram.startRoverSetup(read, write);
			} catch (SerialPortException e) {
				e.printStackTrace();
				System.err.println("Got serial port exception!");
				return 1;
			}
		}
		return 0;
	}
	private static int connectOuthouse(OuthouseProgramOptions options) {
		PacketCollectionIdGenerator idGenerator = createIdGenerator(options.getUniqueIdsInOneHour());
		List<PacketHandler> packetHandlers = getPacketHandlers(options, "outhouse");
		initReader(
			System.in,
			new OuthousePacketCreator(),
			new PacketHandlerMultiplexer(packetHandlers),
			idGenerator,
			10,
			OnDataReceive.Defaults.NOTHING,
			getAdditionalPacketProvider(options)
		);
		return 0;
	}
	
	private static void initReader(InputStream in, TextPacketCreator packetCreator, PacketHandler packetHandler, PacketCollectionIdGenerator idGenerator, long samePacketTime, OnDataReceive onDataReceive, PacketProvider additionalPacketProvider) {
		Runnable run = new SolarReader(in, packetCreator, packetHandler, idGenerator, samePacketTime, onDataReceive, additionalPacketProvider);
		try {
			while (!Thread.currentThread().isInterrupted()) {
				run.run();
				Thread.sleep(5);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
	private static PacketProvider getAdditionalPacketProvider(PacketHandlingOption options){
		String source = options.getSourceId();
		Integer fragment = options.getFragmentId();
		requireNonNull(source);
		return () -> {
			List<Packet> r = new ArrayList<>();
			r.add(InstanceSourcePackets.create(source));
			if(fragment != null){
				r.add(InstanceFragmentIndicatorPackets.create(fragment));
			}
			return r;
		};
	}
	private static PacketCollectionIdGenerator createIdGenerator(Integer uniqueIdsInOneHour){
		if(uniqueIdsInOneHour == null){
			return PacketCollectionIdGenerator.Defaults.UNIQUE_GENERATOR;
		}
		if(uniqueIdsInOneHour <= 0){
			throw new IllegalArgumentException("--unique must be > 0 or not specified!");
		}
		return new HourIntervalPacketCollectionIdGenerator(uniqueIdsInOneHour, new Random().nextInt());
	}
	private static List<PacketHandler> getPacketHandlers(PacketHandlingOption options, String couchDbDatabaseName){
		List<PacketHandler> packetHandlers = new ArrayList<>();
		{
			File latestSave = options.getLatestPacketJsonSaveLocation();
			if(latestSave != null){
				packetHandlers.add(new FileWritePacketHandler(latestSave, new GsonStringPacketHandler(), false));
			}
		}
		File couchDbFile = options.getCouchPropertiesFile();
		if(couchDbFile != null) {
			final String contents;
			try {
				contents = new String(Files.readAllBytes(couchDbFile.toPath()), Charset.defaultCharset());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			JsonParser parser = new JsonParser();
			JsonObject jsonObject = parser.parse(contents).getAsJsonObject();
			CouchProperties properties = JsonCouchDb.getCouchPropertiesFromJson(jsonObject);
			packetHandlers.add(new ThrottleFactorPacketHandler(
				new PrintPacketHandleExceptionWrapper(new CouchDbPacketSaver(properties, couchDbDatabaseName), System.err),
				3, // TODO get throttle factor
				true
			));
		}
		return packetHandlers;
	}
	
	public static int doMain(String[] args){
		if(args.length < 1){
			System.err.println("Usage: <java -jar ...> {mate|rover|rover-setup|outhouse}");
			return 1;
		}
		String programName = args[0];
		String[] newArgs = new String[args.length - 1];
		System.arraycopy(args, 1, newArgs, 0, newArgs.length);
		
		
		Program program = getProgram(programName);
		if(program == null){
			System.err.println("Usage: <java -jar ...> {mate|rover|rover-setup|outhouse}");
			return 1;
		}
		try {
			if(program == Program.MATE) {
				Cli<MateProgramOptions> cli = CliFactory.createCli(MateProgramOptions.class);
				final MateProgramOptions options;
				try {
					options = cli.parseArguments(newArgs);
				} catch(ArgumentValidationException ex){
					System.err.println(ex.getMessage());
					return 1;
				}
				return connectMate(options);
			} else if(program == Program.ROVER){
				Cli<RoverProgramOptions> cli = CliFactory.createCli(RoverProgramOptions.class);
				final RoverProgramOptions options;
				try {
					options = cli.parseArguments(newArgs);
				} catch(ArgumentValidationException ex){
					System.err.println(ex.getMessage());
					return 1;
				}
				return connectRover(options);
			} else if(program == Program.OUTHOUSE){
				Cli<OuthouseProgramOptions> cli = CliFactory.createCli(OuthouseProgramOptions.class);
				final OuthouseProgramOptions options;
				try {
					options = cli.parseArguments(newArgs);
				} catch(ArgumentValidationException ex){
					System.err.println(ex.getMessage());
					return 1;
				}
				return connectOuthouse(options);
			} else if(program == Program.ROVER_SETUP){
				Cli<RoverSetupProgramOptions> cli = CliFactory.createCli(RoverSetupProgramOptions.class);
				final RoverSetupProgramOptions options;
				try {
					options = cli.parseArguments(newArgs);
				} catch(ArgumentValidationException ex){
					System.err.println(ex.getMessage());
					return 1;
				}
				return connectRoverSetup(options);
			}
			System.out.println("Specify mate|rover|rover-setup|outhouse");
			return 1;
		} catch (Exception t) {
			t.printStackTrace();
			return 1;
		}
	}

	public static void main(String[] args) {
		BasicConfigurator.configure();
		Logger.getRootLogger().setLevel(Level.INFO);
		System.exit(doMain(args));
	}
	private static Program getProgram(String program) {
		if(program == null){
			return null;
		}
		switch (program.toLowerCase()) {
			case "solar": case "mate":
				return Program.MATE;
			case "rover":
				return Program.ROVER;
			case "rover-setup":
				return Program.ROVER_SETUP;
			case "outhouse":
				return Program.OUTHOUSE;
		}
		return null;
	}
	private enum Program {
		MATE,
		ROVER,
		ROVER_SETUP,
		OUTHOUSE
	}
}
