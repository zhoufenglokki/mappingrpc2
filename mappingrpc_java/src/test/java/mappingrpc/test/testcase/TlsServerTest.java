package mappingrpc.test.testcase;

import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration({ "/crossIdc/spring-context-server.xml" })
public class TlsServerTest extends AbstractJUnit4SpringContextTests {

	@Test
	public void testServer() throws Throwable {
		Thread.sleep(1000 * 60 * 20);
	}
}
