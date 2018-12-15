package github.mappingrpc.example.servercall.clientapp.testcase;

import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration({ "/clientApp/spring-context-clientapp.xml" })
public class DataChangeTest extends AbstractJUnit4SpringContextTests {
	@Test
	public void testDataChange_inClientApp() throws Throwable {
		System.err.println("client started.");
		Thread.sleep(60000 * 5);
	}
}
