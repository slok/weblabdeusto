/*
* Copyright (C) 2005-2009 University of Deusto
* All rights reserved.
*
* This software is licensed as described in the file COPYING, which
* you should have received as part of this distribution.
*
* This software consists of contributions made by many individuals, 
* listed below:
*
* Author: Pablo Orduña <pablo@ordunya.com>
*
*/ 
package es.deusto.weblab.client.lab.experiments.plugins.es.deusto.weblab.vm.ui;

import java.util.HashMap;
import java.util.Map;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import es.deusto.weblab.client.comm.exceptions.WlCommException;
import es.deusto.weblab.client.configuration.IConfigurationManager;
import es.deusto.weblab.client.dto.experiments.Command;
import es.deusto.weblab.client.dto.experiments.ResponseCommand;
import es.deusto.weblab.client.lab.comm.callbacks.IResponseCommandCallback;
import es.deusto.weblab.client.lab.experiments.commands.RequestWebcamCommand;
import es.deusto.weblab.client.lab.experiments.plugins.es.deusto.weblab.logic.circuit.Circuit;
import es.deusto.weblab.client.lab.experiments.plugins.es.deusto.weblab.logic.circuit.CircuitParser;
import es.deusto.weblab.client.lab.experiments.plugins.es.deusto.weblab.logic.circuit.Gate;
import es.deusto.weblab.client.lab.experiments.plugins.es.deusto.weblab.logic.circuit.InvalidCircuitException;
import es.deusto.weblab.client.lab.experiments.plugins.es.deusto.weblab.logic.circuit.Operation;
import es.deusto.weblab.client.lab.experiments.plugins.es.deusto.weblab.logic.circuit.Switch;
import es.deusto.weblab.client.lab.experiments.plugins.es.deusto.weblab.logic.commands.GetCircuitCommand;
import es.deusto.weblab.client.lab.experiments.plugins.es.deusto.weblab.logic.commands.SolveCircuitCommand;
import es.deusto.weblab.client.lab.experiments.plugins.es.deusto.weblab.pic.ui.WlDeustoPicBasedBoard;
import es.deusto.weblab.client.lab.ui.BoardBase;
import es.deusto.weblab.client.ui.widgets.EasyGrid;
import es.deusto.weblab.client.ui.widgets.WlTimer;
import es.deusto.weblab.client.ui.widgets.WlWaitingLabel;
import es.deusto.weblab.client.ui.widgets.WlWebcam;
import es.deusto.weblab.client.ui.widgets.WlTimer.IWlTimerFinishedCallback;

public class VMBoard extends BoardBase {
	
	/******************
	 * UIBINDER RELATED
	 ******************/
	
	interface VMBoardUiBinder extends UiBinder<Widget, VMBoard> {
	}

	private static final VMBoardUiBinder uiBinder = GWT.create(VMBoardUiBinder.class);
	
	private static final int IS_READY_QUERY_TIMER = 1000;

	public static class Style   {
		public static final String TIME_REMAINING          = "wl-time_remaining";
		public static final String CLOCK_ACTIVATION_PANEL  = "wl-clock_activation_panel"; 
	}

	private final IConfigurationManager configurationManager;
	
	// Root panel.
	@UiField VerticalPanel widget;
	
	@UiField WlWaitingLabel messages;
	@UiField Label url;
	
	@UiField(provided = true) WlTimer timer;
	
	private Timer readyTimer;
	
	
	public VMBoard(IConfigurationManager configurationManager, IBoardBaseController commandSender) {
		super(commandSender);
		
		this.configurationManager = configurationManager;
		
		this.createProvidedWidgets();
		
		VMBoard.uiBinder.createAndBindUi(this);
	}
	
	/**
	 * Setup certain widgets that require it after the experiment has been 
	 * started.
	 */
	private void setupWidgets() {
		this.timer.setTimerFinishedCallback(new IWlTimerFinishedCallback(){
			@Override
			public void onFinished() {
			    VMBoard.this.boardController.onClean();
			}
		});
		this.timer.start();
		
		
		this.readyTimer = new Timer() {
			@Override
			public void run() {
				final Command command = new Command() {
					@Override
					public String getCommandString() {
						return "is_ready";
					}
				};
				
				VMBoard.this.boardController.sendCommand(command, new IResponseCommandCallback() {
					@Override
					public void onFailure(WlCommException e) {
						VMBoard.this.setMessage("There was an error while trying to find out whether the Virtual Machine is ready");
					}
					@Override
					public void onSuccess(ResponseCommand responseCommand) {
						final String resp = responseCommand.getCommandString();
						
						if(resp.equals("0")) {
							// Not ready
							VMBoard.this.setMessage("Your Virtual Machine is not yet ready. Please, wait.");
							VMBoard.this.readyTimer.schedule(IS_READY_QUERY_TIMER);
						} else if(resp.equals("1")) {
							// Ready
							VMBoard.this.setMessage("Your Virtual Machine is now ready!");
						} else {
							// Unexpected answer
							VMBoard.this.setMessage("Received unexpected response to the is_ready query");
						}
					}
				});
			}
		};
		
		// We do not do a Repeating schedule because we want to wait for a response before asking again
		this.readyTimer.schedule(IS_READY_QUERY_TIMER);
	}
	
	/* Creates those widgets that are placed through UiBinder but
	 * whose ctors need to take certain parameters and hence be
	 * instanced in code.
	 */
	private void createProvidedWidgets() {
		this.timer = new WlTimer(false);	
	}
	

	/**
	 * The initialize function gets called on the "reserve" stage,
	 * before the experiment starts.
	 */
	@Override
	public void initialize(){
	}	
	
	
	/**
	 * This function gets called just when the actual experiment starts, after
	 * the reserve is done and the queue is over.
	 */
	@Override
	public void start() {
	    this.widget.setVisible(true);
	    
	    this.setupWidgets();
	    
	    this.setMessage("Obtaining VM config...");
	    
	    this.sendGetConfigurationCommand();
	}	
	
	@Override
	public void setTime(int time) {
		this.timer.updateTime(time);
	}

	@Override
	public Widget getWidget() {
		return this.widget;
	}

	@Override
	public void end() {
		if(this.timer != null){
			this.timer.dispose();
			this.timer = null;
		}			
	}
	
	public void setMessage(String msg) {
		this.messages.setText(msg);
	}
	
	private void sendGetConfigurationCommand(){
		final Command command = new Command() {
			@Override
			public String getCommandString() {
				return "get_configuration";
			}
		};
		
		this.boardController.sendCommand(command, new IResponseCommandCallback() {
			@Override
			public void onFailure(WlCommException e) {
				VMBoard.this.setMessage("It was not possible to obtain the VM configuration");
			}
			@Override
			public void onSuccess(ResponseCommand responseCommand) {
				final String msg = "VM address:";
				VMBoard.this.setMessage(msg);
				VMBoard.this.url.setText(responseCommand.getCommandString());
			}
		});
	}
	
}