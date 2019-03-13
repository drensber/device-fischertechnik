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

public class TA_INPUT {
        public int[] uni = new int[8];
        public int[] cnt_in = new int[4];
	public List<Integer> counter = Arrays.asList(new Integer[4]);
	public Integer display_button_left = 0;
	public Integer display_button_right = 0;
        public List<Integer> reserved = Arrays.asList(new Integer[20]);

        public static long[] cumulative_counter = new long[4];
	
	public TA_INPUT(String ta_input) {
		for(int i = 0; i < uni.length; i++) {
		        String byte_swapped_string = ta_input.substring(i*4 +2 ,i*4+4) + ta_input.substring(i*4,i*4+2);
		        uni[i] = Integer.parseInt(byte_swapped_string, 16);
		}
		ta_input = ta_input.substring(uni.length*4);
		
		for(int i = 0; i < cnt_in.length; i++) { // cnt_in is an array of UINT8 (despite what FT header file says)
		    cnt_in[i] = Integer.parseInt(ta_input.substring(i*2,i*2+2), 16);
		}
		ta_input = ta_input.substring(cnt_in.length*2);
		
		for(int i = 0; i < counter.size(); i++) {
		    String byte_swapped_string=ta_input.substring(i*4 +2 ,i*4+4) + ta_input.substring(i*4,i*4+2);
		    counter.set(i, Integer.parseInt(byte_swapped_string, 16));
		}
		ta_input = ta_input.substring(counter.size()*4);

		display_button_left = Integer.parseInt(ta_input.substring(2,4) + ta_input.substring(0,2) , 16);
		display_button_right = Integer.parseInt(ta_input.substring(6,8) + ta_input.substring(4,6) , 16);
		ta_input = ta_input.substring(2*4);

		for(int i = 0; i < reserved.size(); i++) {
			reserved.set(i, Integer.parseInt(ta_input.substring(i,i+2), 16));
		}
	}

	
	public TA_INPUT() {
		for(int i = 0; i < uni.length; i++)
			uni[i] = 0;
		for(int i = 0; i < cnt_in.length; i++)
		        cnt_in[i] = 0;
		for(int i = 0; i < counter.size(); i++)
			counter.set(i, 0);
		for(int i = 0; i < reserved.size(); i++)
			reserved.set(i, 0);
		for(int i = 0; i < cumulative_counter.length; i++)
			cumulative_counter[i] = 0;
	}

	@Override
	public String toString() {
	    String out = "\nuni: "; 
		for(int i = 0; i < uni.length; i++)
		out += String.format("%04X ", uni[i]);
		out += "\ncnt_in: ";
		for(int i = 0; i < cnt_in.length; i++) {
		    out += String.format("%01d, ", cnt_in[i]);
		}
		out += "\ncounter: ";
		for(int i = 0; i < counter.size(); i++) {
		    out += String.format("%05d, ", counter.get(i));
		}
		out += "\ndisplay_button_left: " + display_button_left;
		out += "\ndisplay_button_right: " + display_button_right;
		out += "\nreserved: ";
		for(int i = 0; i < reserved.size(); i++)
		out += String.format("%01d, ", reserved.get(i));
		out += "\ncumulative_counter: ";
		for(int i = 0; i < cumulative_counter.length; i++)
		    out += String.format("%d, ", cumulative_counter[i]);
		return out;
	}

        public int getCounter(int counterNum) {
		int zeroBased = counterNum - 1;		
		return counter.get(zeroBased);
	}

	public synchronized int getUni(int uniNum) {
		int zeroBased = uniNum - 1;
		int value = uni[zeroBased];
		return (value == 0) ? 0 : 1;
	}
    
	public int getCntIn(int num) {
		int zeroBased = num - 1;
		return cnt_in[zeroBased];
	}
    
        public static void incrementCumulativeCounter(int num) {
		int zeroBased = num - 1;
		cumulative_counter[zeroBased]++;
	}

	public static long getCumulativeCounter(int num) {
		int zeroBased = num - 1;
		return cumulative_counter[zeroBased];
	}       
}
