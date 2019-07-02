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
package org.edgexfoundry.handler;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.edgexfoundry.controller.DeviceClient;
import org.edgexfoundry.GenericRestClient;
import org.edgexfoundry.data.DeviceStore;
import org.edgexfoundry.domain.FischertechObject;
import org.edgexfoundry.domain.ResponseObject;
import org.edgexfoundry.domain.core.Event;
import org.edgexfoundry.domain.core.Reading;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.OperatingState;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CoreDataMessageHandler {


	private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(CoreDataMessageHandler.class);

	@Value("${service.connect.retries}")
	private int retries;
	@Value("${service.connect.wait}")
	private long delay;

	@Autowired
	private DeviceClient deviceClient;
	
	
	@Autowired
	private DeviceStore devices;
	
	public Reading buildReading(String key, String value, String deviceName) {
		Reading reading = new Reading();
		reading.setName(key);
		reading.setValue(value);
		reading.setDevice(deviceName);
		return reading;
	}

	private Event buildEvent(String deviceName, List<Reading> readings) {
		Event event = new Event(deviceName);
		event.setReadings(readings);
		return event;
	}

	private boolean sendEvent(Event event, int attempt) {
		if (retries == 0 || attempt < retries) {
			if (event != null) {
			    if (GenericRestClient.addEvent(event)) {
				return true;
			    }
			    else {
				logger.error("Problem sending event for " + event.getDevice() +
					     "to core data.  GenericRestClient.addEvent returned false. Retrying (attempt " + (attempt + 1) + ")...");
				return sendEvent(event, ++attempt);
			    }
			}
		}
		return false;
	}

	private void updateLastConnected(String deviceName) {
		Device device = devices.getDevice(deviceName);
		if (device != null) {
			deviceClient.updateLastConnected(device.getId(), Calendar.getInstance().getTimeInMillis());
			if (device.getOperatingState().equals(OperatingState.DISABLED))
				devices.setDeviceByIdOpState(device.getId(), OperatingState.ENABLED);
		} else {
			logger.debug("No device found for device name: " + deviceName + ". Could not update last connected time");
		}
	}

	public List<ResponseObject> sendCoreData(String deviceName, List<Reading> readings, Map<String, FischertechObject> objects) {
		try{
		
			if (objects != null) {
	
				List<ResponseObject> resps = new ArrayList<>();
				logger.debug("readings: " + readings);
				for (Reading reading: readings) {										
					ResponseObject resp = new ResponseObject(reading.getName(), reading.getValue());						
					resps.add(resp);
				}
				boolean success = sendEvent(buildEvent(deviceName, readings), 0);
				if (success) {
					updateLastConnected(deviceName);
					return resps;
				}
				else {
					if (devices.getDevice(deviceName).getOperatingState().equals(OperatingState.ENABLED))
						devices.setDeviceOpState(deviceName, OperatingState.DISABLED);
					logger.error("Could not send event to core data for " + deviceName + ".  Check core data service");
				}
			} else
				logger.debug("No profile object found for the device " + deviceName + ".  MQTT message ignored.");
		}catch(Exception e){
			logger.error("Cannot push the readings to Coredata " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}
}
