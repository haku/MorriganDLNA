package com.vaguehope.morrigan.dlna.players;

import org.fourthline.cling.controlpoint.SubscriptionCallback;
import org.fourthline.cling.model.gena.CancelReason;
import org.fourthline.cling.model.gena.GENASubscription;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.support.avtransport.lastchange.AVTransportLastChangeParser;
import org.fourthline.cling.support.avtransport.lastchange.AVTransportVariable;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.model.TransportState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.util.ErrorHelper;

public class AvSubscriber extends SubscriptionCallback {

	private static final Logger LOG = LoggerFactory.getLogger(AvSubscriber.class);
	private final AbstractDlnaPlayer dlnaPlayer;
	private final AvEventListener listener;

	protected AvSubscriber (final AbstractDlnaPlayer dlnaPlayer, final AvEventListener listener, final Service service, final int requestedDurationSeconds) {
		super(service, requestedDurationSeconds);
		this.dlnaPlayer = dlnaPlayer;
		this.listener = listener;
	}

	@Override
	public void established (final GENASubscription sub) {
		LOG.info("{} Established: {}", this.dlnaPlayer.getId(), sub.getSubscriptionId());
	}

	@Override
	public void failed (final GENASubscription sub, final UpnpResponse response, final Exception ex, final String defaultMsg) {
		LOG.info("{} Failed: {}", this.dlnaPlayer.getId(), defaultMsg);
		reconnectIfAlive();
	}

	@Override
	public void ended (final GENASubscription sub, final CancelReason reason, final UpnpResponse response) {
		if (reason == null) {
			LOG.info("{} Ended.", this.dlnaPlayer.getId());
		}
		else {
			LOG.warn("{} Ended: {} {}", this.dlnaPlayer.getId(), reason, response);
		}
		reconnectIfAlive();
	}

	private void reconnectIfAlive () {
		if (!this.dlnaPlayer.isDisposed()) {
			LOG.error("{} TODO: Restablish subscription.", this.dlnaPlayer.getId());
		}
	}

	@Override
	public void eventsMissed (final GENASubscription sub, final int numberOfMissedEvents) {
		LOG.warn("{} Missed {} events.", this.dlnaPlayer.getId(), numberOfMissedEvents);
	}

	@Override
	public void eventReceived (final GENASubscription sub) {
		LOG.info("{} Event {}: {}", this.dlnaPlayer.getId(), sub.getCurrentSequence().getValue(), sub.getCurrentValues());
		try {
			final Object rawLastChange = sub.getCurrentValues().get("LastChange");
			if (rawLastChange != null) {
				final LastChange lastChange = new LastChange(new AVTransportLastChangeParser(), rawLastChange.toString());
				lastChangeReceived(lastChange);
			}
		}
		catch (final Exception e) {
			LOG.warn("{} Failed to handle event: {}", this.dlnaPlayer.getId(), ErrorHelper.oneLineCauseTrace(e));
		}
	}

	private void lastChangeReceived (final LastChange lastChange) {
		final AVTransportVariable.TransportState varTransportState = lastChange.getEventedValue(0, AVTransportVariable.TransportState.class);
		if (varTransportState != null) {
			final TransportState transportState = varTransportState.getValue();
			if (transportState != null) {
				LOG.info("{} transportState: {}", this.dlnaPlayer.getId(), transportState);
				this.listener.onTransportState(transportState);
			}
		}
	}

}
