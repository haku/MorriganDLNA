package com.vaguehope.morrigan.dlna;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.meta.RemoteService;

import com.vaguehope.morrigan.player.PlayerRegister;

public class PlayerRegisterListener implements ServiceListener {

	private static final String PLAYER_REGISTER_FILTER = "(objectclass=" + PlayerRegister.class.getName() + ")";

	private final BundleContext context;
	private final PlayerHolder playerHolder;
	private final ConcurrentMap<String, PlayerRegister> playerRegisters = new ConcurrentHashMap<String, PlayerRegister>();

	public PlayerRegisterListener (final BundleContext context, final PlayerHolder playerHolder) {
		this.context = context;
		this.playerHolder = playerHolder;
		try {
			context.addServiceListener(this, PLAYER_REGISTER_FILTER);
			for (final ServiceReference<PlayerRegister> ref : context.getServiceReferences(PlayerRegister.class, PLAYER_REGISTER_FILTER)) {
				getPlayerRegisterService(ref);
			}
		}
		catch (final InvalidSyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	private PlayerRegister getPlayerRegisterService (final ServiceReference<PlayerRegister> ref) {
		PlayerRegister pr = this.playerRegisters.get(refToKey(ref));
		if (pr != null) return pr;

		pr = this.context.getService(ref);
		final PlayerRegister prevPr = this.playerRegisters.putIfAbsent(refToKey(ref), pr);
		if (prevPr == null) return pr;
		this.context.ungetService(ref);
		return prevPr;
	}

	public void dispose () {
		this.context.removeServiceListener(this);
	}

	@Override
	public void serviceChanged (final ServiceEvent ev) {
		switch (ev.getType()) {
			case ServiceEvent.REGISTERED:
				final PlayerRegister playerRegister = getPlayerRegisterService((ServiceReference<PlayerRegister>) ev.getServiceReference());
				this.playerHolder.registerCurrentPlayers(playerRegister);
				break;
			case ServiceEvent.UNREGISTERING:
				this.playerRegisters.remove(refToKey(ev.getServiceReference()));
				break;
		}
	}

	public void addAvTransport (final RemoteDevice device, final RemoteService avTransport) {
		this.playerHolder.addAvTransport(device, avTransport, this.playerRegisters.values());
	}

	public void removeAvTransport (final RemoteDevice device) {
		this.playerHolder.removeAvTransport(device);
	}

	private static String refToKey (final ServiceReference<?> ref) {
		return ref.getBundle().getSymbolicName();
	}

}
