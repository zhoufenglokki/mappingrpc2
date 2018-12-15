package github.mappingrpc.core.rpc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import github.mappingrpc.api.annotation.RpcRequestMapping;
import github.mappingrpc.core.io.ConnectionGroup;
import github.mappingrpc.core.metadata.ApiProxyMeta;

public class ServiceInvocationHandler implements InvocationHandler {
	Logger log = LoggerFactory.getLogger(ServiceInvocationHandler.class);

	private ConnectionGroup connectionGroup;
	Map<Method, ApiProxyMeta> apiHolder = new HashMap<>();

	public ServiceInvocationHandler(ConnectionGroup connectionGroup) {
		super();
		this.connectionGroup = connectionGroup;
	}

	public Object generateProxy(String serviceInterface) {
		Class<?> clazz;
		try {
			clazz = Class.forName(serviceInterface);// FIXME
		} catch (ClassNotFoundException ex) {
			log.error("{serviceInterface:\"" + serviceInterface + "\"}", ex);
			throw new RuntimeException(ex);
		}

		boolean foundMapping = false;
		for (Method method : clazz.getMethods()) {
			RpcRequestMapping requestMapping = method.getAnnotation(RpcRequestMapping.class);
			if (requestMapping != null) {
				ApiProxyMeta apiMeta = new ApiProxyMeta();
				apiMeta.setRequestUrl(requestMapping.value());
				apiMeta.setParameterTypes(method.getParameterTypes());
				apiMeta.setReturnType(method.getGenericReturnType());
				// apiMeta.setReturnType(method.getReturnType().getGenericSuperclass());
				apiHolder.put(method, apiMeta);
				foundMapping = true;
			}
		}
		if (!foundMapping) {
			log.error("{msg:\"not found @RpcRequestMapping\", serviceInterface:\"" + serviceInterface + "\"}");
		}

		Object proxy = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] { clazz }, this);
		return proxy;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		// TODO 过滤掉hashCode()/toString()/equals等本地方法
		ApiProxyMeta meta = apiHolder.get(method);
		return connectionGroup.sendRpc(meta.getRequestUrl(), args, meta.getReturnType(), 300000);
	}

}
