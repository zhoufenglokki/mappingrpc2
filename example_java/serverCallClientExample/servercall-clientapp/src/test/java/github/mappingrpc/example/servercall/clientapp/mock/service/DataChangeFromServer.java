package github.mappingrpc.example.servercall.clientapp.mock.service;

import com.aicai.appmodel.domain.result.BaseResult;

import github.mappingrpc.api.annotation.RpcRequestMapping;
import github.mappingrpc.example.servercall.clientapp.commonforserver.domain.DataChangedDO;
import github.mappingrpc.example.servercall.clientapp.commonforserver.service.ServerDataChangeListener;

public class DataChangeFromServer implements ServerDataChangeListener {

	@Override
	@RpcRequestMapping("/dataChange/onDataChanged/v20170928/")
	public BaseResult onDataChanged(DataChangedDO event) {
		System.err.println("data received.");
		return new BaseResult();
	}

}
