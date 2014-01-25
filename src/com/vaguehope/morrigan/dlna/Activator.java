package com.vaguehope.morrigan.dlna;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;

public class Activator implements BundleActivator {

	private MediaServer mediaServer;
	private PlayerHolder playerHolder;
	private UpnpService upnpService;
	private PlayerRegisterListener playerRegisterListener;

	@Override
	public void start (final BundleContext context) {
		this.mediaServer = new MediaServer();
		this.mediaServer.start();

		this.upnpService = new UpnpServiceImpl(new MyUpnpServiceConfiguration());
		this.playerHolder = new PlayerHolder(this.upnpService.getControlPoint(), this.mediaServer);
		this.playerRegisterListener = new PlayerRegisterListener(context, this.playerHolder);

		this.upnpService.getRegistry().addListener(new DeviceWatcher(this.playerRegisterListener));
		this.upnpService.getControlPoint().search();

		System.err.println("DLNA started.");
	}

	@Override
	public void stop (final BundleContext context) {
		this.playerRegisterListener.dispose();
		this.playerHolder.dispose();
		this.upnpService.shutdown();
		this.mediaServer.dispose();
		System.err.println("DLNA stopped.");
	}

}
