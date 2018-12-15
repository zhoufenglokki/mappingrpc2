package github.mappingrpc.example.servercall.clientapp.commonforserver.service;

import com.aicai.appmodel.domain.result.BaseResult;

import github.mappingrpc.api.annotation.RpcRequestMapping;
import github.mappingrpc.example.servercall.clientapp.commonforserver.domain.DataChangedDO;

public interface ServerDataChangeListener {

	@RpcRequestMapping("/dataChange/onDataChanged/v20170928/")
	public BaseResult onDataChanged(DataChangedDO dataChanged);
}
