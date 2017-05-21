package com.vaguehope.morrigan.dlna.httpserver;

import java.net.InetAddress;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaServer {

	private static final int HTTP_PORT = 29085;
	private static final Logger LOG = LoggerFactory.getLogger(MediaServer.class);

	private final Server server;
	private final String bindAddress;

	public MediaServer (final FileLocator fileLocator, final InetAddress bindAddress) {
		if (bindAddress == null) throw new IllegalArgumentException("bindAddress must not be null.");
		this.bindAddress = bindAddress.getHostAddress();
		this.server = makeContentServer(fileLocator, this.bindAddress);
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

	public String uriForId (final String id) {
		return String.format("%s/%s", getExternalHttpUrl(), id);
	}

	private String getExternalHttpUrl () {
		return "http://" + this.bindAddress + ":" + HTTP_PORT;
	}

	private static Server makeContentServer (final FileLocator fileLocator, final String bindAddress) {
		final ServletContextHandler servletHandler = new ServletContextHandler();
		servletHandler.setContextPath("/");
		servletHandler.addServlet(new ServletHolder(new ContentServlet(fileLocator)), "/");

		final HandlerList handler = new HandlerList();
		handler.setHandlers(new Handler[] { servletHandler });

		final Server server = new Server();
		server.setHandler(handler);
		server.addConnector(createHttpConnector(bindAddress, HTTP_PORT));
		return server;
	}

	private static SelectChannelConnector createHttpConnector (final String hostAddress, final int port) {
		final SelectChannelConnector connector = new SelectChannelConnector();
		connector.setStatsOn(false);
		connector.setHost(hostAddress);
		connector.setPort(port);
		LOG.info("Creating connector: {}:{}", hostAddress, port);
		return connector;
	}

}
