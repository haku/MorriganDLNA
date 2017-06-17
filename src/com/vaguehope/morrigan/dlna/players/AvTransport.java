package com.vaguehope.morrigan.dlna.players;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.ModelUtil;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.support.avtransport.callback.GetCurrentTransportActions;
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
import org.fourthline.cling.support.model.TransportAction;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.item.AudioItem;
import org.fourthline.cling.support.model.item.ImageItem;
import org.fourthline.cling.support.model.item.Item;
import org.fourthline.cling.support.model.item.VideoItem;
import org.seamless.util.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.dlna.DlnaException;
import com.vaguehope.morrigan.dlna.DlnaTimeoutException;
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

	public void setUri (final String id, final String uri, final String title, final MimeType mimeType, final long fileSize, final String coverArtUri, final int durationSeconds) throws DlnaException {
		final String metadata = metadataFor(id, uri, title, mimeType, fileSize, coverArtUri, durationSeconds);
		final AtomicReference<String> err = new AtomicReference<String>();
		final Future<?> f = this.controlPoint.execute(new SetAVTransportURI(this.avTransport, uri, metadata) {
			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse response, final String defaultMsg) {
				err.set(String.format("Failed to set av transport URI | %s | %s.", defaultMsg, response));
			}
		});
		await(f, "set URI '%s' on transport '%s'.", uri, this.avTransport);
		if (err.get() != null) throw new DlnaException(err.get());
	}

	private static String metadataFor (final String id, final String uri, final String title, final MimeType mimeType, final long fileSize, final String coverArtUri, final int durationSeconds) {
		if (mimeType == null) return null;
		final Res res = new Res(mimeType, Long.valueOf(fileSize), uri);
		if (durationSeconds > 0) res.setDuration(ModelUtil.toTimeString(durationSeconds));
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
		final AtomicReference<String> err = new AtomicReference<String>();
		final Future<?> f = this.controlPoint.execute(new Play(this.avTransport) {
			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse response, final String defaultMsg) {
				err.set(String.format("Failed to play | %s | %s.", defaultMsg, response));
			}
		});
		await(f, "play on transport '%s'.", this.avTransport);
		if (err.get() != null) throw new DlnaException(err.get());
	}

	public void pause () throws DlnaException {
		final AtomicReference<String> err = new AtomicReference<String>();
		final Future<?> f = this.controlPoint.execute(new Pause(this.avTransport) {
			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse response, final String defaultMsg) {
				err.set(String.format("Failed to pause | %s | %s.", defaultMsg, response));
			}
		});
		await(f, "pause playback on transport '%s'.", this.avTransport);
		if (err.get() != null) {
			try {
				final TransportAction[] actions = getTransportActions();
				err.set(err.get() + "  Supported actions: " + Arrays.toString(actions));
			}
			catch (final DlnaException e) {
				// Ignore.
			}
			throw new DlnaException(err.get());
		}
	}

	public void stop () throws DlnaException {
		final AtomicReference<String> err = new AtomicReference<String>();
		final Future<?> f = this.controlPoint.execute(new Stop(this.avTransport) {
			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse response, final String defaultMsg) {
				err.set(String.format("Failed to stop | %s | %s.", defaultMsg, response));
			}
		});
		await(f, "stop playback on transport '%s'.", this.avTransport);
		if (err.get() != null) throw new DlnaException(err.get());
	}

	public TransportInfo getTransportInfo () throws DlnaException {
		final AtomicReference<String> err = new AtomicReference<String>();
		final AtomicReference<TransportInfo> ref = new AtomicReference<TransportInfo>();
		final Future<?> f = this.controlPoint.execute(new GetTransportInfo(this.avTransport) {
			@Override
			public void received (final ActionInvocation invocation, final TransportInfo transportInfo) {
				ref.set(transportInfo);
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse response, final String defaultMsg) {
				err.set(String.format("Failed to get transport info | %s | %s.", defaultMsg, response));
			}
		});
		await(f, "get playback state for transport '%s'.", this.avTransport);
		if (ref.get() == null || err.get() != null) throw new DlnaException(err.get());
		return ref.get();
	}

	public PositionInfo getPositionInfo () throws DlnaException {
		final AtomicReference<String> err = new AtomicReference<String>();
		final AtomicReference<PositionInfo> ref = new AtomicReference<PositionInfo>();
		final Future<?> f = this.controlPoint.execute(new GetPositionInfo(this.avTransport) {
			@Override
			public void received (final ActionInvocation invocation, final PositionInfo positionInfo) {
				ref.set(positionInfo);
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse response, final String defaultMsg) {
				err.set(String.format("Failed to get position info | %s | %s.", defaultMsg, response));
			}
		});
		await(f, "get position info for transport '%s'.", this.avTransport);
		if (ref.get() == null || err.get() != null) throw new DlnaException(err.get());
		return ref.get();
	}

	public MediaInfo getMediaInfo () throws DlnaException {
		final AtomicReference<String> err = new AtomicReference<String>();
		final AtomicReference<MediaInfo> ref = new AtomicReference<MediaInfo>();
		final Future<?> f = this.controlPoint.execute(new GetMediaInfo(this.avTransport) {
			@Override
			public void received (final ActionInvocation invocation, final MediaInfo mi) {
				ref.set(mi);
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse response, final String defaultMsg) {
				err.set(String.format("Failed to get media info | %s | %s.", defaultMsg, response));
			}
		});
		await(f, "get media info for transport '%s'.", this.avTransport);
		if (ref.get() == null || err.get() != null) throw new DlnaException(err.get());
		return ref.get();
	}

	public TransportAction[] getTransportActions () throws DlnaException {
		final AtomicReference<String> err = new AtomicReference<String>();
		final AtomicReference<TransportAction[]> ref = new AtomicReference<TransportAction[]>();
		final Future<?> f = this.controlPoint.execute(new GetCurrentTransportActions(this.avTransport) {
			@Override
			public void received (final ActionInvocation invocation, final TransportAction[] actions) {
				ref.set(actions);
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse response, final String defaultMsg) {
				err.set(String.format("Failed to get transport actions | %s | %s.", defaultMsg, response));
			}
		});
		await(f, "get actions for transport '%s'.", this.avTransport);
		if (ref.get() == null || err.get() != null) throw new DlnaException(err.get());
		return ref.get();
	}

	public void seek (final long seconds) throws DlnaException {
		final String time = ModelUtil.toTimeString(seconds);
		final AtomicReference<String> err = new AtomicReference<String>();
		final Future<?> f = this.controlPoint.execute(new Seek(this.avTransport, time) {
			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse response, final String defaultMsg) {
				err.set(String.format("Failed to seek to " + time + " | %s | %s.", defaultMsg, response));
			}
		});
		await(f, "seek to %s on transport '%s'.", time, this.avTransport);
		if (err.get() != null) throw new DlnaException(err.get());
	}

	private static void await (final Future<?> f, final String msgFormat, final Object... msgArgs) throws DlnaException {
		try {
			f.get(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}
		catch (final ExecutionException e) {
			throw new DlnaException("Failed to " + String.format(msgFormat, msgArgs), e);
		}
		catch (final TimeoutException e) {
			throw new DlnaTimeoutException("Timed out after " + ACTION_TIMEOUT_SECONDS + "s while trying to " + String.format(msgFormat, msgArgs), e);
		}
		catch (final InterruptedException e) {
			throw new DlnaException("Interupted while trying to " + String.format(msgFormat, msgArgs), e);
		}
	}

}
