package iox.hla.resources;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iox.hla.FederationService;

@Path("/service")
public class IndexResource {

	private static Logger log = LoggerFactory.getLogger(IndexResource.class);

	private FederationService service;
	private Thread thread;

	public IndexResource(FederationService service) {
		try {
			this.service = service;
			thread = new Thread(service, FederationService.THREAD_NAME);
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
		StringBuilder bld = new StringBuilder();
		bld.append(String.format("healthy%n"));
		int i = 0;
		for (Map.Entry<String, String> entry : service.getFederation().entrySet()) {
			bld.append(String.format("%d %s %s%n", i++, entry.getKey(), entry.getValue()));
		}
		try {
			for (URL url : service.getFoms()) {
				bld.append(String.format("%s%n", url));
			}
		} catch (MalformedURLException e) {
			log.error("", e);
		}
		return bld.toString();
	}

	@GET
	@Path("/id")
	@Produces({ MediaType.APPLICATION_JSON })
	public String getFederationId() {
		log.info("service=" + service);
		return String.format("%s%n", service.getFederationId());
	}

//	@GET
//	@Path("/wait")
//	@Produces({ MediaType.APPLICATION_JSON })
//	public String init() {
//		log.info("service=" + service);
//		return service.waitForJoiners();
//	}

	@GET
	@Path("/joined")
	@Produces({ MediaType.APPLICATION_JSON })
	public List<String> getJoinedFederates() {
		log.info("service=" + service);
		return service.getJoinedFederates();
	}

	@GET
	@Path("/resigned")
	@Produces({ MediaType.APPLICATION_JSON })
	public List<String> getResignedFederates() {
		log.info("service=" + service);
		return service.getResignedFederates();
	}

	@GET
	@Path("/start")
	@Produces({ MediaType.APPLICATION_JSON })
	public String startSimulation() {
		log.info("started=");
		String s = service.startSimulation();
		thread.start();
		return s;
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
	}
}
