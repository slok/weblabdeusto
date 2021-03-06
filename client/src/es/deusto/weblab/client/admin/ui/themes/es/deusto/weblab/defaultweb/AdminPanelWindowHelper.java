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

package es.deusto.weblab.client.admin.ui.themes.es.deusto.weblab.defaultweb;

import java.util.ArrayList;

import es.deusto.weblab.client.dto.users.Group;

public class AdminPanelWindowHelper {

	public ArrayList<Group> extractGroupsTreeToList(ArrayList<Group> tree) {
		final ArrayList<Group> list = new ArrayList<Group>();
		this.extractGroupsTreeToListRecursively(tree, list);
		return list;
	}	
	
	private void extractGroupsTreeToListRecursively(ArrayList<Group> tree, ArrayList<Group> list) {
		for ( final Group group: tree ) {
			list.add(group);
			if ( group.getChildren().size() > 0 ) {
				this.extractGroupsTreeToListRecursively(group.getChildren(), list);
			}
		}
	}
	
}
