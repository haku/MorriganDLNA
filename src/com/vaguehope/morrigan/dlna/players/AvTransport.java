package com.vaguehope.morrigan.dlna.players;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.ModelUtil;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.support.avtransport.callback.GetMediaInfo;
import org.fourthline.cling.support.avtransport.callback.GetPositionInfo;
import org.fourthline.cling.support.avtransport.callback.GetTransportInfo;
import org.fourthline.cling.support.avtransport.callback.Pause;
import org.fourthline.cling.support.avtransport.callback.Play;
import org.fourthline.cling.support.avtransport.callback.Seek;
import org.fourthline.cling.support.avtransport.callback.SetAVTransportURI;
import org.fourthline.cling.support.avtransport.callback.Stop;
import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.MediaInfo;
import org.fourthline.cling.support.model.PositionInfo;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.item.AudioItem;
import org.fourthline.cling.support.model.item.ImageItem;
import org.fourthline.cling.support.model.item.Item;
import org.fourthline.cling.support.model.item.VideoItem;
import org.seamless.util.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.dlna.DlnaException;
import com.vaguehope.morrigan.dlna.content.ContentGroup;
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

	public void setUri (final String id, final String uri, final String title, final MimeType mimeType, final long fileSize, final String coverArtUri) throws DlnaException {
		final String metadata = metadataFor(id, uri, title, mimeType, fileSize, coverArtUri);
		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<String> err = new AtomicReference<String>();
		this.controlPoint.execute(new SetAVTransportURI(this.avTransport, uri, metadata) {
			@Override
			public void success (final ActionInvocation invocation) {
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				err.set("Failed to set av transport URI: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "set URI '%s' on transport '%s'.", uri, this.avTransport);
		if (err.get() != null) throw new DlnaException(err.get());
	}

	private static String metadataFor (final String id, final String uri, final String title, final MimeType mimeType, final long fileSize, final String coverArtUri) {
		if (mimeType == null) return null;
		final Res res = new Res(mimeType, Long.valueOf(fileSize), uri);
		final Item item;
		switch (ContentGroup.fromMimeType(mimeType)) {
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

	public void play () throws DlnaException {
		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<String> err = new AtomicReference<String>();
		this.controlPoint.execute(new Play(this.avTransport) {
			@Override
			public void success (final ActionInvocation invocation) {
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				err.set("Failed to play: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "play on transport '%s'.", this.avTransport);
		if (err.get() != null) throw new DlnaException(err.get());
	}

	public void pause () throws DlnaException {
		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<String> err = new AtomicReference<String>();
		this.controlPoint.execute(new Pause(this.avTransport) {
			@Override
			public void success (final ActionInvocation invocation) {
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				err.set("Failed to pause: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "pause playback on transport '%s'.", this.avTransport);
		if (err.get() != null) throw new DlnaException(err.get());
	}

	public void stop () throws DlnaException {
		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<String> err = new AtomicReference<String>();
		this.controlPoint.execute(new Stop(this.avTransport) {
			@Override
			public void success (final ActionInvocation invocation) {
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				err.set("Failed to stop: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "stop playback on transport '%s'.", this.avTransport);
		if (err.get() != null) throw new DlnaException(err.get());
	}

	public TransportInfo getTransportInfo () throws DlnaException {
		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<String> err = new AtomicReference<String>();
		final AtomicReference<TransportInfo> ref = new AtomicReference<TransportInfo>();
		this.controlPoint.execute(new GetTransportInfo(this.avTransport) {
			@Override
			public void received (final ActionInvocation invocation, final TransportInfo transportInfo) {
				ref.set(transportInfo);
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				err.set("Failed get transport info: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "get playback state for transport '%s'.", this.avTransport);
		if (ref.get() == null || err.get() != null) throw new DlnaException(err.get());
		return ref.get();
	}

	public PositionInfo getPositionInfo () throws DlnaException {
		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<String> err = new AtomicReference<String>();
		final AtomicReference<PositionInfo> ref = new AtomicReference<PositionInfo>();
		this.controlPoint.execute(new GetPositionInfo(this.avTransport) {
			@Override
			public void received (final ActionInvocation invocation, final PositionInfo positionInfo) {
				ref.set(positionInfo);
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				err.set("Failed get position info: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "get position info for transport '%s'.", this.avTransport);
		if (ref.get() == null || err.get() != null) throw new DlnaException(err.get());
		return ref.get();
	}

	public MediaInfo getMediaInfo () throws DlnaException {
		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<String> err = new AtomicReference<String>();
		final AtomicReference<MediaInfo> ref = new AtomicReference<MediaInfo>();
		this.controlPoint.execute(new GetMediaInfo(this.avTransport) {
			@Override
			public void received (final ActionInvocation invocation, final MediaInfo mi) {
				ref.set(mi);
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				err.set("Failed get media info: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "get media info for transport '%s'.", this.avTransport);
		if (ref.get() == null || err.get() != null) throw new DlnaException(err.get());
		return ref.get();
	}

	public void seek (final long seconds) throws DlnaException {
		final String time = ModelUtil.toTimeString(seconds);
		final CountDownLatch cdl = new CountDownLatch(1);
		final AtomicReference<String> err = new AtomicReference<String>();
		this.controlPoint.execute(new Seek(this.avTransport, time) {
			@Override
			public void success (final ActionInvocation invocation) {
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				err.set("Failed to seek to " + time + ": " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "seek to %s on transport '%s'.", time, this.avTransport);
		if (err.get() != null) throw new DlnaException(err.get());
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
