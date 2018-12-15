package github.mappingrpc.api;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;

import github.mappingrpc.api.callback.ClientSideConnectedBizListener;
import github.mappingrpc.api.constant.SiteConfigConstant;
import github.mappingrpc.api.domain.TlsConfig;
import github.mappingrpc.core.io.ConnectionGroup;
import github.mappingrpc.core.metadata.MetaHolder;
import github.mappingrpc.core.rpc.StubSkeletonCommonManager;

/**
 * @author zhoufenglokki
 *
 */
public class MappingRpcClient implements BeanNameAware, Closeable {
	static Logger log = LoggerFactory.getLogger(MappingRpcClient.class);

	private long feature1 = 0;
	private TlsConfig tlsConfig = null;

	private Map<String, String> serverList;
	private Map<String, String> siteConfig = new ConcurrentHashMap<String, String>();
	private String beanName;
	private String cookieStoreManagerClass = "github.mappingrpc.api.clientside.manager.CookieStoreManager";
	private boolean needStoreCookieToDisk = false;

	MetaHolder metaHolder = new MetaHolder();
	ConnectionGroup connectionGroup = new ConnectionGroup();

	public MappingRpcClient() {
	}

	public void start() {
		metaHolder.setFeature1(feature1);
		ArrayBlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(300);
		ExecutorService threadPool = new ThreadPoolExecutor(4, 80, 60, TimeUnit.SECONDS, workQueue);// TODO
																									// 加上rejectHandle
		metaHolder.setThreadPool(threadPool);

		siteConfig.put(SiteConfigConstant.client_connectionName, this.beanName);

		connectionGroup.startClient(serverList, tlsConfig, siteConfig, metaHolder);
	}

	public Object createConsumerProxy(String serviceInterface) {
		return StubSkeletonCommonManager.createConsumerProxy(connectionGroup, serviceInterface, metaHolder);
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

	public boolean isRpcWithServerOk() {
		return connectionGroup.isRpcWithServerOk();
	}

	public Map<String, String> getServerList() {
		return serverList;
	}

	public void setServerList(Map<String, String> serverList) {
		this.serverList = serverList;
	}

	public void setFeature1(long connectionFeature) {
		this.feature1 = connectionFeature;
	}

	@Override
	public void setBeanName(String name) {
		this.beanName = name;
	}

	public void setSiteConfig(Map<String, String> siteConfig) {
		this.siteConfig = siteConfig;
	}

	public void setClientSideConnectedBizListener(ClientSideConnectedBizListener listener) {

	}

	public void setTlsConfig(TlsConfig tlsConfig) {
		this.tlsConfig = tlsConfig;
	}

	@Override
	public void close() throws IOException {
		connectionGroup.close();
	}
}
