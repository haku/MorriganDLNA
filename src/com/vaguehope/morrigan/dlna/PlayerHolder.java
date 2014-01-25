package com.vaguehope.morrigan.dlna;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.teleal.cling.controlpoint.ControlPoint;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.meta.RemoteService;
import org.teleal.cling.model.types.UDN;

import com.vaguehope.morrigan.dlna.httpserver.MediaServer;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.player.PlayerRegister;

public class PlayerHolder {

	private final ControlPoint controlPoint;
	private final MediaServer mediaServer;
	private final ScheduledExecutorService scheduledExecutor;

	private final AtomicBoolean alive = new AtomicBoolean(true);
	private final Map<UDN, RemoteService> avTransports = new ConcurrentHashMap<UDN, RemoteService>();
	private final ConcurrentMap<UDN, Set<Player>> players = new ConcurrentHashMap<UDN, Set<Player>>();

	public PlayerHolder (final ControlPoint controlPoint, final MediaServer mediaServer, final ScheduledExecutorService scheduledExecutor) {
		this.controlPoint = controlPoint;
		this.mediaServer = mediaServer;
		this.scheduledExecutor = scheduledExecutor;
	}

	public void addAvTransport (final RemoteDevice device, final RemoteService avTransport, final Collection<PlayerRegister> playerRegisters) {
		checkAlive();
		final UDN udn = device.getIdentity().getUdn();
		this.avTransports.put(udn, avTransport);
		for (final PlayerRegister playerRegister : playerRegisters) {
			registerAvTransport(udn, playerRegister, avTransport);
		}
	}

	public void removeAvTransport (final RemoteDevice device) {
		checkAlive();
		final UDN udn = device.getIdentity().getUdn();
		this.avTransports.remove(udn);
		final Set<Player> playersFor = this.players.remove(udn);
		if (playersFor != null) {
			for (final Player player : playersFor) {
				player.dispose();
			}
		}
	}

	public void dispose () {
		if (this.alive.compareAndSet(true, false)) {
			for (final Set<Player> playersFor : this.players.values()) {
				for (final Player player : playersFor) {
					player.dispose();
				}
			}
			this.players.clear();
			this.avTransports.clear();
		}
	}

	private void checkAlive () {
		if (!this.alive.get()) throw new IllegalStateException();
	}

	public void registerCurrentPlayers (final PlayerRegister register) {
		checkAlive();
		final Collection<Entry<UDN, RemoteService>> avTs = this.avTransports.entrySet();
		for (final Entry<UDN, RemoteService> avT : avTs) {
			registerAvTransport(avT.getKey(), register, avT.getValue());
		}
		System.err.println("Registered " + avTs.size() + " players in " + register + ".");
	}

	private void registerAvTransport (final UDN udn, final PlayerRegister register, final RemoteService avTransport) {
		final DlnaPlayer player = new DlnaPlayer(register.nextIndex(), register, this.controlPoint, avTransport, this.mediaServer, this.scheduledExecutor);
		register.register(player);

		Set<Player> playersFor = this.players.get(udn);
		if (playersFor == null) this.players.putIfAbsent(udn, new HashSet<Player>());
		playersFor = this.players.get(udn);
		if (playersFor == null) throw new IllegalStateException();
		playersFor.add(player);

		System.err.println("Registered player " + player + " for udn=" + udn + " in " + register + ".");
	}

}
