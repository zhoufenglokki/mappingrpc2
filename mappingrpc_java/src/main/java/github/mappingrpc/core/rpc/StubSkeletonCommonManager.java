package github.mappingrpc.core.rpc;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import github.mappingrpc.api.annotation.RpcRequestMapping;
import github.mappingrpc.core.io.ConnectionGroup;
import github.mappingrpc.core.metadata.MetaHolder;
import github.mappingrpc.core.metadata.ProviderMeta;

public class StubSkeletonCommonManager {
	static Logger log = LoggerFactory.getLogger(StubSkeletonCommonManager.class);

	public static Object createConsumerProxy(ConnectionGroup connectionGroup, String serviceInterface, MetaHolder metaHolder) {
		ServiceInvocationHandler proxyHandler = metaHolder.getClientProxyHolder().get(serviceInterface);
		if (proxyHandler == null) {// FIXME
			proxyHandler = new ServiceInvocationHandler(connectionGroup);
			metaHolder.getClientProxyHolder().put(serviceInterface, proxyHandler);
		}

		return proxyHandler.generateProxy(serviceInterface);
	}

	/**
	 * 可以并需要运行在start()前
	 * 
	 * @param serviceImpl
	 */
	public static void createProvider(Object serviceImpl, MetaHolder metaHolder) {
		Class<?> callClass = serviceImpl.getClass();
		Class<?> annotationClass = null;

		// when serviceImpl is proxy by aop
		String classInfo = callClass.toString();
		boolean isProxy = classInfo.contains("EnhancerByCGLIB") || classInfo.contains("com.sun.proxy.$Proxy");
		if (isProxy) {
			String className = classInfo.substring(0, classInfo.indexOf("@"));
			try {
				annotationClass = Class.forName(className);
				Method[] methodList = annotationClass.getMethods();
				for (Method method : methodList) {
					RpcRequestMapping mapping = method.getAnnotation(RpcRequestMapping.class);
					if (mapping != null) {
						ProviderMeta meta = new ProviderMeta();
						meta.setMapping(mapping.value());
						meta.setServiceImpl(serviceImpl);
						try {
							meta.setMethod(callClass.getMethod(method.getName(), method.getParameterTypes()));
							metaHolder.getProviderHolder().put(mapping.value(), meta);
						} catch (NoSuchMethodException | SecurityException e) {
							log.error("{fullInfo:'" + classInfo + "', className:'" + className + "'}", e);
						}
					}
				}
			} catch (ClassNotFoundException e) {
				log.error("{fullInfo:'" + classInfo + "', className:'" + className + "'}", e);
			}
		} else {
			Method[] methodList = callClass.getMethods();
			for (Method method : methodList) {
				RpcRequestMapping mapping = method.getAnnotation(RpcRequestMapping.class);
				if (mapping != null) {
					ProviderMeta meta = new ProviderMeta();
					meta.setMapping(mapping.value());
					meta.setMethod(method);
					meta.setServiceImpl(serviceImpl);
					metaHolder.getProviderHolder().put(mapping.value(), meta);
				}
			}
		}
	}

}
