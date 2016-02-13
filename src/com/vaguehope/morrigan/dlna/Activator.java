package com.vaguehope.morrigan.dlna;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.ValidationException;
import org.fourthline.cling.model.meta.Icon;
import org.fourthline.cling.model.resource.IconResource;
import org.fourthline.cling.model.resource.Resource;
import org.fourthline.cling.protocol.ProtocolFactory;
import org.fourthline.cling.registry.Registry;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.dlna.content.MediaFileLocator;
import com.vaguehope.morrigan.dlna.content.MediaServerDeviceFactory;
import com.vaguehope.morrigan.dlna.httpserver.MediaServer;
import com.vaguehope.morrigan.dlna.players.PlayerHolder;
import com.vaguehope.morrigan.dlna.players.PlayerRegisterListener;
import com.vaguehope.morrigan.dlna.util.LogHelper;
import com.vaguehope.morrigan.model.media.MediaFactoryTracker;
import com.vaguehope.morrigan.player.PlayerStateStorage;

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

		this.mediaFactoryTracker = new MediaFactoryTracker(context);
		final MediaFileLocator mediaFileLocator = new MediaFileLocator(this.mediaFactoryTracker);

		this.mediaServer = new MediaServer(mediaFileLocator);
		this.mediaServer.start();

		this.upnpService = makeUpnpServer();
		this.playerHolder = new PlayerHolder(this.upnpService.getControlPoint(), this.mediaServer, mediaFileLocator, new PlayerStateStorage(this.mediaFactoryTracker), this.scheduledExecutor);
		this.playerRegisterListener = new PlayerRegisterListener(context, this.playerHolder);

		this.upnpService.getRegistry().addDevice(new MediaServerDeviceFactory(
				InetAddress.getLocalHost().getHostName(),
				this.mediaFactoryTracker,
				this.mediaServer,
				mediaFileLocator
				).getDevice());

		this.upnpService.getRegistry().addListener(new DeviceWatcher(this.playerRegisterListener));
		this.upnpService.getControlPoint().search();

		LOG.info("DLNA started.");
	}

	private static UpnpService makeUpnpServer () throws IOException {
		final Map<String, Resource<?>> pathToRes = new HashMap<String, Resource<?>>();

		final Icon icon = MediaServerDeviceFactory.createDeviceIcon();
		final IconResource iconResource = new IconResource(icon.getUri(), icon);
		pathToRes.put("/icon.png", iconResource);

		return new UpnpServiceImpl(new MyUpnpServiceConfiguration()) {
			@Override
			protected Registry createRegistry (final ProtocolFactory protocolFactory) {
				return new RegistryImplWithOverrides(this, pathToRes);
			}
		};
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
