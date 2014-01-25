package com.vaguehope.morrigan.dlna;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;

import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.player.PlayerRegister;

public class Activator implements BundleActivator {

	private Set<Object> remotePlayers;
	private Queue<Player> players;
	private UpnpService upnpService;

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	@Override
	public void start (final BundleContext context) throws Exception {
		this.remotePlayers = new HashSet<Object>();
		this.players = new LinkedList<Player>();
		startPlayerRegisterListener(context);

		this.upnpService = new UpnpServiceImpl(new MyUpnpServiceConfiguration());
		this.upnpService.getRegistry().addListener(new DeviceWatcher());
		this.upnpService.getControlPoint().search();

		System.err.println("DLNA started.");
	}

	@Override
	public void stop (final BundleContext context) throws Exception {
		disposePlayers();
		this.upnpService.shutdown();
		System.err.println("DLNA stopped.");
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private static final String FILTER = "(objectclass=" + PlayerRegister.class.getName() + ")";

	private void startPlayerRegisterListener (final BundleContext context) {
		ServiceListener playerContainerSl = new ServiceListener() {
			@Override
			public void serviceChanged (final ServiceEvent ev) {
				switch (ev.getType()) {
					case ServiceEvent.REGISTERED:
						registerPlayers((PlayerRegister) context.getService(ev.getServiceReference()));
						break;
					case ServiceEvent.UNREGISTERING:
						break;
				}
			}
		};

		try {
			context.addServiceListener(playerContainerSl, FILTER);
			Collection<ServiceReference<PlayerRegister>> refs = context.getServiceReferences(PlayerRegister.class, FILTER);
			for (ServiceReference<PlayerRegister> ref : refs) {
				registerPlayers(context.getService(ref));
			}
		}
		catch (InvalidSyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	protected void registerPlayers (final PlayerRegister register) {
		for (Object remotePlayer : this.remotePlayers) {
//			SshPlayer player = new SshPlayer(register.nextIndex(), host, register);
//			register.register(player);
//			this.players.add(player);
		}
		System.err.println("Registered " + this.remotePlayers.size() + " players in " + register + ".");
	}

	private void disposePlayers () {
		Player p;
		while ((p = this.players.poll()) != null) {
			p.dispose();
		}
	}

}
