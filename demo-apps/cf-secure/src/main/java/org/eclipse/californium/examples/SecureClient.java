/*******************************************************************************
 * Copyright (c) 2015 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Achim Kraus (Bosch Software Innovations GmbH) - use credentials util to setup
 *                                                    DtlsConnectorConfig.Builder.
 ******************************************************************************/
package org.eclipse.californium.examples;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Level;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.examples.CredentialsUtil.Mode;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.ScandiumLogger;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;

public class SecureClient {

	static {
		ScandiumLogger.initialize();
		ScandiumLogger.setLevel(Level.FINE);
	}

	private static final String SERVER_URI = "coaps://localhost/secure";

	private final DTLSConnector dtlsConnector;

	public SecureClient(DTLSConnector dtlsConnector) {
		this.dtlsConnector = dtlsConnector;
	}

	public void test() {
		CoapResponse response = null;
		try {
			URI uri = new URI(SERVER_URI);
			CoapClient client = new CoapClient(uri);
			client.setEndpoint(new CoapEndpoint(dtlsConnector, NetworkConfig.getStandard()));
			response = client.get();
			client.shutdown();
		} catch (URISyntaxException e) {
			System.err.println("Invalid URI: " + e.getMessage());
			System.exit(-1);
		}

		if (response != null) {

			System.out.println(response.getCode());
			System.out.println(response.getOptions());
			System.out.println(response.getResponseText());

			System.out.println("\nADVANCED\n");
			System.out.println(Utils.prettyPrint(response));

		} else {
			System.out.println("No response received.");
		}
	}

	public static void main(String[] args) throws InterruptedException {
		System.out.println("Usage: java -cp ... org.eclipse.californium.examples.SecureClient [PSK] [RPK] [X509]");

		DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder();
		List<Mode> modes = CredentialsUtil.parse(args);
		if (modes.contains(CredentialsUtil.Mode.PSK)) {
			builder.setPskStore(new StaticPskStore(CredentialsUtil.PSK_IDENTITY, CredentialsUtil.PSK_SECRET));
		}
		CredentialsUtil.setupCredentials(builder, CredentialsUtil.CLIENT_NAME, modes);
		DTLSConnector dtlsConnector = new DTLSConnector(builder.build());

		SecureClient client = new SecureClient(dtlsConnector);
		client.test();
	}
}
