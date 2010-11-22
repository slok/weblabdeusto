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
# Author: Jaime Irurzun <jaime.irurzun@gmail.com>
# 

from voodoo.override import Override
from weblab.exceptions.experiment.experiments.ud_xilinx_experiment.UdXilinxExperimentExceptions import InvalidDeviceToProgramException
from weblab.experiment.devices.digilent_adept.DigilentAdept import DigilentAdept
from weblab.experiment.devices.jtag_blazer.JTagBlazer import JTagBlazer


class UdXilinxProgrammer(object):
    
    def __init__(self, cfg_manager, xilinx_impact_device):
        super(UdXilinxProgrammer, self).__init__()
        self._cfg_manager = cfg_manager
        self._xilinx_impact_device = xilinx_impact_device
        
    @staticmethod
    def create(device_name, cfg_manager, xilinx_impact_device):
        if device_name == 'XilinxImpact':
            return XilinxImpactProgrammer(cfg_manager, xilinx_impact_device)
        elif device_name == 'JTagBlazer':
            return JTagBlazerSvfProgrammer(cfg_manager, xilinx_impact_device)
        elif device_name == 'DigilentAdept':
            return DigilentAdeptSvfProgrammer(cfg_manager, xilinx_impact_device)
        else:
            raise InvalidDeviceToProgramException(device_name)   
    
    def program(self, *args, **kargs):
        raise NotImplementedError("This method must be overriden in a subclass.")
    
    
class XilinxImpactProgrammer(UdXilinxProgrammer):
    
    def __init__(self, cfg_manager, xilinx_impact_device):
        super(XilinxImpactProgrammer, self).__init__(cfg_manager, xilinx_impact_device)
    
    @Override(UdXilinxProgrammer)
    def program(self, file_name):
        self._xilinx_impact_device.program_device(file_name)
    
    
class JTagBlazerSvfProgrammer(UdXilinxProgrammer):
    
    def __init__(self, cfg_manager, xilinx_impact_device):
        super(JTagBlazerSvfProgrammer, self).__init__(cfg_manager, xilinx_impact_device)
        self._jtag_blazer = JTagBlazer(cfg_manager)
    
    @Override(UdXilinxProgrammer)
    def program(self, file_name):
        self._xilinx_impact_device.source2svf(file_name)
        svf_file_name = file_name.replace("."+self._xilinx_impact_device.get_suffix(), ".svf")
        device_ip = self._cfg_manager.get_value('xilinx_jtag_blazer_device_ip_' + self._xilinx_impact_device.get_name())
        self._jtag_blazer.program_device(svf_file_name, device_ip)
    
    
class DigilentAdeptSvfProgrammer(UdXilinxProgrammer):
    
    def __init__(self, cfg_manager, xilinx_impact_device):
        super(DigilentAdeptSvfProgrammer, self).__init__(cfg_manager, xilinx_impact_device)
        self._digilent_adept = DigilentAdept(cfg_manager)
    
    @Override(UdXilinxProgrammer)
    def program(self, file_name):
        self._xilinx_impact_device.source2svf(file_name)
        svf_file_name = file_name.replace("."+self._xilinx_impact_device.get_suffix(), ".svf")
        self._digilent_adept.program_device(svf_file_name)