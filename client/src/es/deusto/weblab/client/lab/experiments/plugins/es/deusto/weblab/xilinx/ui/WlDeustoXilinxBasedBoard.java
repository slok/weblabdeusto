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
package es.deusto.weblab.client.lab.experiments.plugins.es.deusto.weblab.xilinx.ui;

import java.util.Vector;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import es.deusto.weblab.client.comm.exceptions.WlCommException;
import es.deusto.weblab.client.configuration.IConfigurationRetriever;
import es.deusto.weblab.client.dto.experiments.Command;
import es.deusto.weblab.client.dto.experiments.ResponseCommand;
import es.deusto.weblab.client.lab.comm.UploadStructure;
import es.deusto.weblab.client.lab.comm.callbacks.IResponseCommandCallback;
import es.deusto.weblab.client.lab.experiments.commands.RequestWebcamCommand;
import es.deusto.weblab.client.lab.experiments.plugins.es.deusto.weblab.xilinx.commands.ExperimentFinishedCommand;
import es.deusto.weblab.client.lab.ui.BoardBase;
import es.deusto.weblab.client.ui.widgets.IWlActionListener;
import es.deusto.weblab.client.ui.widgets.WlClockActivator;
import es.deusto.weblab.client.ui.widgets.WlPredictiveProgressBar;
import es.deusto.weblab.client.ui.widgets.WlSwitch;
import es.deusto.weblab.client.ui.widgets.WlTimedButton;
import es.deusto.weblab.client.ui.widgets.WlTimer;
import es.deusto.weblab.client.ui.widgets.WlWaitingLabel;
import es.deusto.weblab.client.ui.widgets.WlWebcam;
import es.deusto.weblab.client.ui.widgets.WlButton.IWlButtonUsed;
import es.deusto.weblab.client.ui.widgets.WlPredictiveProgressBar.IProgressBarListener;
import es.deusto.weblab.client.ui.widgets.WlPredictiveProgressBar.IProgressBarTextUpdater;
import es.deusto.weblab.client.ui.widgets.WlTimer.IWlTimerFinishedCallback;

public class WlDeustoXilinxBasedBoard extends BoardBase{

	
	 /******************
	 * UIBINDER RELATED
	 ******************/
	
	interface WlDeustoXilinxBasedBoardUiBinder extends UiBinder<Widget, WlDeustoXilinxBasedBoard> {
	}

	private static final WlDeustoXilinxBasedBoardUiBinder uiBinder = GWT.create(WlDeustoXilinxBasedBoardUiBinder.class);
	
	private static final String XILINX_DEMO_PROPERTY                  = "is.demo";
	private static final boolean DEFAULT_XILINX_DEMO                  = false;
	
	private static final String XILINX_MULTIRESOURCE_DEMO_PROPERTY   = "is.multiresource.demo";
	private static final boolean DEFAULT_MULTIRESOURCE_XILINX_DEMO   = false;
	
	private static final String XILINX_WEBCAM_IMAGE_URL_PROPERTY      = "webcam.image.url";
	private static final String DEFAULT_XILINX_WEBCAM_IMAGE_URL       = GWT.getModuleBaseURL() + "/waiting_url_image.jpg";
	
	private static final String XILINX_WEBCAM_REFRESH_TIME_PROPERTY   = "webcam.refresh.millis";
	private static final int    DEFAULT_XILINX_WEBCAM_REFRESH_TIME    = 400;
	
	private static final int IS_READY_QUERY_TIMER = 1000;
	private static final String STATE_NOT_READY = "not_ready";
	private static final String STATE_PROGRAMMING = "programming";
	private static final String STATE_READY = "ready";
	private static final String STATE_FAILED = "failed";
	
	public static class Style{
		public static final String TIME_REMAINING         = "wl-time_remaining";
		public static final String CLOCK_ACTIVATION_PANEL = "wl-clock_activation_panel"; 
	}
	
	protected IConfigurationRetriever configurationRetriever;
	private static final ExperimentFinishedCommand experimentFinishedCommand = new ExperimentFinishedCommand();
	

	private static final boolean DEBUG_ENABLED = false;
	
	@UiField public VerticalPanel verticalPanel;
	@UiField VerticalPanel widget;
	@UiField VerticalPanel innerVerticalPanel;
	@UiField HorizontalPanel uploadStructurePanel;
	
	@UiField Label selectProgram;
	
	@UiField HorizontalPanel timerMessagesPanel;
	@UiField WlWaitingLabel messages;
	@UiField WlClockActivator clockActivator;

	@UiField HorizontalPanel switchesRow;
	@UiField HorizontalPanel buttonsRow;
	@UiField HorizontalPanel webcamPanel;
	
	@UiField WlPredictiveProgressBar progressBar;
	
	//@UiField(provided=true)
	private UploadStructure uploadStructure;
	
	private WlWebcam webcam;
	
	@UiField(provided = true)
	WlTimer timer;
	
	private Timer readyTimer;

	private final Vector<Widget> interactiveWidgets;
	
	public WlDeustoXilinxBasedBoard(IConfigurationRetriever configurationRetriever, IBoardBaseController boardController){
		super(boardController);
		
		this.configurationRetriever = configurationRetriever;
		
		this.interactiveWidgets = new Vector<Widget>();
		
		this.createProvidedWidgets();
		
		WlDeustoXilinxBasedBoard.uiBinder.createAndBindUi(this);
		
		this.webcamPanel.add(this.webcam.getWidget());
		
		this.findInteractiveWidgets();
		
		this.disableInteractiveWidgets();
		
		if(isDemo()){
			if(isMultiresourceDemo()){
				this.selectProgram.setText("This demo demonstrate the multiresource queues of WebLab-Deusto. You will use a CPLD or a FPGA depending on which one is available. You can test to log in ud-demo-pld and ud-demo-fpga and then log in this experiment to check that it will go to the one you free. If this wasn't a demo, you would select here the program that would be sent to the device. Since it could be harmful, in the demo we always send the same demonstration file.");
			}else{
				this.selectProgram.setText("If this wasn't a demo, you would select here the program that would be sent to the device. Since it could be harmful, in the demo we always send the same demonstration file.");
			}
		}
	}
	
	private boolean isDemo(){
		return this.configurationRetriever.getBoolProperty(
				WlDeustoXilinxBasedBoard.XILINX_DEMO_PROPERTY, 
				WlDeustoXilinxBasedBoard.DEFAULT_XILINX_DEMO
			);
	}
	
	private boolean isMultiresourceDemo(){
		return this.configurationRetriever.getBoolProperty(
				WlDeustoXilinxBasedBoard.XILINX_MULTIRESOURCE_DEMO_PROPERTY, 
				WlDeustoXilinxBasedBoard.DEFAULT_MULTIRESOURCE_XILINX_DEMO
			);
	}
	
	private String getWebcamImageUrl() {
		return this.configurationRetriever.getProperty(
				WlDeustoXilinxBasedBoard.XILINX_WEBCAM_IMAGE_URL_PROPERTY, 
				WlDeustoXilinxBasedBoard.DEFAULT_XILINX_WEBCAM_IMAGE_URL
			);
	}

	private int getWebcamRefreshingTime() {
		return this.configurationRetriever.getIntProperty(
				WlDeustoXilinxBasedBoard.XILINX_WEBCAM_REFRESH_TIME_PROPERTY, 
				WlDeustoXilinxBasedBoard.DEFAULT_XILINX_WEBCAM_REFRESH_TIME
			);
	}	
	
	/**
	 * Will find those interactive widgets that are defined on UiBinder
	 * and add them to the interactive widgets list, so that they can
	 * be disabled. This isn't too convenient but currently there doesn't 
	 * seem to be any other way around. That may change in the future.
	 */
	private void findInteractiveWidgets() {
		
		// Find switches
		for(int i = 0; i < this.switchesRow.getWidgetCount(); ++i){
			final Widget wid = this.switchesRow.getWidget(i);
			if(wid instanceof WlSwitch) {
				final WlSwitch swi = (WlSwitch)wid;
				this.addInteractiveWidget(swi);
			}
		}
		
		// Find timed buttons
		for(int i = 0; i < this.buttonsRow.getWidgetCount(); ++i) {
			final Widget wid = this.buttonsRow.getWidget(i);
			if(wid instanceof WlTimedButton) {
				final WlTimedButton swi = (WlTimedButton)wid;
				this.addInteractiveWidget(swi);
			}
		}
		
	}
	
	/**
	 * Creates those widgets that are specified in the UiBinder xml
	 * file but which are marked as provided because they can't be
	 * allocated using the default ctor.
	 */
	private void createProvidedWidgets() {
		this.webcam = new WlWebcam(
				this.getWebcamRefreshingTime(),
				this.getWebcamImageUrl()
			);
		
		this.timer = new WlTimer(false);
		
		this.timer.setTimerFinishedCallback(new IWlTimerFinishedCallback(){
			@Override
			public void onFinished() {
				WlDeustoXilinxBasedBoard.this.boardController.onClean();
			}
		});
		
		if(!isDemo()){
			this.uploadStructure = new UploadStructure();
			this.uploadStructure.setFileInfo("program");
		}
	}
	
	@Override
	public void initialize(){
		
		// Doesn't seem to work from UiBinder.
		if(!isDemo()){
			this.uploadStructurePanel.add(this.uploadStructure.getFormPanel());
		}
	
		this.webcam.setVisible(false);
	}
	
	@Override
	public void queued(){
	    this.widget.setVisible(false);
	    this.selectProgram.setVisible(false);
	}

	@Override
	public void start(){
		
		RequestWebcamCommand.createAndSend(this.boardController, this.webcam, 
				this.messages);
		
	    this.widget.setVisible(true);
	    this.selectProgram.setVisible(false);
	    
		this.loadWidgets();
		this.disableInteractiveWidgets();
		
		
		if(!isDemo()){
			this.uploadStructure.getFormPanel().setVisible(false);
		
			this.boardController.sendFile(this.uploadStructure, this.sendFileCallback);
		}else{
			
			this.boardController.sendCommand(WlDeustoXilinxBasedBoard.experimentFinishedCommand, this.sendExperimentFinishedRequestCommand);
		}
		
		
		// Start polling to know when the board has been programmed and the server is ready
		// to receive our requests.
		setupReadyTimer();
	}
	
	
	/**
	 * Will setup the timer that will poll the experiment server for its state, to
	 * know when the board programming process ends and how.
	 */
	private void setupReadyTimer() {
		
		this.readyTimer = new Timer() {
			@Override
			public void run() {
				
				// Build the command to query the state.
				final Command command = new Command() {
					@Override
					public String getCommandString() {
						return "STATE";
					}
				}; //! new Command
				
				
				// Send the command and react to the response
				WlDeustoXilinxBasedBoard.this.boardController.sendCommand(command, new IResponseCommandCallback() {
					@Override
					public void onFailure(WlCommException e) {
						WlDeustoXilinxBasedBoard.this.messages.setText("There was an error while trying to find out whether the experiment is ready");
					}
					@Override
					public void onSuccess(ResponseCommand responseCommand) {
						
						// Read the full message returned by the exp server and ensure it's not empty
						final String resp = responseCommand.getCommandString();
						if(resp.length() == 0) 
							WlDeustoXilinxBasedBoard.this.messages.setText("The STATE query returned an empty result");
						
						// The command follows the format STATE=ready
						// Extract both parts
						final String [] tokens = resp.split("=", 2);
						if(tokens.length != 2 || !tokens[0].equals("STATE")) {
							WlDeustoXilinxBasedBoard.this.messages.setText("Unexpected response ot the STATE query: " + resp);
							return;
						}
						
						final String state = tokens[1];
						
						if(state.equals(STATE_NOT_READY)) {
							WlDeustoXilinxBasedBoard.this.readyTimer.schedule(IS_READY_QUERY_TIMER);
						} else if(state.equals(STATE_READY)) {
							// Ready
							WlDeustoXilinxBasedBoard.this.onDeviceReady();
						} else if(state.equals(STATE_PROGRAMMING)) {
							WlDeustoXilinxBasedBoard.this.readyTimer.schedule(IS_READY_QUERY_TIMER);
						} else if(state.equals(STATE_FAILED)) {
							WlDeustoXilinxBasedBoard.this.onDeviceProgrammingFailed();
						} else {
							WlDeustoXilinxBasedBoard.this.messages.setText("Received unexpected response to the STATE query");
						}
					} //! onSuccess
				}); //! new IResponseCommandCallback for the STATE command.
			} //! run() of the Timer
		}; //! new Timer
		
		
		this.readyTimer.schedule(1000);
		
	} //! setupReadyTimer
	
	
	final IResponseCommandCallback sendFileCallback = new IResponseCommandCallback() {
	    
	    @Override
	    public void onSuccess(ResponseCommand response) {
	    	WlDeustoXilinxBasedBoard.this.messages.setText("File sent. Programming device");
	    }

	    @Override
	    public void onFailure(WlCommException e) {
	    	
		    if(WlDeustoXilinxBasedBoard.DEBUG_ENABLED)
		    	WlDeustoXilinxBasedBoard.this.enableInteractiveWidgets();
		    
	    	WlDeustoXilinxBasedBoard.this.messages.stop();
	    	
	    	WlDeustoXilinxBasedBoard.this.progressBar.stop();
	    	WlDeustoXilinxBasedBoard.this.progressBar.setTextUpdater(new IProgressBarTextUpdater(){
				@Override
				public String generateText(double progress) {
					return "Error. Could not complete.";
				}});
				
			WlDeustoXilinxBasedBoard.this.messages.setText("Error sending file: " + e.getMessage());
		    
	    }
	};	
	
	private final IResponseCommandCallback sendExperimentFinishedRequestCommand = new IResponseCommandCallback(){
		
	    @Override
	    public void onSuccess(ResponseCommand response) {
			if(response.getCommandString().equals("false")){
				final Timer timer = new Timer(){
					@Override
					public void run(){
						WlDeustoXilinxBasedBoard.this.boardController.sendCommand(WlDeustoXilinxBasedBoard.experimentFinishedCommand, WlDeustoXilinxBasedBoard.this.sendExperimentFinishedRequestCommand);
					}
				};
				timer.schedule(400);
			}else
				WlDeustoXilinxBasedBoard.this.sendFileCallback.onSuccess(new ResponseCommand(""));
	    }
	    
	    @Override
	    public void onFailure(WlCommException e) {
	    	
		    if(WlDeustoXilinxBasedBoard.DEBUG_ENABLED)
		    	WlDeustoXilinxBasedBoard.this.enableInteractiveWidgets();
		    
	    	WlDeustoXilinxBasedBoard.this.messages.stop();
				
			WlDeustoXilinxBasedBoard.this.messages.setText("Error sending command: " + e.getMessage());
	    }
	};
	
	
	/**
	 * Called when the STATE query tells us that the experiment is ready.
	 */
	private void onDeviceReady() {
    	// Make the bar finish in a few seconds, it will make itself
    	// invisible once it is full.
    	WlDeustoXilinxBasedBoard.this.progressBar.finish(1000);

		WlDeustoXilinxBasedBoard.this.enableInteractiveWidgets();
		WlDeustoXilinxBasedBoard.this.messages.setText("Device ready");
		WlDeustoXilinxBasedBoard.this.messages.stop();
	}
	
	
	/**
	 * Called when the STATE query tells us that the board programming failed.
	 */
	private void onDeviceProgrammingFailed() {
    	WlDeustoXilinxBasedBoard.this.progressBar.finish(300);
    	
		WlDeustoXilinxBasedBoard.this.messages.setText("Device programming failed");
		WlDeustoXilinxBasedBoard.this.messages.stop();	
	}
	
	private void loadWidgets() {
		
		this.webcam.setVisible(true);
		this.webcam.start();
		
		this.timer.start();
		
		this.messages.setText("Sending file");
		this.messages.start();
		
		this.progressBar.setTextUpdater(new IProgressBarTextUpdater(){
			@Override
			public String generateText(double progress) {
				return "Programming device (" + (int)(progress*100) + "%)";
			}});
		
		// Set up a listener to automatically remove the progress
		// bar whenever it reaches a 100%.
		this.progressBar.setListener(new IProgressBarListener() {
			@Override
			public void onFinished() {
				WlDeustoXilinxBasedBoard.this.progressBar.setVisible(false);
			}});
		
		this.progressBar.setWaitPoint(0.95);
		this.progressBar.setVisible(true);
		this.progressBar.setEstimatedTime(25000);
		this.progressBar.start();
		
		final ClockActivationListener clockActivationListener = new ClockActivationListener(this.boardController, this.getResponseCommandCallback());
		this.clockActivator.addClockActivationListener(clockActivationListener);
		
		this.addInteractiveWidget(this.timer.getWidget());
		this.addInteractiveWidget(this.clockActivator);
		
		this.prepareSwitchesRow();
		this.prepareButtonsRow();
		
		this.innerVerticalPanel.setSpacing(20);
	}
	
	private void addInteractiveWidget(Widget widget){
		this.interactiveWidgets.add(widget);
	}
	
	private void enableInteractiveWidgets(){
		for(int i = 0; i < this.interactiveWidgets.size(); ++i)
			this.interactiveWidgets.get(i).setVisible(true);		
	}
	
	private void disableInteractiveWidgets(){
		for(int i = 0; i < this.interactiveWidgets.size(); ++i)
			this.interactiveWidgets.get(i).setVisible(false);
	}
	
	/* Iterates through every switch in the switchesRow panel,
	 * setting up a listener for each of them. Switches found on it
	 * are defined anonymously on UiBinder, along with their title.
	 * This title is currently used as an integral identifier.
	 */
	private HorizontalPanel prepareSwitchesRow() {
		
		for(int i = 0; i < this.switchesRow.getWidgetCount(); ++i){
			final Widget wid = this.switchesRow.getWidget(i);
			if(wid instanceof WlSwitch) {
				final WlSwitch swi = (WlSwitch)wid;
				
				// Avoid trying to convert non-numerical titles (which serve
				// as identifiers). Not exactly an elegant way to do it.
				if(swi.getTitle().length() != 1) 
					continue;
				
				final int id = this.switchesRow.getWidgetCount() - Integer.parseInt(swi.getTitle()) - 1;
				final IWlActionListener actionListener = new SwitchListener(id, this.boardController, this.getResponseCommandCallback());
				swi.addActionListener(actionListener);
				this.addInteractiveWidget(swi);
			}
		}
		
		return this.switchesRow;
	}

	/*
	 * Iterates through every timed button in the buttonsRow panel,
	 * setting up a listener for each of them. Buttons found on it
	 * are defined anonymously on UiBinder, along with their title.
	 * This title is currently used as an integral identifier.
	 */
	private HorizontalPanel prepareButtonsRow() {

		for(int i = 0; i < this.buttonsRow.getWidgetCount(); ++i) {
			final Widget wid = this.buttonsRow.getWidget(i);
			if(wid instanceof WlTimedButton) {
				final WlTimedButton timedButton = (WlTimedButton)wid;
				
				if(timedButton.getTitle().length() != 1)
					continue;
				
				final int id = Integer.parseInt(timedButton.getTitle());
				final IWlButtonUsed buttonUsed = 
					new ButtonListener(id, this.boardController, this.getResponseCommandCallback());
				timedButton.addButtonListener(buttonUsed);
				this.addInteractiveWidget(timedButton);
			}
		}
		
		return this.buttonsRow;
	}

	@Override
	public void end(){
		
		if(this.readyTimer != null) {
			this.readyTimer.cancel();
			this.readyTimer = null;
		}
		
		if(this.webcam != null){
			this.webcam.dispose();
			this.webcam = null;
		}
		
		if(this.timer != null){
			this.timer.dispose();
			this.timer = null;
		}
		
		if(this.clockActivator != null){
			this.clockActivator.dispose();
			this.clockActivator = null;
		}
		
		for(int i = 0; i < this.switchesRow.getWidgetCount(); ++i) {
			final Widget wid = this.switchesRow.getWidget(i);
			if(wid instanceof WlSwitch)
				((WlSwitch)wid).dispose();
		}
		
		for(int i = 0; i < this.buttonsRow.getWidgetCount(); ++i) {
			final Widget wid = this.buttonsRow.getWidget(i);
			if(wid instanceof WlTimedButton)
				((WlTimedButton)wid).dispose();
		}
		
		if(this.progressBar != null)
			this.progressBar.stop();
		
		this.messages.stop();
	}
	
	@Override
	public void setTime(int time) {
		this.timer.updateTime(time);
	}
	
	@Override
	public Widget getWidget() {
		return this.widget;
	}
	
	protected IResponseCommandCallback getResponseCommandCallback(){
	    return new IResponseCommandCallback(){
		    @Override
			public void onSuccess(ResponseCommand responseCommand) {
	    		GWT.log("responseCommand: success", null);
		    }

		    @Override
			public void onFailure(WlCommException e) {
    			GWT.log("responseCommand: failure", null);
    			WlDeustoXilinxBasedBoard.this.messages.stop();
    			WlDeustoXilinxBasedBoard.this.messages.setText("Error sending command: " + e.getMessage());
		    }
		};	    
	}
}
