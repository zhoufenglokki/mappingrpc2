package github.mappingrpc.api;

import java.io.Closeable;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;

import github.mappingrpc.api.clientside.manager.ClientCookieManager;
import github.mappingrpc.api.clientside.manager.CookieStoreManager;
import github.mappingrpc.api.constant.SiteConfigConstant;
import github.mappingrpc.api.domain.TlsConfig;
import github.mappingrpc.core.io.ConnectionGroup;
import github.mappingrpc.core.io.custompackage.Byte2WampDecoder;
import github.mappingrpc.core.io.custompackage.RpcConnection;
import github.mappingrpc.core.io.custompackage.Wamp2ByteBufEncoder;
import github.mappingrpc.core.io.custompackage.WampJsonArrayHandler;
import github.mappingrpc.core.metadata.MetaHolder;
import github.mappingrpc.core.rpc.StubSkeletonCommonManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;

public class MappingRpcServer implements BeanNameAware, Closeable {
	static Logger log = LoggerFactory.getLogger(MappingRpcServer.class);

	private int listenPort;
	private TlsConfig tlsConfig = null;
	private Map<String, String> siteConfig = new ConcurrentHashMap<String, String>();
	private String beanName;

	EventLoopGroup bossEventLoop = new NioEventLoopGroup();
	EventLoopGroup workerEventLoop = new NioEventLoopGroup();
	SSLContext sslContext = null;
	ConnectionGroup connectionGroup = new ConnectionGroup();
	MetaHolder metaHolder = new MetaHolder();

	public MappingRpcServer() {
	}

	public void start() {
		ArrayBlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(300);
		ExecutorService threadPool = new ThreadPoolExecutor(4, 80, 60, TimeUnit.SECONDS, workQueue);// TODO
																									// 加上rejectHandle
		metaHolder.setThreadPool(threadPool);
		
		siteConfig.put(SiteConfigConstant.client_connectionName, this.beanName);

		CookieStoreManager cookieStoreManager = new CookieStoreManager(siteConfig.get(SiteConfigConstant.client_connectionName), siteConfig.get(SiteConfigConstant.client_fixture_savePath));
		ClientCookieManager cookieManager = new ClientCookieManager(cookieStoreManager);
		cookieManager.start();

		initSslContext();

		connectionGroup.startServer();

		ServerBootstrap nettyBoot = new ServerBootstrap();
		final LengthFieldPrepender customLenFrameEncoder = new LengthFieldPrepender(4, true);
		final Wamp2ByteBufEncoder wamp2ByteBufEncoder = new Wamp2ByteBufEncoder();
		final Byte2WampDecoder byte2WampDecoder = new Byte2WampDecoder();
		final WampJsonArrayHandler msgHandler = new WampJsonArrayHandler(metaHolder);

		nettyBoot.group(bossEventLoop, workerEventLoop)
				.channel(NioServerSocketChannel.class)
				.option(ChannelOption.SO_BACKLOG, 100)
				.handler(new LoggingHandler(LogLevel.INFO))
				.childHandler(new ChannelInitializer<Channel>() {
					@Override
					protected void initChannel(Channel ch) throws Exception {
						ChannelPipeline pipeline = ch.pipeline();
						//pipeline.addLast(new LoggingHandler(LogLevel.INFO));

						if (sslContext != null) {
							SSLEngine tlsEngine = sslContext.createSSLEngine();
							tlsEngine.setNeedClientAuth(true);
							tlsEngine.setUseClientMode(false);
							SslHandler tlsHandler = new SslHandler(tlsEngine, false);
							pipeline.addLast(tlsHandler);
						}
						pipeline.addLast(customLenFrameEncoder)// encoder顺序要保证
								.addLast(wamp2ByteBufEncoder)
								.addLast(new LengthFieldBasedFrameDecoder(1000000, 0, 4, -4, 4))
								.addLast(byte2WampDecoder)
								.addLast(msgHandler);
						RpcConnection rpcConnection = new RpcConnection(ch, metaHolder, cookieManager);
						connectionGroup.addConnection(rpcConnection);
					}
				});
		try {
			ChannelFuture channelFuture = nettyBoot.bind(listenPort).sync();
		} catch (Throwable ex) {
			log.error("{bindPort:" + listenPort + "}", ex);
			throw new RuntimeException(ex);
		}
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
	public void close() {
		bossEventLoop.shutdownGracefully();
		workerEventLoop.shutdownGracefully();
	}

	/**
	 * 可以并需要运行在start()前
	 * 
	 * @param serviceImpl
	 */
	private void createProvider(Object serviceImpl) {
		StubSkeletonCommonManager.createProvider(serviceImpl, metaHolder);
	}

	public void setProviderList(List<Object> serviceImplList) {
		for (Object serviceImpl : serviceImplList) {
			createProvider(serviceImpl);
		}
	}

	public Object createConsumerProxy(String serviceInterface) {
		return StubSkeletonCommonManager.createConsumerProxy(connectionGroup, serviceInterface, metaHolder);
	}

	public <T> T createConsumerProxyByGenerics(Class<T> clazz) {
		return (T) StubSkeletonCommonManager.createConsumerProxy(connectionGroup, clazz.getName(), metaHolder);
	}

	public void setTlsConfig(TlsConfig tlsConfig) {
		this.tlsConfig = tlsConfig;
	}

	public void setListenPort(int serverPort) {
		this.listenPort = serverPort;
	}

	@Override
	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}
}
