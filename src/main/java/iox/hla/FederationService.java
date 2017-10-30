package iox.hla;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.portico.impl.hla1516e.Rti1516eAmbassador;
import org.portico.impl.hla1516e.types.time.DoubleTime;
import org.portico.lrc.model.ObjectModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hla.rti1516e.CallbackModel;
import hla.rti1516e.InteractionClassHandle;
import hla.rti1516e.LogicalTime;
import hla.rti1516e.ParameterHandle;
import hla.rti1516e.exceptions.AlreadyConnected;
import hla.rti1516e.exceptions.CallNotAllowedFromWithinCallback;
import hla.rti1516e.exceptions.ConnectionFailed;
import hla.rti1516e.exceptions.CouldNotCreateLogicalTimeFactory;
import hla.rti1516e.exceptions.CouldNotOpenFDD;
import hla.rti1516e.exceptions.ErrorReadingFDD;
import hla.rti1516e.exceptions.FederateAlreadyExecutionMember;
import hla.rti1516e.exceptions.FederateNotExecutionMember;
import hla.rti1516e.exceptions.FederateServiceInvocationsAreBeingReportedViaMOM;
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.InconsistentFDD;
import hla.rti1516e.exceptions.InteractionClassNotDefined;
import hla.rti1516e.exceptions.InvalidLocalSettingsDesignator;
import hla.rti1516e.exceptions.NameNotFound;
import hla.rti1516e.exceptions.NotConnected;
import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.exceptions.RTIinternalError;
import hla.rti1516e.exceptions.RestoreInProgress;
import hla.rti1516e.exceptions.SaveInProgress;
import hla.rti1516e.exceptions.SynchronizationPointLabelNotAnnounced;
import hla.rti1516e.exceptions.UnsupportedCallbackModel;
import hla.rti1516e.time.HLAfloat64Time;
import hla.rti1516e.time.HLAfloat64TimeFactory;
import io.dropwizard.lifecycle.Managed;
import iox.hla.core.AbstractFederate;
import iox.hla.core.FederateAmbassador;
import iox.hla.core.InteractionRef;
import iox.hla.core.RTIAmbassadorException;

public class FederationService extends AbstractFederate implements Managed, Runnable {

	private static final Logger log = LoggerFactory.getLogger(FederationService.class);

	private Map<String, String> federation;
	boolean federationAttempted = false;
	boolean timeRegulationEnabled = false;
	boolean timeConstrainedEnabled = false;
	boolean granted = false;
	long timeInMillisec = 0;
	long timeDiff;
	boolean realtime = true;
	Set<Double> pauseTimes = new TreeSet<Double>();
	double mainLoopStartTime = 0.0;
	double mainLoopEndTime = 0.0;
	boolean executionTimeRecorded = false;
	boolean killingFederation = false;
	List<String> foms;
	private AtomicBoolean advancing = new AtomicBoolean(false);
	private AtomicBoolean started = new AtomicBoolean(false);
	private AtomicBoolean paused = new AtomicBoolean(true);
	private AtomicBoolean init = new AtomicBoolean(false);
	private final List<String> joinedFederates;
	private final List<String> resignedFederates;

	final String federationId;
	final String federateName;
	final boolean mode;
	final double lookahead;
	final int federationEndTime;

	public FederationService(Map<String, String> federation, List<String> foms) {

		this(federation.get("federationId"), federation.get("federateName"),
				Boolean.parseBoolean(federation.get("mode")), Integer.parseInt(federation.get("stepSize")),
				Double.parseDouble(federation.get("lookahead")), Integer.parseInt(federation.get("federationEndTime")),
				foms);
		this.federation = federation;
		this.foms = foms;
	}

	public FederationService(String federationId, String federateName, boolean mode, int stepSize, double lookahead,
			int federationEndTime, List<String> foms) {
		super();
		this.federationId = federationId;
		this.federateName = federateName;
		this.mode = mode;
		super.stepSize = stepSize;
		this.lookahead = lookahead;
		this.federationEndTime = federationEndTime;
		this.foms = foms;
		this.joinedFederates = new ArrayList<String>();
		this.resignedFederates = new ArrayList<String>();
	}

	public void stop() {
		terminateSimulation();
		try {
			rtiAmb.destroyFederationExecution(federationId);
		} catch (FederatesCurrentlyJoined | FederationExecutionDoesNotExist | NotConnected | RTIinternalError e) {
			log.error("" + e);
		}
	}

	public void start() {
		if (init.get()) {
			return;
		}

		log.info("Attempting to create federation \"" + federationId + "\" ... ");
		try {
			log.debug("fedAmb=" + fedAmb);
			log.debug("foms=" + getFoms());
			rtiAmb.connect(fedAmb, CallbackModel.HLA_EVOKED);
			rtiAmb.createFederationExecution(federationId, getFoms());
		} catch (InconsistentFDD | ErrorReadingFDD | CouldNotOpenFDD | NotConnected | RTIinternalError
				| ConnectionFailed | InvalidLocalSettingsDesignator | UnsupportedCallbackModel | AlreadyConnected
				| CallNotAllowedFromWithinCallback | hla.rti1516e.exceptions.FederationExecutionAlreadyExists
				| MalformedURLException e) {
			log.error("", e);
		}
		log.info("Created federation \"" + federationId + "\" ... ");
		Rti1516eAmbassador _1516 = (Rti1516eAmbassador) rtiAmb;
		ObjectModel om = _1516.getHelper().getFOM();
		log.debug("om=" + om);
		try {
			rtiAmb.joinFederationExecution(federateName, federationId);
			setTimeFactory((HLAfloat64TimeFactory) rtiAmb.getTimeFactory());
		} catch (CouldNotCreateLogicalTimeFactory | FederationExecutionDoesNotExist | SaveInProgress | RestoreInProgress
				| FederateAlreadyExecutionMember | NotConnected | CallNotAllowedFromWithinCallback | RTIinternalError
				| FederateNotExecutionMember e1) {
			log.error("", e1);
		}

		// ObjectClassHandle objectClassHandle = new
		// HLA1516eHandle(PorticoConstants.MOM_FEDERATION_OBJECT_HANDLE);
		// try {
		// log.debug(rtiAmb.getObjectClassName(objectClassHandle));
		// rtiAmb.subscribeObjectClassAttributes(objectClassHandle, new
		// HLA1516eAttributeHandleSet());
		// } catch (SaveInProgress | RestoreInProgress | FederateNotExecutionMember |
		// NotConnected | RTIinternalError
		// | InvalidObjectClassHandle | hla.rti1516e.exceptions.AttributeNotDefined
		// | hla.rti1516e.exceptions.ObjectClassNotDefined e) {
		// log.error("", e);
		// }

		try {

			enableTimeConstrained();
			enableTimeRegulation();
			enableAsynchronousDelivery();
			publishAndSubscribe();

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
				| InterruptedException | RTIAmbassadorException e) {
			log.error("", e);
		}
		waitForJoiners();
		init.set(true);
	}

	private void handleMessages() {
		log.trace("handleMessages==>");
		try {
			InteractionRef receivedInteraction;
			while ((receivedInteraction = fedAmb.nextInteraction()) != null) {
				log.trace("receivedInteraction=" + receivedInteraction);
				String interactionName = rtiAmb.getInteractionClassName(receivedInteraction.getInteractionClassHandle());
				if ("HLAinteractionRoot.JoinInteraction".equals(interactionName)) {
					ParameterHandle parameterHandle = rtiAmb
							.getParameterHandle(receivedInteraction.getInteractionClassHandle(), "federateName");
					byte[] value = receivedInteraction.getParameters().get(parameterHandle);
					String federateName = new String(value);
					joinedFederates.add(federateName);
					log.debug("Joined up=" + federateName);
				} else {
					log.debug("Not Joined up=" + interactionName);
				}
			}
		} catch (RTIexception e) {
			log.error("", e);
		}
		log.trace("<==handleMessages");
	}

	private void publishAndSubscribe() {
		InteractionClassHandle joinHandle = null;
		InteractionClassHandle resignHandle = null;
		try {
			joinHandle = rtiAmb.getInteractionClassHandle("JoinInteraction");
			rtiAmb.subscribeInteractionClass(joinHandle);
			resignHandle = rtiAmb.getInteractionClassHandle("ResignInteraction");
			rtiAmb.subscribeInteractionClass(resignHandle);
			log.debug("Subscribed==> joinHandle=" + joinHandle + " resignHandle=" + resignHandle);

		} catch (NameNotFound | FederateNotExecutionMember | RTIinternalError | InteractionClassNotDefined
				| SaveInProgress | RestoreInProgress | FederateServiceInvocationsAreBeingReportedViaMOM
				| NotConnected e) {
			log.error("Continuing", e);
		}
	}

	public void run() {
		if (started.get()) {
			while (!paused.get()) {
				advanceLogicalTime();
			}
		}
	}

	public double advanceLogicalTime() {
		advancing.set(true);
		setLogicalTime(getLogicalTime() + getStepSize());
		log.info("advancing logical time to " + getLogicalTime());
		try {
			fedAmb.setTimeAdvancing();
			HLAfloat64Time time = getTimeFactory().makeTime(getLogicalTime());
			rtiAmb.timeAdvanceRequest(time);
		} catch (RTIexception e) {
			log.error("", e);
		}
		while (fedAmb.isTimeAdvancing()) {
			tick();
		}
		log.info("advanced logical time to " + getLogicalTime());
		advancing.set(false);
		return getLogicalTime();
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

		System.exit(0);
	}

	ExecutorService executorService = Executors.newFixedThreadPool(10);
	Future<List<String>> result = null;
	public String waitForJoiners() {
		log.info("Waiting for federates to join...");
		result = executorService.submit(new WaitForJoiners());
		return "rval";
	}

	public String startSimulation() {
		paused.set(false);
		started.set(true);
		result.cancel(true);
		executorService.shutdown();
		return String.format("Started at %f on thread %s", getLogicalTime(), THREAD_NAME);
	}

	public String pauseSimulation() {
		paused.set(true);
		return String.format("Paused at %f on thread %s", getLogicalTime(), THREAD_NAME);
	}

	public String resumeSimulation() {
		paused.set(false);
		result.cancel(true);
		executorService.shutdown();
		return String.format("Resumed at %f on thread %s", getLogicalTime(), THREAD_NAME);
	}

	public String terminateSimulation() {
		paused.set(false);
		started.set(false);
		result.cancel(true);
		executorService.shutdown();
		return String.format("Terminated at %f on thread %s", getLogicalTime(), THREAD_NAME);
	}

	public void updateLogLevel(int level) {
		// TODO Auto-generated method stub

	}

	public Map<String, String> getFederation() {
		return federation;
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

	public String getFederationId() {
		return federationName;
	}

	public String getFederationName() {
		return federationName;
	}

	public boolean isMode() {
		return mode;
	}

	public double getLookahead() {
		return lookahead;
	}

	public int getFederationEndTime() {
		return federationEndTime;
	}

	public FederateAmbassador getFedAmb() {
		return fedAmb;
	}

	public List<String> getJoinedFederates() {
		return joinedFederates;
	}

	public List<String> getResignedFederates() {
		return resignedFederates;
	}

	class WaitForJoiners implements Callable<List<String>> {

		@Override
		public List<String> call() throws Exception {
			while (paused.get()) {
				handleMessages();
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					log.error("", e);
				}
			}
			return joinedFederates;
		}
	}
}