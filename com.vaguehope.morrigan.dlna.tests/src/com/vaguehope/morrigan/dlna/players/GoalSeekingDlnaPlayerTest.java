package com.vaguehope.morrigan.dlna.players;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ScheduledExecutorService;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteDeviceIdentity;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.ServiceId;
import org.fourthline.cling.model.types.ServiceType;
import org.fourthline.cling.model.types.UDN;
import org.junit.Before;
import org.junit.Test;

import com.vaguehope.morrigan.dlna.content.MediaFileLocator;
import com.vaguehope.morrigan.dlna.httpserver.MediaServer;
import com.vaguehope.morrigan.engines.playback.IPlaybackEngine.PlayState;
import com.vaguehope.morrigan.player.PlayerRegister;

public class GoalSeekingDlnaPlayerTest {

	private MediaFileLocator mediaFileLocator;
	private MediaServer mediaServer;
	private PlayerRegister playerRegister;

	private ControlPoint controlPoint;
	private RemoteService avTransportSvc;

	private ScheduledExecutorService scheduledExecutor;
	private GoalSeekingDlnaPlayer undertest;

	@Before
	public void before () throws Exception {
		this.mediaFileLocator = mock(MediaFileLocator.class);
		this.mediaServer = mock(MediaServer.class);
		this.playerRegister = mock(PlayerRegister.class);
		this.controlPoint = mock(ControlPoint.class);
		this.scheduledExecutor = mock(ScheduledExecutorService.class);
		makeTransportService();
		this.undertest = new GoalSeekingDlnaPlayer(
				this.playerRegister, this.controlPoint, this.avTransportSvc,
				this.mediaServer, this.mediaFileLocator, this.scheduledExecutor);
	}

	@Test
	public void itDoesNothingWhenThereIsNoInput () throws Exception {
		this.undertest.readEventQueue();
		assertEquals(PlayState.STOPPED, this.undertest.readStateAndSeekGoal());
	}

	private void makeTransportService () throws Exception {
		final InetAddress addr = InetAddress.getLocalHost();

		this.avTransportSvc = new RemoteService(
				ServiceType.valueOf("urn:schemas-upnp-org:service:AVTransport:1"),
				ServiceId.valueOf("urn:upnp-org:serviceId:AVTransport"),
				new URI("/dev/8bee114e-919e-1603-ffff-ffffa44fffff/svc/upnp-org/AVTransport/desc"),
				new URI("/dev/8bee114e-919e-1603-ffff-ffffa44fffff/svc/upnp-org/AVTransport/action"),
				new URI("/dev/8bee114e-919e-1603-ffff-ffffa44fffff/svc/upnp-org/AVTransport/event"));
		RemoteDeviceIdentity identity = new RemoteDeviceIdentity(
				new UDN("uuid:8bee114e-919e-1603-ffff-ffffa44fffff"),
				60,
				new URL("http://" + addr.getHostAddress() + ":12345/dev/8bee114e-919e-1603-ffff-ffffa44fffff/desc"),
				null,
				addr);
		DeviceType type = DeviceType.valueOf("urn:schemas-upnp-org:device:MediaRenderer:1");
		DeviceDetails details = new DeviceDetails("Very Friendly Name");
		new RemoteDevice(identity, type, details, this.avTransportSvc);
	}

}
