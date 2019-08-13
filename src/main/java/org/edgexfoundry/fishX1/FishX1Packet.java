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
package org.edgexfoundry.fishX1;

import org.springframework.stereotype.Component;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;

@Component
public class FishX1Packet {
	
        private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(FishX1Packet.class);
	private String header = "0255";
	private String from = littleEndian(2,4);
	private String to = littleEndian(1,4);
	private String TID;
	private String SID;
	private String command = littleEndian(1,4);
	private int numStructures = 0;
	private String TAID = "";
	private String footer = "03";
	
	// For detecting missing packets in the sequence
    	public int tid_int;
	
	private String messagePayload = "";
	private TA_OUTPUT output;
	private TA_CONFIG config;

        private byte[] byteArray75 = new byte[75];
        private byte[] byteArray79 = new byte[79];

	public FishX1Packet() {
		this(2);
	}
	
	public FishX1Packet(int type) {
		this(type, 0x01, 0x00);
	}
	
	public FishX1Packet(int type, int tid, int sid) {
		if (tid > 65535) {
			tid = 0;
			sid += 1;
		}
		if (sid > 65535) {
			sid = 0;
		}
		TID = littleEndian(tid, 2);
		tid_int=tid;
		SID = littleEndian(sid, 2);
		command = littleEndian(type,4);
		if (type == 2) {
			output = new TA_OUTPUT();
			//output.setDuty(1, 0); // 0: press down, 1: press up, 2: belt right, 3: belt left, 5: light
			//output.setDuty(2, 127);
			//output.setDuty(3, 8);
			setOutput(output);
		} else if (type == 5) {
			config = new TA_CONFIG();
			setConfig(config);
		} else if (type == 7 || type == 6) {
			addStructure("");
		}
	}
	
	public FishX1Packet(int tid, int sid) {
		TID = littleEndian(tid, 2);
		tid_int=tid;
		SID = littleEndian(sid, 2);
	}
	
	public String getTID() {
		return TID;
	}
	public void update(int tid, int sid) {
		TID = littleEndian(tid, 2);
		tid_int=tid;
		SID = littleEndian(sid, 2);
	}
	
	private Integer getDataLength() {
		return 20 + TAID.length() / 2 + messagePayload.length() / 2;
	}
	
	public void addStructure(String structure) {
		TAID = littleEndian(0,4);
		numStructures = 1;
		messagePayload = structure;
	}
	
	public TA_OUTPUT getOutput() {
		return output;
	}
	
	public void setOutput(TA_OUTPUT output) {
		this.output = output;
		addStructure(output.toString());
	}
	
	public void setConfig(TA_CONFIG config) {
		this.config = config;
		addStructure(config.toString());
	}
	
	private String getNumStructures() {
		return littleEndian(numStructures,4);
	}
	
	private String getLength() {
		return String.format("%04X", getDataLength());
	}
	
	private String getCRC() {
		String payload = getPayload();
		int crc = 0;
		for (int i = 0; i < payload.length()/2; i++) {
			Integer current = Integer.parseInt(payload.substring(i*2, i*2+2), 16);
			crc += current;
		}
		crc = 65535 - crc + 1;
        return String.format("%04X", crc);
	}
	
	private String getPayload() {
		return getLength() + from + to + TID + SID + command + getNumStructures() + TAID + messagePayload;
	}
	
	@Override
	public String toString() {
		return header + getPayload() + getCRC() + footer;
	}
	
	public byte[] getFrame() {
		return hexToBytes(this.toString());
	}
	
	private byte[] hexToBytes(String s) {
	    byte[] byteArray;

	    // To avoid dynamic allocations
	    if ((s.length() / 2) == 75) {
		byteArray = byteArray75;
	    }
	    else if ((s.length() / 2) == 79) {
		byteArray = byteArray79;
	    }
	    else {
		byteArray = new byte[s.length() / 2];
	    }
	    
	    for (int i = 0; i < byteArray.length; i += 1) {
	    	byteArray[i] = (byte) (Integer.parseInt(s.substring(i*2, (i+1)*2), 16));
	    }
	    return byteArray;
	}
	
	private String littleEndian(int value, int length) {
		String out = "";
		for (int i = 0; i < length; i++) {
			out += String.format("%02X", value % 256);
			value = value >> 8;
		}
		return out;
	}
}
