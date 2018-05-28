package iox.hla;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import hla.rti1516e.RTIambassador;
import hla.rti1516e.exceptions.FederateNotExecutionMember;
import hla.rti1516e.exceptions.NotConnected;
import hla.rti1516e.exceptions.RTIinternalError;
import hla.rti1516e.exceptions.RestoreInProgress;
import hla.rti1516e.exceptions.SaveInProgress;
import iox.hla.core.AbstractFederate;
import iox.hla.core.FederateAmbassador;

public class FederationServiceTest {
	
	static FederationService sut;
	
	@BeforeClass
	public static void beforeClass() {
		List<String> ss = new ArrayList<String>();
		ss.add("conf/little.xml");
		sut = new FederationService("FedMgr", "FederationManager", false, 1, 0.1, 0, ss);
		sut.start();
	}
	

	@Test
	public void testAdvanceLogicalTime() {
		double d = sut.advanceLogicalTime();
		assertEquals(2D, d, 0);
	}

	@Test
	public void testRegisterReadyToPopulate() {
		FederateAmbassador fedAmb = sut.getFedAmb();
		RTIambassador rtiAmb = sut.getRtiAmb();
		try {
			rtiAmb.registerFederationSynchronizationPoint(AbstractFederate.SYNCH_POINTS.readyToPopulate.name(), null);
		} catch (SaveInProgress | RestoreInProgress | FederateNotExecutionMember | NotConnected | RTIinternalError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		assertTrue(fedAmb.isSychronizationPointRegistered(AbstractFederate.SYNCH_POINTS.readyToPopulate.name()));
	}

}
