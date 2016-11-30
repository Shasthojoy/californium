/*******************************************************************************
 * Copyright (c) 2015, 2016 Bosch Software Innovations GmbH and others.
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
 *    Achim Kraus (Bosch Software Innovations GmbH) - initial creation 
 *                                                    derived from ObserveTest
 ******************************************************************************/
package org.eclipse.californium.core.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.californium.category.Medium;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.EmptyMessage;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.interceptors.MessageInterceptor;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.tcp.TcpServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * This test tests pbserves over TCP.
 */
@Category(Medium.class)
public class TcpObserveTest {

	static final String TARGET_X = "resX";
	static final String TARGET_Y = "resY";
	static final String RESPONSE = "hi";

	private CoapServer server;
	private MyResource resourceX;
	private MyResource resourceY;

	private String uriX;
	private String uriY;

	@Before
	public void startupServer() {
		System.out.println(System.lineSeparator() + "Start " + getClass().getSimpleName());
		createServer();
	}

	@After
	public void shutdownServer() {
		server.destroy();
		System.out.println("End " + getClass().getSimpleName());
	}

	@Test
	public void testObserveClient() throws Exception {

		final AtomicInteger notificationCounter = new AtomicInteger(0);
		final CountDownLatch latch = new CountDownLatch(1);
		final CountDownLatch canceled = new CountDownLatch(1);

		server.getEndpoints().get(0).addInterceptor(new ServerMessageInterceptor(canceled));
		resourceX.setObserveType(Type.NON);

		int repeat = 3;

		CoapClient client = new CoapClient(uriX);

		CoapObserveRelation rel = client.observeAndWait(new CoapHandler() {

			@Override
			public void onLoad(CoapResponse response) {
				int counter = notificationCounter.incrementAndGet();
				System.out.println("Received " + counter + ". Notification: " + response.advanced());
				latch.countDown();
			}

			@Override
			public void onError() {
			}
		});

		assertFalse("Response not received", rel.isCanceled());

		// onLoad is called asynchronous to returning the response
		// therefore wait for onLoad
		assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));

		// only one notification (the response) received
		assertEquals(1, notificationCounter.get());

		rel.reactiveCancel();
		System.out.println(uriX + " reactive canceled change to proactive!");

		assertTrue(canceled.await(5, TimeUnit.SECONDS));

		for (int i = 0; i < repeat; ++i) {
			resourceX.changed("client");
			Thread.sleep(50);
		}

		assertEquals(0, resourceX.getObserverCount());
		// one notification and one response for cancel
		assertEquals(2, notificationCounter.get());
	}

	private void createServer() {
		// retransmit constantly all 200 milliseconds
		NetworkConfig config = new NetworkConfig().setInt(NetworkConfig.Keys.ACK_TIMEOUT, 200)
				.setFloat(NetworkConfig.Keys.ACK_RANDOM_FACTOR, 1f).setFloat(NetworkConfig.Keys.ACK_TIMEOUT_SCALE, 1f);

		TcpServerConnector connector = new TcpServerConnector(
				new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1, 100);
		CoapEndpoint endpoint = new CoapEndpoint(connector, config);

		server = new CoapServer();
		server.addEndpoint(endpoint);
		resourceX = new MyResource(TARGET_X);
		resourceY = new MyResource(TARGET_Y);
		server.add(resourceX);
		server.add(resourceY);
		server.start();

		URI uri = endpoint.getUri();
		uriX = uri.toString() + "/" + TARGET_X;
		uriY = uri.toString() + "/" + TARGET_Y;
		System.out.println("Resource: " + uriX);
		System.out.println("Resource: " + uriY);
	}

	private class ServerMessageInterceptor implements MessageInterceptor {

		private final AtomicReference<byte[]> cancelToken = new AtomicReference<byte[]>(null);
		private final CountDownLatch canceled;

		public ServerMessageInterceptor(CountDownLatch canceled) {
			this.canceled = canceled;
		}

		@Override
		public void receiveResponse(Response response) {
		}

		@Override
		public void sendRequest(Request request) {
		}

		@Override
		public void sendResponse(Response response) {
			byte[] token = cancelToken.get();
			if (null != token) {
				Integer observe = response.getOptions().getObserve();
				if (null == observe) {
					System.out.println("cancel responded!");
					canceled.countDown();
				}
			}
		}

		@Override
		public void sendEmptyMessage(EmptyMessage message) {
		}

		@Override
		public void receiveRequest(Request request) {
			Integer observe = request.getOptions().getObserve();
			if (null != observe && 1 == observe) {
				System.out.println("cancel received!");
				cancelToken.set(request.getToken());
			}
		}

		@Override
		public void receiveEmptyMessage(EmptyMessage message) {
		}
	}

	private static class MyResource extends CoapResource {

		private int counter = 0;
		private String currentLabel;
		private String currentResponse;

		public MyResource(String name) {
			super(name);
			prepareResponse();
			setObservable(true);
		}

		@Override
		public void handleGET(CoapExchange exchange) {
			Response response = new Response(ResponseCode.CONTENT);
			response.setPayload(currentResponse);
			exchange.respond(response);
		}

		@Override
		public void changed() {
			prepareResponse();
			super.changed();
		}

		public void changed(String label) {
			currentLabel = label;
			changed();
		}

		public void prepareResponse() {
			if (null == currentLabel) {
				currentResponse = String.format("\"%s says hi for the %d time\"", getName(), ++counter);
			} else {
				currentResponse = String.format("\"%s says %s for the %d time\"", getName(), currentLabel, ++counter);
			}
			System.out.println("Resource " + getName() + " changed to " + currentResponse);
		}

	}
}
