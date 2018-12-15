package mappingrpc.test.localserver.export2center.service;

import github.mappingrpc.api.annotation.RpcRequestMapping;

public interface PingService {
	
	@RpcRequestMapping("/pingservice/echo/20140528/")
	public String echo(String msg);
}
