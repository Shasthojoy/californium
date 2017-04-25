/*******************************************************************************
 * Copyright (c) 2016 Bosch Software Innovations GmbH and others.
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
 *    Bosch Software Innovations GmbH - initial implementation. 
 ******************************************************************************/
package org.eclipse.californium.elements.tcp;

import static org.eclipse.californium.elements.tcp.ConnectorTestUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.tcp.ConnectorTestUtil.SSLTestContext;
import org.eclipse.californium.elements.tcp.TlsServerConnector.ClientAuthMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class TlsSelfSignedConnectorTest {

	@Rule
	public final Timeout timeout = new Timeout(10, TimeUnit.SECONDS);
	private final List<Connector> cleanup = new ArrayList<>();

	private SSLTestContext serverContext;
	private SSLTestContext clientContext;

	@Before
	public void initializeSsl() throws Exception {
		serverContext = initializeSelfSignedContext(null, SELF_SIGNED_SERVER_NAME, SELF_SIGNED_CLIENT_NAME_PATTERN);
		clientContext = initializeSelfSignedContext(null, SELF_SIGNED_CLIENT_NAME, SELF_SIGNED_SERVER_NAME);
	}

	@After
	public void cleanup() {
		for (Connector connector : cleanup) {
			connector.stop();
		}
	}

	/**
	 * Test, if the clients principal is reported on receiving a message at
	 * server side.
	 */
	@Test
	public void testServerSideClientPrincipal() throws Exception {

		TlsServerConnector server = new TlsServerConnector(serverContext.context, ClientAuthMode.NEEDED,
				createServerAddress(0), NUMBER_OF_THREADS, IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientContext.context, NUMBER_OF_THREADS,
				CONNECTION_TIMEOUT_IN_MS, IDLE_TIMEOUT_IN_S);

		Catcher serverCatcher = new Catcher();
		Catcher clientCatcher = new Catcher();
		server.setRawDataReceiver(serverCatcher);
		client.setRawDataReceiver(clientCatcher);
		cleanup.add(server);
		cleanup.add(client);
		server.start();
		client.start();

		RawData msg = createMessage(server.getAddress(), 100, null, null);

		client.send(msg);
		serverCatcher.blockUntilSize(1, 2000);
		assertThat(serverCatcher.hasMessage(0), is(true));
		RawData receivedMessage = serverCatcher.getMessage(0);
		assertThat(receivedMessage, is(notNullValue()));
		assertThat(receivedMessage.getSenderIdentity(), is(clientContext.subjectDN));
	}

	/**
	 * Test, if the connection is refused (no message received), when the server
	 * doesn't trust the client.
	 */
	@Test
	public void testServerSideDoesntTrustClient() throws Exception {
		/* server doesn't trust client */
		SSLTestContext serverContext = initializeSelfSignedContext(null, SELF_SIGNED_SERVER_NAME,
				SELF_SIGNED_NO_TRUST_NAME);

		TlsServerConnector server = new TlsServerConnector(serverContext.context, ClientAuthMode.NEEDED,
				createServerAddress(0), NUMBER_OF_THREADS, IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientContext.context, NUMBER_OF_THREADS,
				CONNECTION_TIMEOUT_IN_MS, IDLE_TIMEOUT_IN_S);

		Catcher serverCatcher = new Catcher();
		Catcher clientCatcher = new Catcher();
		server.setRawDataReceiver(serverCatcher);
		client.setRawDataReceiver(clientCatcher);
		cleanup.add(server);
		cleanup.add(client);
		server.start();
		client.start();

		RawData msg = createMessage(server.getAddress(), 100, null, null);

		client.send(msg);
		serverCatcher.blockUntilSize(1, 2000);
		assertThat(serverCatcher.hasMessage(0), is(false));
	}

	/**
	 * Test, if the connection is refused (no message received), when the
	 * clients certificate is broken (private key doesn't match the public key
	 * in its certificate).
	 */
	@Test
	public void testServerSideClientWithBrokenCertificate() throws Exception {
		/*
		 * create client credential using the client certificate, but wrong
		 * private key.
		 */
		SSLTestContext clientContext = initializeSelfSignedContext(SELF_SIGNED_NO_TRUST_NAME, SELF_SIGNED_CLIENT_NAME,
				SELF_SIGNED_SERVER_NAME);

		TlsServerConnector server = new TlsServerConnector(serverContext.context, ClientAuthMode.NEEDED,
				createServerAddress(0), NUMBER_OF_THREADS, IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientContext.context, NUMBER_OF_THREADS,
				CONNECTION_TIMEOUT_IN_MS, IDLE_TIMEOUT_IN_S);

		Catcher serverCatcher = new Catcher();
		Catcher clientCatcher = new Catcher();
		server.setRawDataReceiver(serverCatcher);
		client.setRawDataReceiver(clientCatcher);
		cleanup.add(server);
		cleanup.add(client);
		server.start();
		client.start();

		RawData msg = createMessage(server.getAddress(), 100, null, null);

		client.send(msg);
		serverCatcher.blockUntilSize(1, 2000);
		assertThat(serverCatcher.hasMessage(0), is(false));
	}

	/**
	 * Test, if the severs principal is reported on receiving a message at
	 * client side.
	 */
	@Test
	public void testClientSideServerPrincipal() throws Exception {
		TlsServerConnector server = new TlsServerConnector(serverContext.context, ClientAuthMode.NEEDED,
				createServerAddress(0), NUMBER_OF_THREADS, IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientContext.context, NUMBER_OF_THREADS,
				CONNECTION_TIMEOUT_IN_MS, IDLE_TIMEOUT_IN_S);

		Catcher serverCatcher = new Catcher();
		Catcher clientCatcher = new Catcher();
		server.setRawDataReceiver(serverCatcher);
		client.setRawDataReceiver(clientCatcher);
		cleanup.add(server);
		cleanup.add(client);
		server.start();
		client.start();

		RawData msg = createMessage(server.getAddress(), 100, null, null);

		client.send(msg);
		serverCatcher.blockUntilSize(1, 2000);
		assertThat(serverCatcher.hasMessage(0), is(true));

		msg = createMessage(serverCatcher.getMessage(0).getInetSocketAddress(), 100, null, null);
		server.send(msg);
		clientCatcher.blockUntilSize(1);

		RawData receivedMessage = clientCatcher.getMessage(0);
		assertThat(receivedMessage, is(notNullValue()));
		assertThat(receivedMessage.getSenderIdentity(), is(serverContext.subjectDN));
	}

	/**
	 * Test, if the connection is refused (no message received), when the client
	 * doesn't trust the server.
	 */
	@Test
	public void testClientSideDoesntTrustServer() throws Exception {
		/* client doesn't trust server */
		SSLTestContext clientContext = initializeSelfSignedContext(null, SELF_SIGNED_CLIENT_NAME,
				SELF_SIGNED_NO_TRUST_NAME);

		TlsServerConnector server = new TlsServerConnector(serverContext.context, ClientAuthMode.NEEDED,
				createServerAddress(0), NUMBER_OF_THREADS, IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientContext.context, NUMBER_OF_THREADS,
				CONNECTION_TIMEOUT_IN_MS, IDLE_TIMEOUT_IN_S);

		Catcher serverCatcher = new Catcher();
		Catcher clientCatcher = new Catcher();
		server.setRawDataReceiver(serverCatcher);
		client.setRawDataReceiver(clientCatcher);
		cleanup.add(server);
		cleanup.add(client);
		server.start();
		client.start();

		RawData msg = createMessage(server.getAddress(), 100, null, null);

		client.send(msg);
		serverCatcher.blockUntilSize(1, 2000);
		assertThat(serverCatcher.hasMessage(0), is(false));
	}

	/**
	 * Test, if the connection is refused (no message received), when the server
	 * certificate is broken (private key doesn't match the public key in its
	 * certificate).
	 */
	@Test
	public void testClientSideServerWithBrokenCertificate() throws Exception {
		/*
		 * create server credential using the server certificate, but wrong
		 * private key.
		 */
		SSLTestContext serverContext = initializeSelfSignedContext(SELF_SIGNED_NO_TRUST_NAME, SELF_SIGNED_SERVER_NAME,
				SELF_SIGNED_CLIENT_NAME_PATTERN);

		TlsServerConnector server = new TlsServerConnector(serverContext.context, ClientAuthMode.NEEDED,
				createServerAddress(0), NUMBER_OF_THREADS, IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientContext.context, NUMBER_OF_THREADS,
				CONNECTION_TIMEOUT_IN_MS, IDLE_TIMEOUT_IN_S);

		Catcher serverCatcher = new Catcher();
		Catcher clientCatcher = new Catcher();
		server.setRawDataReceiver(serverCatcher);
		client.setRawDataReceiver(clientCatcher);
		cleanup.add(server);
		cleanup.add(client);
		server.start();
		client.start();

		RawData msg = createMessage(server.getAddress(), 100, null, null);

		client.send(msg);
		serverCatcher.blockUntilSize(1, 2000);
		assertThat(serverCatcher.hasMessage(0), is(false));
	}

}
