package com.vaguehope.morrigan.dlna.httpserver;

import java.io.File;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.vaguehope.morrigan.dlna.util.HashHelper;
import com.vaguehope.morrigan.dlna.util.NetHelper;

public class MediaServer {

	private static final int HTTP_PORT = 29085;

	private final Server server;
	private final Map<String, File> files = new ConcurrentHashMap<String, File>();

	public MediaServer () {
		this.server = makeContentServer(this.files);
	}

	public void start () {
		try {
			this.server.start();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public void dispose () {
		try {
			this.server.stop();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static String idForFile (final File file) {
		return HashHelper.sha1(file.getAbsolutePath()) + "-" + file.getName().replaceAll("[^a-zA-Z0-9]", "_");
	}

	public String uriForFile (final File file) {
		return uriForFile(idForFile(file), file);
	}

	public String uriForFile (final String id, final File file) {
		final String externalHttpUrl = getExternalHttpUrl();
		this.files.put(id, file);
		return externalHttpUrl + "/" + id;
	}

	private static String getExternalHttpUrl () {
		try {
			final List<InetAddress> addresses = NetHelper.getIpAddresses();
			final InetAddress address = addresses.iterator().next();
			return "http://" + address.getHostAddress() + ":" + HTTP_PORT;
		}
		catch (SocketException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Server makeContentServer (final Map<String, File> files) {
		final ServletContextHandler servletHandler = new ServletContextHandler();
		servletHandler.setContextPath("/");
		servletHandler.addServlet(new ServletHolder(new ContentServlet(files)), "/");

		final HandlerList handler = new HandlerList();
		handler.setHandlers(new Handler[] { servletHandler });

		final Server server = new Server();
		server.setHandler(handler);
		server.addConnector(createHttpConnector(HTTP_PORT));
		return server;
	}

	private static SelectChannelConnector createHttpConnector (final int port) {
		final SelectChannelConnector connector = new SelectChannelConnector();
		connector.setStatsOn(false);
		connector.setPort(port);
		return connector;
	}

}
