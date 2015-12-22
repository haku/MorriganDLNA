package com.vaguehope.morrigan.dlna;

import org.fourthline.cling.DefaultUpnpServiceConfiguration;
import org.fourthline.cling.transport.impl.apache.StreamClientConfigurationImpl;
import org.fourthline.cling.transport.impl.apache.StreamClientImpl;
import org.fourthline.cling.transport.impl.apache.StreamServerConfigurationImpl;
import org.fourthline.cling.transport.impl.apache.StreamServerImpl;
import org.fourthline.cling.transport.spi.NetworkAddressFactory;
import org.fourthline.cling.transport.spi.StreamClient;
import org.fourthline.cling.transport.spi.StreamServer;

/**
 * Note '.apache.' in the above imports.
 * http://mailinglists.945824.n3.nabble.com/Starting-cling-inside-jboss-or-glassfish-td2240086.html
 * http://4thline.org/projects/cling/core/manual/cling-core-manual.html#section.BasicAPI.UpnpService.Configuration
 */
class MyUpnpServiceConfiguration extends DefaultUpnpServiceConfiguration {

	@Override
	public StreamClient createStreamClient () {
		return new StreamClientImpl(new StreamClientConfigurationImpl(getSyncProtocolExecutorService()));
	}

	@Override
	public StreamServer createStreamServer (final NetworkAddressFactory networkAddressFactory) {
		return new StreamServerImpl(new StreamServerConfigurationImpl(networkAddressFactory.getStreamListenPort()));
	}

}
