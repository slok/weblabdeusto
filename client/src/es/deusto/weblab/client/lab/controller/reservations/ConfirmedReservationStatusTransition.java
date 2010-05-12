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
package es.deusto.weblab.client.lab.controller.reservations;

import es.deusto.weblab.client.dto.experiments.ExperimentID;
import es.deusto.weblab.client.dto.reservations.ConfirmedReservationStatus;
import es.deusto.weblab.client.dto.reservations.ReservationStatus;
import es.deusto.weblab.client.lab.controller.ReservationStatusCallback;
import es.deusto.weblab.client.lab.experiments.exceptions.WlExperimentException;

public class ConfirmedReservationStatusTransition extends ReservationStatusTransition{
	public ConfirmedReservationStatusTransition(ReservationStatusCallback reservationStatusCallback) {
		super(reservationStatusCallback);
	}

	@Override
	public void perform(final ReservationStatus reservationStatus) {
		ConfirmedReservationStatusTransition.this.reservationStatusCallback.getPollingHandler().start();
		
		final ExperimentID experimentID = ConfirmedReservationStatusTransition.this.reservationStatusCallback.getExperimentBeingReserved();
		try {
			ConfirmedReservationStatusTransition.this.reservationStatusCallback.getUimanager().onExperimentReserved(
					(ConfirmedReservationStatus)reservationStatus,
					experimentID,
					ConfirmedReservationStatusTransition.this.reservationStatusCallback.getExperimentBaseBeingReserved()
				);
		} catch (final WlExperimentException e) {
			ConfirmedReservationStatusTransition.this.reservationStatusCallback.getUimanager().onError(e.getMessage());
			return;
		}
	}
}