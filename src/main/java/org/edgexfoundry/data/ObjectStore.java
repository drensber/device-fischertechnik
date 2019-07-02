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
package org.edgexfoundry.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.edgexfoundry.domain.FischertechDevice;
import org.edgexfoundry.domain.FischertechObject;
import org.edgexfoundry.domain.core.Reading;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.OperatingState;
import org.edgexfoundry.domain.meta.PropertyValue;
import org.edgexfoundry.domain.meta.ResourceOperation;
import org.edgexfoundry.exception.controller.NotFoundException;
import org.edgexfoundry.fischertech.ObjectTransform;
import org.edgexfoundry.handler.CoreDataMessageHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.gson.JsonObject;

@Component
public class ObjectStore {

	@Value("${data.transform:true}")
	private Boolean transformData;
	
	@Autowired
	private ProfileStore profiles;
	
	@Autowired
	private ObjectTransform transform;
	
	@Autowired
	private CoreDataMessageHandler processor;
	
	@Value("${data.cache.size:1}")
	private int CACHE_SIZE;
	
	private Map<String,Map<String,List<String>>> objectCache = new HashMap<>();
	
	private Map<String,Map<String,List<Reading>>> responseCache = new HashMap<>();
	
	public Boolean getTransformData() {
		return transformData;
	}
	
	public void setTransformData(Boolean transform) {
		transformData = transform;
	}

	public void put(FischertechDevice device, ResourceOperation operation, String value) {
		if (value == null || value.equals("") || value.equals("{}"))
			return;
		
		List<FischertechObject> objectsList = createObjectsList(operation, device);
		
		String deviceId = device.getId();
		List<Reading> readings = new ArrayList<>();
		
		for (FischertechObject obj: objectsList) {
			String objectName = obj.getName();
			String result = transformResult(value, obj, device, operation);

			Reading reading = processor.buildReading(objectName, result, device.getName());
			readings.add(reading);
			
			synchronized(objectCache) {
				if (objectCache.get(deviceId) == null)
					objectCache.put(deviceId, new HashMap<String,List<String>>());
				if (objectCache.get(deviceId).get(objectName) == null)
					objectCache.get(deviceId).put(objectName, new ArrayList<String>());
				objectCache.get(deviceId).get(objectName).add(0, result);
				if (objectCache.get(deviceId).get(objectName).size() == CACHE_SIZE)
					objectCache.get(deviceId).get(objectName).remove(CACHE_SIZE-1);
			}
		}
		
		String operationId = objectsList.stream().map(o -> o.getName()).collect(Collectors.toList()).toString();
		
		synchronized(responseCache) {
			if (responseCache.get(deviceId) == null)
				responseCache.put(deviceId, new HashMap<String,List<Reading>>());
			responseCache.get(deviceId).put(operationId,readings);
		}
	}
	
	private List<FischertechObject> createObjectsList(ResourceOperation operation, Device device) {
		Map<String, FischertechObject> objects = profiles.getObjects().get(device.getName());
		List<FischertechObject> objectsList = new ArrayList<FischertechObject>();
		if (operation != null && objects != null) {
			FischertechObject object = objects.get(operation.getObject());
			if (!profiles.getValueDescriptors().stream().filter(
					desc -> desc.getName().equals(operation.getParameter()))
					.collect(Collectors.toList()).isEmpty())
				object.setName(operation.getParameter());
			objectsList.add(object);
			
			if(operation.getSecondary() != null){
				for (String secondary: operation.getSecondary())
					objectsList.add(objects.get(secondary));
			}
		}
		
		return objectsList;
	}

	private String transformResult(String result, FischertechObject object, FischertechDevice device, ResourceOperation operation) {
		
		PropertyValue propValue = object.getProperties().getValue();
		
		String transformResult = transform.transform(propValue, result);
		
		// if there is an assertion set for the object on a get command, test it
		// if it fails the assertion, pass error to core services (disable device?)
		if (propValue.getAssertion() != null)
			if(!transformResult.equals(propValue.getAssertion().toString())) {
				device.setOperatingState(OperatingState.DISABLED);
				return "Assertion failed with value: " + transformResult;
			}
		
		Map<String, String> mappings = operation.getMappings();
		
		if (mappings != null && mappings.containsKey(transformResult))
			transformResult = mappings.get(transformResult);
		return transformResult;
	}

	public String get(String deviceId, String object) {
		return get(deviceId, object, 1).get(0);
	}

	private List<String> get(String deviceId, String object, int i) {
		if (objectCache.get(deviceId) == null 
				|| objectCache.get(deviceId).get(object) == null 
				|| objectCache.get(deviceId).get(object).size() < i)
			return null;
		return objectCache.get(deviceId).get(object).subList(0, i);
	}

	public JsonObject get(FischertechDevice device, ResourceOperation operation) {
		JsonObject jsonObject = new JsonObject();
		List<FischertechObject> objectsList = createObjectsList(operation, device);
		for (FischertechObject obj: objectsList) {
			String objectName = obj.getName();
			jsonObject.addProperty(objectName, get(device.getId(),objectName));
		}
		return jsonObject;
	}
	
	public List<Reading> getResponses(FischertechDevice device, ResourceOperation operation) {
		String deviceId = device.getId();
		List<FischertechObject> objectsList = createObjectsList(operation, device);
		if (objectsList == null)
			throw new NotFoundException("device", deviceId);
		String operationId = objectsList.stream().map(o -> o.getName()).collect(Collectors.toList()).toString();
		return responseCache.get(deviceId).get(operationId);
	}
	
}
