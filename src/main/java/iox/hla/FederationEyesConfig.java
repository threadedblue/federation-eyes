package iox.hla;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.dropwizard.Configuration;

public class FederationEyesConfig extends Configuration {

	private Map<String, String> federation;
	private List<String> foms = new ArrayList<String>();

	public Map<String, String> getFederation() {
		return federation;
	}

	@JsonProperty("federation")
	public void setFederation(Map<String, String> federation) {
		this.federation = federation;
	}

	public List<String> getFoms() {
		return foms;
	}

 	@JsonProperty("FOMS")
	public void setFoms(List<String>foms) {
		this.foms = foms;
	}
}
