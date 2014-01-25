package com.vaguehope.morrigan.dlna;

import java.util.logging.Logger;

import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.meta.RemoteService;
import org.teleal.cling.registry.DefaultRegistryListener;
import org.teleal.cling.registry.Registry;

public class DeviceWatcher extends DefaultRegistryListener {

	private static final Logger LOG = Logger.getLogger(DeviceWatcher.class.getName());

	@Override
	public void remoteDeviceAdded (final Registry registry, final RemoteDevice device) {
		LOG.info("found: " + device.getDisplayString() + " (udn=" + device.getIdentity().getUdn() + ")");
		LOG.info("  model name: " + device.getDetails().getModelDetails().getModelName());
		for (final RemoteService s : device.getServices()) {
			LOG.info("  service: " + s + " " + s.getServiceType().getType());
		}
	}

	@Override
	public void remoteDeviceRemoved (final Registry registry, final RemoteDevice device) {
		LOG.info("lost: " + device.getDisplayString() + " (udn=" + device.getIdentity().getUdn() + ")");
	}

}
