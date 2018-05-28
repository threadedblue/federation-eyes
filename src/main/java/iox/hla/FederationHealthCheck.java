package iox.hla;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.health.HealthCheck;

public class FederationHealthCheck extends HealthCheck {

	private static Logger log = LoggerFactory.getLogger(FederationHealthCheck.class);
	private FederationService service;

	public FederationHealthCheck(FederationService service) {
		super();
		this.service = service;
	}

	@Override
	protected Result check() throws Exception {
		log.trace("run==>");
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
		return Result.healthy(bld.toString());
	}

}
