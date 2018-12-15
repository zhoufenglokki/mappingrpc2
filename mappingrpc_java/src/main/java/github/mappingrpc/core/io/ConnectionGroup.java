package github.mappingrpc.core.io;

import java.io.FileInputStream;
import java.lang.reflect.Type;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import github.mappingrpc.api.clientside.manager.ClientCookieManager;
import github.mappingrpc.api.clientside.manager.CookieStoreManager;
import github.mappingrpc.api.constant.SiteConfigConstant;
import github.mappingrpc.api.domain.TlsConfig;
import github.mappingrpc.core.event.ClientDaemonThreadEvent;
import github.mappingrpc.core.event.TimerAndEventDaemonThread;
import github.mappingrpc.core.io.custompackage.RpcConnection;
import github.mappingrpc.core.metadata.MetaHolder;
import github.mappingrpc.core.metadata.ServerMeta;

public class ConnectionGroup {
	static Logger log = LoggerFactory.getLogger(ConnectionGroup.class);

	private List<ServerMeta> serverMetaList = new CopyOnWriteArrayList<>();
	private Map<String, String> siteConfig;
	List<RpcConnection> connectionList = new CopyOnWriteArrayList<>();
	SSLContext sslContext = null;
	// private TlsConfig tlsConfig = null;

	MetaHolder metaHolder;
	BlockingQueue<ClientDaemonThreadEvent> bossThreadEventQueue;
	TimerAndEventDaemonThread daemonThread;

	ClientCookieManager cookieManager;

	public ConnectionGroup() {
	}

	public void start() {
	}

	public void startClient(Map<String, String> serverList, TlsConfig tlsConfig, Map<String, String> siteConfig, MetaHolder metaHolder) {
		bossThreadEventQueue = new LinkedBlockingQueue<ClientDaemonThreadEvent>(100);
		daemonThread = new TimerAndEventDaemonThread(3000, bossThreadEventQueue);

		this.siteConfig = siteConfig;
		this.metaHolder = metaHolder;

		for (Map.Entry<String, String> serverPair : serverList.entrySet()) {
			ServerMeta serverMeta = new ServerMeta(serverPair.getKey(), Integer.parseInt(serverPair.getValue()));
			serverMetaList.add(serverMeta);

			if (serverList.size() == 1) {
				serverMetaList.add(serverMeta);
				serverMetaList.add(serverMeta);
			}
		}

		if (tlsConfig != null) {
			initClientSslContext(tlsConfig);
		}

		CookieStoreManager cookieStoreManager = new CookieStoreManager(siteConfig.get(SiteConfigConstant.client_connectionName), siteConfig.get(SiteConfigConstant.client_fixture_savePath));
		cookieManager = new ClientCookieManager(cookieStoreManager);
		cookieManager.start();

		RpcConnection lastConnection = null;
		for (ServerMeta serverMeta : serverMetaList) {
			RpcConnection connection = new RpcConnection(serverMeta, this.metaHolder, sslContext, cookieManager);
			connection.startClient();
			connectionList.add(connection);
			daemonThread.addTimerJob(new KeepConnectionJob(connection));
			lastConnection = connection;
		}
		lastConnection.makeConnectionInCallerThread();

		daemonThread.addTimerJob(new FlushCookieJob());
		daemonThread.start();
	}

	public void startServer() {
		bossThreadEventQueue = new LinkedBlockingQueue<ClientDaemonThreadEvent>(100);
		daemonThread = new TimerAndEventDaemonThread(1000, bossThreadEventQueue);
	}

	private void initClientSslContext(TlsConfig tlsConfig) {
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

	public void addConnection(RpcConnection connection) {
		connectionList.add(connection);
	}

	public boolean isRpcWithServerOk() {
		for (RpcConnection connection : connectionList) {
			if (connection.isConnectionOk()) {
				return true;
			}
		}

		return false;
	}

	public void sendRpcOneWay(String requestUrl, Object[] args) {
		RpcConnection findConnection = findConnection();
		findConnection.sendRpcOneWay(requestUrl, args);
	}

	public void broadcastRpcOneWay(String requestUrl, Object[] args) {
		for (RpcConnection connection : connectionList) {
			try {
				connection.sendRpcOneWay(requestUrl, args);
			} catch (Throwable ex) {
				log.error("{msg:\"log for continue loop\"}", ex);
			}
		}
	}

	public Object sendRpc(String requestUrl, Object[] args, Type returnType, long timeoutInMs) {
		RpcConnection connection = findConnection();
		return connection.sendRpc(requestUrl, args, returnType, timeoutInMs);
	}

	private RpcConnection findConnection() {
		RpcConnection selectedConnection = null;
		Random rand = new Random();
		for (int i = 0; i < connectionList.size(); i++) {
			int index = rand.nextInt(connectionList.size());
			RpcConnection connection = connectionList.get(index);
			if (connection.isConnectionOk()) {
				selectedConnection = connection;
				break;
			} else {
				log.error("{msg:\"some connection not ok in random choice\"}");
			}
		}
		if (selectedConnection == null) {
			for (RpcConnection connection : connectionList) {
				if (connection.isConnectionOk()) {
					selectedConnection = connection;
					break;
				} else {
					// connection.log TODO
					log.error("{msg:\"some connection not ok in loop\"}");
				}
			}
		}

		if (selectedConnection == null) {
			log.error("{msg:\"this connection maybe not ok\"}");
			int index = rand.nextInt(connectionList.size());
			selectedConnection = connectionList.get(index);
		}
		return selectedConnection;
	}

	public void close() { // FIXME 前后顺序未确定
		for (RpcConnection connection : connectionList) {
			connection.close();
		}

		if (daemonThread != null) {
			try {
				daemonThread.close();
			} catch (Throwable ex) {
				log.error(ex.getMessage(), ex);
			}
		}
	}

	class KeepConnectionJob implements Runnable {
		private RpcConnection connection;

		public KeepConnectionJob(RpcConnection connection) {
			super();
			this.connection = connection;
		}

		@Override
		public void run() {
			if (connection.isNeedOpenConnection()) {
				connection.makeConnectionInCallerThread();
			}
		}
	}

	class FlushCookieJob implements Runnable {

		@Override
		public void run() {
			cookieManager.flushCookieToDisk();
		}

	}
}
