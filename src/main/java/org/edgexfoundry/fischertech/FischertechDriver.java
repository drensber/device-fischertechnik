/*******************************************************************************
 * Copyright 2016-2017 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @microservice:  device-fischertechnik
 * @author: Tyler Cox, Dell
 * @version: 1.0.0
 *******************************************************************************/
package org.edgexfoundry.fischertech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;

import org.edgexfoundry.data.DeviceStore;
import org.edgexfoundry.data.ObjectStore;
import org.edgexfoundry.data.ProfileStore;
import org.edgexfoundry.domain.FischertechAttribute;
import org.edgexfoundry.domain.FischertechDevice;
import org.edgexfoundry.domain.FischertechObject;
import org.edgexfoundry.domain.ScanList;
import org.edgexfoundry.domain.TransportArea;
import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.OperatingState;
import org.edgexfoundry.domain.meta.ResourceOperation;
import org.edgexfoundry.exception.controller.NotFoundException;
import org.edgexfoundry.fishX1.FishX1Packet;
import org.edgexfoundry.fishX1.TA_INPUT;
import org.edgexfoundry.fishX1.TA_OUTPUT;
import org.edgexfoundry.handler.FischertechHandler;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fazecast.jSerialComm.SerialPort;

@Service
public class FischertechDriver {

	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(FischertechDriver.class);
	
	@Autowired
	DeviceStore devices;
	
	@Autowired
	ProfileStore profiles;
	
	@Autowired
	ObjectStore objectCache;
	
	@Autowired
	FischertechHandler handler;
	
	@Autowired
	TransportArea TA;
	
	private Boolean initializer = true;
	
	private SerialPort client = null;

	private int serialBaudRate = 38400;
	private int serialDataBits = 8;
	private int serialStopBits = 1;
	private int serialParity   = 0;
	
	Boolean safety = true;
	
	Boolean connected = false;
	
	private TA_OUTPUT ta_output;
	
	private TA_INPUT ta_input;
	
	private FischertechDevice device;
	
	public ScanList discover() {
		ScanList scan = new ScanList();
		Map<String, String> newDevice = new HashMap<String, String>();
		newDevice.put("name", "Fischertechnik");
		newDevice.put("address", "Punching Machine");
		
		if (!connected) {
			initialize();
			if (connected)
				scan.add(newDevice);
		}
		
		return scan;
	}
	
	// operation is get or set
	// Device to be written to
	// Fischertech Object to be written to
	// value is string to be written or null
	public void process(ResourceOperation operation, FischertechDevice device, FischertechObject object, String value, String transactionId, String opId) {
		String result = "";
		
		result = processCommand(operation.getOperation(), device.getAddressable(), object.getAttributes(), value);
		if (this.device == null) {
			this.device = device;
		}
		
		objectCache.put(device, operation, result);
		handler.completeTransaction(transactionId, opId, objectCache.getResponses(device, operation));
	}

	// Modify this function as needed to pass necessary metadata from the device and its profile to the driver interface
	public String processCommand(String operation, Addressable addressable, FischertechAttribute attributes, String value) {
		if (!connected) {
			initialize();
			if (!connected)
				throw new NotFoundException("Fischertechnik device", addressable.getName());
		}
			
		String address = addressable.getPath();
		String intface = addressable.getAddress();
		logger.debug("ProcessCommand: " + operation + ", interface: " + intface + ", address: " + address + ", attributes: " + attributes.getInterfaceName() + ", value: " + value );
		String result = "";
		
		if (operation.equals("set")) {
			if (attributes.getInterfaceName().startsWith("M")) {
				int motorNum = Integer.parseInt(attributes.getInterfaceName().substring(1));
				synchronized(ta_output) {
					ta_output.setDuty(motorNum, Integer.parseInt(value));
					result = value;
				}
			} else if (attributes.getInterfaceName().equals("S1")) {
				safety = (Integer.parseInt(value) == 1) ? true : false;
				result = safety ? "1" : "0";
			} else {
				throw new NotFoundException("Fischertech interface", attributes.getInterfaceName());
			}
		} else {
			int ioNum = Integer.parseInt(attributes.getInterfaceName().substring(1));
			if (attributes.getInterfaceName().startsWith("M")) {
				synchronized(ta_output) {
					result = String.valueOf(ta_output.getDuty(ioNum));
				}
			} else if (attributes.getInterfaceName().startsWith("I")) {
				synchronized(ta_input) {
					result = String.valueOf(ta_input.getUni(ioNum));
				}
			}
		}
		
		return result;
	}
	
	private void receive(String interfaceName, String value) {
		logger.debug("Event detected for: " + interfaceName + " value: " + value);
		try {
			FischertechObject object = profiles.getObjects().get(device.getName()).values().stream().filter(o -> o.getAttributes().getInterfaceName().equals(interfaceName)).findFirst().orElse(null);
			if (object != null) {
				ResourceOperation operation = profiles.getCommands().get(device.getName()).get(object.getName().toLowerCase()).get("get").get(0);
				objectCache.put(device, operation, value);
				handler.sendTransaction(device.getName(), objectCache.getResponses(device, operation));
				
			}
		} catch (Exception e) {
			return;
		}
	}
	
	public void disconnectDevice(Addressable address) {
		cleanup();
	}
	
	@PreDestroy
	public void cleanup() {
		if (device != null)
			devices.remove(device.getId());
		client.closePort();
		connected = false;
		device = null;
	}
	
	public void initialize() {
		try {
			synchronized(initializer) {
				if (connected) 
					return;
				SerialPort ports[] = SerialPort.getCommPorts();
				String address = "fischertechnik";
				String address2 = "ROBO TX Controller";
				//String address = device.getAddressable().getPath();
				for (int i = 0; i < ports.length; i++) {
					logger.debug(ports[i].getDescriptivePortName());
					if(ports[i].getDescriptivePortName().contains(address) ||
							ports[i].getDescriptivePortName().contains(address2)) {
						client = ports[i];
						break;
					}
				}
				
				if (client == null) {
					logger.info("No devices found for connection!");
					return;
				}
				
				client.setComPortParameters(serialBaudRate, serialDataBits, serialStopBits, serialParity);
				client.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 100);
				client.openPort();
				logger.info("Port is " + (client.isOpen() ? "open" : "closed") + " for: " + client.getDescriptivePortName());
				
				ta_output = new TA_OUTPUT();
				ta_input = new TA_INPUT();
				
				connected = true;
								
				final List<Integer> order = configure();
				
				if (order.size() == 0) {
					logger.error("Error initializing device " + client.getDescriptivePortName());
					disconnectDevice(null);
					return;
				}
				
				if (device != null) {
					if (device.getOperatingState().equals(OperatingState.DISABLED))
						devices.setDeviceByIdOpState(device.getId(), OperatingState.ENABLED);
					handler.initializeDevice(device);
				}
				
				logger.debug("Initialized the device");
				
				new Thread(new Runnable() {
					public void run() {
						connection(order);
					}
				}).start();
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			connected = false;
		}
	}
	
	private List<Integer> configure() {
		FishX1Packet packet = new FishX1Packet(5);
		List<Integer> order = writeToDevice(packet);
		return order;
	}

	protected void connection(List<Integer> order) {
		FishX1Packet packet = new FishX1Packet(2, order.get(0), order.get(1));
		ta_output = packet.getOutput();
		while (connected) {
			packet.update(order.get(0), order.get(1));
			try {
				order = writeToDevice(packet);
			} catch (Exception e) {
				disconnectDevice(null);
			}
			synchronized(ta_output) {
				if (safety) {
					if (ta_input.getUni(7) > 0) {
						if (ta_output.getDuty(1) < 0)
							ta_output.setDuty(1, 0);//8);
					}
					if (ta_input.getUni(8) > 0) {
						if (ta_output.getDuty(1) > 0)
							ta_output.setDuty(1, 0);//-8);
					}
					if (ta_input.getUni(5) == 0) {
						if (ta_output.getDuty(2) < 0)
							ta_output.setDuty(2, 0);//127);
					}	
					if (ta_input.getUni(6) == 0) {
						if (ta_output.getDuty(2) > 0)
							ta_output.setDuty(2, 0);//-127);
					}
				}
				packet.setOutput(ta_output);
			}
		}
	}

	private List<Integer> writeToDevice(FishX1Packet packet) {
		List<Integer> tid = new ArrayList<Integer>();
		
		byte[] buffer = new byte[200];
		
		Integer readbytes = 0;
		byte[] frame = packet.getFrame();
		client.writeBytes(frame, frame.length/2);
		readbytes = client.readBytes(buffer, buffer.length);
		String output = "";
		for (byte b: buffer) 
			output += String.format("%02X", b);
		
		if (readbytes <= 0 || output.length() < readbytes*2) {
			logger.error("Could not read from device " + client.getDescriptivePortName());
			disconnectDevice(null);
			return tid;
		}
		
		output = output.substring(0, readbytes*2);
			
		tid.add(Integer.parseInt(output.substring(24,26), 16) + Integer.parseInt(output.substring(26,28), 16) * 256 + 1);
		tid.add(Integer.parseInt(output.substring(28,30), 16) + Integer.parseInt(output.substring(30,32), 16) * 256);
		
		if (output.substring(16*2,16*2+2).equals("66")) {
			String target = output.substring(7*4*2, output.length()-6);
			synchronized(ta_input) {
				TA_INPUT old = ta_input;
				ta_input = new TA_INPUT(target);
				for (int i = 1; i <= ta_input.uni.size(); i++)
					if (old.getUni(i) != ta_input.getUni(i))
						receive("I" + i, String.valueOf(ta_input.getUni(i)));
				if (String.format("%04X", ta_input.uni.get(0)).equals("983A")) {
					tid = configure(); // attempt to recover connection
					if (tid.size() == 0)
						disconnectDevice(null);
				}
			}
		}
		
		return tid;
	}

}
