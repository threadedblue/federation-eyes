package iox.hla;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import iox.hla.resources.IndexResource;

public class FederationEyesApplication extends Application<FederationEyesConfig> {

	private static Logger log = LoggerFactory.getLogger(FederationEyesApplication.class);

	@Override
	public void initialize(Bootstrap<FederationEyesConfig> bootstrap) {
		log.trace("initialize==>");
	}

	@Override
	public void run(FederationEyesConfig configuration, Environment environment) throws Exception {
		log.trace("run==>");
		FederationService service = new FederationService(configuration.getFederation(), configuration.getFoms());
		FederationHealthCheck chk = new FederationHealthCheck(service);
		environment.healthChecks().register("health", chk);
		environment.lifecycle().manage(service);
		environment.jersey().register(new IndexResource(service));
	}
	
    public static void main(String[] args) throws Exception {
        new FederationEyesApplication().run(args);
    }
}
