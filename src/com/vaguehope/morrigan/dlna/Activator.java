package com.vaguehope.morrigan.dlna;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;
import org.teleal.cling.model.ValidationException;

import com.vaguehope.morrigan.dlna.content.MediaServerDeviceFactory;
import com.vaguehope.morrigan.dlna.httpserver.MediaServer;
import com.vaguehope.morrigan.dlna.util.LogHelper;
import com.vaguehope.morrigan.model.media.MediaFactoryTracker;

public class Activator implements BundleActivator {

	private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

	private MediaServer mediaServer;
	private PlayerHolder playerHolder;
	private UpnpService upnpService;
	private PlayerRegisterListener playerRegisterListener;
	private MediaFactoryTracker mediaFactoryTracker;
	private ScheduledExecutorService scheduledExecutor;

	@Override
	public void start (final BundleContext context) throws ValidationException, IOException {
		LogHelper.bridgeJul();

		this.scheduledExecutor = Executors.newScheduledThreadPool(1);

		this.mediaServer = new MediaServer();
		this.mediaServer.start();

		this.upnpService = new UpnpServiceImpl(new MyUpnpServiceConfiguration());
		this.playerHolder = new PlayerHolder(this.upnpService.getControlPoint(), this.mediaServer, this.scheduledExecutor);
		this.playerRegisterListener = new PlayerRegisterListener(context, this.playerHolder);

		this.mediaFactoryTracker = new MediaFactoryTracker(context);
		this.upnpService.getRegistry().addDevice(new MediaServerDeviceFactory(
				InetAddress.getLocalHost().getHostName(),
				this.mediaFactoryTracker,
				this.mediaServer
				).getDevice());

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

		if (this.mediaFactoryTracker != null) {
			this.mediaFactoryTracker.dispose();
			this.mediaFactoryTracker = null;
		}

		LOG.info("DLNA stopped.");
	}

}
