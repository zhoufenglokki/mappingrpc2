package github.mappingrpc.core.io.wamp.handler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import github.mappingrpc.core.metadata.MetaHolder;
import github.mappingrpc.core.rpc.CallResultFuture;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResultCommandHandler {
	static Logger log = LoggerFactory.getLogger(ResultCommandHandler.class);

	public static void processCommand(MetaHolder metaHolder, ChannelHandlerContext channelCtx, JSONArray jsonArray) {
		long requestId = jsonArray.getLongValue(1);
		CallResultFuture callResult = metaHolder.getRequestPool().get(requestId);
		if (callResult == null) {
			log.error("{msg:'receive timeout result, maybe server method too slow', requestId:" + requestId + "}");
			return;
		}
		callResult.setDetail(jsonArray.getJSONObject(2));

		if (callResult.getReturnType() == null) {
			callResult.returnWithVoid();
		} else {
			String resultJson = jsonArray.getJSONArray(3).getJSONObject(0).toJSONString();

//			Object result = GlobalInstance.getGson().fromJson(resultJson, callResult.getReturnType());
			Object result = JSONObject.parseObject(resultJson, callResult.getReturnType());
			callResult.putResultAndReturn(result);
		}
	}
}
