package com.vaguehope.morrigan.dlna;

import org.teleal.cling.DefaultUpnpServiceConfiguration;
import org.teleal.cling.transport.impl.apache.StreamClientConfigurationImpl;
import org.teleal.cling.transport.impl.apache.StreamClientImpl;
import org.teleal.cling.transport.impl.apache.StreamServerConfigurationImpl;
import org.teleal.cling.transport.impl.apache.StreamServerImpl;
import org.teleal.cling.transport.spi.NetworkAddressFactory;
import org.teleal.cling.transport.spi.StreamClient;
import org.teleal.cling.transport.spi.StreamServer;

/**
 * http://mailinglists.945824.n3.nabble.com/Starting-cling-inside-jboss-or-glassfish-td2240086.html
 * http://4thline.org/projects/cling/core/manual/cling-core-manual.html#section.BasicAPI.UpnpService.Configuration
 */
class MyUpnpServiceConfiguration extends DefaultUpnpServiceConfiguration {

	@Override
	public StreamClient createStreamClient () {
		return new StreamClientImpl(new StreamClientConfigurationImpl());
	}

	@Override
	public StreamServer createStreamServer (final NetworkAddressFactory networkAddressFactory) {
		return new StreamServerImpl(new StreamServerConfigurationImpl(networkAddressFactory.getStreamListenPort()));
	}

}
