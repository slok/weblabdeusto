#!/usr/bin/env python
#-*-*- encoding: utf-8 -*-*-
#
# Copyright (C) 2005 onwards University of Deusto
# All rights reserved.
#
# This software is licensed as described in the file COPYING, which
# you should have received as part of this distribution.
#
# This software consists of contributions made by many individuals,
# listed below:
#
# Author: Pablo Orduña <pablo@ordunya.com>
#         Luis Rodriguez <luis.rodriguez@opendeusto.es>
#

from voodoo.log import logged
import voodoo.sessions.session_id as SessionId
from weblab.data.experiments import ExperimentId

import weblab.comm.manager as RFM
from weblab.core.comm.user_manager import EXCEPTIONS

class AbstractAdminRemoteFacadeManager(RFM.AbstractRemoteFacadeManager):

    @logged()
    @RFM.check_exceptions(EXCEPTIONS)
    @RFM.check_nullable
    def get_groups(self, session_id, parent_id = None):
        sess_id = self._parse_session_id(session_id)
        response = self._server.get_groups(sess_id, parent_id)
        return response

    @logged()
    @RFM.check_exceptions(EXCEPTIONS)
    @RFM.check_nullable
    def get_roles(self, session_id):
        sess_id = self._parse_session_id(session_id)
        response = self._server.get_roles(sess_id)
        return response

    @logged()
    @RFM.check_exceptions(EXCEPTIONS)
    @RFM.check_nullable
    def get_users(self, session_id):
        """
        get_users(session_id)

        @param session_id A possibly-encoded session ID
        @return Users and its data, possibly encoded as well
        """
        sess_id = self._parse_session_id(session_id)
        response = self._server.get_users(sess_id)
        return response

    @logged()
    @RFM.check_exceptions(EXCEPTIONS)
    @RFM.check_nullable
    def get_user_permissions(self, session_id):
        """
        get_user_permissions(session_id)

        @param session_id A possibly-encoded session ID
        @return User permissions
        """
        sess_id = self._parse_session_id(session_id)
        response = self._server.get_user_permissions(sess_id)
        return response

    @logged()
    @RFM.check_exceptions(EXCEPTIONS)
    @RFM.check_nullable
    def get_permission_types(self, session_id):
        sess_id = self._parse_session_id(session_id)
        response = self._server.get_permission_types(sess_id)
        return response


    @logged()
    @RFM.check_exceptions(EXCEPTIONS)
    @RFM.check_nullable
    def get_experiments(self, session_id):
        sess_id = self._parse_session_id(session_id)
        response = self._server.get_experiments(sess_id)
        return response

    @logged()
    @RFM.check_exceptions(EXCEPTIONS)
    @RFM.check_nullable
    def get_experiment_uses(self, session_id, from_date, to_date, group_id, experiment_id, start_row, end_row, sort_by):
        sess_id = self._parse_session_id(session_id)
        response = self._server.get_experiment_uses(sess_id, from_date, to_date, group_id, experiment_id, start_row, end_row, sort_by)
        return response

    def _check_nullable_response(self, response):
        # This is the default behaviuor. Overrided by XML-RPC,
        # where None is not an option
        return response

class AdminRemoteFacadeManagerJSON(RFM.AbstractJSON, AbstractAdminRemoteFacadeManager):
    def _parse_session_id(self, session_id):
        return SessionId.SessionId(session_id['id'])

    def _parse_experiment_id(self, exp_id):
        return ExperimentId(
            exp_id['exp_name'],
            exp_id['cat_name']
        )



