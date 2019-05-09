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

public class TA_CONFIG {
	public Integer pgm_state_req = 1;
	public List<Integer> reserved_1 = Arrays.asList(new Integer[3]);
	public List<Integer> motor = Arrays.asList(new Integer[4]);
	public List<UNI_CONFIG> uni = Arrays.asList(new UNI_CONFIG[8]);
	public List<CNT_CONFIG> cnt = Arrays.asList(new CNT_CONFIG[4]);
	public List<Integer> reserved = Arrays.asList(new Integer[32]);
	
	public TA_CONFIG() {
		for(int i = 0; i < reserved_1.size(); i++)
			reserved_1.set(i, 1);
		for(int i = 0; i < motor.size(); i++)
			motor.set(i, 1);
		for(int i = 0; i < uni.size(); i++)
			uni.set(i, new UNI_CONFIG(1, 1));
		for(int i = 0; i < cnt.size(); i++)
			cnt.set(i, new CNT_CONFIG(1));
		for(int i = 0; i < reserved.size(); i++)
			reserved.set(i, 0);
	}
	
	@Override
	public synchronized String toString() {
		String out = String.format("%02X", pgm_state_req);
		for(int i = 0; i < reserved_1.size(); i++)
			out += String.format("%02X", reserved_1.get(i));
		for(int i = 0; i < uni.size(); i++)
			out += "81";//uni.get(i).toString();
		for(int i = 0; i < cnt.size(); i++)
			out += "01";//cnt.get(i).toString();
		for(int i = 0; i < reserved.size(); i++)
			out += String.format("%02X", reserved.get(i));
		return out;
	}
}
