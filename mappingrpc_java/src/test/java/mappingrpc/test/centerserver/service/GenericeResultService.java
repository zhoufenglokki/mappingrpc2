package mappingrpc.test.centerserver.service;

import com.github.mappingrpc.connector.test.domain.User;
import com.github.mappingrpc.connector.test.domain.result.ModelResult;

import mappingrpc.test.fastjson.GenericsResult;

public interface GenericeResultService {
	//@GenericsResult(returnType = User.class)
	ModelResult<User> queryUser();
}
