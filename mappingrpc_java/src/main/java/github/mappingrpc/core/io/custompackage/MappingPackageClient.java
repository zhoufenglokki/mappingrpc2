package github.mappingrpc.core.io.custompackage;

import java.io.Closeable;
import java.io.FileInputStream;
import java.lang.reflect.Type;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import github.mappingrpc.api.clientside.domain.Cookie;
import github.mappingrpc.api.clientside.manager.ClientCookieManager;
import github.mappingrpc.api.clientside.manager.CookieStoreManager;
import github.mappingrpc.api.constant.Feature1;
import github.mappingrpc.api.constant.SiteConfigConstant;
import github.mappingrpc.api.domain.TlsConfig;
import github.mappingrpc.api.exception.TimeoutException;
import github.mappingrpc.core.constant.ClientDaemonThreadEventType;
import github.mappingrpc.core.event.ClientDaemonThreadEvent;
import github.mappingrpc.core.event.TimerAndEventDaemonThread;
import github.mappingrpc.core.io.commonhandler.DisconnectDetectHandler;
import github.mappingrpc.core.io.wamp.domain.command.CallCmdCookie;
import github.mappingrpc.core.io.wamp.domain.command.CallCommand;
import github.mappingrpc.core.metadata.MetaHolder;
import github.mappingrpc.core.metadata.ServerMeta;
import github.mappingrpc.core.rpc.CallResultFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;

@Deprecated
public final class MappingPackageClient implements Closeable {
	static Logger log = LoggerFactory.getLogger(MappingPackageClient.class);

	private MetaHolder metaHolder;
	private List<ServerMeta> serverList = new ArrayList<>();
	private byte reconnectCountWhenSendRpc = 3;
	private boolean needKeepConnection = true;
	private TlsConfig tlsConfig = null;
	private Map<String, String> siteConfig = new ConcurrentHashMap<String, String>(1);

	volatile Channel channel = null;
	SSLContext sslContext = null;

	AtomicBoolean isReconnecting = new AtomicBoolean(false);
	BlockingQueue<ClientDaemonThreadEvent> bossThreadEventQueue = new LinkedBlockingQueue<ClientDaemonThreadEvent>(100);
	TimerAndEventDaemonThread daemonThread = new TimerAndEventDaemonThread(500, bossThreadEventQueue);

	ClientCookieManager cookieManager;

	// FIXME not share queue between connection

	public MappingPackageClient(MetaHolder metaHolder, Map<String, String> serverMap, TlsConfig tlsConfig) {
		this.metaHolder = metaHolder;
		this.tlsConfig = tlsConfig;

		for (Map.Entry<String, String> serverPair : serverMap.entrySet()) {
			ServerMeta serverMeta = new ServerMeta(serverPair.getKey(), Short.parseShort(serverPair.getValue()));
			serverList.add(serverMeta);

			if (serverMap.size() == 1) {
				serverList.add(serverMeta);
				serverList.add(serverMeta);
			}
		}
	}

	/** app instance start */
	public void start() {
		initSslContext();

		if ((metaHolder.getFeature1() & Feature1.clientFeature_needReturnIdcSetCookieToUserClient) > 0) {

		}

		if ((metaHolder.getFeature1() & Feature1.clientFeature_needKeepConnection) > 0) {
			needKeepConnection = true;
		} else {
			needKeepConnection = false;
		}

		if (needKeepConnection) {
			KeepConnectionJob job = new KeepConnectionJob();
			daemonThread.addEventHanler(ClientDaemonThreadEventType.channelDisconnected, job);
		}

		CookieStoreManager cookieStoreManager = new CookieStoreManager(siteConfig.get(SiteConfigConstant.client_connectionName), siteConfig.get(SiteConfigConstant.client_fixture_savePath));

		cookieManager = new ClientCookieManager(cookieStoreManager);
		cookieManager.start();
		daemonThread.addTimerJob(new FlushCookieJob());

		daemonThread.start();
	}

	private void initSslContext() {
		if (tlsConfig != null && sslContext == null) {
			try {
				KeyStore keyStore = KeyStore.getInstance("JKS");
				keyStore.load(new FileInputStream(tlsConfig.getTlsKeyStoreFilePath()), tlsConfig.getTlsKeyStorePassword().toCharArray());
				KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				keyManagerFactory.init(keyStore, tlsConfig.getTlsKeyPassword().toCharArray());

				KeyStore certStore = KeyStore.getInstance("JKS");
				certStore.load(new FileInputStream(tlsConfig.getTlsCertStorePath()), tlsConfig.getTlsCertStorePassword().toCharArray());
				TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				trustManagerFactory.init(certStore);

				sslContext = SSLContext.getInstance("TLS");
				sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
			} catch (Throwable ex) {
				String errMsg = "{tlsKeyStoreFilePath:\"" + tlsConfig.getTlsKeyStoreFilePath() + "\"}";
				log.error(errMsg, ex);
				throw new RuntimeException(errMsg, ex);
			}
		}
	}

	@Override
	/**
	 * appp instance close
	 */
	public void close() {
		clossResource();
	}

	/**
	 * close resource
	 */
	public synchronized void clossResource() {
		if (daemonThread != null) {
			try {
				daemonThread.close();
			} catch (Throwable ex) {
				log.error(ex.getMessage(), ex);
			}
		}

		if (channel != null) {
			try {
				channel.close();
			} catch (Throwable ex) {
				log.error(ex.getMessage(), ex);
			} finally {
				channel = null;
			}
		}
	}

	private void makeConnectionInCallerThread() {
		if (isChannelActive()) {
			return;
		}
		metaHolder.getRequestPool().clear();

		boolean success = isReconnecting.compareAndSet(false, true);
		if (!success) {
			return;
		}
		try {
			reconnect();
		} finally {
			isReconnecting.set(false);
		}
	}

	private boolean isChannelActive() {
		if (channel != null && (channel.isActive() || channel.isOpen())) {
			return true;
		} else {
			return false;
		}
	}

	public boolean isRpcWithServerOk() {
		return isChannelActive(); // FIXME too simple
	}

	private void reconnect() {
		Channel localChannel = null;
		Random rand = new Random();
		for (int i = 0; i < serverList.size(); i++) {
			int index = rand.nextInt(serverList.size());
			localChannel = connect(serverList.get(index));
			if (localChannel != null) {
				channel = localChannel;
				return;
			}
		}
		for (ServerMeta serverConfig : serverList) {
			localChannel = connect(serverConfig);
			if (localChannel != null) {
				channel = localChannel;
				return;
			}
		}
	}

	private Channel connect(ServerMeta serverConfig) {
		final LengthFieldPrepender customLenFrameEncoder = new LengthFieldPrepender(4, true);
		final Wamp2ByteBufEncoder wamp2ByteBufEncoder = new Wamp2ByteBufEncoder();
		final Byte2WampDecoder byte2WampDecoder = new Byte2WampDecoder();
		final WampJsonArrayHandler msgHandler = new WampJsonArrayHandler(metaHolder);
		final DisconnectDetectHandler disconnectDetectHandler = new DisconnectDetectHandler(bossThreadEventQueue);

		Bootstrap clientBoot = new Bootstrap();
		clientBoot.group(new NioEventLoopGroup())
				.channel(NioSocketChannel.class)
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 8000) // IMPORTANT
				.handler(new ChannelInitializer<Channel>() {
					@Override
					protected void initChannel(Channel ch) throws Exception {
						ChannelPipeline pipeline = ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));

						if (sslContext != null) {
							SSLEngine tlsEngine = sslContext.createSSLEngine();
							tlsEngine.setNeedClientAuth(true);
							tlsEngine.setUseClientMode(true);
							SslHandler tlsHandler = new SslHandler(tlsEngine, false);
							pipeline.addLast(tlsHandler);
						}

						pipeline.addLast(disconnectDetectHandler)
								.addLast(customLenFrameEncoder)// encoder顺序要保证
								.addLast(wamp2ByteBufEncoder)
								.addLast(new LengthFieldBasedFrameDecoder(1000000, 0, 4, -4, 4))
								.addLast(byte2WampDecoder)
								.addLast(msgHandler);
					}
				});

		try {
			ChannelFuture future = clientBoot.connect(serverConfig.getServerName(), serverConfig.getPort());
			boolean isSuccess = future.awaitUninterruptibly(8, TimeUnit.SECONDS);
			if (isSuccess) {
				serverConfig.addConnectSuccessCount();
				return future.channel();
			} else {
				serverConfig.addConnectErrorCount();
				future.cancel(true);
				return null;
			}
		} catch (Throwable ex) {
			log.error(serverConfig.toString(), ex);
			serverConfig.addConnectErrorCount();
			return null;
		}
	}

	public void sendRpcOneWay(String requestUrl, Object[] args, Class<?> returnType) {
		if (!isChannelActive()) {
			return;
		}
		CallCommand callCmd = new CallCommand();
		callCmd.setProcedureUri(requestUrl);
		callCmd.setArgs(args);
		channel.write(callCmd);
	}

	/**
	 * 
	 * @param requestUrl
	 * @param args
	 * @param returnType
	 * @param timeoutInMs
	 * @return
	 */
	public Object sendRpc(String requestUrl, Object[] args, Type returnType, long timeoutInMs) {
		if (!needKeepConnection) {
			makeConnectionInCallerThread();
		}
		CallCommand callCmd = new CallCommand();
		callCmd.setProcedureUri(requestUrl);
		callCmd.setArgs(args);

		String cookieJson = cookieManager.getCookieForSendToServer();
		if (cookieJson != null && cookieJson.length() > 0) {
			CallCmdCookie callCmdCookie = new CallCmdCookie(cookieJson);
			callCmd.getOptions().put("Cookie", callCmdCookie);
		}

		CallResultFuture future = new CallResultFuture(returnType);
		metaHolder.getRequestPool().put(callCmd.getRequestId(), future);
		try {
			boolean sended = false;
			for (int i = 0; i < 150; i++) {
				if (channel == null || !channel.isActive()) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
					continue;
				} else {
					channel.writeAndFlush(callCmd);
					sended = true;
					break;
				}
			}
			if (sended) {
				future.waitReturn(timeoutInMs);
				processSetCookie(future.getDetail());

				return future.getResult();
			} else {
				throw new TimeoutException("timeout exceed 300000ms");// TODO
			}
		} /* catch (Throwable ex) {
			String errMsg = "{requestUrl:\"" + requestUrl + "\"}";
			log.error(errMsg, ex);
			throw new RuntimeException(errMsg, ex);
		} */
		finally {
			metaHolder.getRequestPool().remove(callCmd.getRequestId());
		}
	}

	private void processSetCookie(JSONObject detail) {
		JSONArray setCookieList = detail.getJSONArray("Set-Cookie");
		if (setCookieList != null) {
			Cookie[] cookieList = new Cookie[setCookieList.size()];
			for (int i = 0; i < setCookieList.size(); i++) {
				cookieList[i] = setCookieList.getObject(i, Cookie.class);
			}
			cookieManager.processSetCookie(cookieList);
		}
	}

	public void setSiteConfig(Map<String, String> siteConfig) {
		this.siteConfig = siteConfig;
	}

	public void setTlsConfig(TlsConfig tlsConfig) {
		this.tlsConfig = tlsConfig;
	}

	class KeepConnectionJob implements Runnable {

		@Override
		public void run() {
			makeConnectionInCallerThread();
		}
	}

	class FlushCookieJob implements Runnable {

		@Override
		public void run() {
			cookieManager.flushCookieToDisk();
		}

	}
}
