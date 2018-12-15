package github.mappingrpc.example.servercall.serverapp.testcase;

import javax.inject.Inject;

import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import com.aicai.appmodel.domain.result.BaseResult;

import github.mappingrpc.example.servercall.clientapp.commonforserver.domain.DataChangedDO;
import github.mappingrpc.example.servercall.clientapp.commonforserver.service.ServerDataChangeListener;

@ContextConfiguration({ "/serverApp/spring-context-serverapp.xml" })
public class ServerCallTest extends AbstractJUnit4SpringContextTests {

	@Inject
	private ServerDataChangeListener serverDataChangeListener;

	@Test
	public void testDataChange_inServerApp() throws Throwable {

		System.err.println("server started.");
		Thread.sleep(1000 * 30);
		System.err.println("server sending datachange.");
		DataChangedDO dataChange = new DataChangedDO();
		BaseResult result = serverDataChangeListener.onDataChanged(dataChange);
		System.err.println("server sended datachange.");
		System.err.println(result);
		Thread.sleep(60000 * 6);
	}
}
