package com.vaguehope.morrigan.dlna;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.fourthline.cling.model.meta.RemoteService;

public final class UpnpHelper {

	private UpnpHelper () {}

	public static String idFromRemoteService (final RemoteService rs) {
		return String.format("%s-%s",
				rs.getDevice().getIdentity().getUdn().getIdentifierString(),
				rs.getServiceId().getId())
				.replaceAll("[^a-zA-Z0-9-]", "_");
	}

	public static String remoteServiceUid (final RemoteService rs) {
		return String.format("%s/%s", rs.getDevice().getIdentity().getUdn(), rs.getServiceId().getId());
	}

	public static ThreadLocal<SimpleDateFormat> DC_DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
		@Override
		protected SimpleDateFormat initialValue () {
			final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
			dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			return dateFormat;
		}
	};

}
