#!/usr/bin/python
# -*- coding: utf-8 -*-
#
# Copyright (C) 2005-2009 University of Deusto
# All rights reserved.
#
# This software is licensed as described in the file COPYING, which
# you should have received as part of this distribution.
#
# This software consists of contributions made by many individuals, 
# listed below:
#
# Author: Pablo Orduña <pablo@ordunya.com>
# 

import sys
try:
    import ldap
except ImportError:
    LDAP_AVAILABLE = False
else:
    LDAP_AVAILABLE = True

import voodoo.log as log
import weblab.data.ClientAddress as ClientAddress
import weblab.login.database.dao.UserAuth as UserAuth
import weblab.exceptions.login.LoginExceptions as LoginExceptions

class LoginAuth(object):

    HANDLERS = ()

    @staticmethod
    def create(user_auth):
        for i in LoginAuth.HANDLERS:
            if i.NAME == user_auth.name:
                return i(user_auth)
        raise LoginExceptions.LoginUserAuthNotImplementedException(
                "UserAuth not implemented in LoginAuth: %s" % (
                    user_auth.name
                )
            )
    
    def authenticate(self, login, password):
        raise NotImplementedError("This method should be implemented by the subclass")

    def __repr__(self):
        return "<LoginAuth class='%s'><UserAuth>%s</UserAuth></LoginAuth>" % (self.__class__, (getattr(self,'_user_auth') or 'Not available'))

# TODO: no test actually test the real ldap module. The problem is
# requiring a LDAP server in the integration machine (in our integration
# machine it's not a problem, but running those test in "any computer"
# might be a bigger problem)

if LDAP_AVAILABLE:
    class _LdapProvider(object):
        def __init__(self):
            self.ldap_module = ldap
        def get_module(self):
            return self.ldap_module

    _ldap_provider = _LdapProvider()

class LdapLoginAuth(LoginAuth):

    NAME = UserAuth.LdapUserAuth.NAME

    def __init__(self, user_auth):
        self._user_auth = user_auth

    def authenticate(self, login, password):
        if not LDAP_AVAILABLE:
            msg = "The optional library 'ldap' is not available. The users trying to be authenticated with LDAP will not be able to do so. %s tried to do it. " % login
            print >> sys.stderr, msg
            log.log(self, log.LogLevel.Error, msg)
            return False

        password = str(password)
        ldap_module = _ldap_provider.get_module()
        try:
            ldapobj = ldap_module.initialize(
                self._user_auth.ldap_uri
            )
        except Exception, e:
            raise LoginExceptions.LdapInitializingException(
                "Exception initializing the LDAP module: %s" % e
            )

        dn = "%s@%s" % (login, self._user_auth.domain)
        pw = password

        try:
            ldapobj.simple_bind_s(dn, pw)
        except ldap.INVALID_CREDENTIALS, e:
            return False
        except Exception, e:
            raise LoginExceptions.LdapBindingException(
                "Exception binding to the server: %s" % e
            )
        else:
            ldapobj.unbind_s()
            return True

LoginAuth.HANDLERS += (LdapLoginAuth,)


class TrustedIpAddressesLoginAuth(LoginAuth):
    NAME = UserAuth.TrustedIpAddressesUserAuth.NAME

    def __init__(self, user_auth):
        self._user_auth = user_auth

    def authenticate(self, login, password):
        if not isinstance(password, ClientAddress.ClientAddress):
            return False
        client_address = password.client_address
        return client_address in self._user_auth.addresses

LoginAuth.HANDLERS += (TrustedIpAddressesLoginAuth,)


class WebLabDBLoginAuth(LoginAuth):
    NAME = UserAuth.TrustedIpAddressesUserAuth.NAME

    def __init__(self, user_auth):
        self._user_auth = user_auth

    def authenticate(self, login, password):
        if not isinstance(password, ClientAddress.ClientAddress):
            return False
        client_address = password.client_address
        return client_address in self._user_auth.addresses

LoginAuth.HANDLERS += (TrustedIpAddressesLoginAuth,)