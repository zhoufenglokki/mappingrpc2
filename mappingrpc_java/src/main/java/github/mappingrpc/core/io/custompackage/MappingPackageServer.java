package github.mappingrpc.core.io.custompackage;

import java.io.Closeable;
import java.io.FileInputStream;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import github.mappingrpc.api.domain.TlsConfig;
import github.mappingrpc.core.metadata.MetaHolder;
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

@Deprecated
public class MappingPackageServer implements Closeable {
	static Logger log = LoggerFactory.getLogger(MappingPackageServer.class);

	private MetaHolder metaHolder;
	private short serverPort;
	private TlsConfig tlsConfig = null;

	EventLoopGroup bossEventLoop = new NioEventLoopGroup();
	EventLoopGroup workerEventLoop = new NioEventLoopGroup();
	SSLContext sslContext = null;

	public MappingPackageServer(MetaHolder metaHolder, short serverPort, TlsConfig tlsConfig) {
		this.metaHolder = metaHolder;
		this.serverPort = serverPort;
		this.tlsConfig = tlsConfig;
	}

	public void start() {
		initSslContext();

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
						ChannelPipeline pipeline = ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));

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
					}
				});
		try {
			ChannelFuture channelFuture = nettyBoot.bind(serverPort).sync();
		} catch (InterruptedException ex) {
			log.error("{bindPort:" + serverPort + "}", ex);
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
}
