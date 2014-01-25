package com.vaguehope.morrigan.dlna;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
import org.teleal.cling.support.model.MediaInfo;
import org.teleal.cling.support.model.PositionInfo;
import org.teleal.cling.support.model.TransportInfo;

public class AvTransport {

	private static final int ACTION_TIMEOUT_SECONDS = 10;

	private final ControlPoint controlPoint;
	private final RemoteService avTransport;

	public AvTransport (final ControlPoint controlPoint, final RemoteService avTransportSvc) {
		this.controlPoint = controlPoint;
		this.avTransport = avTransportSvc;
	}

	public void setUri (final String uri) {
		final CountDownLatch cdl = new CountDownLatch(1);
		this.controlPoint.execute(new SetAVTransportURI(this.avTransport, uri) {
			@Override
			public void success (final ActionInvocation invocation) {
				cdl.countDown();
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse operation, final String defaultMsg) {
				System.err.println("Failed to set av transport URI: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "set URI '" + uri + "' on transport '" + this.avTransport + "'.");
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
				System.err.println("Failed to play: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "play on transport '" + this.avTransport + "'.");
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
				System.err.println("Failed to pause: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "pause playback on transport '" + this.avTransport + "'.");
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
				System.err.println("Failed to stop: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "stop playback on transport '" + this.avTransport + "'.");
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
				System.err.println("Failed get transport info: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "get playback state for transport '" + this.avTransport + "'.");
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
				System.err.println("Failed get position info: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "get position info for transport '" + this.avTransport + "'.");
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
				System.err.println("Failed get media info: " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "get media info for transport '" + this.avTransport + "'.");
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
				System.err.println("Failed to seek to " + time + ": " + defaultMsg);
				cdl.countDown();
			}
		});
		await(cdl, "seek to " + time + " on transport '" + this.avTransport + "'.");
	}

	private static void await (final CountDownLatch cdl, final String msg) {
		try {
			if (cdl.await(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) return;
			throw new IllegalStateException("Timed out while trying to " + msg);
		}
		catch (final InterruptedException e) {
			throw new IllegalStateException("Interupted while trying to " + msg, e);
		}
	}

}
