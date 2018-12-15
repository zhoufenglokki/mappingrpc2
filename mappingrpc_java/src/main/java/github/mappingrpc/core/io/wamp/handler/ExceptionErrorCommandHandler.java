package github.mappingrpc.core.io.wamp.handler;

import com.alibaba.fastjson.JSONArray;
import github.mappingrpc.core.metadata.MetaHolder;
import github.mappingrpc.core.rpc.CallResultFuture;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionErrorCommandHandler {
	static Logger log = LoggerFactory.getLogger(ExceptionErrorCommandHandler.class);

	public static void processCommand(MetaHolder metaHolder, ChannelHandlerContext channelCtx, JSONArray jsonArray) {
		long requestId = jsonArray.getLongValue(2);
		CallResultFuture callResult = metaHolder.getRequestPool().get(requestId);
		if (callResult == null) {
			log.error("{msg:'receive timeout result, maybe server method too slow', requestId:" + requestId + "}");
			return;
		}

		String errorMsg = jsonArray.getObject(5, String[].class)[0];
		if (errorMsg == null) {
			errorMsg = jsonArray.getObject(5, String[].class)[1];
		}

		if (errorMsg == null) {
			errorMsg = "Unexpected error occur.";
		}

		callResult.returnWithErrorMsg(errorMsg);

	}
}
