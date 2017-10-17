package iox.hla.resources;

import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iox.hla.FederationService;

@Path("service")
public class IndexResource {

	private static Logger log = LoggerFactory.getLogger(IndexResource.class);

	private FederationService service;

	public IndexResource(FederationService service) {
		try {
			this.service = service;
		} catch (NumberFormatException e) {
			log.error("", e);
		} catch (Exception e) {
			log.error("", e);
		}
	}

	// We have this to determine if anything is even working.
	// A response of healthy indicates minimal functionality.
	@GET
	@Produces({ MediaType.APPLICATION_JSON })
	public String health() {
		log.info("healthy==>");
		log.info("service=" + service);
		return "healthy";

	}

	@GET
	@Path("/id")
	@Produces({ MediaType.APPLICATION_JSON })
	public String getFederationId() {
		log.debug("service=" + service);
		return service.getFederationId();
	}

	@GET
	@Path("/discovered")
	@Produces({ MediaType.APPLICATION_JSON })
	public Map<Integer, String> getDiscoveredFederates() {
		return service.getDiscoveredFederates();
	}

	@GET
	@Path("/expected")
	@Produces({ MediaType.APPLICATION_JSON })
	public Set<String> getExpectedFederates() {
		return service.getExpectedFederates();
	}

//	@GET
//	@Path("/incomplete")
//	@Produces({ MediaType.APPLICATION_JSON })
//	public Set<FederateObject> getIncompleteFederates() {
//		return service.getIncompleteFederates();
//	}

	@GET
	@Path("/start")
	@Produces({ MediaType.APPLICATION_JSON })
	public String startSimulation() {
		return service.startSimulation();
	}

	@GET
	@Path("/pause")
	@Produces({ MediaType.APPLICATION_JSON })
	public String pauseSimulation() {
		return service.pauseSimulation();
	}

	@GET
	@Path("/resume")
	@Produces({ MediaType.APPLICATION_JSON })
	public String resumeSimulation() {
		return service.resumeSimulation();
	}

	@GET
	@Path("/terminate")
	@Produces({ MediaType.APPLICATION_JSON })
	public String terminateSimulation() {
		return service.terminateSimulation();
	}

	@GET
	@Path("/updateloglevel/{level}")
	public void updateLogLevel(@PathParam("level") int level) {
		service.updateLogLevel(level);
	}}
