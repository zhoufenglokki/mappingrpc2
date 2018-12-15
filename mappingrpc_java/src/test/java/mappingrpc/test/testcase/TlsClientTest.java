package mappingrpc.test.testcase;

import javax.inject.Inject;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import com.github.mappingrpc.connector.test.domain.User;
import com.github.mappingrpc.connector.test.domain.option.LoginOption;
import com.github.mappingrpc.connector.test.domain.result.ModelResult;

import mappingrpc.test.centerserver.service.UserService;

@ContextConfiguration({ "/crossIdc/spring-context-client.xml" })
public class TlsClientTest extends AbstractJUnit4SpringContextTests {
	Logger log = LoggerFactory.getLogger(getClass());

	@Inject
	private UserService userService;

	@Test
	public void testClient() throws Throwable {
		User user = new User();
		user.setDisplayName("lokki");
		LoginOption option = new LoginOption();

		// Thread.sleep(1000 * 5);

		for (int i = 0; i < 10000; i++) {
			try {
				ModelResult<User> loginResult = userService.login(user, "123password", option);
				System.err.println("login:" + loginResult.getModel().getDisplayName());
			} catch (Exception ex) {
				log.debug("{msg:\"loop continues\"}", ex);
			}
			Thread.sleep(1000 * 5);
		}
		Thread.sleep(1000 * 60 * 20);
	}
}
