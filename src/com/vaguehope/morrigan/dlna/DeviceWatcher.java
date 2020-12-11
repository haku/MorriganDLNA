package com.vaguehope.morrigan.dlna;

import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.dlna.extcd.ContentDirectoryHolder;
import com.vaguehope.morrigan.dlna.players.PlayerRegisterListener;

public class DeviceWatcher extends DefaultRegistryListener {

	private static final Logger LOG = LoggerFactory.getLogger(DeviceWatcher.class);

	private final PlayerRegisterListener playerRegisterListener;
	private final ContentDirectoryHolder contentDirectoryHolder;

	public DeviceWatcher (final PlayerRegisterListener playerRegisterListener, final ContentDirectoryHolder contentDirectoryHolder) {
		this.playerRegisterListener = playerRegisterListener;
		this.contentDirectoryHolder = contentDirectoryHolder;
	}

	@Override
	public void remoteDeviceAdded (final Registry registry, final RemoteDevice device) {
		final RemoteService avTransport = UpnpHelper.findFirstServiceOfType(device, UpnpHelper.SERVICE_AVTRANSPORT);
		if (avTransport != null) {
			LOG.info("found: {} on {} (udn={})",
					avTransport.getServiceId().getId(),
					device.getDetails().getFriendlyName(),
					device.getIdentity().getUdn());
			this.playerRegisterListener.addAvTransport(device, avTransport);
		}

		final RemoteService contentDirectory = UpnpHelper.findFirstServiceOfType(device, UpnpHelper.SERVICE_CONTENTDIRECTORY);
		if (contentDirectory != null) {
			LOG.info("found: {} on {} (udn={})",
					contentDirectory.getServiceId().getId(),
					device.getDetails().getFriendlyName(),
					device.getIdentity().getUdn());
			this.contentDirectoryHolder.addContentDirectory(device, contentDirectory);
		}
	}

	@Override
	public void remoteDeviceRemoved (final Registry registry, final RemoteDevice device) {
		LOG.info("lost: {} (udn={})",
				device.getDetails().getFriendlyName(),
				device.getIdentity().getUdn());
		this.playerRegisterListener.removeAvTransport(device);
		this.contentDirectoryHolder.removeContentDirectory(device);
	}

}
