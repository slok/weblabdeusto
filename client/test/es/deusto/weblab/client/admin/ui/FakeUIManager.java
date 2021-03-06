/*
* Copyright (C) 2005 onwards University of Deusto
* All rights reserved.
*
* This software is licensed as described in the file COPYING, which
* you should have received as part of this distribution.
*
* This software consists of contributions made by many individuals, 
* listed below:
*
* Author: Jaime Irurzun <jaime.irurzun@gmail.com>
*
*/ 

package es.deusto.weblab.client.admin.ui;

import es.deusto.weblab.client.dto.SessionID;
import es.deusto.weblab.client.dto.users.User;
import es.deusto.weblab.client.testing.util.WebLabFake;

public class FakeUIManager extends WebLabFake implements IUIManager {

    public static final String ON_INIT                              = "FakeUIManager::onInit";
    public static final String ON_LOGGED_IN                         = "FakeUIManager::onLoggedIn";
    public static final String ON_LOGGED_OUT                        = "FakeUIManager::onLoggedOut";
    
    public static final String ON_WRONG_LOGIN_OR_PASSWORD_GIVEN     = "FakeUIManager::onWrongLoginOrPasswordGiven";
    public static final String ON_NOT_ALLOWED_TO_ACCESS_ADMIN_PANEL = "FakeUIManager::onNotAllowedToAccessAdminPanel";
    public static final String ON_ERROR                             = "FakeUIManager::onError";
    public static final String ON_ERROR_AND_FINISH_SESSION          = "FakeUIManager::onErrorAndFinishSession";
    public static final String ON_MESSAGE                           = "FakeUIManager::onMessage";

    /*
     * Happy path scenario
     */
    
    @Override
    public void onInit() {
    	this.append(FakeUIManager.ON_INIT);
    }

    @Override
    public void onLoggedIn(User user, SessionID sessionId) {
    	this.append(FakeUIManager.ON_LOGGED_IN, new Object[]{user, sessionId});
    }

    @Override
    public void onLoggedOut() {
    	this.append(FakeUIManager.ON_LOGGED_OUT);
    }    

    /*
     * Alternative scenario
     */

    @Override
    public void onWrongLoginOrPasswordGiven() {
    	this.append(FakeUIManager.ON_WRONG_LOGIN_OR_PASSWORD_GIVEN);
    }       

	@Override
	public void onNotAllowedToAccessAdminPanel() {
		this.append(FakeUIManager.ON_NOT_ALLOWED_TO_ACCESS_ADMIN_PANEL);
	}
    
    @Override
    public void onError(String message) {
    	this.append(FakeUIManager.ON_ERROR, new Object[] {message});
    }

    @Override
    public void onErrorAndFinishSession(String message) {
    	this.append(FakeUIManager.ON_ERROR_AND_FINISH_SESSION, new Object[] {message});
    }
}