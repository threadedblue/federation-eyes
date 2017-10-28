	private synchronized void createFederation() throws Exception {

		federationAttempted = true;

		log.info("Waiting for \"" + SYNCH_POINTS.readyToPopulate.name() + "\" ... ");
		readyToPopulate();
		log.info("done.\n");

		log.info("Waiting for \"" + SYNCH_POINTS.readyToRun.name() + "\" ... ");
		readyToRun();
		log.info("done.\n");

		// AS ALL FEDERATES ARE READY TO RUN, WAIT 3 SECS FOR BRITNEY TO
		// INITIALIZE
		Thread.sleep(3000);

		fireTimeUpdate(0.0);

		// set time
		fireTimeUpdate(rtiAmb.queryLogicalTime());
		resetTimeOffset();

		// run rti on a separate thread
		Thread t = new Thread() {
			public void run() {

				try {
					recordMainExecutionLoopStartTime();

					int numStepsExecuted = 0;
					while (running) {
						if (realtime) {
							long sleep_time = timeInMillisec - (timeDiff + System.currentTimeMillis());
							while (sleep_time > 0 && realtime) {
								long localSleepTime = sleep_time;
								if (localSleepTime > 1000)
									localSleepTime = 1000;
								Thread.sleep(localSleepTime);
								sleep_time = timeInMillisec - (timeDiff + System.currentTimeMillis());
							}
						}

						if (!paused) {
							synchronized (rtiAmb) {

								// executeCOAGraph();

								DoubleTime next_time = new DoubleTime(getLogicalTime() + getStepsize());
								System.out.println("Current_time = " + getLogicalTime() + " and step = " + getStepsize()
										+ " and requested_time = " + next_time.getTime());
								rtiAmb.timeAdvanceRequest(next_time);
								if (realtime) {
									timeDiff = timeInMillisec - System.currentTimeMillis();
								}

								// wait for grant
								granted = false;
								int numTicks = 0;
								boolean stuckWhileWaiting = false;
								while (!granted && running) {
									tick();
								}
								numTicks = 0;

								numStepsExecuted++;

								// if we passed next pause time go to pause mode
								Iterator<Double> it = pauseTimes.iterator();
								if (it.hasNext()) {
									double pause_time = it.next();
									if (getLogicalTime() > pause_time) {
										it.remove();
										pauseSimulation();
									}
								}
							}

							if (numStepsExecuted == 10) {
								log.info("Federation manager current time = " + getLogicalTime());
								numStepsExecuted = 0;
							}
						} else {
							Thread.sleep(10);
						}

						// If we have reached federation end time (if it was
						// configured), terminate the federation
						if (federationEndTime > 0 && getLogicalTime() > federationEndTime) {
							terminateSimulation();
						}

					}

					log.info("Waiting for \"ReadyToResign\" ... ");
					readyToResign();
					log.info("done.\n");

					// waitForFederatesToResign();

					// destroy federation
					rtiAmb.resignFederationExecution(ResignAction.DELETE_OBJECTS);
					rtiAmb.destroyFederationExecution(getFederationName());
					destroyRTI();

					// In case some federate is still hanging around
					killEntireFederation();
				}

				catch (Exception e) {
					e.printStackTrace();
				}
			}
		};

		running = true;
		t.start();
	}