package iox.hla;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.portico.impl.hla1516e.types.HLA1516eAttributeHandleSet;
import org.portico.impl.hla1516e.types.HLA1516eHandle;
import org.portico.impl.hla1516e.types.time.DoubleTime;
import org.portico.lrc.PorticoConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hla.rti1516e.CallbackModel;
import hla.rti1516e.LogicalTime;
import hla.rti1516e.ObjectClassHandle;
import hla.rti1516e.ResignAction;
import hla.rti1516e.exceptions.AlreadyConnected;
import hla.rti1516e.exceptions.CallNotAllowedFromWithinCallback;
import hla.rti1516e.exceptions.ConnectionFailed;
import hla.rti1516e.exceptions.CouldNotCreateLogicalTimeFactory;
import hla.rti1516e.exceptions.CouldNotOpenFDD;
import hla.rti1516e.exceptions.ErrorReadingFDD;
import hla.rti1516e.exceptions.FederateAlreadyExecutionMember;
import hla.rti1516e.exceptions.FederateNotExecutionMember;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.InconsistentFDD;
import hla.rti1516e.exceptions.InvalidLocalSettingsDesignator;
import hla.rti1516e.exceptions.InvalidObjectClassHandle;
import hla.rti1516e.exceptions.NotConnected;
import hla.rti1516e.exceptions.RTIinternalError;
import hla.rti1516e.exceptions.RestoreInProgress;
import hla.rti1516e.exceptions.SaveInProgress;
import hla.rti1516e.exceptions.SynchronizationPointLabelNotAnnounced;
import hla.rti1516e.exceptions.UnsupportedCallbackModel;
import iox.hla.core.AbstractFederate;
import iox.hla.core.RTIAmbassadorException;

public class FederationService extends AbstractFederate implements Runnable {

	private static final Logger log = LoggerFactory.getLogger(FederationService.class);
	private Set<String> expectedFederates = new HashSet<String>();

	private Map<String, String> federation;
	double lookahead;
	boolean federationAttempted = false;
	boolean timeRegulationEnabled = false;
	boolean timeConstrainedEnabled = false;
	boolean granted = false;
	long timeInMillisec = 0;
	long timeDiff;
	boolean realtime = true;
	boolean running = false;
	boolean paused = false;
	Set<Double> pauseTimes = new TreeSet<Double>();
	double mainLoopStartTime = 0.0;
	double mainLoopEndTime = 0.0;
	boolean executionTimeRecorded = false;
	boolean killingFederation = false;
	double federationEndTime = 0.0;
	List<String> foms;
	protected FederateAmbassador fedAmb;

	public FederationService(Map<String, String> federation, List<String> foms) {
		super();
		this.federation = federation;
		this.foms = foms;
	}

	public void init() {
		log.info("Attempting to create federation \"" + federation.get("federation_name") + "\" ... ");
		try {
			log.info(FEDERATION_EVENTS.CREATING_FEDERATION.toString());
			this.fedAmb = new FederateAmbassador(rtiAmb);
			rtiAmb.connect(fedAmb, CallbackModel.HLA_EVOKED);
			rtiAmb.createFederationExecution(federation.get("federation_name"), getFoms());
			log.info(FEDERATION_EVENTS.FEDERATION_CREATED.toString());
		} catch (InconsistentFDD | ErrorReadingFDD | CouldNotOpenFDD | NotConnected | RTIinternalError
				| ConnectionFailed | InvalidLocalSettingsDesignator | UnsupportedCallbackModel | AlreadyConnected
				| CallNotAllowedFromWithinCallback | hla.rti1516e.exceptions.FederationExecutionAlreadyExists
				| MalformedURLException e) {
			log.error("", e);
		}
		log.info("created.\n");

		try {
			rtiAmb.joinFederationExecution(federation.get("federation_id"), federation.get("federation_name"));
		} catch (CouldNotCreateLogicalTimeFactory | FederationExecutionDoesNotExist | SaveInProgress | RestoreInProgress
				| FederateAlreadyExecutionMember | NotConnected | CallNotAllowedFromWithinCallback
				| RTIinternalError e1) {
			log.error("", e1);
		}

//		ObjectClassHandle objectClassHandle = new HLA1516eHandle(PorticoConstants.MOM_FEDERATION_OBJECT_HANDLE);
//		try {
//			log.debug(rtiAmb.getObjectClassName(objectClassHandle));
//			rtiAmb.subscribeObjectClassAttributes(objectClassHandle, new HLA1516eAttributeHandleSet());
//		} catch (SaveInProgress | RestoreInProgress | FederateNotExecutionMember | NotConnected | RTIinternalError
//				| InvalidObjectClassHandle | hla.rti1516e.exceptions.AttributeNotDefined
//				| hla.rti1516e.exceptions.ObjectClassNotDefined e) {
//			log.error("", e);
//		}
		
		try {
			// enableTimeConstrained();

			// enableTimeRegulation(logicalTime, lookahead);

			// enableAsynchronousDelivery();

			log.info("Registering synchronization points ... ");
			// REGISTER "ReadyToPopulate" SYNCHRONIZATION POINT
			rtiAmb.registerFederationSynchronizationPoint(SYNCH_POINTS.readyToPopulate.name(), null);
			tick();
			while (!fedAmb.isSychronizationPointRegistered(SYNCH_POINTS.readyToPopulate.name())) {
				Thread.sleep(500);
				tick();
			}

			// REGISTER "ReadyToRun" SYNCHRONIZATION POINT
			rtiAmb.registerFederationSynchronizationPoint(SYNCH_POINTS.readyToRun.name(), null);
			tick();
			while (!fedAmb.isSychronizationPointRegistered(SYNCH_POINTS.readyToRun.name())) {
				Thread.sleep(500);
				tick();
				// REGISTER "ReadyToResign" SYNCHRONIZATION POINT

				rtiAmb.registerFederationSynchronizationPoint(SYNCH_POINTS.readyToResign.name(), null);
				tick();
				while (!fedAmb.isSychronizationPointRegistered(SYNCH_POINTS.readyToResign.name())) {
					Thread.sleep(500);
					tick();
				}
			}
		} catch (RTIinternalError | FederateNotExecutionMember | SaveInProgress | RestoreInProgress | NotConnected
				| InterruptedException e) {
			log.error("", e);
		}

		log.info("done.\n");
	}

	public void run() {

	}

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

	@Override
	public void readyToPopulate() {
		try {
			synchronize(SYNCH_POINTS.readyToPopulate);
		} catch (CallNotAllowedFromWithinCallback | RTIinternalError | RTIAmbassadorException e) {
			log.error("", e);
		}
	}

	@Override
	public void readyToRun() {
		try {
			synchronize(SYNCH_POINTS.readyToRun);
		} catch (CallNotAllowedFromWithinCallback | RTIinternalError | RTIAmbassadorException e) {
			log.error("", e);
		}
	}

	public void readyToResign() {

	}

	public void destroyRTI() {
		setRtiAmb(null);
	}

	private void synchronize(SYNCH_POINTS point)
			throws CallNotAllowedFromWithinCallback, RTIinternalError, RTIAmbassadorException {
		log.info("waiting for announcement of the synchronization point " + point);
		while (!fedAmb.isSynchronizationPointPending(point.name())) {
			tick();
		}

		try {
			synchronized (rtiAmb) {
				rtiAmb.synchronizationPointAchieved(point.name());
			}
		} catch (SynchronizationPointLabelNotAnnounced | SaveInProgress | RestoreInProgress | FederateNotExecutionMember
				| NotConnected | RTIinternalError e) {
			log.error("", e);
		}

		log.info("waiting for federation to synchronize on synchronization point " + point.name());
		while (!fedAmb.isSynchronizationPointPending(point.name())) {
			tick();
		}
		log.info("federation synchronized on " + point.name());
	}

	private void resetTimeOffset() {
		timeInMillisec = (long) (getLogicalTime() * 1000);
		timeDiff = timeInMillisec - System.currentTimeMillis();
	}

	public double getCurrentTime() {
		return getLogicalTime();
	}

	// public URL[] prepareFoms(List<String> foms) {
	// List<URL> urls = new ArrayList<URL>();
	// for (String fileName : foms) {
	// File file = new File(fileName);
	// if (file.exists()) {
	// try {
	// URL url = file.toURI().toURL();
	// urls.add(url);
	// } catch (MalformedURLException e) {
	// log.error("", e);
	// }
	// }
	// }
	// return urls.toArray(new URL[urls.size()]);
	// }

	private void fireTimeUpdate(@SuppressWarnings("rawtypes") LogicalTime t) {
		fireTimeUpdate(((DoubleTime) t).getTime());
	}

	private void fireTimeUpdate(double t) {
		DoubleTime prevTime = new DoubleTime(0);
		prevTime.setTime(getLogicalTime());
		setLogicalTime(t);
		// support.firePropertyChange(PROP_LOGICAL_TIME,
		// Double.valueOf(prevTime.getTime()),
		// Double.valueOf(time.getTime()));
	}

	public void recordMainExecutionLoopStartTime() {
		System.out.println("Main execution loop of federation started at: " + new Date());
		mainLoopStartTime = System.currentTimeMillis();
	}

	public void recordMainExecutionLoopEndTime() {
		if (!executionTimeRecorded) {
			System.out.println("Main execution loop of federation stopped at: " + new Date());
			mainLoopEndTime = System.currentTimeMillis();
			executionTimeRecorded = true;
			double execTimeInSecs = (mainLoopEndTime - mainLoopStartTime) / 1000.0;
			if (execTimeInSecs > 0) {
				System.out.println("Total execution time of the main loop: " + execTimeInSecs + " seconds");
			}
		}
	}

	public void killEntireFederation() {
		killingFederation = true;

		recordMainExecutionLoopEndTime();

		// Kill the entire federation
		// String killCommand = "bash -x " + _stopScriptFilepath;
		// try {
		// System.out.println("Killing federation by executing: " + killCommand +
		// "\n\tIn directory: " + _c2wtRoot);
		// Runtime.getRuntime().exec(killCommand, null, new File(_c2wtRoot));
		// Runtime.getRuntime().exec(killCommand, null, new File(_c2wtRoot));
		// Runtime.getRuntime().exec(killCommand, null, new File(_c2wtRoot));
		// } catch (IOException e) {
		// System.out.println("Exception while killing the federation");
		// e.printStackTrace();
		// }
		System.exit(0);
	}
	// public RTIambassador rtiAmb throws RTIAmbassadorException {
	// if (rtiAmb == null) {
	// try {
	// rtiAmb = RtiFactoryFactory.getRtiFactory().getRtiAmbassador();
	// encoderFactory = RtiFactoryFactory.getRtiFactory().getEncoderFactory();
	// rtiAmb.connect(fedAmb, CallbackModel.HLA_EVOKED);
	// } catch (RTIinternalError | ConnectionFailed | InvalidLocalSettingsDesignator
	// | UnsupportedCallbackModel
	// | AlreadyConnected | CallNotAllowedFromWithinCallback e) {
	// throw new RTIAmbassadorException(e);
	// }
	// }
	// return rtiAmb;
	// }
	//
	// FederateAmbassador getFedAmb() {
	// if (fedAmb == null) {
	// fedAmb = new FederateAmbassador();
	// }
	// return fedAmb;
	// }

	public String getFederationId() {
		return federation.get(federation.get("federation_id"));
	}

	public Map<Integer, String> getDiscoveredFederates() {
		// TODO Auto-generated method stub
		return null;
	}

	public Set<String> getExpectedFederates() {
		// TODO Auto-generated method stub
		return null;
	}

	// public Set<FederateObject> getIncompleteFederates() {
	// // TODO Auto-generated method stub
	// return null;
	// }

	public String startSimulation() {
		// TODO Auto-generated method stub
		return null;
	}

	public String pauseSimulation() {
		// TODO Auto-generated method stub
		return null;
	}

	public String resumeSimulation() {
		// TODO Auto-generated method stub
		return null;
	}

	public String terminateSimulation() {
		// TODO Auto-generated method stub
		return null;
	}

	public void updateLogLevel(int level) {
		// TODO Auto-generated method stub

	}

	public URL[] getFoms() throws MalformedURLException {
		List<URL> urls = new ArrayList<URL>(foms.size());
		for (String s : foms) {
			File file = new File(s);
			if (file.exists()) {
				URL url = file.toURI().toURL();
				urls.add(url);
			} else {
				log.error("FATAL doesn't exist fom file=" + s);
			}
		}
		return urls.toArray(new URL[urls.size()]);
	}

	// federation.get(federation.get("federation_name")),
	// federation.get("FOM_file_name"),
	// federation.get("script_file_name"),
	// federation.get("dbName"),
	// federation.get("logLevel"),
	// new Boolean(federation.get("mode")),
	// federation.get("lockFilename"),
	// new Double(federation.get("step")),
	// new Double(federation.get("lookahead")),
	// new Boolean(federation.get("_terminateOnCOAFinish")),
	// new Double(federation.get("_federationEndTime")),
	// new Long(federation.get("seed4Dur")),
	// new Boolean(federation.get("autoStart")),
	// new Boolean(federation.get("gui")));
}