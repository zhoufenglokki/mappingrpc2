package github.mappingrpc.core.metadata;

import java.lang.reflect.Type;

public class ApiProxyMeta {
	private String requestUrl;
	private Type returnType;
	private Class<?>[] parameterTypes;
	private boolean paramTypeGeneric;

	public String getRequestUrl() {
		return requestUrl;
	}

	public void setRequestUrl(String requestUrl) {
		this.requestUrl = requestUrl;
	}

	public Type getReturnType() {
		return returnType;
	}

	public void setReturnType(Type returnType) {
		this.returnType = returnType;
	}

	public Class<?>[] getParameterTypes() {
		return parameterTypes;
	}

	public void setParameterTypes(Class<?>[] parameterTypes) {
		this.parameterTypes = parameterTypes;
	}

	public boolean isParamTypeGeneric() {
		return paramTypeGeneric;
	}

	public void setParamTypeGeneric(boolean paramTypeGeneric) {
		this.paramTypeGeneric = paramTypeGeneric;
	}

}
