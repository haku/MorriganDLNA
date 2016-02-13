package com.vaguehope.morrigan.dlna;

import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.dlna.players.PlayerRegisterListener;

public class DeviceWatcher extends DefaultRegistryListener {

	private static final String SERVICE_AVTRANSPORT = "AVTransport";
	private static final String SERVICE_CONTENTDIRECTORY = "ContentDirectory";
	private static final Logger LOG = LoggerFactory.getLogger(DeviceWatcher.class);

	private final PlayerRegisterListener playerRegisterListener;

	public DeviceWatcher (final PlayerRegisterListener playerRegisterListener) {
		this.playerRegisterListener = playerRegisterListener;
	}

	@Override
	public void remoteDeviceAdded (final Registry registry, final RemoteDevice device) {
		final RemoteService avTransport = findService(device, SERVICE_AVTRANSPORT);
		if (avTransport != null) {
			LOG.info("found: {} on {} (udn={})",
					avTransport.getServiceId().getId(),
					device.getDetails().getFriendlyName(),
					device.getIdentity().getUdn());
			this.playerRegisterListener.addAvTransport(device, avTransport);
		}

		final RemoteService contentDirectory = findService(device, SERVICE_CONTENTDIRECTORY);
		if (contentDirectory != null) {
			LOG.info("found: {} on {} (udn={})",
					contentDirectory.getServiceId().getId(),
					device.getDetails().getFriendlyName(),
					device.getIdentity().getUdn());
			// TODO
		}
	}

	@Override
	public void remoteDeviceRemoved (final Registry registry, final RemoteDevice device) {
		LOG.info("lost: {} (udn={})",
				device.getDetails().getFriendlyName(),
				device.getIdentity().getUdn());
		this.playerRegisterListener.removeAvTransport(device);
	}

	private static RemoteService findService (final RemoteDevice rd, final String service) {
		for (final RemoteService rs : rd.getServices()) {
			if (service.equals(rs.getServiceType().getType())) return rs;
		}
		return null;
	}

}
