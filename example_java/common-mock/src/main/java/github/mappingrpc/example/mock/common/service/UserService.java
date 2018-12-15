package github.mappingrpc.example.mock.common.service;

import github.mappingrpc.api.annotation.RpcRequestMapping;
import github.mappingrpc.example.mock.common.domain.User;
import github.mappingrpc.example.mock.common.domain.option.LoginOption;
import github.mappingrpc.example.mock.common.domain.option.RegisterOption;
import github.mappingrpc.example.mock.common.domain.result.ModelResult;

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
