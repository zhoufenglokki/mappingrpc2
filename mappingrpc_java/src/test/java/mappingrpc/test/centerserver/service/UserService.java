package mappingrpc.test.centerserver.service;

import com.github.mappingrpc.connector.test.domain.User;
import com.github.mappingrpc.connector.test.domain.option.LoginOption;
import com.github.mappingrpc.connector.test.domain.option.RegisterOption;
import com.github.mappingrpc.connector.test.domain.result.ModelResult;

import github.mappingrpc.api.annotation.RpcRequestMapping;

public interface UserService {
	
	@Deprecated
	@RpcRequestMapping("/userservice/register/20140305/")
	public User registerUser(User user, String password);

	@RpcRequestMapping("/userService/registerUser/v20140308/")
	public User registerUser(User user, String password, RegisterOption option);
	
	@RpcRequestMapping("/userService/loginMobile/v20141013/")
	public ModelResult<User> login(User user, String password, LoginOption option);
	
	@RpcRequestMapping("/userService/loginNoGenericsResult/v20141110/")
	public User loginNoGenericsResult(User user, String password, LoginOption option);
}
