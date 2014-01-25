package com.vaguehope.morrigan.dlna;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;

import com.vaguehope.morrigan.dlna.httpserver.MediaServer;

public class Activator implements BundleActivator {

	private MediaServer mediaServer;
	private PlayerHolder playerHolder;
	private UpnpService upnpService;
	private PlayerRegisterListener playerRegisterListener;
	private ScheduledExecutorService scheduledExecutor;

	@Override
	public void start (final BundleContext context) {
		this.scheduledExecutor = Executors.newScheduledThreadPool(1);

		this.mediaServer = new MediaServer();
		this.mediaServer.start();

		this.upnpService = new UpnpServiceImpl(new MyUpnpServiceConfiguration());
		this.playerHolder = new PlayerHolder(this.upnpService.getControlPoint(), this.mediaServer, this.scheduledExecutor);
		this.playerRegisterListener = new PlayerRegisterListener(context, this.playerHolder);

		this.upnpService.getRegistry().addListener(new DeviceWatcher(this.playerRegisterListener));
		this.upnpService.getControlPoint().search();

		System.err.println("DLNA started.");
	}

	@Override
	public void stop (final BundleContext context) {
		this.scheduledExecutor.shutdownNow();
		this.playerRegisterListener.dispose();
		this.playerHolder.dispose();
		this.upnpService.shutdown();
		this.mediaServer.dispose();
		System.err.println("DLNA stopped.");
	}

}
