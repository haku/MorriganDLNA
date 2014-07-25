package com.vaguehope.morrigan.dlna;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;

import com.vaguehope.morrigan.dlna.httpserver.MediaServer;
import com.vaguehope.morrigan.dlna.util.LogHelper;

public class Activator implements BundleActivator {

	private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

	private MediaServer mediaServer;
	private PlayerHolder playerHolder;
	private UpnpService upnpService;
	private PlayerRegisterListener playerRegisterListener;
	private ScheduledExecutorService scheduledExecutor;

	@Override
	public void start (final BundleContext context) {
		LogHelper.bridgeJul();

		this.scheduledExecutor = Executors.newScheduledThreadPool(1);

		this.mediaServer = new MediaServer();
		this.mediaServer.start();

		this.upnpService = new UpnpServiceImpl(new MyUpnpServiceConfiguration());
		this.playerHolder = new PlayerHolder(this.upnpService.getControlPoint(), this.mediaServer, this.scheduledExecutor);
		this.playerRegisterListener = new PlayerRegisterListener(context, this.playerHolder);

		this.upnpService.getRegistry().addListener(new DeviceWatcher(this.playerRegisterListener));
		this.upnpService.getControlPoint().search();

		LOG.info("DLNA started.");
	}

	@Override
	public void stop (final BundleContext context) {
		if (this.scheduledExecutor != null) {
			this.scheduledExecutor.shutdownNow();
			this.scheduledExecutor = null;
		}

		if (this.playerRegisterListener != null) {
			this.playerRegisterListener.dispose();
			this.playerRegisterListener = null;
		}

		if (this.playerHolder != null) {
			this.playerHolder.dispose();
			this.playerHolder = null;
		}

		if (this.upnpService != null) {
			this.upnpService.shutdown();
			this.upnpService = null;
		}

		if (this.mediaServer != null) {
			this.mediaServer.dispose();
			this.mediaServer = null;
		}

		LOG.info("DLNA stopped.");
	}

}
