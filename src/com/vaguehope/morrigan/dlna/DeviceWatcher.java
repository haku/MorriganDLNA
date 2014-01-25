package com.vaguehope.morrigan.dlna;

import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.meta.RemoteService;
import org.teleal.cling.registry.DefaultRegistryListener;
import org.teleal.cling.registry.Registry;

public class DeviceWatcher extends DefaultRegistryListener {

	private static final String SERVICE_AVTRANSPORT = "AVTransport";

	private final PlayerRegisterListener playerRegisterListener;

	public DeviceWatcher (final PlayerRegisterListener playerRegisterListener) {
		this.playerRegisterListener = playerRegisterListener;
	}

	@Override
	public void remoteDeviceAdded (final Registry registry, final RemoteDevice device) {
		final RemoteService avTransport = findAvTransportService(device);
		if (avTransport != null) {
			System.err.println("found: " + device.getDetails().getFriendlyName() + " (udn=" + device.getIdentity().getUdn() + ")");
			this.playerRegisterListener.addAvTransport(device, avTransport);
		}
	}

	@Override
	public void remoteDeviceRemoved (final Registry registry, final RemoteDevice device) {
		System.err.println("lost: " + device.getDisplayString() + " (udn=" + device.getIdentity().getUdn() + ")");
		this.playerRegisterListener.removeAvTransport(device);
	}

	private static RemoteService findAvTransportService (final RemoteDevice rd) {
		for (final RemoteService rs : rd.getServices()) {
			if (SERVICE_AVTRANSPORT.equals(rs.getServiceType().getType())) {
				return rs;
			}
		}
		return null;
	}

}
