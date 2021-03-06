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
#

import voodoo.log as log
from voodoo.log import logged
from weblab.core.coordinator.exc import NoSchedulerFoundError, ExpiredSessionError
from weblab.core.coordinator.scheduler import Scheduler
from voodoo.override import Override

import weblab.core.coordinator.status as WSS

DEBUG = False

#############################################################
#
# The Independent Scheduler Aggregator aggregates different
# schedulers that schedule independent resources. For
# instance, one experiment that can be executed in two
# different types of "rigs" will be waiting in two queues
# at the same time. This class will handle established
# policies such as priorities among schedulers.
#
# Take into account that a possible scheduler is an external
# scheduler (such as another WebLab-Deusto). Therefore
# policies can become complex here.
#
# In summary, IndependentSchedulerAggregator is an 'OR'
# aggregator, since it reserves in all the aggregated
# schedulers and then it it responds with the best option.
#
# Therefore, while it is compliant with the Scheduler
# API, it can not be configured as such in the core config
# files: it will be created by the coordinator in the
# constructor, creating one per experiment_id.
#
class IndependentSchedulerAggregator(Scheduler):

    def __init__(self, generic_scheduler_arguments, experiment_id, schedulers, particular_configuration):
        super(IndependentSchedulerAggregator, self).__init__(generic_scheduler_arguments)
        if len(schedulers) == 0:
            # This case should never happen given that if the experiment_id
            # exists, then there should be at least one scheduler for that
            raise NoSchedulerFoundError("No scheduler provider at IndependentSchedulerAggregator")

        remote_schedulers = []
        local_schedulers  = []
        for resource_type_name in schedulers:
            scheduler = schedulers[resource_type_name]
            if scheduler.is_remote():
                remote_schedulers.append(resource_type_name)
            else:
                local_schedulers.append(resource_type_name)

        #
        # Local schedulers go first
        #
        self.sorted_schedulers = local_schedulers + remote_schedulers

        log.log( IndependentSchedulerAggregator, log.level.Info,
                 "Creating a new IndependentSchedulerAggregator. experiment_id: %s; schedulers: %s" % (experiment_id, self.sorted_schedulers), max_size = 100000)

        self.experiment_id            = experiment_id
        self.schedulers               = schedulers
        self.particular_configuration = particular_configuration

    def stop(self):
        pass

    @logged()
    @Override(Scheduler)
    def is_remote(self):
        return True

    @logged()
    @Override(Scheduler)
    def removing_current_resource_slot(self, session, resource_instance):
        pass

    @logged()
    @Override(Scheduler)
    def reserve_experiment(self, reservation_id, experiment_id, time, priority, initialization_in_accounting, client_initial_data, request_info):
        all_reservation_status = {}

        used_schedulers = []
        any_assigned = False
        for resource_type_name in self.sorted_schedulers:

            # TODO: catch possible exceptions and "continue"

            scheduler = self.schedulers[resource_type_name]

            self.resources_manager.associate_scheduler_to_reservation(reservation_id, self.experiment_id, resource_type_name)


            reservation_status = scheduler.reserve_experiment(reservation_id, experiment_id, time, priority, initialization_in_accounting, client_initial_data, request_info)
            if reservation_status is None:
                self.resources_manager.dissociate_scheduler_from_reservation(reservation_id, self.experiment_id, resource_type_name)
                continue

            all_reservation_status[resource_type_name] = reservation_status

            if not reservation_status.status in WSS.WebLabSchedulingStatus.NOT_USED_YET_EXPERIMENT_STATUS:
                any_assigned = True
                break
            else:
                used_schedulers.append(resource_type_name)

        if any_assigned:
            for resource_type_name in used_schedulers:
                used_scheduler = self.schedulers[resource_type_name]
                log.log(
                    IndependentSchedulerAggregator, log.level.Info,
                    "reserve_experiment: %s: assigned to %s, so finishing in scheduler %s" % (reservation_id, scheduler.__class__.__name__, used_scheduler.__class__.__name__), max_size = 1000)

                used_scheduler.finish_reservation(reservation_id)
                all_reservation_status.pop(resource_type_name)
                self.resources_manager.dissociate_scheduler_from_reservation(reservation_id, self.experiment_id, resource_type_name)

        return self.select_best_reservation_status(all_reservation_status.values())

    @logged()
    @Override(Scheduler)
    def get_reservation_status(self, reservation_id):
        all_reservation_status = {}

        if DEBUG:
            print
            client = self.redis_maker()
            url = str(client.connection_pool.connection_kwargs.get('db', 0))

            if url.endswith('2'):
                tabs = '\t\t'
            elif url.endswith('1'):
                tabs = '\t'
            else:
                tabs = ''

            print tabs, "<", url, self.schedulers.values(), ">"

        self.reservations_manager.lock_reservation(reservation_id)
        try:
            assigned_resource_type_name = None

            reservation_schedulers = self.resources_manager.retrieve_schedulers_per_reservation(reservation_id, self.experiment_id)

            for resource_type_name in reservation_schedulers:
                scheduler = self.schedulers[resource_type_name]
                try:
                    reservation_status = scheduler.get_reservation_status(reservation_id)
                except:
                    new_reservation_schedulers = self.resources_manager.retrieve_schedulers_per_reservation(reservation_id, self.experiment_id)
                    if resource_type_name in new_reservation_schedulers:
                        raise
                    else:
                        continue # That scheduler is not relevant anymore
                if DEBUG:
                    print tabs, scheduler, reservation_status
                all_reservation_status[resource_type_name] = reservation_status

                if not reservation_status.status in WSS.WebLabSchedulingStatus.NOT_USED_YET_EXPERIMENT_STATUS:
                    assigned_resource_type_name = resource_type_name
                    log.log(
                        IndependentSchedulerAggregator, log.level.Info,
                        "get_reservation_status: %s: assigned to %s" % (reservation_id, scheduler.__class__.__name__), max_size = 1000)
                    break

            log.log(
                IndependentSchedulerAggregator, log.level.Debug,
                "Got reservation_status (%s) for reservation_id %s. Got assigned? %s" % (str(all_reservation_status.values()), reservation_id, assigned_resource_type_name is not None ), max_size = 100000)

            if assigned_resource_type_name is not None:
                for resource_type_name in reservation_schedulers:
                    if resource_type_name != assigned_resource_type_name:
                        new_reservation_schedulers = self.resources_manager.retrieve_schedulers_per_reservation(reservation_id, self.experiment_id)
                        if resource_type_name not in new_reservation_schedulers:
                            continue

                        self.resources_manager.dissociate_scheduler_from_reservation(reservation_id, self.experiment_id, resource_type_name)
                        used_scheduler = self.schedulers[resource_type_name]
                        log.log(
                            IndependentSchedulerAggregator, log.level.Info,
                            "get_reservation_status: %s: finishing from %s" % (reservation_id, used_scheduler.__class__.__name__), max_size = 1000)
                        used_scheduler.finish_reservation(reservation_id)
                        all_reservation_status.pop(resource_type_name, None)


            if len(all_reservation_status) == 0:
                raise ExpiredSessionError("Expired reservation")

            best_reservation = self.select_best_reservation_status(all_reservation_status.values())
            log.log(
                IndependentSchedulerAggregator, log.level.Info,
                "Had to select for reservation_id %s among %s and chose %s" % (reservation_id, str(all_reservation_status.values()), str(best_reservation) ), max_size = 100000)
        finally:
            self.reservations_manager.unlock_reservation(reservation_id)

        if DEBUG:
            print tabs, "</", url, best_reservation, "/>"
            print
        return best_reservation


    def select_best_reservation_status(self, all_reservation_status):
        if len(all_reservation_status) == 0:
            import traceback
            traceback.print_stack()
            raise NoSchedulerFoundError("There must be at least one reservation status, zero provided!")

        return sorted(all_reservation_status)[0]

    @logged()
    @Override(Scheduler)
    def confirm_experiment(self, reservation_id, lab_session_id, initial_configuration):
        resource_type_names = self.resources_manager.retrieve_schedulers_per_reservation(reservation_id, self.experiment_id)
        for resource_type_name in resource_type_names:
            scheduler = self.schedulers[resource_type_name]
            scheduler.confirm_experiment(reservation_id, lab_session_id, initial_configuration)

    @logged()
    @Override(Scheduler)
    def finish_reservation(self, reservation_id):
        for resource_type_name in self.resources_manager.retrieve_schedulers_per_reservation(reservation_id, self.experiment_id):
            scheduler = self.schedulers[resource_type_name]
            scheduler.finish_reservation(reservation_id)

        self.resources_manager.clean_associations_for_reservation(reservation_id, self.experiment_id)

    @Override(Scheduler)
    def _clean(self):
        pass

###########################################################
#
# The SharedSchedulerAggregator is a scheduler aggregator
# for resources being shared through more than one
# scheduler. We could say that it is an 'AND' scheduler:
# it reserves only in one scheduler.
#
# An interesting use case for this class is if an
# experiment can be shared through queues and booking.
# Depending on the reservation, it will only call one of
# the schedulers (the most appropriate). But whenever we
# want to know free slots or position in a queue, we
# have to ask all the aggregated systems.
#
# Another possible use case for this class would be
# sharing a single experiment through different mechanisms,
# such as weblabdeusto and an external one (VISIR, iLabs,
# Sahara). One of the schedulers would check in the
# internal database and define whether it is possible to
# serve the experiment or not
#

class SharedSchedulerAggregator(object):

    def __init__(self, generic_scheduler_arguments, schedulers, particular_configuration):
        super(SharedSchedulerAggregator, self).__init__(generic_scheduler_arguments)
        self.particular_configuration = particular_configuration

    def stop(self):
        pass

    @logged()
    @Override(Scheduler)
    def is_remote(self):
        return False

    @logged()
    @Override(Scheduler)
    def removing_current_resource_slot(self, session, resource_instance):
        pass

    @logged()
    @Override(Scheduler)
    def reserve_experiment(self, reservation_id, experiment_id, time, priority, initialization_in_accounting, client_initial_data, request_info):
        pass

    @logged()
    @Override(Scheduler)
    def get_reservation_status(self, reservation_id):
        pass

    @logged()
    @Override(Scheduler)
    def confirm_experiment(self, reservation_id, lab_session_id, initial_configuration):
        pass

    @logged()
    @Override(Scheduler)
    def finish_reservation(self, reservation_id):
        pass

    @Override(Scheduler)
    def _clean(self):
        pass

