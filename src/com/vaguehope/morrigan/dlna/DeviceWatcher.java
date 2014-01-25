package com.vaguehope.morrigan.dlna;

import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.meta.RemoteService;
import org.teleal.cling.registry.DefaultRegistryListener;
import org.teleal.cling.registry.Registry;

public class DeviceWatcher extends DefaultRegistryListener {

	@Override
	public void remoteDeviceAdded (final Registry registry, final RemoteDevice device) {
		System.err.println("found: " + device.getDisplayString() + " (udn=" + device.getIdentity().getUdn() + ")");
		System.err.println("  model name: " + device.getDetails().getModelDetails().getModelName());
		for (final RemoteService s : device.getServices()) {
			System.err.println("  service: " + s + " " + s.getServiceType().getType());
		}
	}

	@Override
	public void remoteDeviceRemoved (final Registry registry, final RemoteDevice device) {
		System.err.println("lost: " + device.getDisplayString() + " (udn=" + device.getIdentity().getUdn() + ")");
	}

}
