/*******************************************************************************
 * Copyright 2019 Beechwoods Inc.
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
 * @author: Dave Rensberger, Beechwoods
 * @version: 1.0.0
 *******************************************************************************/
package org.edgexfoundry;

import org.edgexfoundry.domain.meta.OperatingState;
import org.edgexfoundry.domain.core.Event;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStreamWriter; 
import java.net.MalformedURLException;

import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;


public class GenericRestClient {
    
    private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(GenericRestClient.class);
    private final static String baseURL = "http://localhost";
    private final static String coreMetadataPort=":48081";
    private final static String coreDataPort=":48080";
    private final static String enabledMessage = "{ \"operatingState\":\"enabled\" }";
    private final static String disabledMessage = "{ \"operatingState\":\"disabled\" }";

    // Core metadata client
    public static boolean updateOpStateByName(String device, OperatingState state) {
	URL url;
	boolean rv=false;
	try {
	    url = new URL(baseURL + coreMetadataPort + "/api/v1/device/name/" + device);
	    rv = makeGenericPutRequest(url, (state == OperatingState.ENABLED ? enabledMessage : disabledMessage));
	}
	catch (MalformedURLException e) {
	    logger.error("MalformedURLException e="+e);
	}
	return rv;
    }

    public static boolean updateOpState(String id, OperatingState state) {
	URL url;
	boolean rv=false;

	try {
	    url = new URL(baseURL + coreMetadataPort + "/api/v1/device/" + id);
	    rv = makeGenericPutRequest(url, (state == OperatingState.ENABLED ? enabledMessage : disabledMessage));
	}
	catch (MalformedURLException e) {
	    logger.error("MalformedURLException e="+e);
	}

	return rv;	
    }

    // Core data client
    public static boolean addEvent(Event event) {
	URL url;
	boolean rv=false;
	StringBuilder message =
	    new StringBuilder("{\"origin\":" + event.getOrigin() +
			      ",\"device\":\"" + event.getDevice() + "\"" +
			      ",\"readings\":[" );


	for (int i=0; i < event.getReadings().size(); i++) {
	    if (i > 0) { message.append(","); };
	    message.append("{\"origin\":" + event.getReadings().get(i).getOrigin() +
			   ",\"name\":\"" + event.getReadings().get(i).getName() + "\"" +
			   ",\"value\":\"" + event.getReadings().get(i).getValue() + "\"}" );
	}
	
	message.append("]}");

	logger.error("Sending message to core-data with body \"" + message.toString() + "\"");
	try {
	    url = new URL(baseURL + coreDataPort + "/api/v1/event");
	    rv = makeGenericPostRequest(url, message.toString());
	}
	catch (MalformedURLException e) {
	    logger.error("MalformedURLException e="+e);
	}

	return rv;	
    }


    // Generic calls
    private static boolean makeGenericPostRequest(URL url, String messageBody) {
	return makeGenericSendRequest(url, messageBody, "POST");
    }
    
    private static boolean makeGenericPutRequest(URL url, String messageBody) {
	return makeGenericSendRequest(url, messageBody, "PUT");
    }
    
    private static boolean makeGenericSendRequest(URL url, String messageBody, String httpMethod) {

	logger.debug("Calling makeGenericSendRequest(url=" +url + ", messageBody=" + messageBody +", httpMethod=" + httpMethod);
	HttpURLConnection connection;
	int responseCode;
	
	try {
	    connection = (HttpURLConnection) url.openConnection();

	    connection.setRequestProperty("Content-Type", "application/json");
	    connection.setRequestMethod(httpMethod);
	    connection.setDoOutput(true);
	    connection.setRequestProperty("Accept", "application/json");
	    OutputStreamWriter osw = new OutputStreamWriter(connection.getOutputStream());
	    osw.write(messageBody);
	    osw.flush();
	    osw.close();
	    responseCode = connection.getResponseCode();
	    logger.debug("response code = " + responseCode);
	}
	catch (java.io.IOException e) {
	    logger.error("IOException e="+e);
	}
	
	return true;
    }
}
