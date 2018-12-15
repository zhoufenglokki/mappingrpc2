package github.mappingrpc.api.constant;

public class Feature1 {
	public static final long clientFeature_needKeepConnection = 1L;
	@Deprecated
	public static final long clientFeature_needReturnDownStreamSetCookieToUpStream = 1L << 1;
	public static final long clientFeature_needReturnIdcSetCookieToUserClient = 1L << 1;
}
