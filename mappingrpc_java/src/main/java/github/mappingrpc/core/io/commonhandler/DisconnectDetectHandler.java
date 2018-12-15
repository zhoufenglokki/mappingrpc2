package github.mappingrpc.core.io.commonhandler;

import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import github.mappingrpc.core.constant.ClientDaemonThreadEventType;
import github.mappingrpc.core.event.ClientDaemonThreadEvent;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

@Deprecated
/**
 * @Deprecated，转用 ConnectionStateHandler
 * 
 * @author zhoufenglokki
 *
 */
public class DisconnectDetectHandler extends ChannelDuplexHandler {
	Logger log = LoggerFactory.getLogger(DisconnectDetectHandler.class);

	private BlockingQueue<ClientDaemonThreadEvent> nettyEventToOuter;

	public DisconnectDetectHandler(BlockingQueue<ClientDaemonThreadEvent> nettyEventToOuter) {
		this.nettyEventToOuter = nettyEventToOuter;
	}

	/*
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		nettyEventToOuter.add(new BossThreadEvent(BossThreadEventType.channelConnected));
	}*/

	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		nettyEventToOuter.add(new ClientDaemonThreadEvent(ClientDaemonThreadEventType.channelDisconnected));
		super.channelInactive(ctx);
	}

	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		log.error("{msg:\"DisconnectDetectHandler will close socket\"}", cause);
		ctx.close();
		nettyEventToOuter.add(new ClientDaemonThreadEvent(ClientDaemonThreadEventType.channelDisconnected));
	}
}
