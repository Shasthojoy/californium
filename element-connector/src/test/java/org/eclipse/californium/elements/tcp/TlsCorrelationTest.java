/*******************************************************************************
 * Copyright (c) 2017 Bosch Software Innovations GmbH and others.
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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.text.IsEmptyString.isEmptyOrNullString;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.TlsCorrelationContext;
import org.eclipse.californium.elements.TlsCorrelationContextMatcher;
import org.eclipse.californium.elements.tcp.ConnectorTestUtil.SSLTestContext;
import org.eclipse.californium.elements.tcp.TlsServerConnector.ClientAuthMode;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class TlsCorrelationTest {

	@Rule
	public final Timeout timeout = new Timeout(10, TimeUnit.SECONDS);
	private final List<Connector> cleanup = new ArrayList<>();

	@BeforeClass
	public static void initializeSsl() throws Exception {
		ConnectorTestUtil.initializeSsl();
	}

	@After
	public void cleanup() {
		for (Connector connector : cleanup) {
			connector.stop();
		}
	}

	/**
	 * Test, if the correlation context is determined proper.
	 *
	 * <pre>
	 * 1. Send a request and check, if the client created a secure correlation context.
	 * 2. Check, if the server received the request within the same security information
	 *    in the correlation context as the client
	 * 3. Send a response and check, if the server sent it with same correlation context
	 *    as the request was received.
	 * 4. Check, if the client received the response within the same correlation context
	 *    as the request was sent.
	 * 5. Send a second request and check, if this has the same correlation
	 *    context on the client side as the first.
	 * </pre>
	 */
	@Test
	public void testCorrelationContext() throws Exception {
		TlsServerConnector server = new TlsServerConnector(serverSslContext, createServerAddress(0), NUMBER_OF_THREADS,
				IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientSslContext, NUMBER_OF_THREADS,
				CONNECTION_TIMEOUT_IN_MS, IDLE_TIMEOUT_IN_S);

		Catcher serverCatcher = new Catcher();
		Catcher clientCatcher = new Catcher();
		server.setRawDataReceiver(serverCatcher);
		client.setRawDataReceiver(clientCatcher);
		cleanup.add(server);
		cleanup.add(client);
		server.start();
		client.start();

		SimpleMessageCallback clientCallback = new SimpleMessageCallback();
		RawData msg = createMessage(server.getAddress(), 100, null, clientCallback);

		client.send(msg);
		serverCatcher.blockUntilSize(1);

		/* client context sent */
		TlsCorrelationContext clientContext = clientCallback.getCorrelationContext(TlsCorrelationContext.class);
		assertThat(clientContext.getConnectionId(), is(not(isEmptyOrNullString())));
		assertThat(clientContext.getSessionId(), is(not(isEmptyOrNullString())));
		assertThat(clientContext.getCipher(), is(not(isEmptyOrNullString())));

		/* server context received, matching client TLS context */
		TlsCorrelationContext serverContext = serverCatcher.getCorrelationContext(0, TlsCorrelationContext.class);
		assertThat(serverContext.getConnectionId(), is(not(isEmptyOrNullString())));
		assertThat(serverContext.getSessionId(), is(clientContext.getSessionId()));
		assertThat(serverContext.getCipher(), is(clientContext.getCipher()));

		// Response message must go over the same connection the client already
		// opened
		SimpleMessageCallback serverCallback = new SimpleMessageCallback();
		msg = createMessage(serverCatcher.getMessage(0).getInetSocketAddress(), 10000, null, serverCallback);
		server.send(msg);
		clientCatcher.blockUntilSize(1);

		/* server context sent, matching received context */
		TlsCorrelationContext serverResponseContext = serverCallback.getCorrelationContext(TlsCorrelationContext.class);
		assertThat(serverResponseContext.getSessionId(), is(serverContext.getSessionId()));
		assertThat(serverResponseContext.getCipher(), is(serverContext.getCipher()));
		assertThat(serverResponseContext.getConnectionId(), is(serverContext.getConnectionId()));

		/* client context received, matching sent context */
		TlsCorrelationContext tlsContext = clientCatcher.getCorrelationContext(0, TlsCorrelationContext.class);
		assertThat(tlsContext.getSessionId(), is(clientContext.getSessionId()));
		assertThat(tlsContext.getCipher(), is(clientContext.getCipher()));
		assertThat(tlsContext.getConnectionId(), is(clientContext.getConnectionId()));

		/* send second request */
		clientCallback = new SimpleMessageCallback();
		msg = createMessage(server.getAddress(), 100, null, clientCallback);
		client.send(msg);

		/* client context second sent, matching first sent context */
		tlsContext = clientCallback.getCorrelationContext(TlsCorrelationContext.class, CONTEXT_TIMEOUT_IN_MS);
		assertThat(tlsContext.getSessionId(), is(clientContext.getSessionId()));
		assertThat(tlsContext.getCipher(), is(clientContext.getCipher()));
		assertThat(tlsContext.getConnectionId(), is(clientContext.getConnectionId()));
	}

	/**
	 * Test, if the correlation context is different when reconnect after
	 * timeout.
	 *
	 * <pre>
	 * 1. Send a request and fetch the correlation context on client and
	 *    server side.
	 * 2. Wait for connection timeout.
	 * 3. Send a new request and fetch the correlation context on client and
	 *    server side. The correlation contexts must be different.
	 * </pre>
	 */
	@Test
	public void testCorrelationContextWhenReconnectAfterTimeout() throws Exception {
		TlsServerConnector server = new TlsServerConnector(serverSslContext, createServerAddress(0), NUMBER_OF_THREADS,
				IDLE_TIMEOUT_RECONNECT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientSslContext, NUMBER_OF_THREADS,
				CONNECTION_TIMEOUT_IN_MS, IDLE_TIMEOUT_RECONNECT_IN_S);

		cleanup.add(server);
		cleanup.add(client);

		Catcher serverCatcher = new Catcher();
		Catcher clientCatcher = new Catcher();
		server.setRawDataReceiver(serverCatcher);
		client.setRawDataReceiver(clientCatcher);
		server.start();
		client.start();

		SimpleMessageCallback clientCallback = new SimpleMessageCallback();
		RawData msg = createMessage(server.getAddress(), 100, null, clientCallback);

		client.send(msg);
		serverCatcher.blockUntilSize(1);

		TlsCorrelationContext serverContext = serverCatcher.getCorrelationContext(0, TlsCorrelationContext.class);
		TlsCorrelationContext clientContext = clientCallback.getCorrelationContext(TlsCorrelationContext.class);

		// timeout connection, hopefully this triggers a reconnect
		Thread.sleep(TimeUnit.MILLISECONDS.convert(IDLE_TIMEOUT_RECONNECT_IN_S * 2, TimeUnit.SECONDS));

		clientCallback = new SimpleMessageCallback();
		msg = createMessage(server.getAddress(), 100, null, clientCallback);

		client.send(msg);
		serverCatcher.blockUntilSize(2);

		TlsCorrelationContext clientContextAfterReconnect = clientCallback
				.getCorrelationContext(TlsCorrelationContext.class);
		// new (different) client side connection
		assertThat(clientContextAfterReconnect.getConnectionId(), is(not(clientContext.getConnectionId())));
		// the session may be resumed ... so the session id may be not renewed
		assertThat(clientContextAfterReconnect, is(not(clientContext)));

		TlsCorrelationContext serverContextAfterReconnect = serverCatcher.getCorrelationContext(1,
				TlsCorrelationContext.class);
		// new (different) server side connection
		assertThat(serverContextAfterReconnect.getConnectionId(), is(not(serverContext.getConnectionId())));
		// the session may be resumed ... so the session id may be not renewed
		assertThat(serverContextAfterReconnect, is(not(serverContext)));
	}

	/**
	 * Test, if the correlation context is different when reconnect after server
	 * stop/start.
	 *
	 * <pre>
	 * 1. Send a request and fetch the correlation context on client and
	 *    server side.
	 * 2. Stop/start the server.
	 * 3. Send a new request and fetch the correlation context on client and
	 *    server side. The correlation contexts must be different.
	 * </pre>
	 */
	@Test
	public void testCorrelationContextWhenReconnectAfterStopStart() throws Exception {
		TlsServerConnector server = new TlsServerConnector(serverSslContext, createServerAddress(0), NUMBER_OF_THREADS,
				IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientSslContext, NUMBER_OF_THREADS,
				CONNECTION_TIMEOUT_IN_MS, IDLE_TIMEOUT_IN_S);

		cleanup.add(server);
		cleanup.add(client);

		Catcher serverCatcher = new Catcher();
		Catcher clientCatcher = new Catcher();
		server.setRawDataReceiver(serverCatcher);
		client.setRawDataReceiver(clientCatcher);
		server.start();
		client.start();

		SimpleMessageCallback clientCallback = new SimpleMessageCallback();
		RawData msg = createMessage(server.getAddress(), 100, null, clientCallback);

		client.send(msg);
		serverCatcher.blockUntilSize(1);

		TlsCorrelationContext serverContext = serverCatcher.getCorrelationContext(0, TlsCorrelationContext.class);
		TlsCorrelationContext clientContext = clientCallback.getCorrelationContext(TlsCorrelationContext.class);

		// restart server, should trigger a reconnect
		server.stop();
		server.start();

		clientCallback = new SimpleMessageCallback();
		msg = createMessage(server.getAddress(), 100, null, clientCallback);

		client.send(msg);
		serverCatcher.blockUntilSize(2);

		TlsCorrelationContext clientContextAfterReconnect = clientCallback
				.getCorrelationContext(TlsCorrelationContext.class);
		// new (different) client side connection
		assertThat(clientContextAfterReconnect.getConnectionId(), is(not(clientContext.getConnectionId())));
		// the session may be resumed ... so the session id may be not renewed
		assertThat(clientContextAfterReconnect, is(not(clientContext)));

		TlsCorrelationContext serverContextAfterReconnect = serverCatcher.getCorrelationContext(1,
				TlsCorrelationContext.class);
		// new (different) server side connection
		assertThat(serverContextAfterReconnect.getConnectionId(), is(not(serverContext.getConnectionId())));
		// the session may be resumed ... so the session id may be not renewed
		assertThat(serverContextAfterReconnect, is(not(serverContext)));
	}

	/**
	 * Test, if the correlation context provided for sending is handled proper
	 * on the client side.
	 * 
	 * <pre>
	 * 1. Send a request with correlation context and check, that the message
	 *    is dropped (server doesn't receive a message).
	 * 2. Send a request without correlation context and check, that the
	 *    message is sent (server receives the message).
	 * 3. Send a 2. request with retrieved correlation context and check,
	 *    that the message is sent (server receives a 2. message).
	 * 4. Send a 3. request with different correlation context and check,
	 *    that the message is dropped (server doesn't receive a 3. message).
	 * 5. Send a 4. request without correlation context and check, that the
	 *    message is sent (server receives a 3. message).
	 * </pre>
	 */
	@Test
	public void testClientSendingCorrelationContext() throws Exception {
		TlsCorrelationContextMatcher matcher = new TlsCorrelationContextMatcher();
		TlsCorrelationContext invalidContext = new TlsCorrelationContext("n.a.", "n.a.", "n.a.");
		TlsServerConnector server = new TlsServerConnector(serverSslContext, createServerAddress(0), NUMBER_OF_THREADS,
				IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientSslContext, NUMBER_OF_THREADS,
				CONNECTION_TIMEOUT_IN_MS, IDLE_TIMEOUT_IN_S);

		client.setCorrelationContextMatcher(matcher);

		cleanup.add(server);
		cleanup.add(client);

		Catcher serverCatcher = new Catcher();
		Catcher clientCatcher = new Catcher();
		server.setRawDataReceiver(serverCatcher);
		client.setRawDataReceiver(clientCatcher);
		server.start();
		client.start();

		// message context without connector context => drop
		SimpleMessageCallback clientCallback = new SimpleMessageCallback();
		RawData msg = createMessage(server.getAddress(), 100, invalidContext, clientCallback);

		client.send(msg);
		serverCatcher.blockUntilSize(1, 2000);
		assertThat("Serverside received unexpected message", !serverCatcher.hasMessage(0));

		// no message context without connector context => send
		clientCallback = new SimpleMessageCallback();
		msg = createMessage(server.getAddress(), 100, null, clientCallback);
		client.send(msg);
		serverCatcher.blockUntilSize(1);

		TlsCorrelationContext clientContext = clientCallback.getCorrelationContext(TlsCorrelationContext.class);
		assertThat(clientContext.getConnectionId(), is(not(isEmptyOrNullString())));
		assertThat(clientContext.getSessionId(), is(not(isEmptyOrNullString())));
		assertThat(clientContext.getCipher(), is(not(isEmptyOrNullString())));

		// message context with matching connector context => send
		clientCallback = new SimpleMessageCallback();
		msg = createMessage(server.getAddress(), 100, clientContext, clientCallback);
		client.send(msg);
		serverCatcher.blockUntilSize(2);

		// invalid message context with connector context => drop
		clientCallback = new SimpleMessageCallback();
		msg = createMessage(server.getAddress(), 100, invalidContext, clientCallback);
		client.send(msg);

		serverCatcher.blockUntilSize(3, 2000);
		assertThat("Serverside received unexpected message", !serverCatcher.hasMessage(3));

		// no message context with connector context => send
		clientCallback = new SimpleMessageCallback();
		msg = createMessage(server.getAddress(), 100, null, clientCallback);
		client.send(msg);
		serverCatcher.blockUntilSize(3);
	}

	/**
	 * Test, if the correlation context provided for sending is handled proper
	 * on the server side.
	 *
	 * <pre>
	 * 1. Send a request without correlation context and check, that the
	 *    message is received by the server.
	 * 2. Send a response with the received correlation context, and check,
	 *    if the client receives the response.
	 * 3. Send a 2. response without a correlation context, and check,
	 *    if the client received the 2. response.
	 * 4. Send a 3. response with a different correlation context, and check,
	 *    if the client doesn't receive the 3. response.
	 * </pre>
	 */
	@Test
	public void testServerSendingCorrelationContext() throws Exception {
		TlsCorrelationContextMatcher matcher = new TlsCorrelationContextMatcher();
		TlsCorrelationContext invalidContext = new TlsCorrelationContext("n.a.", "n.a.", "n.a.");
		TlsServerConnector server = new TlsServerConnector(serverSslContext, createServerAddress(0), NUMBER_OF_THREADS,
				IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientSslContext, NUMBER_OF_THREADS,
				CONNECTION_TIMEOUT_IN_MS, IDLE_TIMEOUT_IN_S);

		server.setCorrelationContextMatcher(matcher);

		cleanup.add(server);
		cleanup.add(client);

		Catcher serverCatcher = new Catcher();
		Catcher clientCatcher = new Catcher();
		server.setRawDataReceiver(serverCatcher);
		client.setRawDataReceiver(clientCatcher);
		server.start();
		client.start();

		SimpleMessageCallback clientCallback = new SimpleMessageCallback();
		RawData msg = createMessage(server.getAddress(), 100, null, clientCallback);

		client.send(msg);
		serverCatcher.blockUntilSize(1);

		RawData receivedMsg = serverCatcher.getMessage(0);
		TlsCorrelationContext serverContext = serverCatcher.getCorrelationContext(0, TlsCorrelationContext.class);
		assertThat(serverContext.getConnectionId(), is(not(isEmptyOrNullString())));
		assertThat(serverContext.getSessionId(), is(not(isEmptyOrNullString())));
		assertThat(serverContext.getCipher(), is(not(isEmptyOrNullString())));

		SimpleMessageCallback serverCallback = new SimpleMessageCallback();
		msg = createMessage(receivedMsg.getInetSocketAddress(), 100, serverContext, serverCallback);
		server.send(msg);

		clientCatcher.blockUntilSize(1);

		serverCallback = new SimpleMessageCallback();
		msg = createMessage(receivedMsg.getInetSocketAddress(), 100, null, serverCallback);
		server.send(msg);

		clientCatcher.blockUntilSize(2);

		serverCallback = new SimpleMessageCallback();
		msg = createMessage(receivedMsg.getInetSocketAddress(), 100, invalidContext, serverCallback);
		server.send(msg);

		clientCatcher.blockUntilSize(3, 2000);
		assertThat("Clientside received unexpected message", !clientCatcher.hasMessage(3));

	}

	/**
	 * Test, if the clients principal is reported on receiving a message at
	 * server side.
	 */
	@Test
	public void testServerSideClientPrincipal() throws Exception {
		TlsServerConnector server = new TlsServerConnector(serverSslContext, ClientAuthMode.NEEDED,
				createServerAddress(0), NUMBER_OF_THREADS, IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientSslContext, NUMBER_OF_THREADS,
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
		serverCatcher.blockUntilSize(1);
		RawData receivedMessage = serverCatcher.getMessage(0);
		assertThat(receivedMessage, is(notNullValue()));
		assertThat(receivedMessage.getSenderIdentity(), is(clientSubjectDN));
	}

	/**
	 * Test, if the connection is established, when the server doesn't trust the
	 * client but don't force the client to authenticate. In that case, no
	 * principal will be available on the server side.
	 */
	@Test
	public void testServerSideDoesntTrustClientWithWantedAuthentication() throws Exception {
		/*
		 * server doesn't trust client. use different ca's for server side trust
		 */
		SSLTestContext serverContext = initializeNoTrustContext(null, SERVER_NAME, null);
		TlsServerConnector server = new TlsServerConnector(serverContext.context, ClientAuthMode.WANTED,
				createServerAddress(0), NUMBER_OF_THREADS, IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientSslContext, NUMBER_OF_THREADS,
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
		serverCatcher.blockUntilSize(1);
		RawData receivedMessage = serverCatcher.getMessage(0);
		assertThat(receivedMessage, is(notNullValue()));
		assertThat(receivedMessage.getSenderIdentity(), is(nullValue()));
		/*
		 * If issuers are supplied by the server, the client doesn't provide a
		 * certificate, if setWantClientAuth is used and no certificate is
		 * signed by a provided issuer. Therefore the connection is established.
		 */
	}

	/**
	 * Test, if the connection is refused (no message received), when the server
	 * doesn't trust the client and needs the client authentication.
	 */
	@Test
	public void testServerSideDoesntTrustClientWithNeededAuthentication() throws Exception {
		/*
		 * server doesn't trust client. use different ca's for server side trust
		 */
		SSLTestContext serverContext = initializeNoTrustContext(null, SERVER_NAME, null);
		TlsServerConnector server = new TlsServerConnector(serverContext.context, ClientAuthMode.NEEDED,
				createServerAddress(0), NUMBER_OF_THREADS, IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientSslContext, NUMBER_OF_THREADS,
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
		SSLTestContext clientContext = initializeContext(SERVER_NAME, CLIENT_NAME, null);

		TlsServerConnector server = new TlsServerConnector(serverSslContext, ClientAuthMode.NEEDED,
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
		TlsServerConnector server = new TlsServerConnector(serverSslContext, createServerAddress(0), NUMBER_OF_THREADS,
				IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientSslContext, NUMBER_OF_THREADS,
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
		serverCatcher.blockUntilSize(1);

		msg = createMessage(serverCatcher.getMessage(0).getInetSocketAddress(), 100, null, null);
		server.send(msg);
		clientCatcher.blockUntilSize(1);

		RawData receivedMessage = clientCatcher.getMessage(0);
		assertThat(receivedMessage, is(notNullValue()));
		assertThat(receivedMessage.getSenderIdentity(), is(serverSubjectDN));
	}

	/**
	 * Test, if the connection is refused (no message received), when the client
	 * doesn't trust the server.
	 */
	@Test
	public void testClientSideDoesntTrustServer() throws Exception {
		/*
		 * client doesn't trust server. use different ca's for client side trust
		 */
		SSLTestContext clientContext = initializeNoTrustContext(null, CLIENT_NAME, null);
		TlsServerConnector server = new TlsServerConnector(serverSslContext, createServerAddress(0), NUMBER_OF_THREADS,
				IDLE_TIMEOUT_IN_S);
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

		/*
		 * a client always checks the servers certificate, so the message never
		 * arrives
		 */
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
		SSLTestContext serverContext = initializeContext(CLIENT_NAME, SERVER_NAME, null);

		TlsServerConnector server = new TlsServerConnector(serverContext.context, ClientAuthMode.NEEDED,
				createServerAddress(0), NUMBER_OF_THREADS, IDLE_TIMEOUT_IN_S);
		TlsClientConnector client = new TlsClientConnector(clientSslContext, NUMBER_OF_THREADS,
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
