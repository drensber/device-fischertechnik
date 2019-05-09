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
 * @author: Dave Rensberger, Beechwoods Software
 * @version: 1.0.0
 *******************************************************************************/
package org.edgexfoundry.controller;

import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.edgexfoundry.fishX1.TA_INPUT;
import org.edgexfoundry.fishX1.TA_OUTPUT;

public class AutomationController {

    public final static int NotRunning = 0;
    public final static int Running = 1;

    private final static EdgeXLogger logger = EdgeXLoggerFactory.getEdgeXLogger(AutomationController.class);
    
    private boolean running=false;
    private Thread runningThread;
    
    private int requestedState=NotRunning;
    private String automationProcedureName="pickBoxUpAndMoveLeft";

    private TA_INPUT ta_input;
    private TA_OUTPUT ta_output;
    
    // It's a singleton
    private static AutomationController instance;
    public static synchronized AutomationController getInstance() {
        if (instance == null) {
            instance = new AutomationController();
        }
        return instance;
    }

    public void setTaInput(TA_INPUT in) {
	ta_input=in;
    }

    public void setTaOutput(TA_OUTPUT out) {
	ta_output=out;
    }
    
    public synchronized boolean setAutomationProcedureName(String procedure) {
	//TODO: validate and return false if procedure name is not valid
	automationProcedureName = procedure;
	return true;
    }

    public String getAutomationProcedureName() {
	return automationProcedureName;
    }

    public synchronized boolean setRequestedState(int state) {
	boolean rv=false;
	
	logger.debug("Entering setRequestedState(int state="+state+")");
	if (state != requestedState) {
	    requestedState = state;
	    rv=true;
	}
	else {
	    rv=false;
	}
	logger.debug("Leaving setRequestedState(int state="+state+")");
	return rv;
    }
	
    public synchronized boolean isRunning() {
	return running;
    }
    
    private synchronized void setRunning(boolean runningFlag) {
	logger.debug("Setting running flag to "+runningFlag);
	running=runningFlag;
    }

    public synchronized boolean invokeRequestedStateChange() {
	logger.debug("Entering invokeRequestedStateChange()");
	boolean rv=false;
	
	switch (requestedState)
	    {
	    case NotRunning:
		if (isRunning()) {
		    stop();
		    rv=true;
		}
		else {
		    logger.error("Called invokeRequestedStateChange(NotRunning) when automation was already not running.");
		    rv=true;
		}
		break;
	    case Running:
		if (! isRunning()) {
		    start();
		    rv=true;
		}
		else {
		    logger.error("Called invokeRequestedStateChange(Running) when automation was already running.");
		    rv=false;
		}
		break;
	    default:
		logger.error(requestedState+" is an invalid argument for invokeRequestedStateChange()");
		rv=false;
	       
	    }
	logger.debug("Leaving invokeRequestedStateChange()");
	return rv;
    }
    
    public synchronized boolean start() {
	logger.debug("Entering start()");
	setRunning(true);
	boolean rv=false;
	if (automationProcedureName.equals("pickBoxUpAndMoveLeft") ||
	    automationProcedureName.equals("reset")) {
	    (runningThread = new Thread(() -> this.doRobotAutomation(automationProcedureName))).start();
	    rv=true;
	}
	else {
	    setRunning(false);
	    rv=false;
	}

	logger.debug("Leaving start()");
	return rv;
    }

    private synchronized boolean stop() {
	//Stop thread
	runningThread.interrupt();
	stopAllMotors();
	return true;
    }

    final int ArmRotationMotor=1;
    final int ArmRotationCW=-512;
    final int ArmRotationCCW=512;
    
    final int ArmVerticalMotor=3; 
    final int ArmVerticalUp=-512;
    final int ArmVerticalDown=512;
    
    final int GripperMotor=4;
    final int GripperMotorClose=-512;
    final int GripperMotorOpen=512;
    
    final int InputPressed=1;
    final int InputReleased=0;

    private void stopAllMotors() {
	logger.debug("Entering stopAllMotors()");
	
	ta_output.setDuty(ArmRotationMotor, 0);
	ta_output.setDuty(ArmVerticalMotor, 0);
	ta_output.setDuty(GripperMotor, 0);

	logger.debug("Leaving stopAllMotors()");
    }

    private void doRobotAutomation(String name) {
	logger.error("Entering doRobotAutomation(name="+name+")");
	try {
	    if (name.equals("pickBoxUpAndMoveLeft")) {
		pickBoxUpAndMoveLeftProcedure();
	    }
	    else if (name.equals("reset")) {
		resetProcedure();
	    }

	}
	catch (InterruptedException e) {
	    stopAllMotors();
	    logger.error("doRobotAutomation(name="+name+") was interrupted");
	}

	setRunning(false);
	logger.debug("Leaving doRobotAutomation(name="+name+")");
    }
    
    private void resetProcedure() throws InterruptedException {
	logger.error("Entering resetProcedure()");
	
	try {
	    // Move Arm vertical motor back to top
	    if ( ta_input.getUni(ArmVerticalMotor) != InputPressed ) {
		logger.error("ta_output.setDuty(ArmVerticalMotor, ArmVerticalUp)");
		ta_output.setDistance(ArmVerticalMotor, 16383);
		ta_output.setDuty(ArmVerticalMotor, ArmVerticalUp);

		while ( ta_input.getUni(ArmVerticalMotor) != InputPressed ) {
		    Thread.sleep(25);
		}

		logger.error("ta_output.setDuty(ArmVerticalMotor, 0)");
		ta_output.setDuty(ArmVerticalMotor, 0);
	    }
	    
	    // Move Arm rotation motor CCW back to home
	    if ( ta_input.getUni(ArmRotationMotor) != InputPressed ) {
		logger.error("ta_output.setDuty(ArmRotationMotor, ArmRotationCW)");
		ta_output.setDistance(ArmRotationMotor, 16383);
		ta_output.setDuty(ArmRotationMotor, ArmRotationCW);

		while ( ta_input.getUni(ArmRotationMotor) != InputPressed ) {
		    Thread.sleep(25);
		}

		logger.error("ta_output.setDuty(ArmRotationMotor, 0)");
		ta_output.setDuty(ArmRotationMotor, 0);
	    }
	    	    
	    // Move gripper motor back to open
	    if ( ta_input.getUni(GripperMotor) != InputPressed ) {
		logger.error("ta_output.setDuty(GripperMotor, GripperMotorOpen)");
		ta_output.setDistance(GripperMotor, 16383);
		ta_output.setDuty(GripperMotor, GripperMotorOpen);

		while ( ta_input.getUni(GripperMotor) != InputPressed ) {
		    Thread.sleep(25);
		}
		logger.error("ta_output.setDuty(GripperMotor, 0)");
		ta_output.setDuty(GripperMotor, 0);
	    }
	}
	catch (InterruptedException interrupt) {
	    logger.error("Leaving resetProcedure() because it was interrupted");
	    throw(interrupt);
	}
	
	logger.error("Leaving resetProcedure() after normal finish");
    }

    private void moveMotorAsynchronous(int motor, int direction, int distance) {
	synchronized(ta_output) {
	    ta_output.setDistance(motor, distance);
	    ta_output.setDuty(motor, direction);
	}
    }
    
    private void moveMotorSynchronous(int motor, int direction, int distance) throws InterruptedException {
	try {
	    //logger.debug("Calling moveMotorSynchronous(motor="+motor+", direction="+direction+", distance="+distance+") counter="+ta_input.getCounter(motor));
	    logger.error("Calling moveMotorSynchronous(motor="+motor+", direction="+direction+", distance="+distance+") counter="+ta_input.getCounter(motor));

	    int priorToReset=ta_input.getCounter(motor);

	    // Reset counter if necessary
	    if (priorToReset !=0) {
	    	int numMsForCounterReset=0;
		
	        ta_output.resetCounter(motor);
		
	        //Test for "value has gone down" rather than actual zero, since counter may move slightly after the reset
	    	while(ta_input.getCounter(motor) >= priorToReset) {
		    Thread.sleep(10);
		    numMsForCounterReset += 10;
		    if (numMsForCounterReset > 1000) {		    
			break;
		    }
	    	}
	    	logger.error("Counter should be reset by now. numMsForCounterReset is " + numMsForCounterReset + "  ta_input.getCounter(" + motor + ") is " + ta_input.getCounter(motor));
            }
	    
	    
	    moveMotorAsynchronous(motor, direction, distance);
	    int currentCounter=0, previousCounter=0;
	    int stuckCount=0;
	    while (currentCounter < distance) {
		previousCounter=currentCounter;

		// Gripper motor counts much slower
		if (motor == GripperMotor) {
		    Thread.sleep(150);
		}
		else {
		    Thread.sleep(50);
		}
		
		currentCounter=ta_input.getCounter(motor);

		if (currentCounter == previousCounter) {
		    stuckCount++;
		}

		if (((motor == GripperMotor) && stuckCount > 5) ||
		    stuckCount > 10) {
		    logger.error("Exiting moveMotorSynchronous. motor=" + motor + " stuckCount=" + stuckCount + " currentCounter=" + currentCounter);
		    break;
		}
	    }
	    if (currentCounter >= distance) {
		logger.error("Exiting moveMotorSynchronous. motor=" + motor + " currentCounter=" + currentCounter + " distance="+distance);
	    }
	}
	catch (InterruptedException interrupt) {
	    throw(interrupt);
	}
    }

    private void pickBoxUpAndMoveLeftProcedure() throws InterruptedException {
	logger.debug("Entering pickBoxUpAndMoveLeftProcedure()");
	boolean loopForever = true;
	while (loopForever) {
	    try {
		
		resetProcedure();
		
		moveMotorSynchronous(ArmVerticalMotor, ArmVerticalDown, 1240);
		moveMotorSynchronous(GripperMotor, GripperMotorClose, 10);
		moveMotorSynchronous(ArmVerticalMotor, ArmVerticalUp, 700);
		moveMotorSynchronous(ArmRotationMotor, ArmRotationCCW, 800);
		moveMotorSynchronous(ArmVerticalMotor, ArmVerticalDown, 700);
		moveMotorSynchronous(GripperMotor, GripperMotorOpen, 9);
		moveMotorSynchronous(ArmVerticalMotor, ArmVerticalUp, 1230);
		
		moveMotorSynchronous(ArmVerticalMotor, ArmVerticalDown, 1230);
		moveMotorSynchronous(GripperMotor, GripperMotorClose, 9);
		moveMotorSynchronous(ArmVerticalMotor, ArmVerticalUp, 700);
		moveMotorSynchronous(ArmRotationMotor, ArmRotationCW, 797);
		moveMotorSynchronous(ArmVerticalMotor, ArmVerticalDown, 700);
		moveMotorSynchronous(GripperMotor, GripperMotorOpen, 10);
	    }
	    catch (InterruptedException interrupt) {
		logger.error("Leaving pickBoxUpAndMoveLeftProcedure() because it was interrupted");
		throw(interrupt);
	    }
	}
	
	logger.debug("Leaving pickBoxUpAndMoveLeftProcedure() after normal finish");
    }
}
