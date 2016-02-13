package com.vaguehope.morrigan.dlna.players;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.types.UDN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.dlna.content.MediaFileLocator;
import com.vaguehope.morrigan.dlna.httpserver.MediaServer;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.player.PlayerRegister;
import com.vaguehope.morrigan.player.PlayerStateStorage;

public class PlayerHolder {

	private static final Logger LOG = LoggerFactory.getLogger(PlayerHolder.class);

	private final ControlPoint controlPoint;
	private final MediaServer mediaServer;
	private final MediaFileLocator mediaFileLocator;
	private final PlayerStateStorage stateStorage;
	private final ScheduledExecutorService scheduledExecutor;

	private final AtomicBoolean alive = new AtomicBoolean(true);
	private final Map<UDN, RemoteService> avTransports = new ConcurrentHashMap<UDN, RemoteService>();
	private final ConcurrentMap<UDN, Set<DlnaPlayer>> players = new ConcurrentHashMap<UDN, Set<DlnaPlayer>>();
	private final Map<String, PlayerState> backedupPlayerState = new ConcurrentHashMap<String, PlayerState>();


	public PlayerHolder (final ControlPoint controlPoint,
			final MediaServer mediaServer, final MediaFileLocator mediaFileLocator,
			final PlayerStateStorage playerStateStorage, final ScheduledExecutorService scheduledExecutor) {
		this.controlPoint = controlPoint;
		this.mediaServer = mediaServer;
		this.mediaFileLocator = mediaFileLocator;
		this.stateStorage = playerStateStorage;
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
		final Set<DlnaPlayer> playersFor = this.players.remove(udn);
		if (playersFor != null) {
			for (final DlnaPlayer player : playersFor) {
				final PlayerState backupState = player.backupState();
				this.backedupPlayerState.put(player.getUid(), backupState);
				LOG.info("Backed up {}: {}.", player.getUid(), backupState);
				player.dispose();
			}
		}
	}

	public void dispose () {
		if (this.alive.compareAndSet(true, false)) {
			for (final Set<DlnaPlayer> playersFor : this.players.values()) {
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
		LOG.info("Registered {} players in {}.", avTs.size(), register);
	}

	private void registerAvTransport (final UDN udn, final PlayerRegister register, final RemoteService avTransport) {
		final DlnaPlayer player = new DlnaPlayer(register,
				this.controlPoint, avTransport, this.mediaServer, this.mediaFileLocator,
				this.scheduledExecutor);

		final PlayerState previousState = this.backedupPlayerState.get(DlnaPlayer.remoteServiceUid(avTransport));
		if (previousState != null) {
			player.restoreBackedUpState(previousState);
		}
		else {
			this.stateStorage.readState(player);
		}

		register.register(player);

		Set<DlnaPlayer> playersFor = this.players.get(udn);
		if (playersFor == null) this.players.putIfAbsent(udn, new HashSet<DlnaPlayer>());
		playersFor = this.players.get(udn);
		if (playersFor == null) throw new IllegalStateException();
		playersFor.add(player);

		LOG.info("Registered {}: {}.", player.getUid(), player, udn, register);
	}

}
