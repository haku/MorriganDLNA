package com.vaguehope.morrigan.dlna;

import java.io.File;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.teleal.cling.controlpoint.ControlPoint;
import org.teleal.cling.model.ModelUtil;
import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.meta.RemoteService;
import org.teleal.cling.support.avtransport.callback.GetMediaInfo;
import org.teleal.cling.support.avtransport.callback.GetPositionInfo;
import org.teleal.cling.support.avtransport.callback.GetTransportInfo;
import org.teleal.cling.support.avtransport.callback.Pause;
import org.teleal.cling.support.avtransport.callback.Play;
import org.teleal.cling.support.avtransport.callback.Seek;
import org.teleal.cling.support.avtransport.callback.SetAVTransportURI;
import org.teleal.cling.support.avtransport.callback.Stop;
import org.teleal.cling.support.contentdirectory.DIDLParser;
import org.teleal.cling.support.model.DIDLContent;
import org.teleal.cling.support.model.DIDLObject;
import org.teleal.cling.support.model.MediaInfo;
import org.teleal.cling.support.model.PositionInfo;
import org.teleal.cling.support.model.Res;
import org.teleal.cling.support.model.TransportInfo;
import org.teleal.cling.support.model.item.AudioItem;
import org.teleal.cling.support.model.item.ImageItem;
import org.teleal.cling.support.model.item.Item;
import org.teleal.cling.support.model.item.VideoItem;

import com.vaguehope.morrigan.util.ErrorHelper;

public class AvTransport {

	private static final int ACTION_TIMEOUT_SECONDS = 10;
	private static final Logger LOG = LoggerFactory.getLogger(AvTransport.class);

	private final ControlPoint controlPoint;
	private final RemoteService avTransport;

	public AvTransport (final ControlPoint controlPoint, final RemoteService avTransportSvc) {
		this.controlPoint = controlPoint;
		this.avTransport = avTransportSvc;
	}

	public void setUri (final String id, final String uri, final String title, final File file, final String coverArtUri) {
		final String metadata = metadataFor(id, uri, title, file, coverArtUri);
		final CountDownLatch cdl = new CountDownLatch(1);
		this.controlPoint.execute(new SetAVTransportURI(this.avTransport, uri, metadata) {
			@Override
			public void success (final ActionInvocation invocation) {
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				LOG.info("Failed to set av transport URI: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "set URI '%s' on transport '%s'.", uri, this.avTransport);
	}

	private static String metadataFor (final String id, final String uri, final String title, final File file, final String coverArtUri) {
		final MediaFormat mf = MediaFormat.identify(file);
		if (mf == null) return null;
		final Res res = new Res(mf.getMimeType(), Long.valueOf(file.length()), uri);
		final Item item;
		switch (mf.getContentGroup()) {
			case VIDEO:
				item = new VideoItem(id, "", title, "", res);
				break;
			case IMAGE:
				item = new ImageItem(id, "", title, "", res);
				break;
			case AUDIO:
				item = new AudioItem(id, "", title, "", res);
				break;
			default:
				return null;
		}
		if (coverArtUri != null) item.addProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(URI.create(coverArtUri)));
		final DIDLContent didl = new DIDLContent();
		didl.addItem(item);
		try {
			return new DIDLParser().generate(didl);
		}
		catch (final Exception e) {
			LOG.info("Failed to generate metedata: " + ErrorHelper.getCauseTrace(e));
			return null;
		}
	}

	public void play () {
		final CountDownLatch cdl = new CountDownLatch(1);
		this.controlPoint.execute(new Play(this.avTransport) {
			@Override
			public void success (final ActionInvocation invocation) {
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				LOG.info("Failed to play: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "play on transport '%s'.", this.avTransport);
	}

	public void pause () {
		final CountDownLatch cdl = new CountDownLatch(1);
		this.controlPoint.execute(new Pause(this.avTransport) {
			@Override
			public void success (final ActionInvocation invocation) {
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				LOG.info("Failed to pause: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "pause playback on transport '%s'.", this.avTransport);
	}

	public void stop () {
		final CountDownLatch cdl = new CountDownLatch(1);
		this.controlPoint.execute(new Stop(this.avTransport) {
			@Override
			public void success (final ActionInvocation invocation) {
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				LOG.info("Failed to stop: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "stop playback on transport '%s'.", this.avTransport);
	}

	public TransportInfo getTransportInfo () {
		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<TransportInfo> ref = new AtomicReference<TransportInfo>();
		this.controlPoint.execute(new GetTransportInfo(this.avTransport) {
			@Override
			public void received (final ActionInvocation invocation, final TransportInfo transportInfo) {
				ref.set(transportInfo);
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				LOG.info("Failed get transport info: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "get playback state for transport '%s'.", this.avTransport);
		return ref.get();
	}

	public PositionInfo getPositionInfo () {
		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<PositionInfo> ref = new AtomicReference<PositionInfo>();
		this.controlPoint.execute(new GetPositionInfo(this.avTransport) {
			@Override
			public void received (final ActionInvocation invocation, final PositionInfo positionInfo) {
				ref.set(positionInfo);
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				LOG.info("Failed get position info: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "get position info for transport '%s'.", this.avTransport);
		return ref.get();
	}

	public MediaInfo getMediaInfo () {
		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<MediaInfo> ref = new AtomicReference<MediaInfo>();
		this.controlPoint.execute(new GetMediaInfo(this.avTransport) {
			@Override
			public void received (final ActionInvocation invocation, final MediaInfo mi) {
				ref.set(mi);
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				LOG.info("Failed get media info: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "get media info for transport '%s'.", this.avTransport);
		return ref.get();
	}

	public void seek (final long seconds) {
		final String time = ModelUtil.toTimeString(seconds);
		final CountDownLatch cdl = new CountDownLatch(1);
		this.controlPoint.execute(new Seek(this.avTransport, time) {
			@Override
			public void success (final ActionInvocation invocation) {
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				LOG.info("Failed to seek to " + time + ": " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "seek to %s on transport '%s'.", time, this.avTransport);
	}

	private static void await (final CountDownLatch cdl, final String msgFormat, final Object... msgArgs) {
		try {
			if (cdl.await(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) return;
			throw new IllegalStateException("Timed out while trying to " + String.format(msgFormat, msgArgs));
		}
		catch (final InterruptedException e) {
			throw new IllegalStateException("Interupted while trying to " + String.format(msgFormat, msgArgs), e);
		}
	}

}
