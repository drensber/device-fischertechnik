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
import java.util.Calendar;

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
import org.edgexfoundry.controller.AutomationController;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.edgexfoundry.exception.controller.ServiceException;
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

	private int serialBaudRate = 115200;
	private int serialDataBits = 8;
	private int serialStopBits = 1;
	private int serialParity   = 0;
	
	//Boolean safety = true;
	Boolean safety = false;
	
	Boolean connected = false;
	
	private TA_OUTPUT ta_output;
	
	private TA_INPUT ta_input;
	
        private int old_cnt_reset[] = new int[4];
        private long previous_cc_event_value[] = new long[4];
        private long previous_cc_event_timestamp[] = new long[4];
        private final long cc_event_threshold_ms = 100;

        // For tracking sequence numbers and time between packets.   
        private int previous_tid = 0;
        private Calendar cal = Calendar.getInstance();
        private long previous_timestamp=0;
    
	private FischertechDevice device;
	public ScanList discover() {
		ScanList scan = new ScanList();
		Map<String, String> newDevice = new HashMap<String, String>();
		newDevice.put("name", "Fischertechnik");
		//newDevice.put("address", "Punching Machine");
		newDevice.put("address", "Gripper Robot");
		
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
	    logger.debug("Calling process(ResourceOperation operation=\""+operation+"\", FischertechDevice device=<TLDR>, FischertechObject object=\""+object+"\", String value=\""+value+"\", String transactionId=\""+transactionId+"\", String opId=\""+opId+"\")");
		String result = "";
		
		result = processCommand(operation.getOperation(), object.getAttributes(), value);
		logger.debug("Called: processCommand(" + operation.getOperation() + ", " + object.getAttributes() + ", " + value + ")");
		logger.debug("  returned: " + result);
		if (this.device == null) {
			this.device = device;
		}
		
		objectCache.put(device, operation, result);
		logger.debug(" Calling: handler.completeTransaction(" + transactionId + ", " + opId + ", " + objectCache.getResponses(device, operation) + ")");
		handler.completeTransaction(transactionId, opId, objectCache.getResponses(device, operation));
	}

	// Modify this function as needed to pass necessary metadata from the device and its profile to the driver interface
	public String processCommand(String operation, FischertechAttribute attributes, String value) {
		if (!connected) {
			initialize();
			if (!connected) {
			    throw new NotFoundException("Device couldn't connect: ", "Fischertechnik");
			}
		}
			
		logger.debug("ProcessCommand: " + operation + ", attributes: " + attributes.getInterfaceName() + ", value: " + value );
		String result = "";

		logger.debug("ta_input = " + ta_input);
		
		if (operation.equals("set")) {
		        if (attributes.getInterfaceName().equals("AState")) {
			    if (AutomationController.getInstance().setRequestedState(Integer.parseInt(value))) {
				result = value;
				receive("AState", (value=="1" ? "Running" : "NotRunning"));
			    }
			    else {
				result = (AutomationController.getInstance().isRunning() ? "1" : "0");
			    }
			}
			else if (attributes.getInterfaceName().equals("AName")) {
			    if (AutomationController.getInstance().setAutomationProcedureName(value)) {
				result = value;
			    }
			    else {
				result = AutomationController.getInstance().getAutomationProcedureName();
			    }
			}
			else if (attributes.getInterfaceName().startsWith("M")) {
			    int motorNum = Integer.parseInt(attributes.getInterfaceName().substring(1));
			    synchronized(ta_output) {
				logger.debug("Calling ta_output.setDuty(" + motorNum + ", " + Integer.parseInt(value) + ")");
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
		        if (attributes.getInterfaceName().equals("AState")) {
			    result = (AutomationController.getInstance().isRunning() ? "1" : "0");
			}
			else if (attributes.getInterfaceName().equals("AName")) {
			    result = AutomationController.getInstance().getAutomationProcedureName();
			}
		        if (attributes.getInterfaceName().startsWith("CC")) {
			        int ioNum = Integer.parseInt(attributes.getInterfaceName().substring(2));
				synchronized(ta_input) {
				        result = String.valueOf(TA_INPUT.getCumulativeCounter(ioNum));
				}
			}
			else if (attributes.getInterfaceName().startsWith("M")) {
				int ioNum = Integer.parseInt(attributes.getInterfaceName().substring(1));
				synchronized(ta_output) {
					result = String.valueOf(ta_output.getDuty(ioNum));
				}
			} else if (attributes.getInterfaceName().startsWith("I")) {
			        int ioNum = Integer.parseInt(attributes.getInterfaceName().substring(1));
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
		logger.debug("ta_input = " + ta_input);
	}
	
	public void disconnectDevice() {
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
				logger.debug("Looking for Fischertechnic devices");
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
				
				logger.debug("Device found: " + client.getSystemPortName());
				client.setComPortParameters(serialBaudRate, serialDataBits, serialStopBits, serialParity);
				client.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);
				logger.info("Setting Parameters serialBaudRate="+serialBaudRate+", serialDataBits="+serialDataBits+", serialStopBits="+serialStopBits+", serialParity="+serialParity);
				client.openPort();
				logger.info("Port is " + (client.isOpen() ? "open" : "closed") + " for: " + client.getDescriptivePortName());
				
				logger.debug("Setting up Transfer Area output");
				ta_output = new TA_OUTPUT();
				logger.debug("ta_output = \"" + ta_output + "\"");
				logger.debug("Setting up Transfer Area input");
				ta_input = new TA_INPUT();
				logger.debug("ta_input = \"" + ta_input + "\"");

				connected = true;
								
				final List<Integer> order = configure();
				
				if (order.size() == 0) {
					logger.error("Error initializing device " + client.getDescriptivePortName());
					disconnectDevice();
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
		AutomationController.getInstance().setTaOutput(ta_output);		
		while (connected) {
			packet.update(order.get(0), order.get(1));

			// 50 packets/second should be enough granularity
			try {
			    Thread.sleep(20);
			}catch(InterruptedException e) {
			    //IGNORED
			    e.printStackTrace();
			}

			try {
			    order = writeToDevice(packet);
			} catch (Exception e) {
			    logger.error("FischertechDriver.connection() caught exception e=" + e);
			    e.printStackTrace();
				disconnectDevice();
			}

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

	private List<Integer> writeToDevice(FishX1Packet packet) {
		List<Integer> tid = new ArrayList<Integer>();
		
		byte[] buffer = new byte[200];
		
		Integer readbytes = 0;
		byte[] frame = packet.getFrame();

		if (frame[16] == 5) {
		    logger.debug("Sending packet=\"" + packet + "\"");
		}
		for (int i=0; i < 100; i++) {
		    int write_rv = client.writeBytes(frame, frame.length);
		    if (write_rv != 75) {
			logger.debug("write_rv = " + write_rv);
		    }

		    if (write_rv < 0) {
			logger.error("writeBytes returned " + write_rv + ". Retrying in 10ms");
			continue;
		    }

		    readbytes = client.readBytes(buffer, buffer.length);
		    if (readbytes > 0) {
			if (readbytes != 79) {
			    logger.debug("readbytes=" + readbytes);
			}
			break;
		    }
		    if (i > 5) {
		    	logger.error("readBytes returned " + readbytes + ". Retrying in 10ms");
		    }
		}

		// Check for missing FT serial protocol packets 
		//if (packet.tid_int != ++previous_tid) {
		//    logger.error("Wrong packet.tid_int. (expected " + previous_tid +", but got "+ packet.tid_int + ").  Resetting.");
		//    previous_tid=packet.tid_int;
		//}
		//if (previous_tid>65535) { // roll over after 2^16
		//    previous_tid=0;
		//} 
		
		// Make sure we meet our soft-realtime expectations.
		/* long now_timestamp = System.currentTimeMillis();
		if (previous_timestamp != 0) {
		    if ((now_timestamp - previous_timestamp) > 1) {
			logger.error("Difference between now_timestamp("+now_timestamp+") and previous_timestamp("+previous_timestamp+") exceeds 1 millisecond.");
		    }
		}
		else {
		    logger.debug("previous_timestamp was not set, so setting it to " + now_timestamp);
		}
		previous_timestamp=now_timestamp; */
	       
		String output = "";
		for (byte b: buffer) 
			output += String.format("%02X", b);
		
		if (readbytes <= 0 || output.length() < readbytes*2) {		        
			logger.error("Could not read from device " + client.getDescriptivePortName());
			logger.error("readbytes = " + readbytes + ",  output.length()="+output.length());
			disconnectDevice();
			return tid;
		}
		
		output = output.substring(0, readbytes*2);
			
		if (frame[16] == 5) {  // 5 is TA_CONFIG
		    logger.debug("output=\"" + output + "\"");
		}
		
		tid.add(Integer.parseInt(output.substring(24,26), 16) + Integer.parseInt(output.substring(26,28), 16) * 256 + 1);
		tid.add(Integer.parseInt(output.substring(28,30), 16) + Integer.parseInt(output.substring(30,32), 16) * 256);
		
		if (output.substring(16*2,16*2+2).equals("66")) {		    
			String target = output.substring(7*4*2, output.length()-6);
			TA_INPUT old = ta_input;
			ta_input = new TA_INPUT(target);
			synchronized(ta_input) {
				AutomationController.getInstance().setTaInput(ta_input);
				for (int i = 0; i < ta_input.uni.length; i++) {
				    //Send update events if the input switch has changed
				    if (old.uni[i] != ta_input.uni[i]) {
					receive("I" + (i + 1), String.valueOf(ta_input.uni[i]));
				    }
				}

				for (int i = 0; i < ta_input.cnt_in.length; i++) {
					int counter_difference = ta_input.counter.get(i) - old.counter.get(i);
					if (counter_difference != 0) {
					    if ( counter_difference > 0 ) {
						TA_INPUT.cumulative_counter[i] += ( counter_difference );
					    }
					    else {
						// This is totally hokey, but checking for counter_difference > -10000 is the best
						// way I can find to detect a counter reset situation (as opposed to rollover situation)
						if (counter_difference > -10000) {
						    TA_INPUT.cumulative_counter[i] += ta_input.counter.get(i);
						}
						else {
						    logger.error("counter_difference is "+counter_difference);
						    TA_INPUT.cumulative_counter[i] += ( counter_difference + 65536 );
						}
					    }
					
					    if (TA_INPUT.cumulative_counter[i] > previous_cc_event_value[i]) {
						long current_time = System.currentTimeMillis();
						if ((current_time - previous_cc_event_timestamp[i]) > cc_event_threshold_ms) {
						    previous_cc_event_timestamp[i] = current_time;
						    previous_cc_event_value[i] = TA_INPUT.cumulative_counter[i];
						    receive("CC" + (i + 1), String.valueOf(TA_INPUT.cumulative_counter[i]));
						}
					    }
					}
				
				}

				if (String.format("%04X", ta_input.uni[0]).equals("983A")) {
					tid = configure(); // attempt to recover connection
					if (tid.size() == 0) {
						disconnectDevice();
					}
				}
			}
		}
		
		return tid;
	}

}
