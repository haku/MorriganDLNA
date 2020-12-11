package com.vaguehope.morrigan.dlna.players;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.support.renderingcontrol.callback.GetVolume;
import org.fourthline.cling.support.renderingcontrol.callback.SetVolume;

import com.vaguehope.morrigan.dlna.DlnaException;
import com.vaguehope.morrigan.dlna.DlnaResponseException;

/**
 * ListPresets
 * SelectPreset
 * GetMute
 * SetMute
 * GetVolume
 * SetVolume
 */
public class RenderingControlActions extends AbstractActions {

	public RenderingControlActions (final ControlPoint controlPoint, final RemoteService removeService) {
		super(controlPoint, removeService);
	}

	public int getVolume () throws DlnaException {
		final AtomicReference<Failure> err = new AtomicReference<Failure>();
		final AtomicReference<Integer> ref = new AtomicReference<Integer>();
		final Future<?> f = this.controlPoint.execute(new GetVolume(this.removeService) {

			@Override
			public void received (final ActionInvocation invocation, final int volume) {
				ref.set(volume);
			}

			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse response, final String defaultMsg) {
				err.set(new Failure("get volume", response, defaultMsg));
			}
		});
		await(f, "get volume for renderer '%s'.", this.removeService);
		if (ref.get() == null || err.get() != null) throw new DlnaResponseException(err.get().msg(), err.get().getResponse());
		return ref.get();
	}

	public void setVolume (final int newVolume) throws DlnaException {
		final AtomicReference<Failure> err = new AtomicReference<Failure>();
		final Future<?> f = this.controlPoint.execute(new SetVolume(this.removeService, newVolume) {
			@Override
			public void failure (final ActionInvocation invocation, final UpnpResponse response, final String defaultMsg) {
				err.set(new Failure("stop", response, defaultMsg));
			}
		});
		await(f, "stop playback on transport '%s'.", this.removeService);
		if (err.get() != null) throw new DlnaResponseException(err.get().msg(), err.get().getResponse());
	}

}
