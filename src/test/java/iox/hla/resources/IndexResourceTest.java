package iox.hla.resources;

import static com.jayway.restassured.RestAssured.given;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.restassured.response.Response;

public class IndexResourceTest {

	private static Logger log = LoggerFactory.getLogger(IndexResourceTest.class);

	@Test
	public void testHealth() {
		String n = "http://localhost:08080/fedmgr/";
		Response response = given().when().get(n).then().extract().response();
		log.trace("response=" + response.asString());
	}

	@Test
	public void testIds() {
		String n = "http://localhost:08080/fedmgr/id";
		Response response = given().when().get(n).then().extract().response();
		log.trace("response=" + response.asString());
	}
}
