package iox.hla;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.portico.impl.hla1516e.types.HLA1516eFederateHandleSet;
import org.portico.impl.hla1516e.types.time.DoubleTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hla.rti1516e.AttributeHandleValueMap;
import hla.rti1516e.FederateHandleSet;
import hla.rti1516e.LogicalTime;
import hla.rti1516e.NullFederateAmbassador;
import hla.rti1516e.ObjectInstanceHandle;
import hla.rti1516e.OrderType;
import hla.rti1516e.RTIambassador;
import hla.rti1516e.SynchronizationPointFailureReason;
import hla.rti1516e.TransportationTypeHandle;
import hla.rti1516e.exceptions.FederateInternalError;

// We assume single threaded environment
public class FederateAmbassador extends NullFederateAmbassador {

	private static final Logger log = LoggerFactory.getLogger(FederateAmbassador.class);

	private final double federateTime = 0.0;
	private final double federateLookahead = 1.0;
	RTIambassador rtiAmb;

	public FederateAmbassador() {
		super();
	}

	public FederateAmbassador(RTIambassador rtiAmb) {
		this.rtiAmb = rtiAmb;
	}

	private Set<String> registeredSynchronizationPoints = new HashSet<String>();

	// synchronization point labels that have been announced but not achieved
	private Set<String> pendingSynchronizationPoints = new HashSet<String>();

	private Set<String> achievedSynchronizationPoints = new HashSet<String>();

	// map the handle for a discovered object instance to its associated
	// ObjectDetails
	// private Map<ObjectInstanceHandle, AnyObject> objectInstances = new
	// HashMap<ObjectInstanceHandle, AnyObject>();

	// names of previously discovered object instances that have since been
	// removed
	private LinkedList<String> removedObjectNames = new LinkedList<String>();

	// private Queue<AnyInteraction> receivedInteractions = new
	// ConcurrentLinkedQueue<AnyInteraction>();
	// private Queue<AnyObject> receivedObjectReflections = new
	// ConcurrentLinkedQueue<AnyObject>();

	private boolean isTimeAdvancing = false;
	private boolean isTimeRegulating = false;
	private boolean isTimeConstrained = false;

	private double logicalTime = 0D;

	private FederateHandleSet federateHandleSet = new HLA1516eFederateHandleSet();

	@Override
	public void announceSynchronizationPoint(String synchronizationPointLabel, byte[] userSuppliedTag) {
		if (pendingSynchronizationPoints.contains(synchronizationPointLabel)) {
			log.warn("duplicate announcement of synchronization point: " + synchronizationPointLabel);
		} else {
			pendingSynchronizationPoints.add(synchronizationPointLabel);
			log.info("synchronization point announced: " + synchronizationPointLabel);
		}
	}

	@Override
	public void federationSynchronized(String synchronizationPointLabel, FederateHandleSet failedToSyncSet) {
		pendingSynchronizationPoints.remove(synchronizationPointLabel);
		achievedSynchronizationPoints.add(synchronizationPointLabel);
		federateHandleSet.addAll(failedToSyncSet);
		log.info("synchronization point achieved: " + synchronizationPointLabel);
	}

	@Override
	public void timeRegulationEnabled(LogicalTime theFederateTime) {
		isTimeRegulating = true;
		logicalTime = convertTime(theFederateTime);
		log.debug("time regulation enabled: t=" + logicalTime);
	}

	@Override
	public void timeConstrainedEnabled(LogicalTime theFederateTime) {
		isTimeConstrained = true;
		logicalTime = convertTime(theFederateTime);
		log.debug("time constrained enabled: t=" + logicalTime);
	}

	@Override
	public void timeAdvanceGrant(LogicalTime theTime) {
		isTimeAdvancing = false;
		logicalTime = convertTime(theTime);
		log.debug("time advance granted: t=" + logicalTime);
	}

	// @Override
	// public void receiveInteraction(InteractionClassHandle interactionClass,
	// ParameterHandleValueMap theInteraction,
	// byte[] userSuppliedTag, OrderType sentOrdering, TransportationTypeHandle
	// theTransport,
	// SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
	// try {
	// receiveInteraction(interactionClass, theInteraction, userSuppliedTag,
	// sentOrdering, theTransport, null,
	// sentOrdering, receiveInfo);
	// } catch (FederateInternalError e) {
	// throw new FederateInternalError(e);
	// }
	// }

	// @Override
	// public void receiveInteraction(InteractionClassHandle interactionClassHandle,
	// ParameterHandleValueMap parameters,
	// byte[] userSuppliedTag, OrderType sentOrdering, TransportationTypeHandle
	// theTransport, LogicalTime theTime,
	// OrderType receivedOrdering, SupplementalReceiveInfo receiveInfo) throws
	// FederateInternalError {
	// log.info("received interaction: handle=" + interactionClassHandle);
	// receivedInteractions.add(new AnyInteraction(interactionClassHandle,
	// parameters));
	// }

	// @Override
	// public void discoverObjectInstance(ObjectInstanceHandle objectInstanceHandle,
	// ObjectClassHandle objectClassHandle,
	// String objectName) throws FederateInternalError {
	// log.info("discovered new object instance: (handle, class, name)=" + "(" +
	// objectInstanceHandle + ", " + objectClassHandle
	// + ", " + objectName + ")");
	// if (objectInstances.get(objectInstanceHandle) == null) {
	// objectInstances.put(objectInstanceHandle, new AnyObject(objectClassHandle,
	// objectInstanceHandle, objectName));
	// } else {
	// log.debug(String.format("Already discovered: theObject=%d theObjectClass=%d
	// objectName=%s its ok carry on",
	// objectInstanceHandle, objectClassHandle, objectName));
	// }
	// }

	@Override
	public void reflectAttributeValues(ObjectInstanceHandle objectInstanceHandle, AttributeHandleValueMap attributes,
			byte[] userSuppliedTag, OrderType sentOrder, TransportationTypeHandle transport,
			SupplementalReflectInfo reflectInfo) throws FederateInternalError {
		reflectAttributeValues(objectInstanceHandle, attributes, userSuppliedTag, sentOrder, transport, null, sentOrder,
				reflectInfo);
	}

	// @Override
	// public void reflectAttributeValues(ObjectInstanceHandle objectInstanceHandle,
	// AttributeHandleValueMap attributes,
	// byte[] userSuppliedTag, OrderType sentOrdering, TransportationTypeHandle
	// theTransport, LogicalTime theTime,
	// OrderType receivedOrdering, SupplementalReflectInfo reflectInfo) throws
	// FederateInternalError {
	// AnyObject details = objectInstances.get(objectInstanceHandle);
	// if (details == null) {
	// try {
	// throw new ObjectNotKnown("no discovered object instance with handle " +
	// objectInstanceHandle);
	// } catch (ObjectNotKnown e) {
	// log.error(e);
	// }
	// }
	// ObjectClassHandle objectClassHandle = details.getObjectClassHandle();
	// String objectName = details.getObjectName();
	// receivedObjectReflections.add(new AnyObject(objectClassHandle,
	// objectInstanceHandle, objectName, attributes));
	// log.info("received object reflection for the object instance " + objectName);
	// }

	// @Override
	// public void removeObjectInstance(ObjectInstanceHandle objectInstanceHandle,
	// byte[] userSuppliedTag, OrderType sentOrdering,
	// SupplementalRemoveInfo removeInfo) throws FederateInternalError {
	// AnyObject details = objectInstances.remove(objectInstanceHandle);
	// if (details == null) {
	// try {
	// throw new ObjectNotKnown("no discovered object instance with handle " +
	// objectInstanceHandle);
	// } catch (ObjectNotKnown e) {
	// log.error(e);
	// }
	// }
	// String objectName = details.getObjectName();
	// removedObjectNames.add(objectName);
	// log.info("received notice to remove object instance with handle=" +
	// objectInstanceHandle + " and name=" + objectName);
	// }

	public boolean isSynchronizationPointPending(String label) {
		return pendingSynchronizationPoints.contains(label);
	}

	public double getFederateTime() {
		return federateTime;
	}

	public double getFederateLookahead() {
		return federateLookahead;
	}

	public double getLogicalTime() {
		return logicalTime;
	}

	public void setTimeAdvancing() {
		isTimeAdvancing = true;
	}

	public void synchronizationPointRegistrationSucceeded(String synchronizationPointLabel)
			throws FederateInternalError {
	}

	public void synchronizationPointRegistrationFailed(String synchronizationPointLabel,
			SynchronizationPointFailureReason reason) throws FederateInternalError {
		registeredSynchronizationPoints.add(synchronizationPointLabel);
	}

	public boolean isTimeAdvancing() {
		return isTimeAdvancing;
	}

	public boolean isTimeRegulating() {
		return isTimeRegulating;
	}

	public boolean isTimeConstrained() {
		return isTimeConstrained;
	}

	public boolean isSychronizationPointRegistered(String sychronizationPoint) {
		return registeredSynchronizationPoints.contains(sychronizationPoint);
	}
	// public AnyInteraction nextInteraction() {
	// return receivedInteractions.poll(); // destructive read
	// }

	// public AnyObject nextObjectReflection() {
	// return receivedObjectReflections.poll(); // destructive read
	// }

	public String nextRemovedObjectName() {
		return removedObjectNames.pollFirst(); // destructive read
	}

	private double convertTime(LogicalTime logicalTime) {
		// conversion from portico to java types
		return ((DoubleTime) logicalTime).getTime();
	}
}
