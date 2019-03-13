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

import java.util.Arrays;
import java.util.List;

public class TA_OUTPUT {
	public List<Integer> cnt_reset = Arrays.asList(new Integer[4]);
	public List<Integer> master = Arrays.asList(new Integer[4]);
	public List<Integer> duty = Arrays.asList(new Integer[8]);
	public List<Integer> distance = Arrays.asList(new Integer[4]);
	public List<Integer> motor_ex_cmd_id = Arrays.asList(new Integer[4]);
	
	public TA_OUTPUT() {
		for(int i = 0; i < cnt_reset.size(); i++)
			cnt_reset.set(i, 0);
		for(int i = 0; i < master.size(); i++)
			master.set(i, 0);
		for(int i = 0; i < duty.size(); i++)
			duty.set(i, 0);
		for(int i = 0; i < distance.size(); i++)
			distance.set(i, 0);
		for(int i = 0; i < motor_ex_cmd_id.size(); i++)
			motor_ex_cmd_id.set(i, 0);
	}

        public synchronized void resetCounter(int motorNum) {
	        int motor = motorNum - 1;
		cnt_reset.set(motor, cnt_reset.get(motor) + 1);	    
        }
    
	public synchronized void setDistance(int motorNum, int distance) {
		int motor = motorNum - 1;
		motor_ex_cmd_id.set(motor, motor_ex_cmd_id.get(motor) + 1);
		this.distance.set(motor, distance);
	}
    
	public synchronized void setDuty(int motorNum, int power) {
		int motor = motorNum - 1;
		motor_ex_cmd_id.set(motor, motor_ex_cmd_id.get(motor) + 1);

		if (power > 0) {
			duty.set(motor * 2, power);
			duty.set(motor * 2 + 1, 0);
		} else {
			duty.set(motor * 2, 0);
			duty.set(motor * 2 + 1, -1 * power);
		}
	}
	
	public synchronized int getDuty(int motorNum) {
		int motor = motorNum - 1;
		int power = duty.get(motor * 2);
		if (power == 0)
			power = -1 * duty.get(motor * 2 + 1);
		return power;
	}

        // This is more important than a regular "toString", because it's actually used to generate
        // the byte array that's sent to the device
	@Override
	public synchronized String toString() {
		String out = "";
		for(int i = 0; i < cnt_reset.size(); i++) {
		    out += String.format("%02X%02X", cnt_reset.get(i)&0x000000FF, cnt_reset.get(i)>>8&0x000000FF);
		}
		for(int i = 0; i < master.size(); i++) {
		        out += String.format("%02X", master.get(i));
		}
		for(int i = 0; i < duty.size(); i++) {
		    out += String.format("%02X%02X", duty.get(i)&0x000000FF, duty.get(i)>>8&0x000000FF);
		}
		for(int i = 0; i < distance.size(); i++) {
		    out += String.format("%02X%02X", distance.get(i)&0x000000FF, distance.get(i)>>8&0x000000FF);
		}
		for(int i = 0; i < motor_ex_cmd_id.size(); i++) {
		    out += String.format("%02X%02X", motor_ex_cmd_id.get(i)&0x000000FF, motor_ex_cmd_id.get(i)>>8&0x000000FF);
		}
		return out;
	}

        public boolean equals(TA_OUTPUT comparison) {
	    for(int i = 0; i < cnt_reset.size(); i++) 
		if (comparison.cnt_reset.get(i) != this.cnt_reset.get(i)) return false;	  
	    for(int i = 0; i < master.size(); i++)
		if (comparison.master.get(i) != this.master.get(i)) return false;	  
	    for(int i = 0; i < duty.size(); i++)
		if (comparison.duty.get(i) != this.duty.get(i)) return false;
	    for(int i = 0; i < distance.size(); i++)
		if (comparison.distance.get(i) != this.distance.get(i)) return false;
	    for(int i = 0; i < motor_ex_cmd_id.size(); i++)
		if (comparison.motor_ex_cmd_id.get(i) != this.motor_ex_cmd_id.get(i)) return false;
	    return true;
	}
}
