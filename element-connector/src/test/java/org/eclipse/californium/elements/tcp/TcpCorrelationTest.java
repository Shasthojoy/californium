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
 *    Achim Kraus (Bosch Software Innovations GmbH) - add test for sending correlation context.
 *    Achim Kraus (Bosch Software Innovations GmbH) - use import static ConnectorTestUtil.
 *    Achim Kraus (Bosch Software Innovations GmbH) - use create server address
 *                                                    (LoopbackAddress)
 *    Achim Kraus (Bosch Software Innovations GmbH) - use typed getter for correlation context
 ******************************************************************************/
package org.eclipse.californium.elements.tcp;

import static org.eclipse.californium.elements.tcp.ConnectorTestUtil.*;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.IsEmptyString.isEmptyOrNullString;
import static org.junit.Assert.assertArrayEquals;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.TcpCorrelationContext;
import org.eclipse.californium.elements.TcpCorrelationContextMatcher;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class TcpCorrelationTest {

	@Rule
	public final Timeout timeout = new Timeout(20, TimeUnit.SECONDS);

	private final List<Connector> cleanup = new ArrayList<>();

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
	 * 1. Send a request and check, if the response has the same correlation
	 *    context on the client side.
	 * 2. Send a second request and check, if this has the same correlation
	 *    context on the client side.
	 * 3. Also check, if the server response is sent with the same context
	 *    as the request was received.
	 * </pre>
	 */
	@Test
	public void testCorrelationContext() throws Exception {
		TcpServerConnector server = new TcpServerConnector(createServerAddress(0), NUMBER_OF_THREADS,
				IDLE_TIMEOUT_IN_S);
		TcpClientConnector client = new TcpClientConnector(NUMBER_OF_THREADS, CONNECTION_TIMEOUT_IN_MS,
				IDLE_TIMEOUT_IN_S);

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
		TcpCorrelationContext receivingServerContext = serverCatcher.getCorrelationContext(0,
				TcpCorrelationContext.class);
		assertThat(receivingServerContext.getConnectionId(), is(not(isEmptyOrNullString())));

		TcpCorrelationContext clientContext = clientCallback.getCorrelationContext(TcpCorrelationContext.class);
		assertThat(clientContext.getConnectionId(), is(not(isEmptyOrNullString())));

		// Response message must go over the same connection
		// the client already opened
		SimpleMessageCallback serverCallback = new SimpleMessageCallback();
		msg = createMessage(serverCatcher.getMessage(0).getInetSocketAddress(), 100, null, serverCallback);
		server.send(msg);
		clientCatcher.blockUntilSize(1);

		TcpCorrelationContext serverContext = serverCallback.getCorrelationContext(TcpCorrelationContext.class);
		assertThat(serverContext, is(receivingServerContext));
		assertThat(serverContext.getConnectionId(), is(receivingServerContext.getConnectionId()));

		// check response correlation context
		TcpCorrelationContext responseContext = clientCatcher.getCorrelationContext(0, TcpCorrelationContext.class);
		assertThat(responseContext, is(clientContext));
		assertThat(responseContext.getConnectionId(), is(clientContext.getConnectionId()));

		// send next message
		clientCallback = new SimpleMessageCallback();
		msg = createMessage(server.getAddress(), 100, null, clientCallback);

		client.send(msg);

		TcpCorrelationContext context2 = clientCallback.getCorrelationContext(TcpCorrelationContext.class,
				CONTEXT_TIMEOUT_IN_MS);
		assertThat(context2, is(clientContext));
		assertThat(context2.getConnectionId(), is(clientContext.getConnectionId()));
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
		TcpServerConnector server = new TcpServerConnector(createServerAddress(0), NUMBER_OF_THREADS,
				IDLE_TIMEOUT_RECONNECT_IN_S);
		TcpClientConnector client = new TcpClientConnector(NUMBER_OF_THREADS, CONNECTION_TIMEOUT_IN_MS,
				IDLE_TIMEOUT_RECONNECT_IN_S);

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

		TcpCorrelationContext serverContext = serverCatcher.getCorrelationContext(0, TcpCorrelationContext.class);
		TcpCorrelationContext clientContext = clientCallback.getCorrelationContext(TcpCorrelationContext.class);

		// timeout connection, hopefully this triggers a reconnect
		Thread.sleep(TimeUnit.MILLISECONDS.convert(IDLE_TIMEOUT_RECONNECT_IN_S * 2, TimeUnit.SECONDS));

		clientCallback = new SimpleMessageCallback();
		msg = createMessage(server.getAddress(), 100, null, clientCallback);

		client.send(msg);
		serverCatcher.blockUntilSize(2);

		TcpCorrelationContext clientContextAfterReconnect = clientCallback
				.getCorrelationContext(TcpCorrelationContext.class);
		// new (different) client side connection id
		assertThat(clientContextAfterReconnect, is(not(clientContext)));
		assertThat(clientContextAfterReconnect.getConnectionId(), is(not(clientContext.getConnectionId())));

		// new (different) server side connection id
		TcpCorrelationContext serverContextAfterReconnect = serverCatcher.getCorrelationContext(1,
				TcpCorrelationContext.class);
		// new (different) server side connection id
		assertThat(serverContextAfterReconnect.getConnectionId(), is(not(serverContext.getConnectionId())));
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
		TcpServerConnector server = new TcpServerConnector(createServerAddress(0), NUMBER_OF_THREADS,
				IDLE_TIMEOUT_IN_S);
		TcpClientConnector client = new TcpClientConnector(NUMBER_OF_THREADS, CONNECTION_TIMEOUT_IN_MS,
				IDLE_TIMEOUT_IN_S);

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
		TcpCorrelationContext serverContext = serverCatcher.getCorrelationContext(0, TcpCorrelationContext.class);
		TcpCorrelationContext clientContext = clientCallback.getCorrelationContext(TcpCorrelationContext.class);

		/* stop / start the server */
		server.stop();
		server.start();

		clientCallback = new SimpleMessageCallback();
		msg = createMessage(server.getAddress(), 100, null, clientCallback);

		client.send(msg);
		serverCatcher.blockUntilSize(2);

		TcpCorrelationContext clientContextAfterReconnect = clientCallback
				.getCorrelationContext(TcpCorrelationContext.class);
		// new (different) client side connection id
		assertThat(clientContextAfterReconnect, is(not(clientContext)));
		assertThat(clientContextAfterReconnect.getConnectionId(), is(not(clientContext.getConnectionId())));

		// Response message must go over the reconnected connection

		TcpCorrelationContext serverContextAfterReconnect = serverCatcher.getCorrelationContext(1,
				TcpCorrelationContext.class);
		assertThat("Serverside no TCP Correlation Context after reconnect", serverContextAfterReconnect,
				is(instanceOf(TcpCorrelationContext.class)));
		// new (different) server side connection id
		assertThat(serverContextAfterReconnect, is(not(serverContext)));
		assertThat(serverContextAfterReconnect.getConnectionId(), is(not(serverContext.getConnectionId())));
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
		TcpCorrelationContextMatcher matcher = new TcpCorrelationContextMatcher();
		TcpCorrelationContext invalidContext = new TcpCorrelationContext("n.a.");
		TcpServerConnector server = new TcpServerConnector(createServerAddress(0), NUMBER_OF_THREADS,
				IDLE_TIMEOUT_IN_S);
		TcpClientConnector client = new TcpClientConnector(NUMBER_OF_THREADS, CONNECTION_TIMEOUT_IN_MS,
				IDLE_TIMEOUT_IN_S);
		client.setCorrelationContextMatcher(matcher);

		cleanup.add(server);
		cleanup.add(client);

		Catcher serverCatcher = new Catcher();
		Catcher clientCatcher = new Catcher();
		server.setRawDataReceiver(serverCatcher);
		client.setRawDataReceiver(clientCatcher);
		server.start();
		client.start();

		SimpleMessageCallback clientCallback = new SimpleMessageCallback();
		RawData msg = createMessage(server.getAddress(), 100, invalidContext, clientCallback);

		// message context without connector context => drop
		client.send(msg);
		serverCatcher.blockUntilSize(1, 2000);
		assertThat("Serverside received unexpected message", !serverCatcher.hasMessage(0));

		// no message context without connector context => send
		clientCallback = new SimpleMessageCallback();
		msg = createMessage(server.getAddress(), 100, null, clientCallback);
		client.send(msg);
		serverCatcher.blockUntilSize(1);

		TcpCorrelationContext clientContext = clientCallback.getCorrelationContext(TcpCorrelationContext.class);
		assertThat(clientContext.getConnectionId(), is(not(isEmptyOrNullString())));

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
		TcpCorrelationContextMatcher matcher = new TcpCorrelationContextMatcher();
		TcpCorrelationContext invalidContext = new TcpCorrelationContext("n.a.");
		TcpServerConnector server = new TcpServerConnector(createServerAddress(0), NUMBER_OF_THREADS,
				IDLE_TIMEOUT_IN_S);
		TcpClientConnector client = new TcpClientConnector(NUMBER_OF_THREADS, CONNECTION_TIMEOUT_IN_MS,
				IDLE_TIMEOUT_IN_S);
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
		TcpCorrelationContext serverContext = serverCatcher.getCorrelationContext(0, TcpCorrelationContext.class);
		assertThat(serverContext.getConnectionId(), is(not(isEmptyOrNullString())));

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
	 * Test, if the correlation context is determined proper when connecting to
	 * different servers.
	 * 
	 * <pre>
	 * 1. Send a message to different servers and determine the used 
	 *    correlation context on the client side.
	 * 2. Send a second message to different servers and determine the 
	 *    correlation context used then on the client side. 
	 * 3. Compare the correlation contexts, they must be the same per server.
	 * </pre>
	 */
	@Test
	public void testSingleClientManyServersCorrelationContext() throws Exception {
		int serverCount = 3;
		Map<InetSocketAddress, Catcher> servers = new IdentityHashMap<>();
		for (int i = 0; i < serverCount; i++) {
			TcpServerConnector server = new TcpServerConnector(createServerAddress(0), NUMBER_OF_THREADS,
					IDLE_TIMEOUT_IN_S);
			cleanup.add(server);
			Catcher serverCatcher = new Catcher();
			server.setRawDataReceiver(serverCatcher);
			server.start();

			servers.put(getDestination(server.getAddress()), serverCatcher);
		}
		Set<InetSocketAddress> serverAddresses = servers.keySet();

		TcpClientConnector client = new TcpClientConnector(NUMBER_OF_THREADS, CONNECTION_TIMEOUT_IN_MS,
				IDLE_TIMEOUT_IN_S);
		cleanup.add(client);
		Catcher clientCatcher = new Catcher();
		client.setRawDataReceiver(clientCatcher);
		client.start();

		/* send messages to all servers */
		List<RawData> messages = new ArrayList<>();
		List<SimpleMessageCallback> callbacks = new ArrayList<>();
		for (InetSocketAddress address : serverAddresses) {
			SimpleMessageCallback callback = new SimpleMessageCallback();
			RawData message = createMessage(address, 100, null, callback);
			callbacks.add(callback);
			messages.add(message);
			client.send(message);
		}

		/* receive messages for all servers */
		for (RawData message : messages) {
			Catcher catcher = servers.get(message.getInetSocketAddress());
			catcher.blockUntilSize(1);
			assertArrayEquals(message.getBytes(), catcher.getMessage(0).getBytes());
		}

		/* send 2. (follow up) messages to all servers */
		List<RawData> followupMessages = new ArrayList<>();
		List<SimpleMessageCallback> followupCallbacks = new ArrayList<>();
		for (InetSocketAddress address : serverAddresses) {
			SimpleMessageCallback callback = new SimpleMessageCallback();
			RawData message = createMessage(address, 100, null, callback);
			followupCallbacks.add(callback);
			followupMessages.add(message);
			client.send(message);
		}

		/* receive 2. (follow up) messages for all servers */
		for (RawData followupMessage : followupMessages) {
			Catcher catcher = servers.get(followupMessage.getInetSocketAddress());
			catcher.blockUntilSize(2);
			assertArrayEquals(followupMessage.getBytes(), catcher.getMessage(1).getBytes());
		}

		/*
		 * check matching correlation contexts for both messages sent to all
		 * servers
		 */
		for (int index = 0; index < messages.size(); ++index) {
			TcpCorrelationContext context1 = callbacks.get(index).getCorrelationContext(TcpCorrelationContext.class);
			TcpCorrelationContext context2 = followupCallbacks.get(index)
					.getCorrelationContext(TcpCorrelationContext.class);
			// same connection id used for follow up message
			assertThat(context1, is(context2));
			assertThat(context1.getConnectionId(), is(context2.getConnectionId()));
		}
	}

}
